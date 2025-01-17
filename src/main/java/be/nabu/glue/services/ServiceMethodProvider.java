/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.services;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.MethodDescription;
import be.nabu.glue.api.ParameterDescription;
import be.nabu.glue.core.api.MethodProvider;
import be.nabu.glue.impl.SimpleMethodDescription;
import be.nabu.glue.impl.SimpleParameterDescription;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.EvaluationException;
import be.nabu.libs.evaluator.api.Operation;
import be.nabu.libs.evaluator.base.BaseMethodOperation;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.services.DefinedServiceResolverFactory;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceLister;
import be.nabu.libs.services.api.DefinedServiceResolver;
import be.nabu.libs.services.api.ExecutionContextProvider;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.java.BeanInstance;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.map.MapContent;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.libs.types.properties.CollectionHandlerProviderProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.structure.Structure;

public class ServiceMethodProvider implements MethodProvider {
	
	public static final String SERVICE_CONTEXT = "serviceContext";
	
	private Logger logger = LoggerFactory.getLogger(getClass());

	private DefinedServiceResolver serviceResolver = DefinedServiceResolverFactory.getInstance().getResolver();

	private ExecutionContextProvider provider;

	private DefinedServiceLister lister;
	
	private List<MethodDescription> methods;
	
	private ServiceRunner runner;

	public ServiceMethodProvider(DefinedServiceLister lister, ExecutionContextProvider provider) {
		this(lister, provider, null);
	}
	
	public ServiceMethodProvider(DefinedServiceLister lister, ExecutionContextProvider provider, ServiceRunner runner) {
		this.lister = lister;
		this.provider = provider;
		this.runner = runner;
	}
	
	@Override
	public Operation<ExecutionContext> resolve(String name) {
		DefinedService resolve = serviceResolver.resolve(name);
		return resolve == null ? null : new GlueServiceOperation(resolve);
	}

	@Override
	public List<MethodDescription> getAvailableMethods() {
		if (methods == null) {
			synchronized(this) {
				if (methods == null) {
					methods = getMethods();
				}
			}
		}
		return methods;
	}
	
	private List<MethodDescription> getMethods() {
		List<MethodDescription> methods = new ArrayList<MethodDescription>();
		if (lister != null) {
			for (DefinedService service : lister.getServices()) {
				try {
					int index = service.getId().lastIndexOf('.');
					String namespace = index >= 0 ? service.getId().substring(0, index) : null;
					String name = index >= 0 ? service.getId().substring(index + 1) : service.getId();
					methods.add(new SimpleMethodDescription(
						namespace, 
						name, 
						null, 
						toParameters(service.getServiceInterface().getInputDefinition(), true), 
						toParameters(service.getServiceInterface().getOutputDefinition())
					));
				}
				catch (Exception e) {
					logger.error("Could not load service: " + service.getId(), e);
				}
			}
		}
		return methods;
	}
	
	public List<ParameterDescription> toParameters(ComplexType complexType) {
		return toParameters(complexType, false);
	}
	
	@SuppressWarnings("rawtypes")
	public List<ParameterDescription> toParameters(ComplexType complexType, boolean allowVarargs) {
		List<ParameterDescription> parameters = new ArrayList<ParameterDescription>();
		List<Element<?>> allChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(complexType));
		for (Element<?> child : allChildren) {
			String type = null;
			if (child.getType() instanceof DefinedType) { 
				type = ((DefinedType) child.getType()).getId();
			}
			else if (child.getType() instanceof SimpleType) {
				type = ((SimpleType) child.getType()).getInstanceClass().getName();
			}
			Value<Integer> maxOccurs = child.getProperty(MaxOccursProperty.getInstance());
			boolean isList = maxOccurs != null && maxOccurs.getValue() != 1;
			boolean varargs = allowVarargs && isList && allChildren.indexOf(child) == allChildren.size() - 1;
			SimpleParameterDescription parameter = new SimpleParameterDescription(child.getName(), null, type, varargs);
			parameter.setList(isList);
			parameters.add(parameter);
		}
		return parameters;
	}

	public class GlueServiceOperation extends BaseMethodOperation<ExecutionContext> {

		private DefinedService service;

		public GlueServiceOperation(DefinedService service) {
			this.service = service;
		}
		
		@Override
		public void finish() throws ParseException {
			// do nothing
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object evaluate(ExecutionContext context) throws EvaluationException {
			be.nabu.libs.services.api.ExecutionContext serviceContext = ScriptRuntime.getRuntime() == null ? null : (be.nabu.libs.services.api.ExecutionContext) ScriptRuntime.getRuntime().getContext().get(SERVICE_CONTEXT);
			CombinedExecutionContext combinedContext = null;
			if (serviceContext != null) {
				combinedContext = new CombinedExecutionContextImpl(context, serviceContext);
			}
			else if (context instanceof CombinedExecutionContext) {
				combinedContext = (CombinedExecutionContext) context;
			}
			else if (context instanceof be.nabu.libs.services.api.ExecutionContext) {
				combinedContext = new CombinedExecutionContextImpl(context, (be.nabu.libs.services.api.ExecutionContext) context);
			}
			else {
				combinedContext = new CombinedExecutionContextImpl(context, provider, context.getPrincipal());
			}
			int counter = 1;
			ComplexContent input = service.getServiceInterface().getInputDefinition().newInstance();
			List<Element<?>> allChildren = new ArrayList<Element<?>>(TypeUtils.getAllChildren(input.getType()));
			for (Element<?> element : allChildren) {
				// if we do not have enough parts, stop
				if (counter >= getParts().size()) {
					break;
				}
				boolean isVarargs = allChildren.indexOf(element) == allChildren.size() - 1 && element.getType().isList(element.getProperties());
				int limit = (isVarargs ? getParts().size() - counter : 1);
				// if this is the last element and it is a list, allow for varargs
				for (int j = 0; j < limit; j++) {
					Operation<ExecutionContext> argumentOperation = (Operation<ExecutionContext>) getParts().get(counter).getContent();
					Object value = argumentOperation.evaluate(context);
					if (value != null) {
						if (element.getType().isList(element.getProperties())) {
							CollectionHandlerProvider collectionHandler = ValueUtils.getValue(CollectionHandlerProviderProperty.getInstance(), element.getProperties());
							// defaults to a list
							if (collectionHandler == null) {
								collectionHandler = CollectionHandlerFactory.getInstance().getHandler().getHandler(List.class);
							}
							List<Object> original = null;
							if (value instanceof Object []) {
								original = Arrays.asList((Object[]) value);
							}
							else if (value instanceof Collection) {
								original = new ArrayList<Object>((Collection<Object>) value);
							}
							else {
								original = Arrays.asList(value);
							}
							// if we are beyond 1, we are in a varargs situation and we want to append to the existing collection
							if (j >= 1) {
								value = input.get(element.getName());
							}
							else {
								value = collectionHandler.create(null, original.size());
							}
							for (int i = 0; i < original.size(); i++) {
								if (original.get(i) == null || element.getType() instanceof BeanType && ((BeanType) element.getType()).getBeanClass().isAssignableFrom(original.get(i).getClass())) {
									collectionHandler.set(value, i + j, original.get(i));
								}
								// if we have a complex type, we may need to mask it
								else if (element.getType() instanceof ComplexType) {
									collectionHandler.set(value, i + j, cast(original.get(i), (ComplexType) element.getType()));
								}
								else {
									collectionHandler.set(value, i + j, original.get(i));
								}
							}
						}
						else if (element.getType() instanceof BeanType && ((BeanType) element.getType()).getBeanClass().isAssignableFrom(value.getClass())) {
							// do nothing
						}
						else if (element.getType() instanceof ComplexType) {
							value = cast(value, (ComplexType) element.getType());
						}
						input.set(element.getName(), value);
					}
					counter++;
				}
			}
			if (runner != null) {
				Future<ServiceResult> run = runner.run(service, combinedContext, input);
				try {
					ServiceResult result = run.get();
					if (result.getException() != null) {
						throw new EvaluationException(result.getException());
					}
					return result.getOutput();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
			else {
				try {
					ServiceRuntime serviceRuntime = new ServiceRuntime(service, combinedContext);
					if (ScriptRuntime.getRuntime() != null) {
						serviceRuntime.getContext(true).putAll(ScriptRuntime.getRuntime().getContext());
					}
					return serviceRuntime.run(input);
				}
				catch (ServiceException e) {
					throw new EvaluationException(e);
				}
			}
		}
		
		@SuppressWarnings({ "rawtypes", "unchecked" })
		private ComplexContent cast(Object object, ComplexType type) {
			if (object instanceof ExecutionContext) {
				return new MaskedContent(new MapContent(type, ((ExecutionContext) object).getPipeline()), type);
			}
			else if (object instanceof Map) {
				return new MaskedContent(new MapContent(type, (Map) object), type);
			}
			else {
				if (!(object instanceof ComplexContent)) {
					object = new BeanInstance(object);
				}
				ComplexContent cast = Structure.cast((ComplexContent) object, type);
				return cast == null ? new MaskedContent((ComplexContent) object, type) : cast;
			}
		}
	}
}

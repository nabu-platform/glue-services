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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.api.PostProcessor;
import be.nabu.glue.core.impl.methods.v2.SeriesMethods;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.evaluator.impl.VariableOperation;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.mask.MaskedContent;

public class GlueServiceInstance implements ServiceInstance {

	private GlueService service;

	GlueServiceInstance(GlueService service) {
		this.service = service;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		Map<String, Object> map = new HashMap<String, Object>();
		if (input != null) {
			// map input
			for (Element<?> element : TypeUtils.getAllChildren(input.getType())) {
				map.put(element.getName(), input.get(element.getName()));
			}
		}
		ScriptRuntime currentRuntime = ScriptRuntime.getRuntime();
		ScriptRuntime runtime = new ScriptRuntime(service.getScript(), new CombinedExecutionContextImpl(executionContext, service.getEnvironment(), service.getLabelEvaluator()), map);
		
		runtime.setFormatter(new GlueServiceFormatter(ServiceRuntime.getRuntime().getRuntimeTracker(), currentRuntime == null ? null : currentRuntime.getFormatter()));
		
		// add a post processor to automatically resolve iterables
		// the returned variables could be used outside of a glue context which makes lazy resolving sometimes impossible (depending on the type of series)
		List<PostProcessor> postProcessors = new ArrayList<PostProcessor>();
		
		// only resolve the returned values
		// otherwise we might use infinite series for internal purposes (e.g. index generators) that get resolved afterwards though they are not necessary
		// if they support outputted variables, the part that is necessary will be resolved by resolving the actual return parameters
		final List<String> returnedVariables = new ArrayList<String>();
		for (Element<?> child : TypeUtils.getAllChildren(service.getServiceInterface().getOutputDefinition())) {
			returnedVariables.add(child.getName());
		}
		
		// lists should have their own reference?
		postProcessors.add(new PostProcessor() {
			@Override
			public void postProcess(be.nabu.glue.api.ExecutionContext context) {
				for (String key : returnedVariables) {
					if (context.getPipeline().get(key) instanceof Iterable && !(context.getPipeline().get(key) instanceof Collection)) {
						context.getPipeline().put(key, SeriesMethods.resolve((Iterable<?>) context.getPipeline().get(key)));
					}	
				}
			}
		});
		runtime.setPostProcessors(postProcessors);
		
		VariableOperation.registerRoot();
		try {
			runtime.run();
		}
		finally {
			VariableOperation.unregisterRoot();
		}
		
		if (runtime.getException() != null) {
			throw new ServiceException(runtime.getException());
		}
		// map output back
		ComplexContent output = service.getServiceInterface().getOutputDefinition().newInstance();
		for (Element<?> element : TypeUtils.getAllChildren(output.getType())) {
			Object value = runtime.getExecutionContext().getPipeline().get(element.getName());
			boolean isObject = element.getType() instanceof BeanType && ((BeanType) element.getType()).getBeanClass().equals(java.lang.Object.class);
			// type mask if necessary (don't need to mask for object)
			if (value != null && element.getType() instanceof ComplexType && !isObject) {
				if (element.getType().isList(element.getProperties())) {
					CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
					if (handler == null) {
						throw new RuntimeException("No collection handler found for: " + value.getClass());
					}
					int index = 0;
					for (Object item : handler.getAsCollection(value)) {
						if (item != null) {
							if (!(item instanceof ComplexContent)) {
								Object cast = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(item);
								if (cast == null) {
									throw new RuntimeException("Could not wrap complex content around field: " + element.getName() + " (" + item + ")");
								}
								else {
									item = cast;
								}
							}
							ComplexType type = ((ComplexContent) item).getType();
							if (!type.equals(element.getType()) && TypeUtils.getUpcastPath(type, element.getType()).isEmpty()) {
								item = new MaskedContent((ComplexContent) item, (ComplexType) element.getType());
							}
						}
						output.set(element.getName() + "[" + index++ + "]", item);
					}
					continue;
				}
				else {
					if (!(value instanceof ComplexContent)) {
						value = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(value);
						if (value == null) {
							throw new RuntimeException("Could not wrap complex content around field: " + element.getName());
						}
					}
					ComplexType type = ((ComplexContent) value).getType();
					if (!type.equals(element.getType()) && TypeUtils.getUpcastPath(type, element.getType()).isEmpty()) {
						value = new MaskedContent((ComplexContent) value, (ComplexType) element.getType());
					}
				}
			}
			output.set(element.getName(), value);
		}
		return output;
	}

	@Override
	public Service getDefinition() {
		return service;
	}

}

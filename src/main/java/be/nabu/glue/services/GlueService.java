package be.nabu.glue.services;

import java.io.IOException;
import java.text.ParseException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.api.Script;
import be.nabu.glue.types.GlueTypeUtils;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.glue.utils.ScriptUtils.ExecutorFilter;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.StructureGenerator;
import be.nabu.libs.types.structure.SuperTypeProperty;

public class GlueService implements Service {

	private Script script;
	private ExecutionEnvironment environment;
	private LabelEvaluator labelEvaluator;
	private ComplexType input, output;
	private ServiceInterface implementedInterface;
	private DefinedTypeResolver typeResolver;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public GlueService(Script script, ExecutionEnvironment environment, LabelEvaluator labelEvaluator) {
		this.script = script;
		this.environment = environment;
		this.labelEvaluator = labelEvaluator;
	}
	
	@Override
	public Set<String> getReferences() {
		return null;
	}

	@Override
	public ServiceInterface getServiceInterface() {
		try {
			if (input == null) {
				synchronized(this) {
					if (input == null) {
						input = GlueTypeUtils.toType(ScriptUtils.getFullName(script), ScriptUtils.getInputs(script), new StructureGenerator(), ScriptUtils.getRoot(script.getRepository()), getTypeResolver());
						((ModifiableComplexType) input).setName("input");
						if (implementedInterface != null) {
							((ModifiableComplexType) input).setProperty(new ValueImpl<Type>(new SuperTypeProperty(), implementedInterface.getInputDefinition()));
						}
					}
				}
			}
			if (output == null) {
				synchronized(this) {
					if (output == null) {
						final boolean returnAll = script.getRoot().getContext() != null && script.getRoot().getContext().getAnnotations() != null && script.getRoot().getContext().getAnnotations().containsKey("returnAll");
						output = GlueTypeUtils.toType(ScriptUtils.getOutputs(script, new ExecutorFilter() {
							@Override
							public boolean accept(Executor executor) {
								return returnAll || (executor.getContext() != null && executor.getContext().getAnnotations() != null && executor.getContext().getAnnotations().containsKey("return"));
							}
						}), new StructureGenerator(), ScriptUtils.getRoot(script.getRepository()), getTypeResolver());
						((ModifiableComplexType) output).setName("output");
						if (implementedInterface != null) {
							((ModifiableComplexType) output).setProperty(new ValueImpl<Type>(new SuperTypeProperty(), implementedInterface.getOutputDefinition()));
						}
					}
				}
			}
			return new ServiceInterface() {
				@Override
				public ComplexType getInputDefinition() {
					return input;
				}
				@Override
				public ComplexType getOutputDefinition() {
					return output;
				}
				@Override
				public ServiceInterface getParent() {
					return implementedInterface;
				}
			};
		}
		catch (ParseException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public ServiceInstance newInstance() {
		return new GlueServiceInstance(this);
	}

	public Script getScript() {
		return script;
	}

	public ExecutionEnvironment getEnvironment() {
		return environment;
	}

	public LabelEvaluator getLabelEvaluator() {
		return labelEvaluator;
	}

	public ServiceInterface getImplementedInterface() {
		return implementedInterface;
	}

	public void setImplementedInterface(ServiceInterface implementedInterface) {
		this.implementedInterface = implementedInterface;
	}

	public DefinedTypeResolver getTypeResolver() {
		if (typeResolver == null) {
			typeResolver = new DefinedTypeResolver() {
				private DefinedTypeResolver centralTypeResolver = DefinedTypeResolverFactory.getInstance().getResolver(); 
				@Override
				
				public DefinedType resolve(String id) {
					DefinedType resolve = centralTypeResolver.resolve(id);
					if (resolve == null) {
						logger.warn("Could not resolve type '" + id + "', falling back to Object");
						resolve = centralTypeResolver.resolve(Object.class.getName());
					}
					return resolve;
				}
			};
		}
		return typeResolver;
	}

	public void setTypeResolver(DefinedTypeResolver typeResolver) {
		this.typeResolver = typeResolver;
	}
	
}

package be.nabu.glue.services;

import java.io.IOException;
import java.text.ParseException;
import java.util.Set;

import be.nabu.glue.ScriptUtils;
import be.nabu.glue.ScriptUtils.ExecutorFilter;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.api.Script;
import be.nabu.glue.types.GlueTypeUtils;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.structure.StructureGenerator;

public class GlueService implements Service {

	private Script script;
	private ExecutionEnvironment environment;
	private LabelEvaluator labelEvaluator;
	private ComplexType input, output;

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
						input = GlueTypeUtils.toType(ScriptUtils.getFullName(script), ScriptUtils.getInputs(script), new StructureGenerator(), ScriptUtils.getRoot(script.getRepository()));
						((ModifiableComplexType) input).setName("input");
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
						}), new StructureGenerator(), ScriptUtils.getRoot(script.getRepository()));
						((ModifiableComplexType) output).setName("output");
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
					return null;
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
	
}

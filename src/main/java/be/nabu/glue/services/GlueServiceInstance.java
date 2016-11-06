package be.nabu.glue.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.glue.api.PostProcessor;
import be.nabu.glue.core.impl.methods.v2.SeriesMethods;
import be.nabu.glue.utils.ScriptRuntime;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.Service;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.Element;

public class GlueServiceInstance implements ServiceInstance {

	private GlueService service;

	GlueServiceInstance(GlueService service) {
		this.service = service;
	}
	
	@Override
	public ComplexContent execute(ExecutionContext executionContext, ComplexContent input) throws ServiceException {
		Map<String, Object> map = new HashMap<String, Object>();
		if (input != null) {
			// map input
			for (Element<?> element : TypeUtils.getAllChildren(input.getType())) {
				map.put(element.getName(), input.get(element.getName()));
			}
		}
		ScriptRuntime runtime = new ScriptRuntime(service.getScript(), new CombinedExecutionContextImpl(executionContext, service.getEnvironment(), service.getLabelEvaluator()), map);
		
		// add a post processor to automatically resolve iterables
		// the returned variables could be used outside of a glue context which makes lazy resolving sometimes impossible (depending on the type of series)
		List<PostProcessor> postProcessors = new ArrayList<PostProcessor>();
		postProcessors.add(new PostProcessor() {
			@Override
			public void postProcess(be.nabu.glue.api.ExecutionContext context) {
				for (String key : context.getPipeline().keySet()) {
					if (context.getPipeline().get(key) instanceof Iterable && !(context.getPipeline().get(key) instanceof Collection)) {
						context.getPipeline().put(key, SeriesMethods.resolve((Iterable<?>) context.getPipeline().get(key)));
					}	
				}
			}
		});
		runtime.setPostProcessors(postProcessors);
		runtime.run();
		if (runtime.getException() != null) {
			throw new ServiceException(runtime.getException());
		}
		// map output back
		ComplexContent output = service.getServiceInterface().getOutputDefinition().newInstance();
		for (Element<?> element : TypeUtils.getAllChildren(output.getType())) {
			output.set(element.getName(), runtime.getExecutionContext().getPipeline().get(element.getName()));
		}
		return output;
	}

	@Override
	public Service getDefinition() {
		return service;
	}

}

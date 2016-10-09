package be.nabu.glue.services;

import java.util.HashMap;
import java.util.Map;

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

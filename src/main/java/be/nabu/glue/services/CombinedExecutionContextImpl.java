package be.nabu.glue.services;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Map;
import java.util.Set;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.impl.SimpleExecutionContext;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.services.api.ExecutionContextProvider;
import be.nabu.libs.services.api.SecurityContext;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.TransactionContext;

public class CombinedExecutionContextImpl implements CombinedExecutionContext {

	private be.nabu.libs.services.api.ExecutionContext serviceContext;
	private ExecutionContext glueContext;
	
	public CombinedExecutionContextImpl(ExecutionContext glueContext, be.nabu.libs.services.api.ExecutionContext serviceContext) {
		this.glueContext = glueContext;
		this.serviceContext = serviceContext;
	}
	
	public CombinedExecutionContextImpl(ExecutionContext glueContext, ExecutionContextProvider provider, Principal principal) {
		this.glueContext = glueContext;
		this.serviceContext = provider.newExecutionContext(principal);
	}
	
	public CombinedExecutionContextImpl(be.nabu.libs.services.api.ExecutionContext serviceContext, ExecutionEnvironment environment, LabelEvaluator labelEvaluator) {
		this.serviceContext = serviceContext;
		this.glueContext = new SimpleExecutionContext(environment, labelEvaluator, false);
	}
	
	@Override
	public SecurityContext getSecurityContext() {
		return serviceContext.getSecurityContext();
	}

	@Override
	public ServiceContext getServiceContext() {
		return serviceContext.getServiceContext();
	}

	@Override
	public TransactionContext getTransactionContext() {
		return serviceContext.getTransactionContext();
	}

	@Override
	public ExecutionEnvironment getExecutionEnvironment() {
		return glueContext.getExecutionEnvironment();
	}

	@Override
	public Map<String, Object> getPipeline() {
		return glueContext.getPipeline();
	}

	@Override
	public boolean isDebug() {
		return glueContext.isDebug();
	}

	@Override
	public boolean isTrace() {
		return glueContext.isTrace();
	}

	@Override
	public Executor getCurrent() {
		return glueContext.getCurrent();
	}

	@Override
	public void setCurrent(Executor executor) {
		glueContext.setCurrent(executor);
	}

	@Override
	public Set<String> getBreakpoints() {
		return glueContext.getBreakpoints();
	}

	@Override
	public void addBreakpoint(String... id) {
		glueContext.addBreakpoint(id);
	}

	@Override
	public void removeBreakpoint(String id) {
		glueContext.removeBreakpoint(id);
	}

	@Override
	public void removeBreakpoints() {
		glueContext.removeBreakpoints();
	}

	@Override
	public LabelEvaluator getLabelEvaluator() {
		return glueContext.getLabelEvaluator();
	}

	@Override
	public InputStream getContent(String name) throws IOException {
		return glueContext.getContent(name);
	}

	@Override
	public void setTrace(boolean trace) {
		glueContext.setTrace(trace);
	}

	@Override
	public int getBreakCount() {
		return glueContext.getBreakCount();
	}

	@Override
	public void incrementBreakCount(int breakCount) {
		glueContext.incrementBreakCount(breakCount);
	}

	@Override
	public Principal getPrincipal() {
		return getSecurityContext() == null ? null : getSecurityContext().getPrincipal();
	}

	@Override
	public MetricInstance getMetricInstance(String id) {
		return serviceContext.getMetricInstance(id);
	}

}

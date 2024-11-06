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

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import be.nabu.glue.api.ExecutionContext;
import be.nabu.glue.api.ExecutionEnvironment;
import be.nabu.glue.api.Executor;
import be.nabu.glue.api.LabelEvaluator;
import be.nabu.glue.impl.SimpleExecutionContext;
import be.nabu.libs.authentication.api.Token;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.services.api.ExecutionContextProvider;
import be.nabu.libs.services.api.FeaturedExecutionContext;
import be.nabu.libs.services.api.SecurityContext;
import be.nabu.libs.services.api.ServiceContext;
import be.nabu.libs.services.api.TransactionContext;

public class CombinedExecutionContextImpl implements CombinedExecutionContext, FeaturedExecutionContext {

	private be.nabu.libs.services.api.ExecutionContext serviceContext;
	private ExecutionContext glueContext;
	
	public CombinedExecutionContextImpl(ExecutionContext glueContext, be.nabu.libs.services.api.ExecutionContext serviceContext) {
		this.glueContext = glueContext;
		this.serviceContext = serviceContext;
	}
	
	public CombinedExecutionContextImpl(ExecutionContext glueContext, ExecutionContextProvider provider, Principal principal) {
		this.glueContext = glueContext;
		this.serviceContext = provider.newExecutionContext(principal instanceof Token ? (Token) principal : null);
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
		return getSecurityContext() == null ? null : getSecurityContext().getToken();
	}

	@Override
	public MetricInstance getMetricInstance(String id) {
		return serviceContext.getMetricInstance(id);
	}

	@Override
	public List<String> getEnabledFeatures() {
		return serviceContext instanceof FeaturedExecutionContext ? ((FeaturedExecutionContext) serviceContext).getEnabledFeatures() : new ArrayList<String>();
	}

}

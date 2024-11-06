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

import java.util.Date;
import java.util.Set;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.core.impl.methods.v2.ScriptMethods;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.api.ServiceRuntimeTracker;

public class GlueServiceFormatter implements OutputFormatter {

	private ServiceRuntimeTracker tracker;
	private OutputFormatter parent;

	public GlueServiceFormatter(ServiceRuntimeTracker tracker, OutputFormatter parent) {
		this.tracker = tracker;
		this.parent = parent;
	}
	
	@Override
	public void start(Script script) {
		if (tracker != null) {
			tracker.start(new ScriptService(script));
		}
		if (parent != null) {
			parent.start(script);
		}
	}

	@Override
	public void before(Executor executor) {
		if (tracker != null) {
			String string = executor.getContext().getAnnotations().get("step");
			if (string != null) {
				tracker.before(new ExecutorStep(executor, string));
			}
		}
		if (parent != null) {
			parent.before(executor);
		}
	}

	@Override
	public void after(Executor executor) {
		if (tracker != null) {
			String string = executor.getContext().getAnnotations().get("step");
			if (string != null) {
				tracker.before(new ExecutorStep(executor, string));
			}
		}
		if (parent != null) {
			parent.after(executor);
		}
	}

	@Override
	public void validated(GlueValidation... validations) {
		if (parent != null) {
			parent.validated(validations);
		}
	}

	@Override
	public void print(Object... messages) {
		ScriptMethods.console(messages);
		if (parent != null) {
			parent.print(messages);
		}
	}

	@Override
	public void end(Script script, Date started, Date stopped, Exception exception) {
		if (tracker != null) {
			if (exception != null) {
				tracker.error(new ScriptService(script), exception);
			}
			else {
				tracker.stop(new ScriptService(script));
			}
		}
		if (parent != null) {
			parent.end(script, started, stopped, exception);
		}
	}

	@Override
	public boolean shouldExecute(Executor executor) {
		return true;
	}
	
	public static class ExecutorStep {
		private Executor executor;
		private String name;

		public ExecutorStep(Executor executor, String name) {
			this.executor = executor;
			this.name = name;
		}

		public Executor getExecutor() {
			return executor;
		}

		public void setExecutor(Executor executor) {
			this.executor = executor;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
		@Override
		public boolean equals(Object object) {
			return object instanceof ExecutorStep && ((ExecutorStep) object).executor == executor;
		}
		@Override
		public int hashCode() {
			return executor.hashCode();
		}
	}

	public static class ScriptService implements DefinedService {

		private Script script;
		
		public ScriptService(Script script) {
			this.script = script;
		}
		@Override
		public ServiceInterface getServiceInterface() {
			return null;
		}
		@Override
		public ServiceInstance newInstance() {
			return null;
		}
		@Override
		public Set<String> getReferences() {
			return null;
		}
		@Override
		public String getId() {
			return ScriptUtils.getFullName(script);
		}
		@Override
		public boolean equals(Object object) {
			return object instanceof ScriptService && ((ScriptService) object).script == script;
		}
		@Override
		public int hashCode() {
			return script.hashCode();
		}
	}
}

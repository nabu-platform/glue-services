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

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.glue.api.Executor;
import be.nabu.glue.api.OutputFormatter;
import be.nabu.glue.api.Script;
import be.nabu.glue.api.runs.GlueValidation;
import be.nabu.glue.core.impl.methods.v2.ScriptMethods;
import be.nabu.glue.utils.ScriptUtils;
import be.nabu.libs.services.ServiceRuntime;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.TransactionContext;
import be.nabu.libs.services.api.ServiceInstance;
import be.nabu.libs.services.api.ServiceInterface;
import be.nabu.libs.services.api.ServiceRuntimeTracker;

public class GlueServiceFormatter implements OutputFormatter {

	private static final Logger logger = LoggerFactory.getLogger(GlueServiceFormatter.class);
	private ServiceRuntimeTracker tracker;
	private OutputFormatter parent;
	private Deque<TransactionScope> transactionScopes = new ArrayDeque<TransactionScope>();

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
		if (executor.getContext() != null && executor.getContext().getAnnotations().containsKey("transaction")) {
			TransactionContext transactions = ServiceRuntime.getRuntime().getExecutionContext().getTransactionContext();
			String previousId = transactions.getDefaultTransactionId();
			String transactionId = UUID.randomUUID().toString().replace("-", "");
			transactions.setDefaultTransactionId(transactionId);
			try {
				transactionScopes.push(new TransactionScope(executor, transactions, previousId, transactionId));
			}
			catch (RuntimeException e) {
				logger.error("Could not initialize Glue transaction scope", e);
				transactions.setDefaultTransactionId(previousId);
				throw e;
			}
		}
	}

	@Override
	public void after(Executor executor) {
		finishTransaction(executor, false);
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
	public void error(Executor executor, Exception exception) {
		finishTransaction(executor, true);
		if (parent != null) {
			parent.error(executor, exception);
		}
	}

	private void finishTransaction(Executor executor, boolean failed) {
		if (executor.getContext() != null && executor.getContext().getAnnotations().containsKey("transaction")
				&& (transactionScopes.isEmpty() || transactionScopes.peek().executor != executor)) {
			throw new IllegalStateException("Transaction scope is not active for Glue executor: " + executor.getId());
		}
		if (!transactionScopes.isEmpty() && transactionScopes.peek().executor == executor) {
			TransactionScope scope = transactionScopes.pop();
			try {
				if (failed) {
					scope.transactions.rollback(scope.transactionId);
				}
				else {
					scope.transactions.commit(scope.transactionId);
				}
			}
			finally {
				scope.transactions.setDefaultTransactionId(scope.previousId);
			}
		}
	}

	private static class TransactionScope {
		private final Executor executor;
		private final TransactionContext transactions;
		private final String previousId;
		private final String transactionId;

		private TransactionScope(Executor executor, TransactionContext transactions, String previousId, String transactionId) {
			this.executor = executor;
			this.transactions = transactions;
			this.previousId = previousId;
			this.transactionId = transactionId;
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

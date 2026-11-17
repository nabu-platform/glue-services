package be.nabu.glue.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import be.nabu.glue.api.MethodDescription;
import be.nabu.libs.services.api.DefinedServiceLister;

/** A lazily built description snapshot, independent of execution providers. Callers treat descriptions as read-only. */
public class ServiceMethodCatalog {
	private final DefinedServiceLister lister;
	private final AtomicLong generation = new AtomicLong();
	private final Object publication = new Object();
	private volatile List<MethodDescription> methods;

	public ServiceMethodCatalog(DefinedServiceLister lister) {
		this.lister = lister;
	}

	public void reset() {
		synchronized (publication) {
			generation.incrementAndGet();
			methods = null;
		}
	}

	public List<MethodDescription> getAvailableMethods() {
		List<MethodDescription> current = methods;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			while (methods == null) {
				long before = generation.get();
				List<MethodDescription> built = Collections.unmodifiableList(new ArrayList<MethodDescription>(ServiceMethodProvider.describeServices(lister)));
				synchronized (publication) {
					// A reset during enumeration wins, even if it completes before publication.
					if (before == generation.get()) {
						methods = built;
					}
				}
			}
			return methods;
		}
	}
}

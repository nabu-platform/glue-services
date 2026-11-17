package be.nabu.glue.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import be.nabu.glue.api.MethodDescription;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.DefinedServiceLister;

public class ServiceMethodCatalogTest {
	@Test
	public void sharedProvidersAndReset() {
		AtomicInteger calls = new AtomicInteger();
		List<DefinedService> services = new ArrayList<DefinedService>();
		DefinedServiceLister lister = () -> {
			calls.incrementAndGet();
			return services;
		};
		ServiceMethodCatalog catalog = new ServiceMethodCatalog(lister);
		ServiceMethodProvider first = new ServiceMethodProvider(catalog, null, null);
		ServiceMethodProvider second = new ServiceMethodProvider(catalog, null, null);
		List<MethodDescription> original = first.getAvailableMethods();
		assertSame(original, second.getAvailableMethods());
		assertEquals(1, calls.get());
		catalog.reset();
		assertNotSame(original, second.getAvailableMethods());
		assertEquals(2, calls.get());
	}

	@Test(timeout = 10000)
	public void resetWhileBuilding() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch proceed = new CountDownLatch(1);
		AtomicInteger calls = new AtomicInteger();
		ServiceMethodCatalog catalog = new ServiceMethodCatalog(() -> {
			if (calls.incrementAndGet() == 1) {
				entered.countDown();
				try {
					proceed.await();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e);
				}
			}
			return new ArrayList<DefinedService>();
		});
		final List<MethodDescription>[] returned = new List[1];
		Thread thread = new Thread(() -> returned[0] = catalog.getAvailableMethods());
		thread.start();
		entered.await();
		// Reset must not block behind a repository enumeration.
		catalog.reset();
		proceed.countDown();
		thread.join(5000);
		assertEquals(false, thread.isAlive());
		assertSame(returned[0], catalog.getAvailableMethods());
		assertEquals(2, calls.get());
	}
}

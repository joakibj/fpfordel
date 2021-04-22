package no.nav.foreldrepenger.fordel.web.app.tjenester;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.prometheus.client.hotspot.DefaultExports;
import no.nav.vedtak.apptjeneste.AppServiceHandler;

@ApplicationScoped
public class ApplicationServiceStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationServiceStarter.class);
    private Map<AppServiceHandler, AtomicBoolean> serviceMap = new HashMap<>();

    ApplicationServiceStarter() {
        // CDI
    }

    @Inject
    public ApplicationServiceStarter(@Any Instance<AppServiceHandler> serviceHandlers) {
        var i = serviceHandlers.iterator();
        while (i.hasNext()) {
            serviceMap.put(i.next(), new AtomicBoolean());
        }
    }

    public void startServices() {
        DefaultExports.initialize();
        serviceMap.forEach((key, value) -> {
            if (value.compareAndSet(false, true)) {
                LOGGER.info("starter service: {}", key.getClass().getSimpleName());
                key.start();
            }
        });
    }

    public void stopServices() {
        LOGGER.info("Stopper services. Antall: {}", serviceMap.size());
        var handlers = serviceMap.keySet().stream()
                .map(h -> CompletableFuture.runAsync(() -> {
                    LOGGER.info("stopper services. Antall: {}", serviceMap.size());
                    h.stop();
                })).collect(Collectors.toList());

        CompletableFuture.allOf(handlers.toArray(new CompletableFuture[handlers.size()])).join();
        /*
         * LOGGER.info("stopper services. Antall: {}", serviceMap.size()); List<Thread>
         * threadList = new ArrayList<>(); serviceMap.forEach((key, value) -> { if
         * (value.compareAndSet(true, false)) { Thread t = new Thread(key::stop);
         * t.start(); threadList.add(t); } }); while (!threadList.isEmpty()) { Thread t
         * = threadList.get(0); try { t.join(31000); threadList.remove(t); } catch
         * (InterruptedException e) { LOGGER.warn(e.getMessage()); t.interrupt(); } }
         */
    }
}

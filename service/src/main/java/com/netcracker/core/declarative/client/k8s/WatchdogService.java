package com.netcracker.core.declarative.client.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@ApplicationScoped
public class WatchdogService {

    @Inject
    Operator operator;

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "cloud.microservice.namespace")
    String namespace;

    private final AtomicReference<Instant> lastReconcileTime =
        new AtomicReference<>(Instant.now());

    private volatile Instant silenceDetectedAt = null;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private static final long SILENCE_THRESHOLD_SECONDS = 60;

    private static final long CONFIRMATION_SECONDS = 30;

    // HTTP probe to apiserver timeout
    private static final long PROBE_TIMEOUT_SECONDS = 10;

    public void recordActivity() {
        lastReconcileTime.set(Instant.now());
        silenceDetectedAt = null;
    }

    @Scheduled(every = "30s")
    void checkWatchHealth() {
        if (reconnecting.get()) {
            log.debug("[Watchdog] Reconnect in progress, skipping check");
            return;
        }

        long silenceSeconds = Instant.now().getEpochSecond()
            - lastReconcileTime.get().getEpochSecond();

        if (silenceSeconds < SILENCE_THRESHOLD_SECONDS) {
            log.debug("[Watchdog] OK — last reconcile {}s ago", silenceSeconds);
            return;
        }

        log.warn("[Watchdog] Reconciler silent for {}s — probing apiserver...", silenceSeconds);

        if (!probeApiserver()) {
            log.warn("[Watchdog] Apiserver unreachable — network issue, not a stuck watch");
            return;
        }

        if (silenceDetectedAt == null) {
            silenceDetectedAt = Instant.now();
            log.warn("[Watchdog] Apiserver OK but reconciler silent for {}s. " +
                "Will confirm in {}s before acting.", silenceSeconds, CONFIRMATION_SECONDS);
            return;
        }

        long confirmationSeconds = Instant.now().getEpochSecond()
            - silenceDetectedAt.getEpochSecond();

        if (confirmationSeconds < CONFIRMATION_SECONDS) {
            log.warn("[Watchdog] Confirming stuck watch: {}s/{}s elapsed",
                confirmationSeconds, CONFIRMATION_SECONDS);
            return;
        }

        log.error("[Watchdog] Confirmed stuck watch: apiserver OK, " +
            "reconciler silent {}s total. Forcing watch reconnect.", silenceSeconds);
        reconnectWatches();
    }

    private boolean probeApiserver() {
        try {
            CompletableFuture.runAsync(kubernetesClient::getKubernetesVersion)
                .get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("[Watchdog] Apiserver probe OK");
            return true;
        } catch (TimeoutException e) {
            log.warn("[Watchdog] Apiserver probe timeout after {}s", PROBE_TIMEOUT_SECONDS);
            return false;
        } catch (Exception e) {
            log.warn("[Watchdog] Apiserver probe failed: {}", e.getMessage());
            return false;
        }
    }

    private void reconnectWatches() {
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }
        try {
            var controllers = operator.getRegisteredControllers();
            log.info("[Watchdog] Refreshing {} controllers via namespace change", 
                controllers.size());

            controllers.forEach(rc -> {
                String name = rc.getConfiguration().getName();
                try {
                    rc.changeNamespaces(Set.of(namespace));
                    log.info("[Watchdog] Controller '{}' watch reconnected", name);
                } catch (Exception e) {
                    log.error("[Watchdog] Failed to reconnect controller '{}': {}",
                        name, e.getMessage());
                }
            });

            lastReconcileTime.set(Instant.now());
            silenceDetectedAt = null;
            log.info("[Watchdog] All watches reconnected successfully");
        } catch (Exception e) {
            log.error("[Watchdog] Reconnect failed: {}", e.getMessage(), e);
        } finally {
            reconnecting.set(false);
        }
    }
}
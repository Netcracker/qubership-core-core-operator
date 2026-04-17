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

    @ConfigProperty(name = "core.operator.watchdog.enabled", defaultValue = "false")
    boolean watchdogEnabled;

    @ConfigProperty(name = "core.operator.watchdog.silence-threshold-seconds", defaultValue = "300")
    long silenceThresholdSeconds;

    @ConfigProperty(name = "core.operator.watchdog.confirmation-seconds", defaultValue = "60")
    long confirmationSeconds;

    @ConfigProperty(name = "core.operator.watchdog.probe-timeout-seconds", defaultValue = "10")
    long probeTimeoutSeconds;

    private final AtomicReference<Instant> lastReconcileTime =
        new AtomicReference<>(Instant.now());

    private volatile Instant silenceDetectedAt = null;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public void recordActivity() {
        lastReconcileTime.set(Instant.now());
        silenceDetectedAt = null;
    }

    @Scheduled(every = "30s")
    void checkWatchHealth() {
        if (!watchdogEnabled) {
            log.debug("[Watchdog] Disabled, skipping check");
            return;
        }

        if (reconnecting.get()) {
            log.debug("[Watchdog] Reconnect in progress, skipping check");
            return;
        }

        long silenceSeconds = Instant.now().getEpochSecond()
            - lastReconcileTime.get().getEpochSecond();

        if (silenceSeconds < silenceThresholdSeconds) {
            log.debug("[Watchdog] OK — last reconcile {}s ago (threshold: {}s)",
                silenceSeconds, silenceThresholdSeconds);
            return;
        }

        log.warn("[Watchdog] Reconciler silent for {}s (threshold: {}s) — probing apiserver...",
            silenceSeconds, silenceThresholdSeconds);

        if (!probeApiserver()) {
            log.warn("[Watchdog] Apiserver unreachable — network issue, not a stuck watch. " +
                "Will retry next cycle.");
            return;
        }

        if (silenceDetectedAt == null) {
            silenceDetectedAt = Instant.now();
            log.warn("[Watchdog] Apiserver OK but reconciler silent for {}s. " +
                "Will confirm in {}s before acting.", silenceSeconds, confirmationSeconds);
            return;
        }

        long confirmationElapsed = Instant.now().getEpochSecond()
            - silenceDetectedAt.getEpochSecond();

        if (confirmationElapsed < confirmationSeconds) {
            log.warn("[Watchdog] Confirming stuck watch: {}/{}s elapsed",
                confirmationElapsed, confirmationSeconds);
            return;
        }

        log.error("[Watchdog] Confirmed stuck watch: apiserver OK, " +
            "reconciler silent {}s total. Forcing watch reconnect.", silenceSeconds);
        reconnectWatches();
    }

    private boolean probeApiserver() {
        try {
            CompletableFuture.runAsync(kubernetesClient::getKubernetesVersion)
                .get(probeTimeoutSeconds, TimeUnit.SECONDS);
            log.debug("[Watchdog] Apiserver probe OK");
            return true;
        } catch (TimeoutException e) {
            log.warn("[Watchdog] Apiserver probe timeout after {}s", probeTimeoutSeconds);
            return false;
        } catch (Exception e) {
            log.warn("[Watchdog] Apiserver probe failed: {}", e.getMessage());
            return false;
        }
    }

    private void reconnectWatches() {
        if (!reconnecting.compareAndSet(false, true)) {
            log.debug("[Watchdog] Reconnect already in progress");
            return;
        }
        try {
            var controllers = operator.getRegisteredControllers();
            log.info("[Watchdog] Refreshing {} controllers via namespace change", controllers.size());

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

            long cooldownSeconds = silenceThresholdSeconds + confirmationSeconds;
            lastReconcileTime.set(Instant.now().plusSeconds(cooldownSeconds));
            silenceDetectedAt = null;

            log.info("[Watchdog] All watches reconnected. Next check in ~{}s (cooldown)",
                cooldownSeconds);
        } catch (Exception e) {
            log.error("[Watchdog] Reconnect failed: {}", e.getMessage(), e);
        } finally {
            reconnecting.set(false);
        }
    }
}
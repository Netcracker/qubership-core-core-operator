package com.netcracker.core.declarative.client.k8s;

import com.netcracker.core.declarative.resources.mesh.Mesh;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@ApplicationScoped
public class WatchdogService {

    @Inject
    KubernetesClient kubernetesClient;

    @ConfigProperty(name = "cloud.microservice.namespace")
    String namespace;

    @ConfigProperty(name = "core.operator.watchdog.enabled", defaultValue = "false")
    boolean watchdogEnabled;

    @ConfigProperty(name = "core.operator.watchdog.confirmation-seconds", defaultValue = "60")
    long confirmationSeconds;

    @ConfigProperty(name = "core.operator.watchdog.probe-timeout-seconds", defaultValue = "10")
    long probeTimeoutSeconds;

    private final AtomicReference<String> lastReconcilerResourceVersion =
        new AtomicReference<>(null);

    private final AtomicReference<String> lastObservedResourceVersion =
        new AtomicReference<>(null);

    private volatile Instant divergenceDetectedAt = null;

    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public void recordActivity(String resourceVersion) {
        if (resourceVersion != null) {
            lastReconcilerResourceVersion.set(resourceVersion);
        }
        divergenceDetectedAt = null;
        log.debug("[Watchdog] Activity recorded, resourceVersion={}", resourceVersion);
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

        String clusterRV = fetchClusterResourceVersion();
        if (clusterRV == null) {
            log.warn("[Watchdog] Could not fetch resourceVersion — apiserver unreachable or timeout");
            return;
        }

        lastObservedResourceVersion.set(clusterRV);
        String reconcilerRV = lastReconcilerResourceVersion.get();

        log.debug("[Watchdog] clusterRV={}, reconcilerRV={}", clusterRV, reconcilerRV);

        if (reconcilerRV == null) {
            log.debug("[Watchdog] No reconciler activity yet, skipping divergence check");
            return;
        }

        if (!isNewerVersion(clusterRV, reconcilerRV)) {
            log.debug("[Watchdog] No new events in cluster (clusterRV={}, reconcilerRV={})",
                clusterRV, reconcilerRV);
            divergenceDetectedAt = null;
            return;
        }

        if (divergenceDetectedAt == null) {
            divergenceDetectedAt = Instant.now();
            log.warn("[Watchdog] ResourceVersion divergence detected: " +
                "clusterRV={} > reconcilerRV={}. Will confirm in {}s.",
                clusterRV, reconcilerRV, confirmationSeconds);
            return;
        }

        long elapsed = Instant.now().getEpochSecond() - divergenceDetectedAt.getEpochSecond();
        if (elapsed < confirmationSeconds) {
            log.warn("[Watchdog] Confirming divergence: clusterRV={} > reconcilerRV={}, " +
                "{}/{}s elapsed", clusterRV, reconcilerRV, elapsed, confirmationSeconds);
            return;
        }

        log.error("[Watchdog] Confirmed stuck watch: clusterRV={} > reconcilerRV={} " +
            "for {}s. Triggering pod restart.", clusterRV, reconcilerRV, elapsed);
        haltJvm();
    }

    private String fetchClusterResourceVersion() {
        try {
            return CompletableFuture.supplyAsync(() ->
                kubernetesClient.resources(Mesh.class)
                    .inNamespace(namespace)
                    .list()
                    .getMetadata()
                    .getResourceVersion()
            ).get(probeTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[Watchdog] ResourceVersion fetch timeout after {}s", probeTimeoutSeconds);
            return null;
        } catch (Exception e) {
            log.warn("[Watchdog] ResourceVersion fetch failed: {}", e.getMessage());
            return null;
        }
    }

    boolean isNewerVersion(String clusterRV, String reconcilerRV) {
        try {
            return Long.parseLong(clusterRV) > Long.parseLong(reconcilerRV);
        } catch (NumberFormatException e) {
            log.warn("[Watchdog] Could not parse resourceVersions as numbers: " +
                "clusterRV={}, reconcilerRV={}", clusterRV, reconcilerRV);
            return false;
        }
    }

    void haltJvm() {
        Runtime.getRuntime().halt(0);
    }
}
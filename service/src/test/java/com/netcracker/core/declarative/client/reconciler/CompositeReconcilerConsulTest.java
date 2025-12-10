package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.ConsulTestResource;
import com.netcracker.core.declarative.service.CompositeConsulUpdater;
import com.netcracker.core.declarative.service.CompositeSpec;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = ConsulTestResource.class)
public class CompositeReconcilerConsulTest {
    @Inject
    CompositeConsulUpdater compositeConsulUpdater;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private static final int SATELLITE_COUNT = 100;

    @Test
    public void registerBaselineAndSatellites() throws Exception {
        compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec("", "test-namespace", "", null));

        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < SATELLITE_COUNT; i++) {
            int idx = i;
            futures.add(executor.submit(() -> createCompositeInConsul(idx)));
        }

        assertAll(
                futures.stream()
                        .map(f -> (Executable) () -> assertTrue(f.get()))
                        .toList()
        );

        // cleanup case
        futures = new ArrayList<>();
        for (int i = 0; i < SATELLITE_COUNT; i++) {
            int idx = i;
            futures.add(executor.submit(() -> createCompositeInConsul(idx)));
        }

        assertAll(
                futures.stream()
                        .map(f -> (Executable) () -> assertTrue(f.get()))
                        .toList()
        );

        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
    }

    private boolean createCompositeInConsul(int p) {
        try {
            compositeConsulUpdater.setNamespace("test-namespace-satellite" + p);
            compositeConsulUpdater.updateCompositeStructureInConsul(new CompositeSpec("", "test-namespace-satellite" + p, "", new CompositeSpec.CompositeSpecBaseline("", "test-namespace", "")));
            Set<String> compositeMembers = compositeConsulUpdater.getCompositeMembers("test-namespace");
            return compositeMembers.contains("test-namespace");
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

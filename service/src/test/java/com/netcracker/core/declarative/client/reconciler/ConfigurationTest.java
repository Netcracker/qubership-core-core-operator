package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.core.declarative.service.*;

import java.util.List;
import java.util.Map;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurationTest {

    @Test
    void compositeConsulUpdater_consul_enabled() {
        Configuration configuration = new Configuration();
        Instance<TokenStorage> tokenStorageInstance = mock(Instance.class);
        TokenStorage tokenStorage = mock(TokenStorage.class);
        when(tokenStorageInstance.get()).thenReturn(tokenStorage);
        CompositeConsulUpdater compositeConsulUpdater = configuration.compositeConsulUpdater(
                "test-namespace",
                true,
                mock(ConsulClientFactory.class),
                tokenStorageInstance
        );
        assertInstanceOf(CompositeConsulUpdaterImpl.class, compositeConsulUpdater);
    }

    @Test
    void compositeConsulUpdater_consul_disabled() {
        Configuration configuration = new Configuration();
        CompositeConsulUpdater compositeConsulUpdater = configuration.compositeConsulUpdater(
                "test-namespace",
                false,
                null,
                null
        );
        assertInstanceOf(NoopCompositeConsulUpdaterImpl.class, compositeConsulUpdater);
    }

    @Test
    void compositeStructureUpdateNotifier() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                Map.of(MAAS_NAME, "http://maas:8080/", DBAAS_NAME, "http://dbaas:8080/"),
                List.of("maas", "dbaas"),
                1000L,
                2000L,
                new ObjectMapper()
        );
        assertEquals(2, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> DBAAS_NAME.equals(n.getXaasName())));
    }

    @Test
    void compositeStructureUpdateNotifier_empty_receivers() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                Map.of(MAAS_NAME, "http://maas:8080/", DBAAS_NAME, "http://dbaas:8080/"),
                List.of(),
                1000L,
                2000L,
                new ObjectMapper()
        );
        assertEquals(0, compositeStructureUpdateNotifiers.size());
    }

    @Test
    void compositeStructureUpdateNotifier_maas() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                Map.of(MAAS_NAME, "http://maas:8080/", DBAAS_NAME, "http://dbaas:8080/"),
                List.of("maas"),
                1000L,
                2000L,
                new ObjectMapper()
        );
        assertEquals(1, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
    }

    @Test
    void compositeStructureUpdateNotifier_case_insensitive() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                Map.of(MAAS_NAME, "http://maas:8080/", DBAAS_NAME, "http://dbaas:8080/"),
                List.of("MaAs", "DbAaS"),
                1000L,
                2000L,
                new ObjectMapper()
        );
        assertEquals(2, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> DBAAS_NAME.equals(n.getXaasName())));
    }

    @Test
    void compositeStructureUpdateNotifier_xaas_without_own_agent() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                Map.of("KeyManager", "http://key-manager:8080/"),
                List.of("keymanager"),
                1000L,
                2000L,
                new ObjectMapper()
        );
        assertEquals(1, compositeStructureUpdateNotifiers.size());
        assertEquals("KeyManager", compositeStructureUpdateNotifiers.getFirst().getXaasName());
    }
}

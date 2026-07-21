package com.netcracker.core.declarative.client.reconciler;

import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.core.declarative.service.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigurationTest {

    @Test
    void compositeConsulUpdater_consul_enabled() throws MalformedURLException {
        Configuration configuration = new Configuration();
        Instance<TokenStorage> tokenStorageInstance = mock(Instance.class);
        TokenStorage tokenStorage = mock(TokenStorage.class);
        when(tokenStorageInstance.get()).thenReturn(tokenStorage);
        CompositeConsulUpdater compositeConsulUpdater = configuration.compositeConsulUpdater(
                "test-namespace",
                URI.create("http://consul:8200/").toURL(),
                true,
                1000L,
                mock(ConsulClientFactory.class),
                tokenStorageInstance
        );
        assertInstanceOf(CompositeConsulUpdaterImpl.class, compositeConsulUpdater);
    }

    @Test
    void compositeConsulUpdater_consul_disabled() throws MalformedURLException {
        Configuration configuration = new Configuration();
        CompositeConsulUpdater compositeConsulUpdater = configuration.compositeConsulUpdater(
                "test-namespace",
                URI.create("http://consul:8200/").toURL(),
                false,
                1000L,
                null,
                null
        );
        assertInstanceOf(NoopCompositeConsulUpdaterImpl.class, compositeConsulUpdater);
    }

    @Test
    void compositeStructureUpdateNotifier() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                "http://maas:8080/",
                "http://dbaas:8080/",
                List.of("maas", "dbaas"),
                1000L,
                2000L,
                builder -> builder
        );
        assertEquals(2, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> DBAAS_NAME.equals(n.getXaasName())));
    }

    @Test
    void compositeStructureUpdateNotifier_empty_receivers() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                "http://maas:8080/",
                "http://dbaas:8080/",
                List.of(),
                1000L,
                2000L,
                builder -> builder
        );
        assertEquals(0, compositeStructureUpdateNotifiers.size());
    }

    @Test
    void compositeStructureUpdateNotifier_maas() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                "http://maas:8080/",
                "http://dbaas:8080/",
                List.of("maas"),
                1000L,
                2000L,
                builder -> builder
        );
        assertEquals(1, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
    }

    @Test
    void compositeStructureUpdateNotifier_case_insensitive() {
        Configuration configuration = new Configuration();
        List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers = configuration.compositeStructureUpdateNotifier(
                "http://maas:8080/",
                "http://dbaas:8080/",
                List.of("MaAs", "DbAaS"),
                1000L,
                2000L,
                builder -> builder
        );
        assertEquals(2, compositeStructureUpdateNotifiers.size());
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> MAAS_NAME.equals(n.getXaasName())));
        assertTrue(compositeStructureUpdateNotifiers.stream().anyMatch(n -> DBAAS_NAME.equals(n.getXaasName())));
    }
}
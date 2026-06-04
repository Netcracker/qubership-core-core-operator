package com.netcracker.core.declarative.service.composite;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeCRHolder;
import com.netcracker.core.declarative.service.composite.consul.CompositeStructureUpdateEvent;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import com.netcracker.core.declarative.service.composite.model.transformation.CompositeStructureTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompositeStructureChangeListenerTest {

    private TopologyConfigMapPublisher topologyConfigMapPublisher;
    private CompositeCRHolder compositeCRHolder;
    private Composite composite;
    private CompositeStructureChangeListener listener;

    @BeforeEach
    void setUp() {
        topologyConfigMapPublisher = mock(TopologyConfigMapPublisher.class);
        when(topologyConfigMapPublisher.publish(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        composite = mock(Composite.class);
        compositeCRHolder = mock(CompositeCRHolder.class);
        when(compositeCRHolder.get()).thenReturn(composite);

        // real transformer so produced structures are realistic
        CompositeStructureTransformer compositeStructureTransformer = new CompositeStructureTransformer();
        listener = new CompositeStructureChangeListener(
                compositeStructureTransformer,
                compositeCRHolder,
                topologyConfigMapPublisher
        );
    }

    @Test
    void publishesTransformedStructure() {
        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "baseline",
                "composite/sample/structure/ns-b/compositeRole", "satellite"
        ));

        listener.onStructureUpdated(event);

        CompositeStructure structure = capturePublishedStructure();
        assertEquals("ns-a", structure.baseline().origin());
        assertEquals(1, structure.satellites().size());
        assertEquals("ns-b", structure.satellites().get(0).origin());
    }

    @Test
    void doesNotPublishOnTransformationError() {
        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "INVALID_ROLE"
        ));

        assertDoesNotThrow(() -> listener.onStructureUpdated(event));

        verifyNoInteractions(topologyConfigMapPublisher);
    }

    @Test
    void publishesEmptyStructureForEmptyValues() {
        CompositeStructureUpdateEvent event = new CompositeStructureUpdateEvent(Collections.emptyList(), 0);

        listener.onStructureUpdated(event);

        CompositeStructure structure = capturePublishedStructure();
        assertNull(structure.baseline());
    }

    @Test
    void skipsPublishWhenCompositeIsNull() {
        when(compositeCRHolder.get()).thenReturn(null);

        CompositeStructureUpdateEvent event = createEvent(Map.of(
                "composite/sample/structure/ns-a/compositeRole", "baseline"
        ));

        assertDoesNotThrow(() -> listener.onStructureUpdated(event));

        verifyNoInteractions(topologyConfigMapPublisher);
    }

    private CompositeStructure capturePublishedStructure() {
        ArgumentCaptor<CompositeStructure> captor = ArgumentCaptor.forClass(CompositeStructure.class);
        verify(topologyConfigMapPublisher).publish(captor.capture(), eq(composite));
        return captor.getValue();
    }

    private static CompositeStructureUpdateEvent createEvent(Map<String, String> keyValues) {
        List<GetValue> values = keyValues.entrySet().stream()
                .map(entry -> {
                    GetValue gv = mock(GetValue.class);
                    when(gv.getKey()).thenReturn(entry.getKey());
                    when(gv.getDecodedValue()).thenReturn(entry.getValue());
                    return gv;
                })
                .toList();
        return new CompositeStructureUpdateEvent(values, 0);
    }
}

package com.netcracker.core.declarative.service.composite.model.transformation;

import com.netcracker.core.declarative.service.CompositeSpec;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeSpecTransformerTest {

    private final CompositeSpecTransformer transformer = new CompositeSpecTransformer();

    @Test
    void baselineCrProducesBaselineOnly() {
        // no baseline block -> this namespace IS the baseline
        CompositeStructure structure = transformer.transform(new CompositeSpec("ctrl", "origin", "peer", null));

        assertEquals(new CompositeStructure.NamespaceRoles("ctrl", "origin", "peer"), structure.baseline());
        assertNull(structure.satellites());
    }

    @Test
    void satelliteCrProducesBaselineAndSatellite() {
        CompositeStructure structure = transformer.transform(new CompositeSpec("sat-ctrl", "sat-origin", "sat-peer",
                new CompositeSpec.CompositeSpecBaseline("bl-ctrl", "bl-origin", "bl-peer")));

        assertEquals(new CompositeStructure.NamespaceRoles("bl-ctrl", "bl-origin", "bl-peer"), structure.baseline());
        assertEquals(1, structure.satellites().size());
        assertEquals(new CompositeStructure.NamespaceRoles("sat-ctrl", "sat-origin", "sat-peer"),
                structure.satellites().get(0));
    }

    @Test
    void omitsBlankRoles() {
        CompositeStructure structure = transformer.transform(new CompositeSpec("  ", "origin", "", null));

        assertEquals("origin", structure.baseline().origin());
        assertNull(structure.baseline().controller());
        assertNull(structure.baseline().peer());
    }
}

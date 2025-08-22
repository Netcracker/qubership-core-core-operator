package com.netcracker.core.declarative.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositeSpecTest {

    @Test
    void validate() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeSpec("C", "O", "", null).validate());
        assertThrows(IllegalArgumentException.class, () -> new CompositeSpec(null, "O", "P", null).validate());
        assertThrows(IllegalArgumentException.class, () -> new CompositeSpec(null, "O", "",
                new CompositeSpec.CompositeSpecBaseline("BC", "BO", "")).validate());
        assertThrows(IllegalArgumentException.class, () -> new CompositeSpec(null, "O", "",
                new CompositeSpec.CompositeSpecBaseline(null, "BO", "BP")).validate());
    }

    @Test
    void getCompositeId() {
        assertEquals("O", new CompositeSpec(null, "O", "", null).getCompositeId());
    }

    @Test
    void getCompositeIdForBaseline() {
        assertEquals("BO", new CompositeSpec(null, "O", "",
                new CompositeSpec.CompositeSpecBaseline("BC", "BO", "BP")).getCompositeId());
    }

    @Test
    void isBaseline() {
        assertTrue(new CompositeSpec(null, "O", "", null).isBaseline());
        assertTrue(new CompositeSpec(null, "O", "", new CompositeSpec.CompositeSpecBaseline("", "", "")).isBaseline());
    }

    @Test
    void getCompositeIdForIncompleteSpec() {
        CompositeSpec c = new CompositeSpec(null, "", "", null);
        assertThrows(IllegalArgumentException.class, c::getCompositeId);
    }
}
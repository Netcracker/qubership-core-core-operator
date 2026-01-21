package com.netcracker.core.declarative.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TenantServiceTest {

    @Test
    void getCompositeIdForMember() {
        List<String> composite = List.of(
                "composite/first/structure/first",
                "composite/first/structure/first/",
                "composite/first/structure/second",
                "composite/first/structure/second/"
        );
        assertEquals(Optional.of("first"), TenantService.getCompositeIdForMember(composite, "first"));
        assertEquals(Optional.of("first"), TenantService.getCompositeIdForMember(composite, "second"));
        assertEquals(Optional.empty(), TenantService.getCompositeIdForMember(composite, "wrong"));
    }

    @Test
    void getCompositeIdForMember_InvalidFormat() {
        List<String> composite = List.of(
                "composite/first",
                "composite/first/",
                "composite/first/structure",
                "composite/first/structure/"
        );
        assertEquals(Optional.empty(), TenantService.getCompositeIdForMember(composite, "wrong"));
    }
}
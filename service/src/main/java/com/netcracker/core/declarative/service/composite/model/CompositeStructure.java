package com.netcracker.core.declarative.service.composite.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompositeStructure(NamespaceRoles baseline, List<NamespaceRoles> satellites) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NamespaceRoles(String controller, String origin, String peer) {
    }
}

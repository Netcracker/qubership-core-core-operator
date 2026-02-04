package com.netcracker.core.declarative.service.composite.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CompositeStructureConfigMapPayload(
        String cloudProvider,
        CompositeStructure composite
) {
}

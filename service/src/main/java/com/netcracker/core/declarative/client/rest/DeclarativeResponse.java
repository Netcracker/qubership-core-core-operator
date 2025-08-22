package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeclarativeResponse {
    @JsonProperty
    ProcessStatus status;
    @JsonProperty
    String trackingId;
    @JsonProperty
    List<Condition> conditions;
}
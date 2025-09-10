package com.netcracker.core.declarative.client.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Builder
@Getter
@Setter
public class DeclarativeRequest {
    String apiVersion;
    String kind;
    String subKind;
    Object spec;
    Map<String, Object> metadata;
}

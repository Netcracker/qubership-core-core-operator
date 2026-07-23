package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;

public class QuarkusDeclarativeClient implements DeclarativeClient {

    private final DeclarativeRestClient api;
    private final ObjectMapper objectMapper;

    public QuarkusDeclarativeClient(DeclarativeRestClient api, ObjectMapper objectMapper) {
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeclarativeApiResponse apply(String apiVersion, DeclarativeRequest request) {
        return toApiResponse(api.apply(apiVersion, request));
    }

    @Override
    public DeclarativeApiResponse getStatus(String apiVersion, String trackingId) {
        return toApiResponse(api.getStatus(apiVersion, trackingId));
    }

    private DeclarativeApiResponse toApiResponse(Response response) {
        try (response) {
            byte[] bytes = response.hasEntity() ? response.readEntity(byte[].class) : null;
            return new DeclarativeApiResponse(response.getStatus(), bytes, objectMapper);
        }
    }
}

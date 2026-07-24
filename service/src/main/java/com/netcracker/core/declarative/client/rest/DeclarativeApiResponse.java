package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ProcessingException;
import lombok.Getter;

public class DeclarativeApiResponse {

    @Getter
    private final int statusCode;
    private final byte[] body;
    private final ObjectMapper objectMapper;

    public DeclarativeApiResponse(int statusCode, byte[] body, ObjectMapper objectMapper) {
        this.statusCode = statusCode;
        this.body = body;
        this.objectMapper = objectMapper;
    }

    public <T> T readEntity(Class<T> type) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (Exception e) {
            throw new ProcessingException("Failed to deserialize response body as " + type.getSimpleName(), e);
        }
    }

    public static DeclarativeApiResponse of(int statusCode, Object entity, ObjectMapper objectMapper) {
        try {
            byte[] body = entity != null ? objectMapper.writeValueAsBytes(entity) : null;
            return new DeclarativeApiResponse(statusCode, body, objectMapper);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize test entity", e);
        }
    }
}

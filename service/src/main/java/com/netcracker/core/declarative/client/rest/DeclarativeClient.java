package com.netcracker.core.declarative.client.rest;

import jakarta.ws.rs.core.Response;

public interface DeclarativeClient {
    Response apply(String apiVersion, DeclarativeRequest request);
    Response getStatus(String apiVersion, String trackingId);
}

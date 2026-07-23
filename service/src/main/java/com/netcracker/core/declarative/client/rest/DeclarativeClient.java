package com.netcracker.core.declarative.client.rest;

public interface DeclarativeClient {
    DeclarativeApiResponse apply(String apiVersion, DeclarativeRequest request);
    DeclarativeApiResponse getStatus(String apiVersion, String trackingId);
}

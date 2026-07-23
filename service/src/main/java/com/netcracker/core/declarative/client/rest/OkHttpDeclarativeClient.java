package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ProcessingException;
import okhttp3.*;

import java.io.IOException;

public class OkHttpDeclarativeClient implements DeclarativeClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final HttpUrl baseUrl;
    private final ObjectMapper objectMapper;

    public OkHttpDeclarativeClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.baseUrl = HttpUrl.parse(baseUrl);
        if (this.baseUrl == null) {
            throw new IllegalArgumentException("Invalid base URL for declarative client: " + baseUrl);
        }
        this.objectMapper = objectMapper;
    }

    @Override
    public DeclarativeApiResponse apply(String apiVersion, DeclarativeRequest request) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("declarations")
                .addPathSegment("v" + apiVersion)
                .addPathSegment("apply")
                .build();
        try {
            String json = objectMapper.writeValueAsString(request);
            RequestBody body = RequestBody.create(JSON, json);
            Request httpRequest = new Request.Builder().url(url).post(body).build();
            return execute(httpRequest);
        } catch (IOException e) {
            throw new ProcessingException("Failed to serialize request for apply endpoint", e);
        }
    }

    @Override
    public DeclarativeApiResponse getStatus(String apiVersion, String trackingId) {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("declarations")
                .addPathSegment("v" + apiVersion)
                .addPathSegment("operation")
                .addPathSegment(trackingId)
                .addPathSegment("status")
                .build();
        Request httpRequest = new Request.Builder().url(url).get().build();
        return execute(httpRequest);
    }

    private DeclarativeApiResponse execute(Request httpRequest) {
        try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
            byte[] bytes = httpResponse.body() != null ? httpResponse.body().bytes() : null;
            return new DeclarativeApiResponse(httpResponse.code(), bytes, objectMapper);
        } catch (IOException e) {
            throw new ProcessingException("Failed to execute HTTP request to " + httpRequest.url(), e);
        }
    }
}

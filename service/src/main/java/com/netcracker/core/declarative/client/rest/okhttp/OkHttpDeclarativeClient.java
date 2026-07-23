package com.netcracker.core.declarative.client.rest.okhttp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
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
    public Response apply(String apiVersion, DeclarativeRequest request) {
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
    public Response getStatus(String apiVersion, String trackingId) {
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

    private Response execute(Request httpRequest) {
        try (okhttp3.Response httpResponse = httpClient.newCall(httpRequest).execute()) {
            byte[] entityBytes = httpResponse.body() != null ? httpResponse.body().bytes() : null;
            MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
            httpResponse.headers().names().forEach(name ->
                    httpResponse.headers(name).forEach(value -> headers.add(name, value)));
            return new BufferedJsonResponse(httpResponse.code(), httpResponse.message(), headers, entityBytes, objectMapper);
        } catch (IOException e) {
            throw new ProcessingException("Failed to execute HTTP request to " + httpRequest.url(), e);
        }
    }
}

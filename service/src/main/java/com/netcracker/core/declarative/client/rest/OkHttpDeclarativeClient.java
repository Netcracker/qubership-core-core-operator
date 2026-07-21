package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.MDC;

import java.io.IOException;

import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;

public class OkHttpDeclarativeClient implements DeclarativeClient {
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OkHttpDeclarativeClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
    }

    @Override
    public Response apply(String apiVersion, DeclarativeRequest declarativeRequest) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(declarativeRequest);
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/declarations/v" + apiVersion + "/apply")
                    .header(X_REQUEST_ID, requestId())
                    .post(RequestBody.create(body, JSON))
                    .build();
            return execute(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call declarative apply", e);
        }
    }

    @Override
    public Response getStatus(String apiVersion, String trackingId) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/declarations/v" + apiVersion + "/operation/" + trackingId + "/status")
                    .header(X_REQUEST_ID, requestId())
                    .get()
                    .build();
            return execute(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to call declarative getStatus", e);
        }
    }

    private Response execute(Request request) throws IOException {
        try (okhttp3.Response okResponse = httpClient.newCall(request).execute()) {
            byte[] bodyBytes = okResponse.body() != null ? okResponse.body().bytes() : new byte[0];
            Object entity = null;
            if (bodyBytes.length > 0) {
                try {
                    entity = objectMapper.readValue(bodyBytes, DeclarativeResponse.class);
                } catch (Exception e) {
                    entity = new String(bodyBytes);
                }
            }
            return Response.status(okResponse.code()).entity(entity).build();
        }
    }

    private static String requestId() {
        String id = MDC.get(X_REQUEST_ID);
        return id != null ? id : "";
    }
}

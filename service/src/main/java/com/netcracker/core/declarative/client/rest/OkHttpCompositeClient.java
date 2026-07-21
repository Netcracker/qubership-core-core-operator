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

public class OkHttpCompositeClient implements CompositeClient {
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public OkHttpCompositeClient(OkHttpClient httpClient, String baseUrl, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.objectMapper = objectMapper;
    }

    @Override
    public Response structures(CompositeClient.Request compositeRequest) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(compositeRequest);
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/composite/v1/structures")
                    .header(X_REQUEST_ID, requestId())
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (okhttp3.Response okResponse = httpClient.newCall(request).execute()) {
                byte[] bodyBytes = okResponse.body() != null ? okResponse.body().bytes() : new byte[0];
                String bodyString = bodyBytes.length > 0 ? new String(bodyBytes) : null;
                return Response.status(okResponse.code()).entity(bodyString).build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to call composite structures", e);
        }
    }

    private static String requestId() {
        String id = MDC.get(X_REQUEST_ID);
        return id != null ? id : "";
    }
}

package com.netcracker.core.declarative.client.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Set;

public class CompositeClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public CompositeClient(OkHttpClient client, ObjectMapper mapper, String baseUrl) {
        this.client = client;
        this.mapper = mapper;
        this.baseUrl = baseUrl;
    }

    public Response structures(CompositeRequest payload) throws IOException {
        String jsonBody = mapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(jsonBody, JSON);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(baseUrl + "/api/composite/v1/structures")
                .post(body)
                // You will need to manually invoke your RequestIdHeaderFactory logic here
                .addHeader("X-Request-Id", "your-generated-id-here")
                .build();

        return client.newCall(request).execute();
    }

    public record CompositeRequest(
            String id,
            Set<String> namespaces) {
    }
}

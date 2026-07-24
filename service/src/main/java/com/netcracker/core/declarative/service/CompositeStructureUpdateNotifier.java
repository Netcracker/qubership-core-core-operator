package com.netcracker.core.declarative.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.netcracker.cloud.core.error.rest.tmf.DefaultTmfErrorResponseConverter;
import com.netcracker.cloud.core.error.rest.tmf.TmfErrorResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;

@AllArgsConstructor
public class CompositeStructureUpdateNotifier {
    private static final Logger log = LoggerFactory.getLogger(CompositeStructureUpdateNotifier.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Getter
    private final String xaasName;
    private final OkHttpClient client;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public void notify(String compositeId, Set<String> compositeMembers) {
        CompositeRequest compositeStructure = new CompositeRequest(compositeId, compositeMembers);
        log.info("Send request to {} with composite structure {}", xaasName, compositeStructure);

        Request request;
        try {
            request = new Request.Builder()
                    .url(baseUrl + "/api/composite/v1/structures")
                    .post(RequestBody.create(mapper.writeValueAsString(compositeStructure), JSON))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize composite structure for XaaS: " + xaasName, e);
        }

        try (Response response = client.newCall(request).execute()) {
            int statusCode = response.code();
            if (statusCode == SC_NO_CONTENT) {
                log.info("Successfully updated {} for '{}'", xaasName, compositeStructure);
            } else {
                String responseBody = response.body() != null ? response.body().string() : "";
                try {
                    TmfErrorResponse tmfErrorResponse = mapper.readValue(responseBody, TmfErrorResponse.class);
                    throw new DefaultTmfErrorResponseConverter().buildErrorCodeException(tmfErrorResponse);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(String.format("Unexpected response received from XaaS: %d, %s", statusCode, responseBody));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Communication failure with XaaS: " + xaasName, e);
        }
    }

    public record CompositeRequest(
            String id,
            Set<String> namespaces) {
    }
}

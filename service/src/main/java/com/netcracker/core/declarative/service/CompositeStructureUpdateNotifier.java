package com.netcracker.core.declarative.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.core.declarative.client.rest.CompositeClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.netcracker.cloud.core.error.rest.tmf.DefaultTmfErrorResponseConverter;
import com.netcracker.cloud.core.error.rest.tmf.TmfErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;

@AllArgsConstructor
public class CompositeStructureUpdateNotifier {
    private static final Logger log = LoggerFactory.getLogger(CompositeStructureUpdateNotifier.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Getter
    private final String xaasName;
    private final CompositeClient compositeClient;

    public void notify(String compositeId, Set<String> compositeMembers, long index) {
        CompositeClient.Request compositeStructure = new CompositeClient.Request(compositeId, compositeMembers, index);
        log.info("Send request to {} with composite structure {}", xaasName, compositeStructure);
        try (jakarta.ws.rs.core.Response response = compositeClient.structures(compositeStructure)) {
            if (response.getStatusInfo().getStatusCode() == SC_NO_CONTENT) {
                log.info("Successfully updated {} for '{}'", xaasName, compositeStructure);
            } else {
                try {
                    TmfErrorResponse tmfErrorResponse = mapper.readValue(response.getEntity().toString(), TmfErrorResponse.class);
                    throw new DefaultTmfErrorResponseConverter().buildErrorCodeException(tmfErrorResponse);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(String.format("Unexpected response received from XaaS: %d, %s", response.getStatusInfo().getStatusCode(), response.getEntity()));
                }
            }
        }
    }
}

package com.netcracker.core.declarative.client.rest.tracing;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.slf4j.MDC;

import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;

@ApplicationScoped
public class RequestIdHeaderFactory implements ClientHeadersFactory {
    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> multivaluedMap, MultivaluedMap<String, String> multivaluedMap1) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        result.add(X_REQUEST_ID, MDC.get(X_REQUEST_ID));
        return result;
    }
}

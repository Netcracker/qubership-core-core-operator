package com.netcracker.core.declarative.client.rest.okhttp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.*;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BufferedJsonResponse extends Response {

    private final int status;
    private final String reasonPhrase;
    private final MultivaluedMap<String, Object> headers;
    private final byte[] entityBytes;
    private final ObjectMapper objectMapper;

    public BufferedJsonResponse(int status, String reasonPhrase, MultivaluedMap<String, Object> headers,
                                byte[] entityBytes, ObjectMapper objectMapper) {
        this.status = status;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
        this.entityBytes = entityBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public StatusType getStatusInfo() {
        Status known = Status.fromStatusCode(status);
        if (known != null) {
            return known;
        }
        return new StatusType() {
            @Override public int getStatusCode() { return status; }
            @Override public Status.Family getFamily() { return Status.Family.familyOf(status); }
            @Override public String getReasonPhrase() { return reasonPhrase != null ? reasonPhrase : ""; }
        };
    }

    @Override
    public Object getEntity() {
        return entityBytes;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntity(Class<T> entityType) {
        if (!hasEntity()) {
            return null;
        }
        if (entityType == String.class) {
            return (T) new String(entityBytes, StandardCharsets.UTF_8);
        }
        if (entityType == byte[].class) {
            return (T) entityBytes;
        }
        try {
            return objectMapper.readValue(entityBytes, entityType);
        } catch (Exception e) {
            throw new ProcessingException("Failed to deserialize response entity as " + entityType, e);
        }
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType) {
        throw new UnsupportedOperationException("readEntity(GenericType) is not used by this client");
    }

    @Override
    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
        return readEntity(entityType);
    }

    @Override
    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
        return readEntity(entityType);
    }

    @Override
    public boolean hasEntity() {
        return entityBytes != null && entityBytes.length > 0;
    }

    @Override
    public boolean bufferEntity() {
        return true; // already fully buffered
    }

    @Override
    public void close() {
        // no-op, nothing to release
    }

    @Override
    public MediaType getMediaType() {
        String contentType = getHeaderString("Content-Type");
        return contentType != null ? MediaType.valueOf(contentType) : null;
    }

    @Override
    public Locale getLanguage() { return null; }

    @Override
    public int getLength() {
        return entityBytes != null ? entityBytes.length : -1;
    }

    @Override
    public Set<String> getAllowedMethods() { return Collections.emptySet(); }

    @Override
    public Map<String, NewCookie> getCookies() { return Collections.emptyMap(); }

    @Override
    public EntityTag getEntityTag() { return null; }

    @Override
    public Date getDate() { return null; }

    @Override
    public Date getLastModified() { return null; }

    @Override
    public URI getLocation() {
        String location = getHeaderString("Location");
        return location != null ? URI.create(location) : null;
    }

    @Override
    public Set<Link> getLinks() { return Collections.emptySet(); }

    @Override
    public boolean hasLink(String relation) { return false; }

    @Override
    public Link getLink(String relation) { return null; }

    @Override
    public Link.Builder getLinkBuilder(String relation) { return null; }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return null;
    }

    @Override
    public MultivaluedMap<String, Object> getHeaders() { return headers; }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        headers.forEach((key, values) -> values.forEach(v -> result.add(key, String.valueOf(v))));
        return result;
    }

    @Override
    public String getHeaderString(String name) {
        Object value = headers.getFirst(name);
        return value != null ? String.valueOf(value) : null;
    }
}

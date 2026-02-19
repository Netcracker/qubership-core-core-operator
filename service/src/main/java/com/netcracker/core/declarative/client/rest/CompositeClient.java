package com.netcracker.core.declarative.client.rest;

import com.netcracker.core.declarative.client.rest.tracing.RequestIdHeaderFactory;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

import java.util.Set;

@Path("/api/composite/v1/")
@RegisterClientHeaders(RequestIdHeaderFactory.class)
public interface CompositeClient {
    @POST
    @Path("structures")
    Response structures(Request request);

    record Request(
            String id,
            Set<String> namespaces,
            long modifyIndex
    ) {
    }

}
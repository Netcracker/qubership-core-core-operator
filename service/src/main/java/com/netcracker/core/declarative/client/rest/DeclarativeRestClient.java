package com.netcracker.core.declarative.client.rest;

import com.netcracker.core.declarative.client.rest.tracing.RequestIdHeaderFactory;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

@Path("/api/declarations/v{apiVersion}/")
@RegisterClientHeaders(RequestIdHeaderFactory.class)
public interface DeclarativeRestClient extends DeclarativeClient {

    @Override
    @Path("apply")
    @POST
    Response apply(@PathParam("apiVersion") String apiVersion, DeclarativeRequest request);

    @Override
    @Path("operation/{trackingID}/status")
    @GET
    Response getStatus(@PathParam("apiVersion") String apiVersion, @PathParam("trackingID") String trackingId);
}

package com.netcracker.core.declarative.client.rest.deprecated;

import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.client.rest.tracing.RequestIdHeaderFactory;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;

@Path("/api/v3")
@RegisterClientHeaders(RequestIdHeaderFactory.class)
public interface MeshClientV3 {
    @POST
    @Path("apply-config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON})
    Response applyConfig(DeclarativeRequest request);
}

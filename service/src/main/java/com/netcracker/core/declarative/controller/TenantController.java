package com.netcracker.core.declarative.controller;

import com.netcracker.core.declarative.model.Tenant;
import com.netcracker.core.declarative.service.TenantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

@Slf4j
@Path("/api/v1")
public class TenantController {

    @Inject
    TenantService tenantService;

    @POST
    @Path("/tenant/add")
    @Produces("application/json")
    @Consumes("application/json")
    public void add(Tenant tenant) throws ExecutionException, InterruptedException {
        log.info("Add new tenant:{}", tenant);
        tenantService.add(tenant);
    }
}

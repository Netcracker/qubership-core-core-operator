package com.netcracker.core.declarative.model;

import lombok.Data;

import java.util.List;

@Data
public class Tenant {
    private String tenantId;
    private List<String> defaultTenantVars;
    private String namespace;
}

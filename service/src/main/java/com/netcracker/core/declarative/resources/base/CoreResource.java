package com.netcracker.core.declarative.resources.base;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.runtime.RawExtension;
import io.fabric8.kubernetes.client.CustomResource;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CoreResource extends CustomResource<RawExtension, DeclarativeStatus> implements Namespaced {
    public static final String GROUP = "core.netcracker.com";
    public static final String VERSION = "v1";

    private String subKind;

    @Override
    protected DeclarativeStatus initStatus() {
        return new DeclarativeStatus();
    }
}

package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Startup
@Slf4j
public class VllaTmpConsulWatcher {
    private static final String COMPOSITE_STRUCTURE_REF_TEMPLATE = "config/%s/application/composite/structureRef";

    private final ConsulLongPoller consulLongPoller;
    private final String namespace;

    private final String compositeStructureRefKey;

    @Inject
    public VllaTmpConsulWatcher(@ConfigProperty(name = "cloud.microservice.namespace") String namespace,
                                ConsulLongPoller consulLongPoller) {
        this.namespace = namespace;
        this.consulLongPoller = consulLongPoller;
        this.compositeStructureRefKey = COMPOSITE_STRUCTURE_REF_TEMPLATE.formatted(namespace);
    }

    @PostConstruct
    public void init() {
        log.info("VLLA startWatchConsulRoot for key {}", compositeStructureRefKey);
        consulLongPoller.startWatchConsulRoot(compositeStructureRefKey, CompositeStructureRefConsulUpdateEvent::new);
        log.info("VLLA startWatchConsulRoot for key {} - COMPLETED", compositeStructureRefKey);
    }
}

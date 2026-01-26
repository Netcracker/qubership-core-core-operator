package com.netcracker.core.declarative.service.composite.consul.longpoll2;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.extern.slf4j.Slf4j;

@ApplicationScoped
@Slf4j
public class CompositeStructureRefListener {

    void onConfigUpdated(@Observes CompositeStructureRefConsulUpdateEvent event) {
        log.info("VLLA event CompositeStructureRefConsulUpdateEvent: {}", event);
    }
}

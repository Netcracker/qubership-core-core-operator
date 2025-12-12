package com.netcracker.core.declarative;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.consul.ConsulContainer;

import java.util.HashMap;
import java.util.Map;

public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    ConsulContainer consulContainer;

    @Override
    public Map<String, String> start() {
        consulContainer = new ConsulContainer("hashicorp/consul:1.15");
        consulContainer.start();
        Map<String, String> conf = new HashMap<>();
        conf.put("quarkus.consul-source-config.agent.enabled", "true");
        conf.put("quarkus.consul-source-config.agent.url", "http://" + consulContainer.getHost() + ":" + consulContainer.getFirstMappedPort());
        return conf;
    }

    @Override
    public void stop() {
        consulContainer.stop();
    }
}
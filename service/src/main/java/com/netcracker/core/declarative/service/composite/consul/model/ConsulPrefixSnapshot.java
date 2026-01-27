package com.netcracker.core.declarative.service.composite.consul.model;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import lombok.Getter;

import java.util.*;

/**
 * Immutable snapshot of Consul KV entries under a prefix.
 * <p>
 * Provides access to key-value pairs and the Consul modify index for change tracking.
 */
public class ConsulPrefixSnapshot {

    private final Map<String, String> keyValueMap;
    @Getter
    private final long index;

    private ConsulPrefixSnapshot(Map<String, String> keyValueMap, long index) {
        this.keyValueMap = keyValueMap;
        this.index = index;
    }

    /**
     * Creates a snapshot from NC Quarkus Consul client response.
     *
     * @param values      the list of GetValue entries from Consul
     * @param consulIndex the Consul modify index
     * @return a new snapshot instance
     */
    public static ConsulPrefixSnapshot fromGetValues(List<GetValue> values, long consulIndex) {
        Map<String, String> keyValueMap = new HashMap<>();
        if (values != null) {
            for (GetValue gv : values) {
                keyValueMap.put(gv.getKey(), gv.getDecodedValue());
            }
        }
        return new ConsulPrefixSnapshot(keyValueMap, consulIndex);
    }

    public Set<String> getKeySet() {
        return Collections.unmodifiableSet(keyValueMap.keySet());
    }

    public String getValue(String key) {
        return keyValueMap.get(key);
    }
}

package com.netcracker.core.declarative.service.composite.consul.model;

import io.vertx.ext.consul.KeyValue;
import io.vertx.ext.consul.KeyValueList;
import lombok.Getter;

import java.util.*;

public class ConsulPrefixSnapshot {

    private final Map<String, String> keyValueMap;
    @Getter
    private final long index;

    public ConsulPrefixSnapshot(KeyValueList keyValueList) {
        this.keyValueMap = new HashMap<>();

        final List<KeyValue> entries = (keyValueList != null && keyValueList.getList() != null) ?
                keyValueList.getList() :
                Collections.emptyList();
        for (KeyValue kv : entries) {
            keyValueMap.put(kv.getKey(), kv.getValue());
        }


        this.index = keyValueList == null ? 0 : keyValueList.getIndex();
    }

    public Set<String> getKeySet() {
        return Collections.unmodifiableSet(keyValueMap.keySet());
    }

    public String getValue(String key) {
        return keyValueMap.get(key);
    }
}

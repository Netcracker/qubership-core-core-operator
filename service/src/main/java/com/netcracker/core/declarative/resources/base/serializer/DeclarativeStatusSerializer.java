package com.netcracker.core.declarative.resources.base.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.netcracker.core.declarative.resources.base.CoreCondition;

import java.io.IOException;
import java.util.Map;

public class DeclarativeStatusSerializer extends JsonSerializer<Map<String, CoreCondition>> {
    @Override
    public void serialize(final Map<String, CoreCondition> value, final JsonGenerator jgen, final SerializerProvider provider)
            throws IOException {
        jgen.writeObject(value.values());
    }
}

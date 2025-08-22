package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.netcracker.core.declarative.resources.base.CoreCondition;
import com.netcracker.core.declarative.resources.base.DeclarativeStatus;
import com.netcracker.core.declarative.resources.base.serializer.DeclarativeStatusDeserializer;
import com.netcracker.core.declarative.resources.base.serializer.DeclarativeStatusSerializer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.netcracker.core.declarative.client.rest.ProcessStatus.COMPLETED;
import static com.netcracker.core.declarative.resources.base.Phase.UPDATED_PHASE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class DeclarativeStatusTest {
    @Test
    void serializerTest() throws Exception {
        DeclarativeStatus declarativeStatus = new DeclarativeStatus();
        declarativeStatus.setRequestId("2");
        declarativeStatus.setPhase(UPDATED_PHASE);
        Map<String, CoreCondition> conditions = new LinkedHashMap<>();
        CoreCondition condition1 = new CoreCondition("1", "2", "m", "r", COMPLETED, true, "t");
        CoreCondition condition2 = new CoreCondition("2", "3", "mm", "rr", COMPLETED, true, "tt");
        conditions.put("t", condition1);
        conditions.put("tt", condition2);
        declarativeStatus.setConditions(conditions);
        declarativeStatus.setTrackingId("1");
        declarativeStatus.setAdditionalProperty("p", "w");
        declarativeStatus.setObservedGeneration(1L);

        List<CoreCondition> conditionList = new ArrayList<>();
        conditionList.add(condition1);
        conditionList.add(condition2);

        ObjectMapper objectMapper = new ObjectMapper();

        Writer jsonWriter = new StringWriter();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(jsonWriter);
        jsonGenerator.setCodec(objectMapper);
        SerializerProvider serializerProvider = objectMapper.getSerializerProvider();

        DeclarativeStatusSerializer serializer = new DeclarativeStatusSerializer();
        serializer.serialize(conditions, jsonGenerator, serializerProvider);
        jsonGenerator.flush();

        assertEquals(objectMapper.writeValueAsString(conditionList), jsonWriter.toString());

        DeclarativeStatusDeserializer deserializer = new DeclarativeStatusDeserializer();
        InputStream stream = new ByteArrayInputStream(objectMapper.writeValueAsString(conditionList).getBytes(StandardCharsets.UTF_8));
        JsonParser parser = objectMapper.getFactory().createParser(stream);
        DeserializationContext ctxt = objectMapper.getDeserializationContext();
        LinkedHashMap<String, CoreCondition> deserializedMap = deserializer.deserialize(parser, ctxt);
        assertEquals(2, deserializedMap.size());
    }
}

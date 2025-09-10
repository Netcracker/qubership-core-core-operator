package com.netcracker.core.declarative.resources.base.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.netcracker.core.declarative.resources.base.CoreCondition;

import java.io.IOException;
import java.util.LinkedHashMap;

public class DeclarativeStatusDeserializer extends JsonDeserializer<LinkedHashMap<String, CoreCondition>> {
    ObjectMapper m = new ObjectMapper();

    @Override
    public LinkedHashMap<String, CoreCondition> deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException {
        LinkedHashMap<String, CoreCondition> ret = new LinkedHashMap<>();
        ObjectCodec codec = parser.getCodec();
        TreeNode node = codec.readTree(parser);
        if (node.isArray()) {
            for (JsonNode n : (ArrayNode) node) {
                JsonNode type = n.get("type");
                if (type != null) {
                    ret.put(type.asText(), m.treeToValue(n, CoreCondition.class));
                }
            }
        }
        return ret;
    }
}
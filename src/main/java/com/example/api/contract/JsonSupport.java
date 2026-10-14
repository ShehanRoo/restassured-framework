package com.example.api.contract;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;

/** A privately configured, thread-safe mapper with bounded parsing and no polymorphic typing. */
public final class JsonSupport {
  private static final ObjectMapper MAPPER =
      new ObjectMapper(
              JsonFactory.builder()
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(100)
                          .maxNumberLength(1000)
                          .maxStringLength(1_000_000)
                          .build())
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private JsonSupport() {}

  public static boolean isJsonScalar(Object value) {
    return value == null
        || Set.of(
                String.class,
                Boolean.class,
                Byte.class,
                Short.class,
                Integer.class,
                Long.class,
                Float.class,
                Double.class,
                BigInteger.class,
                BigDecimal.class)
            .contains(value.getClass());
  }

  public static JsonNode parse(String json) throws JsonProcessingException {
    return MAPPER.readTree(json);
  }

  public static JsonNode parse(InputStream input) throws IOException {
    return MAPPER.readTree(input);
  }

  public static String serialize(Object value) throws JsonProcessingException {
    return MAPPER.writeValueAsString(value);
  }

  public static String pretty(JsonNode value) throws JsonProcessingException {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
  }

  public static JsonNode node(Object value) {
    return MAPPER.valueToTree(value);
  }

  public static Object primitive(JsonNode value) {
    return MAPPER.convertValue(value, Object.class);
  }

  public static ObjectNode object() {
    return MAPPER.createObjectNode();
  }

  public static ArrayNode array() {
    return MAPPER.createArrayNode();
  }
}

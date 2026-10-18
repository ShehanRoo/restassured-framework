package com.example.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.qameta.allure.Allure;
import java.io.InputStream;
import java.util.Set;

/** Draft-7 schemas are local and self-contained. No remote $ref resolution is allowed. */
public final class SchemaChecks {
  private SchemaChecks() {}

  public static void validate(
      String direction, BodyContract contract, String body, String contentType) {
    validate(direction, contract, body, contentType, false);
  }

  public static void validateParameters(BodyContract contract, String body) {
    validate("Request", contract, body, "application/json", true);
  }

  private static void validate(
      String direction,
      BodyContract contract,
      String body,
      String contentType,
      boolean parameters) {
    if (!Set.of("Request", "Response").contains(direction)) {
      throw new IllegalArgumentException("Schema direction must be Request or Response");
    }
    Allure.step(
        direction + " body contract",
        () -> {
          if (!contract.hasBody()) {
            if (body != null && !body.isEmpty()) {
              throw new AssertionError(direction + " must have no body");
            }
            return;
          }
          if (contentType == null
              || !contentType
                  .toLowerCase(java.util.Locale.ROOT)
                  .matches("application/(json|[a-z0-9.+-]+\\+json)(\\s*;.*)?")) {
            throw new AssertionError(direction + " requires a JSON content type");
          }
          JsonNode instance;
          JsonSchema schema;
          try (InputStream input =
              SchemaChecks.class.getClassLoader().getResourceAsStream(contract.schemaResource())) {
            if (input == null) {
              throw new IllegalStateException("Schema resource missing");
            }
            JsonNode definition = JsonSupport.parse(input);
            rejectReferences(definition);
            if (!"http://json-schema.org/draft-07/schema#"
                .equals(definition.path("$schema").asText())) {
              throw new IllegalStateException("Schemas must declare Draft 7");
            }
            if (parameters
                && (!"object".equals(definition.path("type").asText())
                    || !definition.path("additionalProperties").isBoolean()
                    || definition.path("additionalProperties").booleanValue()
                    || definition.has("patternProperties"))) {
              throw new IllegalStateException("Parameter schemas must deny undeclared keys");
            }
            schema =
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(definition);
            if (body == null || body.length() > 1_000_000) {
              throw new IllegalArgumentException("Body limit");
            }
            instance = JsonSupport.parse(body);
            if (instance == null) {
              throw new IllegalArgumentException("Missing JSON");
            }
          } catch (Exception exception) {
            throw new AssertionError(
                direction
                    + " schema setup or JSON parsing failed; inspect the local schema and sanitized evidence");
          }
          Set<ValidationMessage> violations;
          try {
            violations = schema.validate(instance);
          } catch (Exception exception) {
            throw new AssertionError(direction + " schema validation could not complete");
          }
          if (!violations.isEmpty()) {
            // Validator messages can include secrets from instance values. Emit keywords only.
            String keywords =
                violations.stream()
                    .map(ValidationMessage::getType)
                    .distinct()
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            Allure.addAttachment(
                direction + " schema violations",
                "text/plain",
                "Schema: "
                    + contract.schemaResource()
                    + "\nViolations: "
                    + violations.size()
                    + "\nFailed constraints: "
                    + keywords
                    + "\nInstance values and dynamic paths withheld",
                ".txt");
            throw new AssertionError(direction + " schema mismatch (" + keywords + ")");
          }
        });
  }

  private static void rejectReferences(JsonNode node) {
    if (node.isObject() && (node.has("$ref") || node.has("$id"))) {
      throw new IllegalArgumentException("Bundle schemas; $ref and $id are not supported");
    }
    if (node.isContainerNode()) {
      node.forEach(SchemaChecks::rejectReferences);
    }
  }
}

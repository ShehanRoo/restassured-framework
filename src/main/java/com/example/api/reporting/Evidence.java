package com.example.api.reporting;

import com.example.api.config.ApiConfig;
import com.example.api.contract.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.qameta.allure.Allure;
import io.restassured.http.Headers;
import java.util.Locale;
import java.util.Set;

/**
 * Fail-closed evidence: only explicitly approved JSON fields and safe header names retain values.
 */
public final class Evidence {
  private static final int MAX_INPUT = 1_000_000;
  private static final int MAX_ATTACHMENT = 32_768;
  private final ApiConfig config;

  public Evidence(ApiConfig config) {
    this.config = config;
  }

  public String body(String body) {
    if (body == null || body.isBlank()) {
      return "[empty body]";
    }
    if (body.length() > MAX_INPUT) {
      return "[body omitted: evidence size limit]";
    }
    try {
      return displaySafe(
          config.scrubKnownSecret(JsonSupport.pretty(sanitize(JsonSupport.parse(body)))));
    } catch (Exception exception) {
      return "[body omitted: not valid JSON]";
    }
  }

  private JsonNode sanitize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = JsonSupport.object();
      node.properties()
          .forEach(
              field -> {
                String key = field.getKey();
                if (sensitive(key) || !config.evidenceFields().contains(key)) {
                  result.put(safeKey(key), "[REDACTED]");
                } else if (field.getValue().isContainerNode()) {
                  result.set(safeKey(key), sanitize(field.getValue()));
                } else {
                  result.set(
                      safeKey(key),
                      field.getValue().isTextual()
                          ? TextNode.valueOf(config.scrubKnownSecret(field.getValue().asText()))
                          : field.getValue());
                }
              });
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = JsonSupport.array();
      node.forEach(
          value ->
              result.add(
                  value.isContainerNode() ? sanitize(value) : TextNode.valueOf("[REDACTED]")));
      return result;
    }
    return TextNode.valueOf("[REDACTED]");
  }

  private String safeKey(String key) {
    boolean known =
        config.evidenceFields().contains(key)
            || Set.of(
                    "name",
                    "message",
                    "email",
                    "phone",
                    "address",
                    "password",
                    "token",
                    "secret",
                    "authorization",
                    "cookie",
                    "set-cookie",
                    "content-type",
                    "accept",
                    "x-api-key")
                .contains(key.toLowerCase(Locale.ROOT));
    return known && key.matches("[a-zA-Z0-9_-]{1,80}")
        ? config.scrubKnownSecret(key)
        : "[field name omitted]";
  }

  public String headers(Headers headers) {
    StringBuilder result = new StringBuilder();
    headers.forEach(
        header -> {
          String name = header.getName().toLowerCase(Locale.ROOT);
          if (!Set.of(
                  "content-type", "accept", "authorization", "cookie", "set-cookie", "x-api-key")
              .contains(name)) {
            return;
          }
          result
              .append(safeKey(name))
              .append(": ")
              .append(
                  name.equals("content-type") || name.equals("accept")
                      ? normalizedMediaType(header.getValue())
                      : "[REDACTED]")
              .append('\n');
        });
    return cap(result.toString());
  }

  private static String normalizedMediaType(String value) {
    String mediaType = value.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    if (mediaType.equals("application/json")) {
      return "application/json";
    }
    if (mediaType.matches("application/[a-z0-9.+-]+\\+json")) {
      return "application/*+json";
    }
    return "[unsupported media type]";
  }

  public String value(String pointer, Object value) {
    String field = pointer.substring(pointer.lastIndexOf('/') + 1);
    if (sensitive(field) || !config.evidenceFields().contains(field)) {
      return "[REDACTED]";
    }
    if (JsonSupport.isJsonScalar(value)) {
      try {
        // JSON literals escape CR/LF, tabs and terminal controls inside scalar values.
        return displaySafe(config.scrubKnownSecret(JsonSupport.serialize(value)));
      } catch (Exception exception) {
        return "[value omitted: cannot encode safely]";
      }
    }
    return "[structured value omitted]";
  }

  public static boolean sensitive(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return normalized.matches(
        ".*(password|passwd|secret|token|authorization|cookie|apikey|session|csrf|signature|credential|privatekey|email|phone|address|name).*");
  }

  public void attach(String name, String content) {
    Allure.addAttachment(name, "text/plain", displaySafe(config.scrubKnownSecret(content)), ".txt");
  }

  private static String displaySafe(String text) {
    StringBuilder safe = new StringBuilder();
    String hex = "0123456789abcdef";
    for (int offset = 0; offset < text.length(); ) {
      int point = text.codePointAt(offset);
      int units = Character.charCount(point);
      int type = Character.getType(point);
      boolean escape =
          type == Character.FORMAT
              || type == Character.LINE_SEPARATOR
              || type == Character.PARAGRAPH_SEPARATOR;
      if (safe.length() + units * (escape ? 6 : 1) > MAX_ATTACHMENT) {
        return safe.append("\n[truncated]").toString();
      }
      if (escape) {
        for (char unit : Character.toChars(point)) {
          safe.append("\\u")
              .append(hex.charAt((unit >>> 12) & 15))
              .append(hex.charAt((unit >>> 8) & 15))
              .append(hex.charAt((unit >>> 4) & 15))
              .append(hex.charAt(unit & 15));
        }
      } else {
        safe.appendCodePoint(point);
      }
      offset += units;
    }
    return safe.toString();
  }

  private static String cap(String text) {
    return text.length() <= MAX_ATTACHMENT
        ? text
        : text.substring(0, MAX_ATTACHMENT) + "\n[truncated]";
  }
}

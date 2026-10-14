package com.example.api.config;

import java.net.URI;
import java.util.Set;

/** Immutable transport configuration. Credentials deliberately have no public accessor. */
public final class ApiConfig {
  private final URI baseUri;
  private final String token;
  private final int timeoutMs;
  private final Set<String> evidenceFields;

  public ApiConfig(URI baseUri, String token, int timeoutMs, Set<String> evidenceFields) {
    if (baseUri == null
        || baseUri.getHost() == null
        || baseUri.getUserInfo() != null
        || baseUri.getQuery() != null
        || baseUri.getFragment() != null) {
      throw new IllegalArgumentException(
          "API_BASE_URL must be an absolute origin without credentials, query or fragment");
    }
    boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(baseUri.getHost());
    if (!("https".equals(baseUri.getScheme()) || (local && "http".equals(baseUri.getScheme())))) {
      throw new IllegalArgumentException("API_BASE_URL requires HTTPS except for loopback mocks");
    }
    if (!Set.of("", "/").contains(baseUri.getPath())) {
      throw new IllegalArgumentException(
          "API_BASE_URL must contain only the origin; put the API prefix in contracts");
    }
    if (timeoutMs < 100 || timeoutMs > 60000) {
      throw new IllegalArgumentException("API_TIMEOUT_MS must be between 100 and 60000");
    }
    if (token != null && (token.contains("\r") || token.contains("\n"))) {
      throw new IllegalArgumentException("API_TOKEN contains an invalid header character");
    }
    this.baseUri = baseUri;
    this.token = token == null ? "" : token;
    this.timeoutMs = timeoutMs;
    this.evidenceFields = Set.copyOf(evidenceFields);
  }

  public static ApiConfig fromEnvironment() {
    String base = System.getenv("API_BASE_URL");
    if (base == null || base.isBlank()) {
      throw new IllegalArgumentException("API_BASE_URL is required for external tests");
    }
    try {
      return new ApiConfig(
          URI.create(base),
          System.getenv("API_TOKEN"),
          Integer.parseInt(envOrDefault("API_TIMEOUT_MS", "5000")),
          Set.of(envOrDefault("EVIDENCE_FIELDS", "id,active,code,total").split(",")));
    } catch (IllegalArgumentException exception) {
      // URI/number parsing exceptions can contain input. Never retain their raw cause.
      throw new IllegalArgumentException(
          "Invalid API environment configuration; check README variable constraints");
    }
  }

  public static ApiConfig local(URI origin) {
    return new ApiConfig(origin, "", 5000, Set.of("id", "active", "code", "total"));
  }

  private static String envOrDefault(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  public URI baseUri() {
    return baseUri;
  }

  public int timeoutMs() {
    return timeoutMs;
  }

  public Set<String> evidenceFields() {
    return evidenceFields;
  }

  public void authorize(io.restassured.specification.RequestSpecification request) {
    if (!token.isEmpty()) {
      request.header("Authorization", "Bearer " + token);
    }
  }

  public String scrubKnownSecret(String value) {
    return token.isEmpty() ? value : value.replace(token, "[REDACTED]");
  }

  @Override
  public String toString() {
    return "ApiConfig[credentials and endpoint withheld]";
  }
}

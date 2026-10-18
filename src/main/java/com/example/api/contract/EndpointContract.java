package com.example.api.contract;

import io.restassured.http.Method;
import java.util.Map;
import java.util.Objects;

/**
 * Status-specific response contracts keep error responses just as strict as successful responses.
 */
public record EndpointContract(
    String name,
    Method method,
    String path,
    BodyContract request,
    Map<Integer, BodyContract> responses,
    BodyContract pathParameters,
    BodyContract queryParameters) {
  /** Calls without parameters deny all parameter keys by default. */
  public EndpointContract(
      String name,
      Method method,
      String path,
      BodyContract request,
      Map<Integer, BodyContract> responses) {
    this(
        name,
        method,
        path,
        request,
        responses,
        BodyContract.json("schemas/no-parameters.json"),
        BodyContract.json("schemas/no-parameters.json"));
  }

  public EndpointContract {
    Objects.requireNonNull(name);
    Objects.requireNonNull(method);
    Objects.requireNonNull(request);
    if (pathParameters == null
        || queryParameters == null
        || !pathParameters.hasBody()
        || !queryParameters.hasBody()) {
      throw new IllegalArgumentException("Path and query parameters require JSON object schemas");
    }
    if (!name.matches("[a-zA-Z0-9 _-]{1,80}")
        || path == null
        || !path.matches(
            "/(?:[a-zA-Z0-9_-]+|\\{[a-zA-Z][a-zA-Z0-9_]*\\})(?:/(?:[a-zA-Z0-9_-]+|\\{[a-zA-Z][a-zA-Z0-9_]*\\}))*/?|/")
        || path.startsWith("//")
        || responses == null
        || responses.isEmpty()) {
      throw new IllegalArgumentException(
          "Contract requires a safe name, relative path template and response schemas");
    }
    responses = Map.copyOf(responses);
    if (responses.keySet().stream().anyMatch(status -> status < 100 || status > 599)) {
      throw new IllegalArgumentException("Invalid response status in contract");
    }
  }
}

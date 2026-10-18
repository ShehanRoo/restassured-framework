package com.example.api.client;

import com.example.api.config.ApiConfig;
import com.example.api.contract.BodyContract;
import com.example.api.contract.EndpointContract;
import com.example.api.contract.JsonSupport;
import com.example.api.contract.ParameterChecks;
import com.example.api.contract.SchemaChecks;
import com.example.api.reporting.Evidence;
import com.example.api.reporting.EvidenceFilter;
import io.qameta.allure.Allure;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RedirectConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;

/** The only transport entry point. Contracts and evidence cannot be accidentally omitted. */
public final class ApiClient {
  private final ApiConfig config;
  private final Evidence evidence;

  public ApiClient(ApiConfig config) {
    this.config = Objects.requireNonNull(config);
    this.evidence = new Evidence(config);
  }

  public ApiResponse execute(EndpointContract contract, Object body) {
    return execute(contract, body, Map.of(), Map.of());
  }

  public ApiResponse execute(
      EndpointContract contract,
      Object body,
      Map<String, ?> pathParameters,
      Map<String, ?> queryParameters) {
    Objects.requireNonNull(contract);
    String payload;
    try {
      payload = body == null ? "" : JsonSupport.serialize(body);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Request body cannot be serialized as JSON");
    }
    return Allure.step(
        contract.method() + " " + contract.name(),
        () -> {
          String requestId = UUID.randomUUID().toString();
          evidence.attach(
              "HTTP request",
              "Request ID: "
                  + requestId
                  + "\nMethod: "
                  + contract.method()
                  + "\nPath template: "
                  + contract.path()
                  + "\nPath and query parameter values withheld\nAuthorization: [REDACTED]\n"
                  + "Content-Type: "
                  + (contract.request().hasBody() ? "application/json" : "[absent]")
                  + "\n\n"
                  + evidence.body(payload));
          SchemaChecks.validate("Request", contract.request(), payload, "application/json");
          Map<String, ?> safePath = ParameterChecks.validate(contract, pathParameters, true);
          Map<String, ?> safeQuery = ParameterChecks.validate(contract, queryParameters, false);
          Response response;
          try {
            RequestSpecification request =
                RestAssured.given()
                    .baseUri(config.baseUri().toString())
                    .config(
                        RestAssuredConfig.config()
                            .redirect(RedirectConfig.redirectConfig().followRedirects(false))
                            .httpClient(
                                HttpClientConfig.httpClientConfig()
                                    .httpClientFactory(
                                        () -> {
                                          // REST Assured requires the legacy AbstractHttpClient
                                          // API.
                                          DefaultHttpClient client = new DefaultHttpClient();
                                          client.setHttpRequestRetryHandler(
                                              new DefaultHttpRequestRetryHandler(0, false));
                                          return client;
                                        })
                                    .setParam("http.connection.timeout", config.timeoutMs())
                                    .setParam("http.socket.timeout", config.timeoutMs())
                                    .setParam(
                                        "http.connection-manager.timeout",
                                        (long) config.timeoutMs())))
                    .accept("application/json")
                    .header("X-Request-ID", requestId)
                    .filter(new EvidenceFilter(evidence, requestId));
            config.authorize(request);
            if (contract.request().hasBody()) {
              request.contentType("application/json").body(payload);
            }
            request.pathParams(safePath).queryParams(safeQuery);
            response = request.request(contract.method(), contract.path());
          } catch (Exception exception) {
            throw new IllegalStateException(
                "Request could not complete; inspect sanitized evidence and endpoint configuration");
          }
          BodyContract responseContract = contract.responses().get(response.statusCode());
          Allure.step(
              "Response status has a declared contract",
              () -> {
                if (responseContract == null) {
                  throw new AssertionError("Undeclared response status: " + response.statusCode());
                }
              });
          SchemaChecks.validate(
              "Response", responseContract, response.asString(), response.contentType());
          return new ApiResponse(response, evidence);
        });
  }
}

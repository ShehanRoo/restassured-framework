package com.example.api.tests;

import static com.example.api.support.UserContracts.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.api.client.ApiClient;
import com.example.api.config.ApiConfig;
import com.example.api.contract.BodyContract;
import com.example.api.contract.EndpointContract;
import com.example.api.support.CreateUser;
import com.example.api.support.MockApi;
import com.github.tomakehurst.wiremock.http.Fault;
import io.restassured.http.Method;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParameterSafetyTest {
  private static final EndpointContract LIST =
      new EndpointContract(
          "List users",
          Method.GET,
          "/users",
          BodyContract.empty(),
          Map.of(200, USER),
          BodyContract.json("schemas/no-parameters.json"),
          BodyContract.json("schemas/list-query.json"));

  @Test
  void typedParametersAndGeneratedCorrelationHeaderReachTheServer() {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          get(urlPathEqualTo("/users"))
              .withQueryParam("limit", equalTo("10"))
              .withQueryParam("active", equalTo("true"))
              .withHeader(
                  "X-Request-ID",
                  matching("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}"))
              .willReturn(okJson("{\"id\":7,\"name\":\"Synthetic\",\"active\":true}")));
      mock.client()
          .execute(LIST, null, Map.of(), Map.of("limit", 10, "active", true))
          .assertStatus(200)
          .assertField("/id", 7);
      assertEquals(1, mock.requestCount());
    }
  }

  @Test
  void invalidPathParametersAreRejectedWithoutNetworkOrValueLeakage() {
    List<Map<String, ?>> cases =
        List.of(
            Map.of(),
            Map.of("id", "7"),
            Map.of("id", 0),
            Map.of("id", -1),
            Map.of("id", 2147483648L),
            Map.of("id", "../users"),
            Map.of("id", "%2e%2e"),
            Map.of("id", "synthetic-security-canary-42\r\nInjected: true"),
            Map.of("id", List.of(7)),
            Map.of("id", 7, "extra", "synthetic-security-canary-42"));
    try (MockApi mock = new MockApi()) {
      for (var parameters : cases) {
        Throwable failure =
            assertThrows(
                Throwable.class, () -> mock.client().execute(GET, null, parameters, Map.of()));
        assertTrue(
            failure instanceof AssertionError || failure instanceof IllegalArgumentException);
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-security-canary-42"));
      }
      assertEquals(0, mock.requestCount());
    }
  }

  @Test
  void querySchemasDenyUnknownKeysMissingValuesWrongTypesAndRanges() {
    List<Map<String, ?>> cases =
        List.of(
            Map.of(),
            Map.of("limit", "10"),
            Map.of("limit", 0),
            Map.of("limit", 101),
            Map.of("limit", 10, "active", "true"),
            Map.of("limit", Double.NaN),
            Map.of("limit", 10, "unknown", "synthetic-security-canary-42"),
            Map.of("limit", 10, "active", List.of(true)),
            Map.of("limit", "x".repeat(2049)));
    try (MockApi mock = new MockApi()) {
      for (var parameters : cases) {
        Throwable failure =
            assertThrows(
                Throwable.class, () -> mock.client().execute(LIST, null, Map.of(), parameters));
        assertTrue(
            failure instanceof AssertionError || failure instanceof IllegalArgumentException);
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-security-canary-42"));
      }
      assertThrows(
          AssertionError.class,
          () -> mock.client().execute(GET, null, Map.of("id", 7), Map.of("unknown", true)));
      assertEquals(0, mock.requestCount());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "schemas/permissive-parameters.json",
        "schemas/pattern-parameters.json",
        "schemas/nonobject-parameters.json"
      })
  void unsafeParameterSchemasFailClosedBeforeTransport(String schema) {
    try (MockApi mock = new MockApi()) {
      mock.stub("GET", "/users", 200, "{\"id\":7,\"name\":\"Synthetic\",\"active\":true}");
      var contract =
          new EndpointContract(
              "Unsafe schema regression",
              Method.GET,
              "/users",
              BodyContract.empty(),
              Map.of(200, USER),
              BodyContract.json("schemas/no-parameters.json"),
              BodyContract.json(schema));
      Map<String, ?> parameters =
          schema.endsWith("nonobject-parameters.json") ? Map.of() : Map.of("undeclared", true);
      assertThrows(
          AssertionError.class, () -> mock.client().execute(contract, null, Map.of(), parameters));
      assertEquals(0, mock.requestCount());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST", "DELETE"})
  void droppedConnectionsAreNeverRetried(String method) {
    try (MockApi mock = new MockApi()) {
      EndpointContract contract =
          switch (method) {
            case "POST" -> CREATE;
            case "DELETE" -> DELETE;
            default -> GET;
          };
      String path = method.equals("POST") ? "/users" : "/users/7";
      mock.stub(
          request(method, urlEqualTo(path))
              .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
      var client =
          new ApiClient(
              new ApiConfig(mock.origin(), "synthetic-security-canary-42", 1000, Set.of("id")));
      var failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  client.execute(
                      contract,
                      method.equals("POST") ? new CreateUser("Synthetic", true) : null,
                      method.equals("POST") ? Map.of() : Map.of("id", 7),
                      Map.of()));
      assertNull(failure.getCause());
      assertEquals(1, mock.requestCount());
    }
  }

  @Test
  void bearerCredentialsRejectAllUnsafeHeaderCharacters() {
    for (String token :
        List.of(
            "synthetic-security-canary-42\u0000",
            "unsafe\tvalue",
            "unsafe value",
            "x".repeat(8193))) {
      var failure =
          assertThrows(
              IllegalArgumentException.class,
              () -> new ApiConfig(URI.create("https://example.test"), token, 1000, Set.of("id")));
      assertNull(failure.getCause());
      assertFalse(failure.toString().contains("synthetic-security-canary-42"));
    }
  }
}

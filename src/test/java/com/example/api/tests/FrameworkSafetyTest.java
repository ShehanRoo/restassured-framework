package com.example.api.tests;

import static com.example.api.support.UserContracts.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.example.api.client.ApiClient;
import com.example.api.config.ApiConfig;
import com.example.api.contract.BodyContract;
import com.example.api.contract.EndpointContract;
import com.example.api.contract.JsonSupport;
import com.example.api.contract.SchemaChecks;
import com.example.api.reporting.Evidence;
import com.example.api.support.MockApi;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.http.Method;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regression tests exercise real exchanges and adversarial failure paths. */
class FrameworkSafetyTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"active\":true}",
        "{\"name\":\"Synthetic\"}",
        "{}",
        "{\"name\":\"\",\"active\":true}",
        "{\"name\":17,\"active\":true}",
        "{\"name\":\"Synthetic\",\"active\":\"true\"}",
        "{\"name\":\"Synthetic\",\"active\":true,\"extra\":1}",
        "{\"name\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"active\":true}"
      })
  void allInvalidCreateRequestsAreRejectedBeforeSending(String json) throws Exception {
    try (MockApi mock = new MockApi()) {
      assertThrows(
          AssertionError.class, () -> mock.client().execute(CREATE, JsonSupport.parse(json)));
      assertEquals(0, mock.requestCount());
    }
  }

  @Test
  void mediaTypeParametersAndUnknownHeaderNamesAreWithheld() {
    Evidence evidence = new Evidence(ApiConfig.local(URI.create("http://127.0.0.1")));
    String canary = "synthetic-security-canary-42";
    String headers =
        evidence.headers(
            new Headers(
                new Header("Content-Type", "application/json; token=" + canary),
                new Header("Accept", "application/problem+json; secret=" + canary),
                new Header(canary, canary)));
    assertFalse(headers.contains(canary));
    assertTrue(headers.contains("application/json"));
    assertTrue(headers.contains("application/*+json"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://example.com",
        "https://user:password@example.com",
        "file:///tmp/api",
        "https://example.com?token=x",
        "https://example.com#token",
        "https://example.com/api"
      })
  void rejectsUnsafeOrigins(String origin) {
    assertThrows(IllegalArgumentException.class, () -> ApiConfig.local(URI.create(origin)));
  }

  @Test
  void requestValidationBlocksNetworkCall() {
    try (MockApi mock = new MockApi()) {
      assertThrows(
          AssertionError.class,
          () -> mock.client().execute(CREATE, Map.of("name", "Synthetic User")));
      assertEquals(0, mock.requestCount());
    }
  }

  @Test
  void emptyRequestContractRejectsBody() {
    try (MockApi mock = new MockApi()) {
      assertThrows(AssertionError.class, () -> mock.client().execute(GET, Map.of("id", 1)));
      assertEquals(0, mock.requestCount());
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"id\":\"wrong-type\",\"name\":\"Synthetic\",\"active\":true}",
        "{\"id\":1,\"active\":true}",
        "{\"id\":1,\"name\":\"Synthetic\",\"active\":true,\"unexpected\":1}",
        "not-json"
      })
  void rejectsInvalidResponseContracts(String body) {
    try (MockApi mock = new MockApi()) {
      mock.stub("GET", "/users/7", 200, body);
      assertThrows(
          AssertionError.class, () -> mock.client().execute(GET, null, Map.of("id", 7), Map.of()));
    }
  }

  @Test
  void rejectsUnexpectedStatus() {
    try (MockApi mock = new MockApi()) {
      mock.stub("GET", "/users/7", 418, "{}");
      assertThrows(
          AssertionError.class, () -> mock.client().execute(GET, null, Map.of("id", 7), Map.of()));
    }
  }

  @Test
  void redirectsAreNotFollowed() {
    try (MockApi mock = new MockApi();
        MockApi destination = new MockApi()) {
      mock.stub(
          get(urlEqualTo("/users/7"))
              .willReturn(
                  aResponse()
                      .withStatus(302)
                      .withHeader("Location", destination.origin() + "/users/7")));
      assertThrows(
          AssertionError.class, () -> mock.client().execute(GET, null, Map.of("id", 7), Map.of()));
      assertEquals(0, destination.requestCount());
    }
  }

  @Test
  void rejectsIncorrectContentType() {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          get(urlEqualTo("/users/7"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "text/html")
                      .withBody("<html>synthetic</html>")));
      assertThrows(
          AssertionError.class, () -> mock.client().execute(GET, null, Map.of("id", 7), Map.of()));
    }
  }

  @Test
  void bodylessResponseIsEnforced() {
    assertThrows(
        AssertionError.class,
        () -> SchemaChecks.validate("Response", BodyContract.empty(), " ", "text/plain"));
  }

  @Test
  void remoteSchemaReferencesAreRejected() {
    assertThrows(
        AssertionError.class,
        () ->
            SchemaChecks.validate(
                "Response",
                BodyContract.json("schemas/unsafe-ref.json"),
                "{}",
                "application/json"));
  }

  @Test
  void schemaPathsAreConstrained() {
    assertThrows(
        IllegalArgumentException.class, () -> BodyContract.json("https://example.com/schema.json"));
    assertThrows(
        IllegalArgumentException.class, () -> BodyContract.json("schemas/../../secret.json"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EndpointContract(
                "Safe",
                Method.GET,
                "https://example.com",
                BodyContract.empty(),
                Map.of(200, USER)));
  }

  @Test
  void recursiveRedactionIsFailClosed() {
    String canary = "synthetic-security-canary-42";
    Evidence evidence =
        new Evidence(
            new ApiConfig(
                URI.create("https://example.com"),
                canary,
                1000,
                Set.of("id", "items", "password", "email")));
    String body =
        evidence.body(
            "{\"id\":1,\"password\":\""
                + canary
                + "\",\"unknown\":\""
                + canary
                + "\",\"email\":\""
                + canary
                + "\",\"items\":[{\"id\":\""
                + canary
                + "\"},\""
                + canary
                + "\"]}");
    assertFalse(body.contains(canary), "Sensitive body values must be withheld");
    assertTrue(body.contains("\"id\" : 1"));
    assertFalse(
        evidence
            .headers(
                new Headers(
                    new Header("aUtHoRiZaTiOn", canary),
                    new Header("Set-Cookie", canary),
                    new Header("X-Unknown", canary),
                    new Header("Content-Type", "application/json")))
            .contains(canary));
    assertFalse(evidence.body("not-json " + canary).contains(canary));
    assertFalse(evidence.body("x".repeat(1_000_001)).contains("xxxx"));
    assertFalse(evidence.value("/password", canary).contains(canary));
    assertFalse(evidence.value("/id", canary).contains(canary));
  }

  @Test
  void assertionFailureDiagnosticsNeverEchoSensitiveValues() {
    try (MockApi mock = new MockApi()) {
      String canary = "synthetic-security-canary-42";
      mock.stub("GET", "/users/7", 200, "{\"id\":7,\"name\":\"" + canary + "\",\"active\":true}");
      var response = mock.client().execute(GET, null, Map.of("id", 7), Map.of());
      AssertionError failure =
          assertThrows(
              AssertionError.class,
              () -> response.assertField("/name", "different synthetic value"));
      assertFalse(failure.toString().contains(canary));
    }
  }

  @Test
  void transportTimeoutIsBoundedAndSanitized() {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          get(urlEqualTo("/users/7")).willReturn(aResponse().withStatus(200).withFixedDelay(1000)));
      ApiClient client =
          new ApiClient(
              new ApiConfig(mock.origin(), "synthetic-security-canary-42", 100, Set.of("id")));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> client.execute(GET, null, Map.of("id", 7), Map.of()));
      assertNull(failure.getCause());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{} {}", "{\"id\":1,\"id\":2}", "["})
  void strictJsonParsingRejectsAmbiguousDocuments(String json) {
    Evidence evidence = new Evidence(ApiConfig.local(URI.create("http://127.0.0.1")));
    assertEquals("[body omitted: not valid JSON]", evidence.body(json));
  }

  @Test
  void numericAssertionsIgnoreJavaNumericWrapperTypes() {
    try (MockApi mock = new MockApi()) {
      mock.stub("GET", "/users/7", 200, "{\"id\":7,\"name\":\"Synthetic\",\"active\":true}");
      mock.client().execute(GET, null, Map.of("id", 7), Map.of()).assertField("/id", 7L);
    }
  }
}

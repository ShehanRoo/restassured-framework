package com.example.api.tests;

import static com.example.api.support.UserContracts.GET;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.example.api.client.ApiClient;
import com.example.api.config.ApiConfig;
import com.example.api.support.MockApi;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Opt-in red test proves real failed Allure steps and portable evidence, never part of default CI.
 */
@Tag("failure-demo")
class ReportingFailureDemoTest {
  @Test
  @DisplayName("DEMO - failed response assertion with sanitized evidence")
  void highlightsFailedResponseField() {
    try (MockApi mock = new MockApi()) {
      String canary = "synthetic-security-canary-42";
      mock.stub(
          get(urlEqualTo("/users/7?token=" + canary))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withHeader("Set-Cookie", "session=" + canary)
                      .withBody("{\"id\":7,\"name\":\"" + canary + "\",\"active\":true}")));
      var client =
          new ApiClient(new ApiConfig(mock.origin(), canary, 5000, Set.of("id", "active")));
      client
          .execute(GET, null, Map.of("id", 7), Map.of("token", canary))
          .assertStatus(200)
          .assertField("/id", 999);
    }
  }
}

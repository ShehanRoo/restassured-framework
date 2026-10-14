package com.example.api.tests;

import static com.example.api.support.UserContracts.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.example.api.client.ApiClient;
import com.example.api.support.CreateUser;
import com.example.api.support.MockApi;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Epic("API automation")
@Feature("Users")
@Tag("smoke")
class UserApiTest {
  @Test
  @DisplayName("Create, retrieve and delete a synthetic user")
  void userLifecycle() {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          post(urlEqualTo("/users"))
              .withRequestBody(equalToJson("{\"name\":\"Synthetic User\",\"active\":true}"))
              .willReturn(
                  aResponse()
                      .withStatus(201)
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"id\":7,\"name\":\"Synthetic User\",\"active\":true}")));
      mock.stub("GET", "/users/7", 200, "{\"id\":7,\"name\":\"Synthetic User\",\"active\":true}");
      mock.stub("DELETE", "/users/7", 204, "");
      ApiClient client = mock.client();
      client
          .execute(CREATE, new CreateUser("Synthetic User", true))
          .assertStatus(201)
          .assertField("/id", 7);
      var response = client.execute(GET, null, Map.of("id", 7), Map.of());
      assertAll(
          "User fields",
          () -> response.assertStatus(200),
          () -> response.assertField("/id", 7),
          () -> response.assertField("/active", true));
      client.execute(DELETE, null, Map.of("id", 7), Map.of()).assertStatus(204);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404})
  @DisplayName("Validate authorization and missing-user error contracts")
  void errorResponsesHaveSchemas(int status) {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          "GET",
          "/users/7",
          status,
          "{\"code\":\"USER_UNAVAILABLE\",\"message\":\"Synthetic error\"}");
      mock.client()
          .execute(GET, null, Map.of("id", 7), Map.of())
          .assertStatus(status)
          .assertField("/code", "USER_UNAVAILABLE");
    }
  }

  @Test
  @DisplayName("A syntactically valid request can receive a schema-validated business rejection")
  void businessRejection() {
    try (MockApi mock = new MockApi()) {
      mock.stub(
          "POST", "/users", 400, "{\"code\":\"USER_REJECTED\",\"message\":\"Synthetic error\"}");
      mock.client()
          .execute(CREATE, new CreateUser("Synthetic User", true))
          .assertStatus(400)
          .assertField("/code", "USER_REJECTED");
    }
  }
}

package com.example.api.tests;

import static com.example.api.support.UserContracts.GET;

import com.example.api.client.ApiClient;
import com.example.api.config.ApiConfig;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Read-only live example; deliberately opt-in and requires a matching Users API. */
@Tag("external")
class ExternalUserApiTest {
  @Test
  void readKnownSyntheticUser() {
    new ApiClient(ApiConfig.fromEnvironment())
        .execute(GET, null, Map.of("id", 7), Map.of())
        .assertStatus(200)
        .assertField("/id", 7);
  }
}

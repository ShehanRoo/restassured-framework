package com.example.api.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.example.api.client.ApiClient;
import com.example.api.config.ApiConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.net.URI;

/** One loopback-only server per test. Dynamic ports and close() permit parallel execution. */
public final class MockApi implements AutoCloseable {
  private final WireMockServer server =
      new WireMockServer(wireMockConfig().bindAddress("127.0.0.1").dynamicPort());

  public MockApi() {
    server.start();
  }

  public URI origin() {
    return URI.create("http://127.0.0.1:" + server.port());
  }

  public ApiClient client() {
    return new ApiClient(ApiConfig.local(origin()));
  }

  public void stub(String method, String path, int status, String body) {
    server.stubFor(
        request(method, urlEqualTo(path))
            .willReturn(
                aResponse()
                    .withStatus(status)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }

  public void stub(MappingBuilder mapping) {
    server.stubFor(mapping);
  }

  public int requestCount() {
    return server.getAllServeEvents().size();
  }

  @Override
  public void close() {
    server.stop();
  }
}

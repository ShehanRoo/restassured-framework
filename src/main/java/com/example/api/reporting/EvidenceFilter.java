package com.example.api.reporting;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/** Attach before assertions so schema and semantic failures retain the exchange evidence. */
public final class EvidenceFilter implements Filter {
  private final Evidence evidence;
  private final String requestId;

  public EvidenceFilter(Evidence evidence, String requestId) {
    this.evidence = evidence;
    this.requestId = requestId;
  }

  @Override
  public Response filter(
      FilterableRequestSpecification request,
      FilterableResponseSpecification responseSpec,
      FilterContext context) {
    Response response;
    try {
      response = context.next(request, responseSpec);
    } catch (Exception exception) {
      evidence.attach(
          "Transport failure",
          "Request ID: "
              + requestId
              + "\nHTTP exchange failed; endpoint and exception details withheld");
      throw new IllegalStateException(
          "HTTP exchange failed; check connectivity, certificates and timeouts");
    }
    evidence.attach(
        "HTTP response",
        "Request ID: "
            + requestId
            + "\nStatus: "
            + response.statusCode()
            + "\n"
            + evidence.headers(response.headers())
            + "\n"
            + evidence.body(response.asString()));
    return response;
  }
}

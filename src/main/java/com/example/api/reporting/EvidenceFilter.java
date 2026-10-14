package com.example.api.reporting;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/** Attach before assertions so schema and semantic failures retain the exchange evidence. */
public final class EvidenceFilter implements Filter {
  private final Evidence evidence;

  public EvidenceFilter(Evidence evidence) {
    this.evidence = evidence;
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
          "Transport failure", "HTTP exchange failed; endpoint and exception details withheld");
      throw new IllegalStateException(
          "HTTP exchange failed; check connectivity, certificates and timeouts");
    }
    evidence.attach(
        "HTTP response",
        "Status: "
            + response.statusCode()
            + "\n"
            + evidence.headers(response.headers())
            + "\n"
            + evidence.body(response.asString()));
    return response;
  }
}

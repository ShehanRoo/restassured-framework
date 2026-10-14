package com.example.api.client;

import com.example.api.contract.JsonSupport;
import com.example.api.reporting.Evidence;
import com.fasterxml.jackson.databind.JsonNode;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import java.util.Objects;

/** Assertions expose selected JSON pointers, never raw bodies or transport exception messages. */
public final class ApiResponse {
  private final Response response;
  private final Evidence evidence;

  ApiResponse(Response response, Evidence evidence) {
    this.response = response;
    this.evidence = evidence;
  }

  public int statusCode() {
    return response.statusCode();
  }

  public ApiResponse assertStatus(int expected) {
    Allure.step(
        "Assert response status " + expected,
        () -> {
          if (response.statusCode() != expected) {
            throw new AssertionError(
                "Expected HTTP " + expected + ", received HTTP " + response.statusCode());
          }
        });
    return this;
  }

  public ApiResponse assertField(String pointer, Object expected) {
    if (pointer == null || !pointer.matches("(/[a-zA-Z0-9_-]+)+")) {
      throw new IllegalArgumentException(
          "Use a static JSON pointer containing safe field names or array indices");
    }
    if (!JsonSupport.isJsonScalar(expected)) {
      throw new IllegalArgumentException(
          "Field assertions accept primitive JSON expectations only");
    }
    Allure.step(
        "Assert response field " + pointer,
        () -> {
          JsonNode root;
          try {
            root = JsonSupport.parse(response.asString());
          } catch (Exception exception) {
            throw new AssertionError("Response JSON could not be read");
          }
          JsonNode actual = root.at(pointer);
          JsonNode expectedNode;
          boolean equal;
          try {
            expectedNode = JsonSupport.node(expected);
            equal =
                actual.isNumber() && expectedNode.isNumber()
                    ? actual.decimalValue().compareTo(expectedNode.decimalValue()) == 0
                    : Objects.equals(actual, expectedNode);
          } catch (Exception exception) {
            throw new AssertionError("Response expectation could not be evaluated safely");
          }
          if (actual.isMissingNode() || !equal) {
            Object display = actual.isValueNode() ? JsonSupport.primitive(actual) : null;
            evidence.attach(
                "Failed response assertion",
                "Field: "
                    + pointer
                    + "\nExpected: "
                    + evidence.value(pointer, expected)
                    + "\nActual: "
                    + (actual.isMissingNode() ? "[missing]" : evidence.value(pointer, display)));
            throw new AssertionError(
                "Response assertion failed at "
                    + pointer
                    + "; see sanitized expected/actual attachment");
          }
        });
    return this;
  }
}

package com.example.api.support;

import com.example.api.contract.BodyContract;
import com.example.api.contract.EndpointContract;
import io.restassured.http.Method;
import java.util.Map;

/** Version-controlled API contracts are shared by mocks and live endpoint clients. */
public final class UserContracts {
  private UserContracts() {}

  public static final BodyContract USER = BodyContract.json("schemas/user.json");
  public static final BodyContract ERROR = BodyContract.json("schemas/error.json");
  public static final EndpointContract CREATE =
      new EndpointContract(
          "Create user",
          Method.POST,
          "/users",
          BodyContract.json("schemas/create-user.json"),
          Map.of(201, USER, 400, ERROR));
  public static final EndpointContract GET =
      new EndpointContract(
          "Read user",
          Method.GET,
          "/users/{id}",
          BodyContract.empty(),
          Map.of(200, USER, 401, ERROR, 403, ERROR, 404, ERROR));
  public static final EndpointContract DELETE =
      new EndpointContract(
          "Delete user",
          Method.DELETE,
          "/users/{id}",
          BodyContract.empty(),
          Map.of(204, BodyContract.empty(), 404, ERROR));
}

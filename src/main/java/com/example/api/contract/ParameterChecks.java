package com.example.api.contract;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

/** Validate and snapshot parameters before transport; never echo dynamic keys or values. */
public final class ParameterChecks {
  private ParameterChecks() {}

  public static Map<String, ?> validate(
      EndpointContract contract, Map<String, ?> parameters, boolean path) {
    Map<String, Object> snapshot = new HashMap<>();
    try {
      if (parameters == null || parameters.size() > 32) {
        throw new IllegalArgumentException();
      }
      for (var entry : parameters.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (key == null
            || !key.matches("[a-zA-Z][a-zA-Z0-9_-]{0,79}")
            || value == null
            || !JsonSupport.isJsonScalar(value)) {
          throw new IllegalArgumentException();
        }
        String text = String.valueOf(value);
        if (text.length() > 2048
            || text.chars().anyMatch(Character::isISOControl)
            || (value instanceof Double number && !Double.isFinite(number))
            || (value instanceof Float number && !Float.isFinite(number))
            || (path && !text.matches("[a-zA-Z0-9_-]{1,200}"))) {
          throw new IllegalArgumentException();
        }
        snapshot.put(key, value);
      }
      if (path) {
        var names = new HashSet<String>();
        var matcher = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}").matcher(contract.path());
        while (matcher.find()) {
          names.add(matcher.group(1));
        }
        if (!names.equals(snapshot.keySet())) {
          throw new IllegalArgumentException();
        }
      }
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid request parameters; keys and values withheld");
    }
    String json;
    try {
      json = JsonSupport.serialize(snapshot);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Request parameters could not be encoded safely");
    }
    SchemaChecks.validateParameters(
        path ? contract.pathParameters() : contract.queryParameters(), json);
    return Map.copyOf(snapshot);
  }
}

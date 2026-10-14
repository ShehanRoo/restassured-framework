package com.example.api.contract;

/** Every body is explicitly JSON with a schema, or absent (including 204 responses). */
public record BodyContract(String schemaResource) {
  public BodyContract {
    if (schemaResource != null && !schemaResource.matches("schemas/[a-zA-Z0-9/_-]+\\.json")) {
      throw new IllegalArgumentException(
          "Schemas must be trusted classpath resources under schemas/");
    }
  }

  public static BodyContract json(String resource) {
    if (resource == null) {
      throw new IllegalArgumentException("A JSON schema is required");
    }
    return new BodyContract(resource);
  }

  public static BodyContract empty() {
    return new BodyContract(null);
  }

  public boolean hasBody() {
    return schemaResource != null;
  }
}

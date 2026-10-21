package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
/** Independent v2 locator publication; published dataset v1 remains unchanged. */
final class SourceLocatorSchema {
 static final String IMAGE_SHA256="b93cead506584a3899be6dd5a3f7a66420291f67e00d796112620e7e24f77d5c";
 static void validate(SchemaCatalogue catalogue,JsonNode value)throws java.io.IOException {
  String kind=value.path("kind").asText();
  TypedSchemaBindings.bind(catalogue,"IMAGE".equals(kind)||"HTML".equals(kind)?SourceEvidenceBindings.LOCATOR_V2:DatasetBindings.LOCATOR,value);
 }
}

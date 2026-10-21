package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
public final class ArtifactEvidenceBindings {
 private ArtifactEvidenceBindings() { }
 /** Only freshly authored internal payloads. Stored boundaries must use declared publication overload. */
 public static <T> TypedSchemaBindings.Binding<T> bind(SchemaCatalogue catalogue,TypedSchemaBindings.Definition<T> original,JsonNode value)throws IOException {
  boolean v2=value.has("schema_version")?"2.0.0".equals(value.path("schema_version").asText()):containsV2(value);
  return TypedSchemaBindings.bind(catalogue,v2?TypedSchemaBindings.withSchema(original,"artifacts-v2"):original,value);
 }
 public static <T> TypedSchemaBindings.Binding<T> bind(SchemaCatalogue catalogue,TypedSchemaBindings.Definition<T> original,JsonNode value,String ref,String sha)throws IOException {
  var publication=catalogue.requirePublished(ref,sha);
  if(!"artifacts".equals(original.schema())||(!"artifacts".equals(publication.name())&&!"artifacts-v2".equals(publication.name()))||!java.util.Objects.equals(original.name(),publication.definition()))throw new IllegalArgumentException("ARTIFACT_PUBLICATION_MISMATCH");
  return TypedSchemaBindings.bind(catalogue,TypedSchemaBindings.withSchema(original,publication.name()),value);
 }
 private static boolean containsV2(JsonNode value) {
  if(value.isObject()&&value.has("locator_kind")&&value.has("source_version")&&"2.0.0".equals(value.path("schema_version").asText()))return true;
  if(value.isContainerNode())for(var child:value)if(containsV2(child))return true;return false;
 }
}

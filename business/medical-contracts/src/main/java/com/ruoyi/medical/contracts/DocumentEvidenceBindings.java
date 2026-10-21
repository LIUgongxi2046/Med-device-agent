package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
public final class DocumentEvidenceBindings {
 private DocumentEvidenceBindings() { }
 public static final TypedSchemaBindings.Definition<InterfaceProtocol.DocumentBody> DOCUMENT_BODY_V2=TypedSchemaBindings.withSchema(InterfaceBindings.DOCUMENT_BODY,"document-body-v2");
 public static <T> TypedSchemaBindings.Binding<T> bind(SchemaCatalogue c,TypedSchemaBindings.Definition<T> original,JsonNode value,String ref,String sha)throws IOException {
  var p=c.requirePublished(ref,sha);if(!"interfaces".equals(original.schema())||(!"interfaces".equals(p.name())&&!"document-body-v2".equals(p.name()))||!java.util.Objects.equals(original.name(),p.definition()))throw new IllegalArgumentException("DOCUMENT_PUBLICATION_MISMATCH");
  return TypedSchemaBindings.bind(c,TypedSchemaBindings.withSchema(original,p.name()),value);
 }
}

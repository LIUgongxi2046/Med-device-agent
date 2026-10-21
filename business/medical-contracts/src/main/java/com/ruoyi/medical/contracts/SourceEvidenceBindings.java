package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
public final class SourceEvidenceBindings {
 private SourceEvidenceBindings() { }
 public static final TypedSchemaBindings.Definition<DatasetProtocol.EvidenceRef> EVIDENCE_V2=TypedSchemaBindings.definition("source-evidence-v2","EvidenceRef",DatasetProtocol.EvidenceRef.class);
 public static final TypedSchemaBindings.Definition<DatasetProtocol.Locator> LOCATOR_V2=TypedSchemaBindings.definition("source-evidence-v2","locator",DatasetProtocol.Locator.class);
 public static TypedSchemaBindings.Binding<DatasetProtocol.EvidenceRef> bind(SchemaCatalogue catalogue,JsonNode value)throws IOException {
  String version=value.path("schema_version").asText();if(!"1.0.0".equals(version)&&!"2.0.0".equals(version))throw new IllegalArgumentException("EVIDENCE_CONTRACT_UNSUPPORTED");
  return TypedSchemaBindings.bind(catalogue,"2.0.0".equals(version)?EVIDENCE_V2:DatasetBindings.EVIDENCE_REF,value);
 }
}

package com.ruoyi.medical.contracts;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Deferred tests: no execution during the user's no-test stage. */
class SourceEvidenceV2Test {
 @Test void v2ImageIsTypedAndDeclaredV1RejectsIt() throws Exception {
  var c=SchemaCatalogue.fromClasspath();var json=new ObjectMapper();
  var value=json.createObjectNode().put("schema_version","2.0.0").put("tenant_id","tenant").put("project_id","project").put("source_id","source").put("source_version","version").put("parse_version","parse").put("chunk_id","chunk").put("locator_kind","IMAGE").put("quote_hash","b".repeat(64)).put("approval_revision","revision");
  value.putObject("locator").put("schema_version","1.0.0").put("kind","IMAGE").put("frame_index",0).put("original_sha256","a".repeat(64)).put("coordinate_system","ORIGINAL_TOP_LEFT_NORMALIZED").put("width",100).put("height",100).put("x0",0).put("y0",0).put("x1",1).put("y1",1);
  assertEquals(DatasetProtocol.LocatorKind.IMAGE,SourceEvidenceBindings.bind(c,value).read().locatorKind());
  assertInstanceOf(DatasetProtocol.ImageLocator.class,SourceEvidenceBindings.bind(c,value).read().locator());
  assertThrows(Exception.class,()->ArtifactEvidenceBindings.bind(c,ArtifactBindings.EVIDENCE_REF,value,c.schemaRef("artifacts","EvidenceRef"),c.schemaDigest("artifacts")));
  assertEquals("2.0.0",ArtifactEvidenceBindings.bind(c,ArtifactBindings.EVIDENCE_REF,value,c.schemaRef("artifacts-v2","EvidenceRef"),c.schemaDigest("artifacts-v2")).read().schemaVersion());
  value.put("schema_version","1.0.0");assertThrows(Exception.class,()->SourceEvidenceBindings.bind(c,value));
 }
 @Test void v2HtmlIsTypedAndDeclaredV1RejectsIt()throws Exception {
  var c=SchemaCatalogue.fromProject(Path.of(System.getProperty("medical.projectRoot")));var json=new ObjectMapper();
  var value=json.createObjectNode().put("schema_version","2.0.0").put("tenant_id","tenant").put("project_id","project").put("source_id","source").put("source_version","version").put("parse_version","parse").put("chunk_id","chunk").put("locator_kind","HTML").put("quote_hash","b".repeat(64)).put("approval_revision","revision");
  var locator=value.putObject("locator").put("schema_version","1.0.0").put("kind","HTML").put("original_sha256","a".repeat(64));locator.putObject("address").put("kind","HTML_ELEMENT").put("element_path","/html[1]/body[1]/p[1]").put("text_start",0).put("text_end",4);
  assertEquals(DatasetProtocol.LocatorKind.HTML,SourceEvidenceBindings.bind(c,value).read().locatorKind());
  assertThrows(Exception.class,()->ArtifactEvidenceBindings.bind(c,ArtifactBindings.EVIDENCE_REF,value,c.schemaRef("artifacts","EvidenceRef"),c.schemaDigest("artifacts")));
  assertEquals("2.0.0",ArtifactEvidenceBindings.bind(c,ArtifactBindings.EVIDENCE_REF,value,c.schemaRef("artifacts-v2","EvidenceRef"),c.schemaDigest("artifacts-v2")).read().schemaVersion());
  value.put("schema_version","1.0.0");assertThrows(Exception.class,()->SourceEvidenceBindings.bind(c,value));
 }
}

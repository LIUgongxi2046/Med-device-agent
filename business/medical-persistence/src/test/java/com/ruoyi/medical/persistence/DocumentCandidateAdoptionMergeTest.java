package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DocumentCandidateAdoptionMergeTest {
 private final SchemaCatalogue schemas=SchemaCatalogue.fromClasspath();
 private final DocumentBodySemantics.Limits limits=new DocumentBodySemantics.Limits(100,100,1000);
 DocumentCandidateAdoptionMergeTest()throws Exception{}
 @Test void selectedSubtreeChangesButUnselectedBytesDoNot()throws Exception{
  var base=body();var candidate=base.deepCopy();((ObjectNode)candidate.required("nodes").get(2).required("content")).put("text","New candidate");
  String selected=base.required("nodes").get(1).required("node_id").asText();var merged=DocumentCandidateAdoptionMerge.merge(schemas,base,candidate,List.of(selected),List.of(selected),limits);
  assertTrue(merged.required("nodes").toString().contains("New candidate"));assertEquals(base.required("nodes").get(3),merged.required("nodes").get(1));
  assertEquals("Original",base.required("nodes").get(2).required("content").required("text").asText());
 }
 @Test void v2AdoptionUsesExplicitBaselinePublicationAndRejectsCrossVersion()throws Exception{
  var base=body().put("schema_version","2.0.0");var candidate=base.deepCopy();String root=base.required("nodes").get(1).required("node_id").asText();
  var merged=DocumentCandidateAdoptionMerge.merge(schemas,base,candidate,List.of(root),List.of(root),limits,schemas.schemaRef("document-body-v2","DocumentBody"),schemas.schemaDigest("document-body-v2"));
  assertEquals("2.0.0",merged.required("schema_version").asText());
  assertThrows(RuntimeException.class,()->DocumentCandidateAdoptionMerge.merge(schemas,base,candidate,List.of(root),List.of(root),limits));
  candidate.put("schema_version","1.0.0");assertThrows(RuntimeException.class,()->DocumentCandidateAdoptionMerge.merge(schemas,base,candidate,List.of(root),List.of(root),limits,schemas.schemaRef("document-body-v2","DocumentBody"),schemas.schemaDigest("document-body-v2")));
 }
 @Test void rejectsRootScopeDuplicateAndReparenting()throws Exception{
  var base=body();String root=base.required("nodes").get(0).required("node_id").asText(),section=base.required("nodes").get(1).required("node_id").asText();
  assertThrows(TenantAccessException.class,()->DocumentCandidateAdoptionMerge.merge(schemas,base,base,List.of(root),List.of(root),limits));
  assertThrows(TenantAccessException.class,()->DocumentCandidateAdoptionMerge.merge(schemas,base,base,List.of(section),List.of(),limits));
  assertThrows(TenantAccessException.class,()->DocumentCandidateAdoptionMerge.merge(schemas,base,base,List.of(section,section),List.of(section),limits));
 }
 @Test void explicitConfirmationCannotBeFalseOrInitialPartial() {
  assertThrows(IllegalArgumentException.class,()->new DocumentCandidateAdoptionProtocol.ConfirmInput("1.0.0",id(),"a".repeat(64),"1",false));
  assertThrows(IllegalArgumentException.class,()->new DocumentCandidateAdoptionProtocol.PreviewInput("1.0.0",id(),id(),"1","a".repeat(64),DocumentCandidateAdoptionProtocol.Mode.INITIAL,List.of(id())));
 }
 private static ObjectNode body(){var b=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("template_version",id());var nodes=b.putArray("nodes");String root=id(),a=id(),c=id();nodes.add(node(root,null,0,"DOCUMENT","Document"));nodes.add(node(a,root,0,"SECTION","Selected"));nodes.add(node(id(),a,0,"TEXT","Original"));nodes.add(node(c,root,1,"SECTION","Untouched"));nodes.add(node(id(),c,0,"TEXT","Keep exactly"));b.putArray("fields");b.putArray("evidence_links");b.putArray("fact_usages");return b;}
 private static ObjectNode node(String id,String parent,int order,String kind,String value){var n=JsonNodeFactory.instance.objectNode().put("node_id",id).put("parent_id",parent).put("ordinal",order);n.putArray("field_ids");n.putArray("evidence_link_ids");var c=n.putObject("content").put("kind",kind);if(kind.equals("TEXT")){c.put("text",value);c.putArray("marks");}else c.put("title",value);return n;}
 private static String id(){return UUID.randomUUID().toString();}
}

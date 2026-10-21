package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline source tests only. No fixture provides a production permission or model receipt. */
final class TaskSectionWriterNormalizerTest {
    private final TaskSectionWriterSchema json=new TaskSectionWriterSchema();
    private final TaskSectionWriterNormalizer subject=new TaskSectionWriterNormalizer(json,new DocumentBodySemantics.Limits(100,100,1000));
    private final SchemaCatalogue schemas=SchemaCatalogue.fromClasspath();
    private final String tenant=id(),project=id(),task=id(),run=id(),step=id(),inputId=id(),plan=id(),snapshot=id(),section=id(),sectionNode=id(),root=id(),textNode=id(),claim=id(),action=id(),template=id(),bodySchema=id();
    TaskSectionWriterNormalizerTest()throws Exception{}
    @Test void stableHostIdentitiesPreservePlanTemplateDistinctionAndUnicodeAnchors()throws Exception {
        var draft=draft();var copy=draft.deepCopy();var first=subject.normalize(fixed(),action,draft);var again=subject.normalize(fixed(),action,draft);
        assertArrayEquals(first.body(),again.body());assertEquals(first.pins(),again.pins());assertEquals(copy,draft);
        var nodes=json.read(first.body()).required("nodes");assertEquals(root,nodes.get(0).required("node_id").textValue());assertEquals(sectionNode,nodes.get(1).required("node_id").textValue());
        assertNotEquals(textNode,nodes.get(2).required("node_id").textValue());assertEquals(section,first.manifest().get(2).sectionId());
        assertEquals(nodes.get(2).required("node_id").textValue(),((ArtifactProtocol.TextAnchor)first.draft().claimRecords().get(0).anchor()).nodeId());
        assertNotEquals(claim,first.draft().claimRecords().get(0).claimId());
    }
    @Test void actionChangesOnlyNewHostIdentities()throws Exception {var a=subject.normalize(fixed(),action,draft());var b=subject.normalize(fixed(),id(),draft());assertNotEquals(json.read(a.body()).required("nodes").get(2),json.read(b.body()).required("nodes").get(2));assertEquals(json.read(a.body()).required("nodes").get(1),json.read(b.body()).required("nodes").get(1));}
    @Test void rejectsUtf16OffsetsAndForgedQuoteDigest()throws Exception {
        var d=draft();((ObjectNode)d.required("claim_records").get(0).required("anchor")).put("end",4);assertThrows(RuntimeException.class,()->subject.normalize(fixed(),action,d));
        var hash=draft();((ObjectNode)hash.required("claim_records").get(0).required("anchor")).put("text_sha256","0".repeat(64));assertThrows(RuntimeException.class,()->subject.normalize(fixed(),action,hash));
    }
    @Test void rejectsForeignSectionAndChangedExistingKind()throws Exception {
        var d=draft();var body=(ObjectNode)json.read(d.required("body_json").textValue());((ObjectNode)body.required("nodes").get(1)).put("node_id",id());d.put("body_json",json.canonical(body));assertThrows(RuntimeException.class,()->subject.normalize(fixed(),action,d));
        var changed=draft();var b=(ObjectNode)json.read(changed.required("body_json").textValue());((ObjectNode)b.required("nodes").get(1).required("content")).put("kind","PARAGRAPH");changed.put("body_json",json.canonical(b));assertThrows(RuntimeException.class,()->subject.normalize(fixed(),action,changed));
    }
    @Test void refusesWrongSchemaAndIndependentCoverageClaims()throws Exception {
        var wrong=draft();wrong.put("body_schema_revision",id());assertThrows(RuntimeException.class,()->subject.normalize(fixed(),action,wrong));
        var extra=draft();extra.put("coverage_complete",true);assertThrows(Exception.class,()->subject.normalize(fixed(),action,extra));
    }
    @Test void missingChecksNeverBecomeWriterPermission()throws Exception {
        var f=fixed();var missing=new ArtifactProtocol.FieldCheckResult(f.fields().targetArtifactId(),f.fields().ruleRevision(),List.of(),List.of(),List.of(id()));
        assertThrows(TenantAccessException.class,()->subject.normalize(new TaskSectionWriterNormalizer.Fixed(f.input(),f.baseline(),missing,f.evidence(),f.sectionOwners()),action,draft()));
    }
    @Test void byteParserRejectsDuplicateAndTrailingObjects() {
        assertThrows(Exception.class,()->json.read("{\"body_json\":\"one\",\"body_json\":\"two\"}"));assertThrows(Exception.class,()->json.read("{} {}"));
    }
    @Test void immutableNormalizedCopiesDoNotLeakMutableBytesOrPins()throws Exception {var n=subject.normalize(fixed(),action,draft());var bytes=n.body();bytes[0]=0;assertNotEquals(0,n.body()[0]);((ObjectNode)n.pins()).put("implementation","forged");assertEquals("N04_SECTION_NORMALIZATION_V1",n.pins().required("implementation").textValue());}
    @Test void chapterWithoutResearchQuestionsHasNoFabricatedPredecessor()throws Exception {var value=(ObjectNode)json.tree(fixed().input());value.putArray("predecessors");var input=json.input(json.bytes(value));assertTrue(input.predecessors().isEmpty());assertFalse(input.targetSections().isEmpty());}
    private TaskSectionWriterNormalizer.Fixed fixed()throws Exception {
        var source=new TaskNodeInputProtocol.SourceSelection(id(),id(),id(),id(),id(),id(),"SECTION_WRITING");
        var predecessor=new TaskNodeInputProtocol.Predecessor(id(),id(),hash(),"result-v",hash(),id(),hash(),"evidence-v",hash(),hash());
        var permission=new RunTokenAuthoritySchema.Permission(new PermissionProtocol.Scope(tenant,project,"TASK",task),"task.candidate.submit","SECTION_WRITING");
        var input=new TaskSectionWriterInput("1.0.0","N04",tenant,project,task,run,step,inputId,id(),hash(),hash(),plan,hash(),snapshot,basis(),basis(),basis(),basis(),id(),id(),bodySchema,schemas.schemaDigest("interfaces"),template,List.of(section),id(),new InterfaceProtocol.ObjectRef("test/field","v1",hash(),"2","application/json"),hash(),List.of(predecessor),List.of(source),List.of(permission));
        return new TaskSectionWriterNormalizer.Fixed(input,body(false),new ArtifactProtocol.FieldCheckResult(id(),id(),List.of(),List.of(),List.of()),List.of(),Map.of(sectionNode,section));
    }
    @Test void explicitV2BodyCannotPassLegacyNormalizer()throws Exception {
        var draft=draft();var body=body(true);body.put("schema_version","2.0.0");draft.put("body_json",json.canonical(body));
        assertThrows(Exception.class,()->subject.normalize(fixed(),action,draft));
    }
    @Test void rolePublicationCannotSelectAnotherArtifactOrWrongDigest()throws Exception {
        var declaration=(ObjectNode)json.tree(Map.of("output_schema_ref",Map.of("schema_ref",schemas.schemaRef("artifacts-v2","SectionDraft"),"schema_sha256",schemas.schemaDigest("artifacts-v2"))));
        assertTrue(TaskWriterEvidenceContract.require(declaration,schemas).v2());
        ((ObjectNode)declaration.required("output_schema_ref")).put("schema_ref",schemas.schemaRef("artifacts-v2","EvidenceBundle"));
        assertThrows(Exception.class,()->TaskWriterEvidenceContract.require(declaration,schemas));
    }
    private ObjectNode draft()throws Exception {
        var d=(ObjectNode)json.tree(Map.of("body_schema_revision",bodySchema,"body_json",json.canonical(body(true)),"claim_records",List.of(),"field_assignments",List.of(),"attachment_bindings",List.of(),"issue_proposals",List.of()));
        var c=d.putArray("claim_records").addObject().put("claim_id",claim).put("claim_kind","TRANSITION");c.putArray("evidence_links");c.putObject("anchor").put("kind","TEXT").put("node_id",textNode).put("start",1).put("end",2).put("text_sha256",WireValues.sha256("😀".getBytes(StandardCharsets.UTF_8)));return d;
    }
    private ObjectNode body(boolean addText){var b=(ObjectNode)json.tree(Map.of("schema_version","1.0.0","template_version",template,"nodes",List.of(),"fields",List.of(),"evidence_links",List.of(),"fact_usages",List.of()));var nodes=b.putArray("nodes");var r=nodes.addObject().put("node_id",root).putNull("parent_id").put("ordinal",0);r.putArray("field_ids");r.putArray("evidence_link_ids");r.putObject("content").put("kind","DOCUMENT").put("title","Draft");var s=nodes.addObject().put("node_id",sectionNode).put("parent_id",root).put("ordinal",0);s.putArray("field_ids");s.putArray("evidence_link_ids");s.putObject("content").put("kind","SECTION").put("title","Chapter");if(addText){var t=nodes.addObject().put("node_id",textNode).put("parent_id",sectionNode).put("ordinal",0);t.putArray("field_ids");t.putArray("evidence_link_ids");t.putObject("content").put("kind","TEXT").put("text","A😀B").putArray("marks");}return b;}
    private static String id(){return UUID.randomUUID().toString();}private static String hash(){return "a".repeat(64);}private static PermissionProtocol.Basis basis(){return new PermissionProtocol.Basis(id(),hash());}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred offline tests. Fixture data cannot instantiate the private production proof. */
final class TaskCandidateAssemblyNormalizerTest {
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final TaskCandidateAssemblyNormalizer subject=new TaskCandidateAssemblyNormalizer(json);
    private final String template=id(),base=id(),schema=id(),root=id(),sectionA=id(),sectionB=id(),nodeA=id(),nodeB=id(),rule=id();
    TaskCandidateAssemblyNormalizerTest()throws Exception{}
    @Test void exactChaptersProduceCandidateSetNotDocumentApproval()throws Exception {
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(),List.of(),List.of())));
        assertTrue(result.ready());assertEquals(ArtifactProtocol.AssemblyKind.CANDIDATE_SET,result.manifest().targetKind());assertNull(result.manifest().documentRevision());
        assertEquals(List.of(sectionA,sectionB),result.manifest().sections().stream().map(ArtifactProtocol.SectionVersion::sectionId).toList());
        assertEquals(3,result.body().required("nodes").size());assertEquals(2,result.report().required("identity_mappings").size());
        assertFalse(result.report().required("document_saved").booleanValue());assertFalse(result.report().required("n07_complete").booleanValue());
        assertFalse(result.report().required("professional_approval").booleanValue());
    }
    @Test void missingChapterBlocksWithoutInventingItsIdentity()throws Exception {
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA)),view(List.of(),List.of(),List.of())));
        assertFalse(result.ready());assertTrue(result.blockers().contains("ASSIGNED_CHAPTER_SET_INCOMPLETE"));assertEquals(1,result.manifest().sections().size());assertFalse(result.report().required("impact").required("complete").booleanValue());
    }
    @Test void duplicateAssignedChapterRejected()throws Exception {
        var a=candidate(sectionA,nodeA);assertThrows(TenantAccessException.class,()->subject.normalize(fixed(List.of(a,a),view(List.of(),List.of(),List.of()))));
    }
    @Test void changedBodyCannotKeepOriginalHash()throws Exception {
        var a=candidate(sectionA,nodeA);var forged=new TaskCandidateAssemblyNormalizer.Candidate(a.id(),a.section(),a.body(),"0".repeat(64),a.evidence(),a.checkId(),a.findingsArtifactId());
        assertThrows(TenantAccessException.class,()->subject.normalize(fixed(List.of(forged,candidate(sectionB,nodeB)),view(List.of(),List.of(),List.of()))));
    }
    @Test void crossFileRuleCannotBeSatisfiedByChapterChecks()throws Exception {
        var cross=new TemplateProtocol.CrossDocumentRule(id(),basis(),List.of(),List.of("OTHER_DOCUMENT"),"BLOCKING");
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(),List.of(cross),List.of())));
        assertFalse(result.ready());assertTrue(result.blockers().contains("CROSS_FILE_INPUT_REQUIRED:"+cross.rule_id()));assertFalse(result.report().required("impact").required("complete").booleanValue());
    }
    @Test void actualHostRulePassAndFailureAreKeptDistinct()throws Exception {
        var cross=new TemplateProtocol.CrossDocumentRule(id(),basis(),List.of(),List.of("OTHER_DOCUMENT"),"BLOCKING");
        var fixed=fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(),List.of(cross),List.of()));
        var pass=json.tree(Map.of("rule_id",cross.rule_id(),"status","PASS"));var failure=json.tree(Map.of("rule_id",cross.rule_id(),"status","FAIL"));
        assertTrue(subject.normalize(fixed,Map.of(cross.rule_id(),pass)).ready());
        var failed=subject.normalize(fixed,Map.of(cross.rule_id(),failure));assertFalse(failed.ready());assertTrue(failed.blockers().contains("CROSS_FILE_FAIL:"+cross.rule_id()));
    }
    @Test void incompleteFactImpactBlocksEvenWithoutCrossFileRules()throws Exception {
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(),List.of(),List.of())),Map.of(),List.of("FACT_IMPACT_SELECTION_INCOMPLETE"));
        assertFalse(result.ready());assertFalse(result.report().required("impact").required("complete").booleanValue());
    }
    @Test void structuralAnchorsAreReboundWithoutConvertingCandidateChecks()throws Exception {
        var original=candidate(sectionB,nodeB);var node=original.body().required("nodes").get(1);var claim=new ArtifactProtocol.ClaimRecord(id(),new ArtifactProtocol.StructuredAnchor("STRUCTURED",nodeB,null,null,json.hash(node)),ArtifactProtocol.ClaimKind.TRANSITION,List.of());
        var section=original.section();var withClaim=new ArtifactProtocol.SectionCandidate(section.baseDocumentRevision(),section.targetSections(),section.bodyObjectRef(),section.bodySchemaRevision(),section.nodeManifest(),List.of(claim),section.fieldAssignments(),section.attachmentBindings(),section.issueProposals());
        var candidate=new TaskCandidateAssemblyNormalizer.Candidate(original.id(),withClaim,original.body(),original.bodySha256(),original.evidence(),original.checkId(),original.findingsArtifactId());
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate),view(List.of(),List.of(),List.of())));
        var mapping=result.report().required("claim_anchor_mappings").get(0);assertNotEquals(mapping.required("candidate_anchor").required("content_sha256"),mapping.required("assembly_anchor").required("content_sha256"));
        assertTrue(result.manifest().checkIds().contains(original.checkId()));assertFalse(result.report().required("n07_complete").booleanValue());
    }
    @Test void missingRequiredAttachmentAndUnknownApplicabilityRemainBlocked()throws Exception {
        var attachment=new TemplateProtocol.Attachment(id(),nodeA,"Report","REPORT","REAL",null,true,"1",null,List.of(),List.of("application/pdf"),null,0);
        var condition=new TemplateApplicabilityProtocol.ConditionResult("ATTACHMENT",attachment.attachment_requirement_id(),null,null,List.of(),"UNKNOWN",List.of("requires_report"));
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(attachment),List.of(),List.of(condition))));
        assertFalse(result.ready());assertTrue(result.blockers().contains("ATTACHMENT_MISSING:"+attachment.attachment_requirement_id()));assertTrue(result.blockers().contains("APPLICABILITY_UNKNOWN:"+attachment.attachment_requirement_id()));
    }
    @Test void nodeIdentityCollisionBetweenChaptersIsRejected()throws Exception {
        assertThrows(TenantAccessException.class,()->subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeA)),view(List.of(),List.of(),List.of()))));
    }
    @Test void bodyAndReportCopiesCannotChangeStoredResult()throws Exception {
        var result=subject.normalize(fixed(List.of(candidate(sectionA,nodeA),candidate(sectionB,nodeB)),view(List.of(),List.of(),List.of())));
        ((ObjectNode)result.body()).put("schema_version","forged");((ObjectNode)result.report()).put("n07_complete",true);
        assertEquals("1.0.0",result.body().required("schema_version").asText());assertFalse(result.report().required("n07_complete").booleanValue());
    }
    private TaskCandidateAssemblyNormalizer.Fixed fixed(List<TaskCandidateAssemblyNormalizer.Candidate> candidates,TemplateApplicabilityProtocol.TemplateView view){return new TaskCandidateAssemblyNormalizer.Fixed(base,schema,List.of(sectionA,sectionB),List.of(nodeA,nodeB),view,body(nodeA,nodeB),rule,candidates);}
    private TaskCandidateAssemblyNormalizer.Candidate candidate(String section,String node)throws Exception {
        var body=body(node);var candidate=new ArtifactProtocol.SectionCandidate(base,List.of(section),new ArtifactProtocol.ObjectRef("test/body","v1",json.hash(body),json.bytes(body).length,"application/json"),schema,List.of(),List.of(),List.of(),List.of(),List.of());
        return new TaskCandidateAssemblyNormalizer.Candidate(id(),candidate,body,json.hash(body),Set.of(),id(),id());
    }
    private TemplateApplicabilityProtocol.TemplateView view(List<TemplateProtocol.Attachment> attachments,List<TemplateProtocol.CrossDocumentRule> cross,List<TemplateApplicabilityProtocol.ConditionResult> conditions){
        var format=new TemplateProtocol.FormatProfile(basis(),List.of("PDF"),basis(),List.of(),List.of(),"TEMPLATE");
        var content=new TemplateProtocol.Content("1.0.0","TEST","TEST",false,"TEST",List.of(),List.of(),List.of(),attachments,cross,null,List.of(),format);
        var stored=new TemplateProtocol.Stored(id(),template,"APPROVED","1",content,"a".repeat(64),null,"Test",id(),"1",id(),"2026-10-20T00:00:00Z",List.of());
        return new TemplateApplicabilityProtocol.TemplateView(stored,"APPLICABLE",conditions,List.of(),"a".repeat(64));
    }
    private ObjectNode body(String... sectionNodes){var body=(ObjectNode)json.tree(Map.of("schema_version","1.0.0","template_version",template,"nodes",List.of(),"fields",List.of(),"evidence_links",List.of(),"fact_usages",List.of()));var nodes=body.putArray("nodes");var r=nodes.addObject().put("node_id",root).putNull("parent_id").put("ordinal",0);r.putArray("field_ids");r.putArray("evidence_link_ids");r.putObject("content").put("kind","DOCUMENT").put("title","Draft");int i=0;for(String node:sectionNodes){var n=nodes.addObject().put("node_id",node).put("parent_id",root).put("ordinal",i++);n.putArray("field_ids");n.putArray("evidence_link_ids");n.putObject("content").put("kind","SECTION").put("title","Chapter");}return body;}
    private static String id(){return UUID.randomUUID().toString();}private static PermissionProtocol.Basis basis(){return new PermissionProtocol.Basis(id(),"a".repeat(64));}
}

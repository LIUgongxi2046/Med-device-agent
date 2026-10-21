package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline tests. Fixtures are data, never production or original-read permissions. */
final class TaskSourceVerifierNormalizerTest {
    private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();
    private final TaskSourceVerifierNormalizer subject=new TaskSourceVerifierNormalizer(json);
    private final String target=id(),rule=id(),claim=id(),node=id(),session=id(),writer=id(),revision=id(),action=id();
    @Test void absentChecksRemainVisibleAndIncomplete()throws Exception {
        var result=subject.normalize(fixed(),action,projection(false));assertFalse(result.findings().coverageComplete());
        assertEquals(ArtifactProtocol.SupportCheck.INSUFFICIENT,result.findings().claimChecks().get(0).supportCheck());
        assertEquals(ArtifactProtocol.RuleResult.BLOCKED,result.findings().ruleChecks().get(0).result());
        assertTrue(result.findings().issueProposals().stream().anyMatch(i->i.code().equals("UNCOVERED_CLAIM")));
        assertTrue(result.findings().issueProposals().stream().anyMatch(i->i.code().equals("UNCOVERED_RULE")));
    }
    @Test void modelCannotShrinkRequiredClaimsOrRules()throws Exception {
        var p=projection(false);p.putArray("required_claim_ids");assertThrows(TenantAccessException.class,()->subject.normalize(fixed(),action,p));
        var r=projection(false);r.putArray("required_rule_ids");assertThrows(TenantAccessException.class,()->subject.normalize(fixed(),action,r));
    }
    @Test void writerSessionCannotBecomeIndependentReviewer()throws Exception {
        var f=fixed();assertThrows(TenantAccessException.class,()->new TaskSourceVerifierNormalizer.Fixed(target,f.bodySha256(),revision,writer,Set.of(writer),f.body(),f.claims(),f.rules(),f.reads(),f.readRuleIds(),f.evidence()));
    }
    @Test void validAccessDoesNotInventSupport()throws Exception {
        var p=projection(true);((ObjectNode)p.required("claim_checks").get(0)).put("support_check","INSUFFICIENT");
        var result=subject.normalize(fixed(),action,p);assertEquals(ArtifactProtocol.AccessCheck.PASS,result.findings().claimChecks().get(0).accessCheck());
        assertEquals(ArtifactProtocol.SupportCheck.INSUFFICIENT,result.findings().claimChecks().get(0).supportCheck());
        assertEquals("BLOCKING",result.findings().issueProposals().get(0).severityCode());
    }
    @Test void unicodePositionIsCodePointBased()throws Exception {
        assertTrue(subject.anchorValid(anchor(),body()));
        assertFalse(subject.anchorValid(new ArtifactProtocol.TextAnchor("TEXT",node,1,3,hash("😀")),body()));
    }
    @Test void originalRuleReadIsRequiredEvenWhenModelSaysPass()throws Exception {
        var f=fixed();var notRead=new TaskSourceVerifierNormalizer.Fixed(target,f.bodySha256(),revision,session,Set.of(writer),f.body(),f.claims(),f.rules(),f.reads(),Set.of(),f.evidence());
        var result=subject.normalize(notRead,action,projection(true));assertFalse(result.findings().coverageComplete());assertEquals(ArtifactProtocol.RuleResult.BLOCKED,result.findings().ruleChecks().get(0).result());
    }
    @Test void omittedBodyContentCannotBeHiddenByClaimSubset()throws Exception {
        var result=subject.normalize(fixed(),action,projection(true));assertFalse(result.findings().coverageComplete());
        assertTrue(result.findings().issueProposals().stream().anyMatch(i->i.code().equals("UNCOVERED_CONTENT")));
    }
    @Test void deterministicCompletionFailureCannotBeOverridden()throws Exception {
        var f=fixed();var rules=Map.of(rule,new TaskSourceVerifierNormalizer.Rule(rule,List.of(node),anchor(),ArtifactProtocol.RuleResult.FAIL));
        var constrained=new TaskSourceVerifierNormalizer.Fixed(target,f.bodySha256(),revision,session,Set.of(writer),f.body(),f.claims(),rules,f.reads(),f.readRuleIds(),f.evidence());
        assertEquals(ArtifactProtocol.RuleResult.FAIL,subject.normalize(constrained,action,projection(true)).findings().ruleChecks().get(0).result());
    }
    @Test void missingOriginalReadCannotRemainSupported()throws Exception {
        var f=sourceFixed(false,false);var p=projection(true);((ObjectNode)p.required("claim_checks").get(0)).put("support_check","SUPPORTED");
        var check=subject.normalize(f,action,p).findings().claimChecks().get(0);assertEquals(ArtifactProtocol.AccessCheck.FAIL,check.accessCheck());assertEquals(ArtifactProtocol.SupportCheck.INSUFFICIENT,check.supportCheck());
    }
    @Test void locatorFailureDowngradesSupportNotAccess()throws Exception {
        var p=projection(true);((ObjectNode)p.required("claim_checks").get(0)).put("support_check","SUPPORTED");
        var check=subject.normalize(sourceFixed(true,false),action,p).findings().claimChecks().get(0);assertEquals(ArtifactProtocol.AccessCheck.PASS,check.accessCheck());assertEquals(ArtifactProtocol.LocatorCheck.FAIL,check.locatorCheck());assertEquals(ArtifactProtocol.SupportCheck.INSUFFICIENT,check.supportCheck());
    }
    @Test void contradictionSurvivesWriterSupportLink()throws Exception {
        var p=projection(true);((ObjectNode)p.required("claim_checks").get(0)).put("support_check","CONTRADICTED");
        var result=subject.normalize(sourceFixed(true,true),action,p);assertEquals(ArtifactProtocol.SupportCheck.CONTRADICTED,result.findings().claimChecks().get(0).supportCheck());assertTrue(result.findings().issueProposals().stream().anyMatch(i->i.code().equals("CONFLICTING_CLAIM")));
    }
    private TaskSourceVerifierNormalizer.Fixed sourceFixed(boolean present,boolean located)throws Exception {
        var f=fixed();var ref=new DatasetProtocol.EvidenceRef("1.0.0",id(),id(),id(),id(),id(),id(),DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),hash("x"),id());
        var c=new ArtifactProtocol.ClaimRecord(claim,anchor(),ArtifactProtocol.ClaimKind.PRODUCT_FACT,List.of(new ArtifactProtocol.EvidenceLink(ref,ArtifactProtocol.Relation.SUPPORT)));
        var reads=present?Map.of(claim,List.of(new TaskSourceVerifierNormalizer.Read(session,id(),ref,true,located,located))):Map.<String,List<TaskSourceVerifierNormalizer.Read>>of();
        return new TaskSourceVerifierNormalizer.Fixed(target,f.bodySha256(),revision,session,Set.of(writer),f.body(),Map.of(claim,c),f.rules(),reads,f.readRuleIds(),Set.of(ref));
    }
    @Test void documentReviewNeverRelabelsCandidatePass()throws Exception {
        assertThrows(TenantAccessException.class,()->subject.normalizeDocument(fixed(),action,projection(true)));
        var document=projection(true);document.put("target_kind","DOCUMENT");
        var result=subject.normalizeDocument(fixed(),action,document);
        assertEquals(ArtifactProtocol.TargetKind.DOCUMENT,result.findings().targetKind());
        assertFalse(result.findings().coverageComplete());
        assertEquals("N07_INDEPENDENT_REVIEW_V1",result.pins().required("implementation").asText());
        assertThrows(TenantAccessException.class,()->subject.normalize(fixed(),action,document));
    }
    @Test void changedSavedBodyRejectsOldDocumentFindings()throws Exception {
        var document=projection(true);document.put("target_kind","DOCUMENT");document.put("target_sha256",hash("older body"));
        assertThrows(TenantAccessException.class,()->subject.normalizeDocument(fixed(),action,document));
    }
    @Test void duplicateAndTrailingJsonRejected(){assertThrows(Exception.class,()->json.read("{\"x\":1,\"x\":2}"));assertThrows(Exception.class,()->json.read("{} {}"));}
    private TaskSourceVerifierNormalizer.Fixed fixed()throws Exception {
        var c=new ArtifactProtocol.ClaimRecord(claim,anchor(),ArtifactProtocol.ClaimKind.TRANSITION,List.of());
        return new TaskSourceVerifierNormalizer.Fixed(target,json.hash(body()),revision,session,Set.of(writer),body(),Map.of(claim,c),Map.of(rule,new TaskSourceVerifierNormalizer.Rule(rule,List.of(node),anchor())),Map.of(),Set.of(rule),Set.of());
    }
    private ObjectNode projection(boolean checks)throws Exception {
        var c=new ArtifactProtocol.ClaimCheck(claim,ArtifactProtocol.AccessCheck.PASS,ArtifactProtocol.LocatorCheck.PASS,ArtifactProtocol.SupportCheck.NOT_APPLICABLE,List.of(rule),List.of(),"This is only a transition.");
        var r=new ArtifactProtocol.RuleCheck(rule,List.of(node),ArtifactProtocol.RuleResult.PASS,List.of(),"Required structure is present.");
        return (ObjectNode)json.tree(new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.CANDIDATE,target,json.hash(body()),revision,List.of(claim),List.of(rule),checks?List.of(c):List.of(),checks?List.of(r):List.of(),List.of(),true));
    }
    private ObjectNode body(){return (ObjectNode)json.tree(Map.of("nodes",List.of(Map.of("node_id",node,"content",Map.of("kind","TEXT","text","a😀b"))),"fields",List.of()));}
    private ArtifactProtocol.TextAnchor anchor(){return new ArtifactProtocol.TextAnchor("TEXT",node,1,2,hash("😀"));}
    private static String hash(String s){return assertDoesNotThrow(()->WireValues.sha256(s.getBytes(StandardCharsets.UTF_8)));}private static String id(){return UUID.randomUUID().toString();}
}

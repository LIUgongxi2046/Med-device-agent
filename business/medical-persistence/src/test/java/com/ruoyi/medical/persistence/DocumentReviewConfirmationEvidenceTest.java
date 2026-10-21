package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred B09 tests. No execution during code-first delivery. */
class DocumentReviewConfirmationEvidenceTest {
 private static final String ID="00000000-0000-4000-8000-000000000001";
 private static ArtifactProtocol.ReviewFindings findings(boolean complete,List<ArtifactProtocol.ClaimCheck> claims,List<ArtifactProtocol.RuleCheck> rules){return new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.DOCUMENT,ID,"0".repeat(64),ID,claims.stream().map(ArtifactProtocol.ClaimCheck::claimId).toList(),rules.stream().map(ArtifactProtocol.RuleCheck::ruleId).toList(),claims,rules,List.of(),complete);}
 @Test void noEvidenceStillRequiresActualCompleteCoverage(){assertThrows(TenantAccessException.class,()->DocumentReviewConfirmationService.requireNoEvidence(findings(false,List.of(),List.of()),JsonNodeFactory.instance.objectNode()));}
 @Test void supportedClaimCannotBeLaunderedAsNoEvidence(){var claim=new ArtifactProtocol.ClaimCheck(ID,ArtifactProtocol.AccessCheck.PASS,ArtifactProtocol.LocatorCheck.PASS,ArtifactProtocol.SupportCheck.SUPPORTED,List.of(ID),List.of(),"claim requires support");assertThrows(TenantAccessException.class,()->DocumentReviewConfirmationService.requireNoEvidence(findings(true,List.of(claim),List.of()),JsonNodeFactory.instance.objectNode()));}
 @Test void nonDeterministicPassingRuleDoesNotProveEvidenceUnnecessary(){var rule=new ArtifactProtocol.RuleCheck(ID,List.of(),ArtifactProtocol.RuleResult.PASS,List.of(),"professional pass");assertThrows(TenantAccessException.class,()->DocumentReviewConfirmationService.requireNoEvidence(findings(true,List.of(),List.of(rule)),JsonNodeFactory.instance.objectNode()));}
 @Test void deterministicCompletionCanBeEvidenceNotRequired(){var rule=new ArtifactProtocol.RuleCheck(ID,List.of(),ArtifactProtocol.RuleResult.PASS,List.of(),"deterministic completion");var manifest=JsonNodeFactory.instance.objectNode();manifest.putObject("completion_results").put(ID,"PASS");assertDoesNotThrow(()->DocumentReviewConfirmationService.requireNoEvidence(findings(true,List.of(),List.of(rule)),manifest));}
}

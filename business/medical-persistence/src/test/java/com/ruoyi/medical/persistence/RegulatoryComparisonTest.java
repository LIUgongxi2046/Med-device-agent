package com.ruoyi.medical.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** DEFERRED: synthetic unit fixtures only, never real sources, runtime seeds or approved acceptance samples. */
final class RegulatoryComparisonTest {
 final RegulatoryComparisonSchema schema=new RegulatoryComparisonSchema();
 String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}
 PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),"a".repeat(64));}
 RegulatoryComparisonSchema.Edition edition(int n){return new RegulatoryComparisonSchema.Edition(basis(n),id(n+1),id(n+2),id(n+3),"SYNTHETIC LAW", "fixture "+n,"fixture jurisdiction","2026-01-01");}
 DatasetProtocol.EvidenceRef evidence(int n){return new DatasetProtocol.EvidenceRef("1.0.0",id(90),id(91),id(n+4),id(n),id(n+1),"fixture-chunk",DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),"a".repeat(64),id(n+2));}
 RegulatoryComparisonSchema.Selection selection(){return new RegulatoryComparisonSchema.Selection("1.0.0","REGULATION_COMPARISON","2026-10-20","SELECTED_CLAUSES",edition(1),edition(11),List.of(new RegulatoryComparisonSchema.Clause("fixture-clause",evidence(1),evidence(11),null)));}
 @Test void roundtripAndNoExtraFields()throws Exception{var n=(ObjectNode)schema.tree(selection());assertEquals(selection(),schema.read(n));n.put("approved",true);assertThrows(Exception.class,()->schema.read(n));}
 @Test void refusesSameEditionAndUnmatchedEvidence()throws Exception{var n=(ObjectNode)schema.tree(selection());n.set("target",n.required("baseline"));assertThrows(Exception.class,()->schema.read(n));}
 @Test void refusesInventedWholeLawCoverage()throws Exception{var n=(ObjectNode)schema.tree(selection());n.put("coverage_mode","WHOLE_LAW");assertThrows(Exception.class,()->schema.read(n));}
 @Test void realReadHashesAreChangesNotLegalApplicability()throws Exception{
  var p=new PermissionSchema();var fixed=p.object();fixed.set("selection",schema.tree(selection()));fixed.set("specification",schema.tree(basis(80)));
  var input=mock(TaskSourceVerifierInputAuthority.Verified.class,RETURNS_DEEP_STUBS);var replay=p.object();replay.putObject("project_profile").putArray("attributes");when(input.production().replay().content()).thenReturn(replay);
  var projects=mock(ProjectSchema.class);when(projects.attributes(any())).thenReturn(List.of());var reads=p.object();var refs=reads.putArray("sources");
  for(int n:List.of(1,11)){var r=refs.addObject().put("access",true).put("located",true).put("complete",true).put("literal_sha256",(n==1?"a":"b").repeat(64)).put("receipt_id",id(n+40));r.set("evidence_ref",schema.tree(evidence(n)));}
  var result=RegulatoryComparisonPipeline.produce(fixed,input,reads,mock(RuleExpression.class),projects);
  assertEquals("EXACT_LITERAL_CHANGED",result.clauses().get(0).literalChange());assertEquals("UNKNOWN",result.clauses().get(0).applicability());assertTrue(result.blocked());assertTrue(result.humanReviewRequired());assertEquals("MACHINE_PROPOSITION_NOT_LEGAL_APPROVAL",result.conclusionAuthority());
 }
}

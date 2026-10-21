package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** DEFERRED synthetic contract fixtures. Never registered sources, customer samples or business acceptance. */
final class MarketAnalysisScenarioTest {
 private final MarketAnalysisScenarioSchema schema=new MarketAnalysisScenarioSchema();
 private String id(int n){return "00000000-0000-4000-8000-%012d".formatted(n);}
 private PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),"a".repeat(64));}
 private DatasetProtocol.EvidenceRef evidence(int n){return new DatasetProtocol.EvidenceRef("1.0.0",id(90),id(91),id(n+4),id(n),id(n+1),"synthetic-chunk",DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),"a".repeat(64),id(n+2));}
 private MarketAnalysisScenarioSchema.Selection selection(){
  var scope=new MarketAnalysisScenarioSchema.Scope("synthetic geography","synthetic population","synthetic product","synthetic statistic","synthetic deduplication","NOT_MONETARY","NOT_MONETARY","2026-01-01","2026-12-31","synthetic exclusions","synthetic limitations");
  var inputs=List.of(new ReportScenarioSchema.Input("a",basis(1),id(5),evidence(1),"0.1","units"),new ReportScenarioSchema.Input("b",basis(11),id(15),evidence(11),"0.2","units"));
  var calculations=List.of(new ReportScenarioSchema.Calculation("sum","SUM",List.of("a","b"),2,"UNNECESSARY","units",id(50),"Total: "," units"));
  return new MarketAnalysisScenarioSchema.Selection("1.0.0",MarketAnalysisScenarioSchema.KIND,"APPROVED_PINNED_SOURCES_ONLY",scope,List.of(id(50)),inputs,calculations,List.of(new MarketAnalysisScenarioSchema.Observation("a","2026-01-01","2026-01-31"),new MarketAnalysisScenarioSchema.Observation("b","2026-02-01","2026-02-28")),List.of(new MarketAnalysisScenarioSchema.Context("method",basis(21),id(25),evidence(21),"Synthetic methodology only")),List.of(new MarketAnalysisScenarioSchema.Conclusion("summary",id(50),"Conclusion:","Check synthetic scope and limitations",List.of("a","b"),List.of("sum"))),basis(70),"Independently check periods, source scope, limitations, calculations and conclusion; no extrapolation.");
 }
 private ObjectNode tree(){return (ObjectNode)schema.tree(selection());}
 @Test void completeContractRoundTrips()throws Exception{assertEquals(selection(),schema.read(tree()));assertEquals(selection(),schema.definition(schema.definition(tree())));}
 @Test void noInventedApprovalOrUnknownFields(){var n=tree();n.put("approved",true);assertThrows(Exception.class,()->schema.read(n));}
 @Test void conclusionPrefixMustRemainCanonical()throws Exception{
  assertDoesNotThrow(()->schema.read(tree()));
  for(String prefix:List.of(" Conclusion:","Conclusion: ","Conclusion:\n")){
   var value=tree();((ObjectNode)value.required("conclusions").get(0)).put("prefix",prefix);
   assertThrows(SchemaRejection.class,()->schema.read(value));
  }
 }
 @Test void webDiscoveryCannotBeFormalEvidence(){var n=tree();n.put("evidence_policy","WEB_SEARCH_RESULTS");assertThrows(Exception.class,()->schema.read(n));}
 @Test void requiredStatisticalDefinitionCannotBeBlank(){var n=tree();((ObjectNode)n.required("scope")).put("statistical_definition"," ");assertThrows(Exception.class,()->schema.read(n));}
 @Test void invertedPeriodIsRejected(){var n=tree();((ObjectNode)n.required("scope")).put("period_end","2025-12-31");assertThrows(Exception.class,()->schema.read(n));}
 @Test void invalidCalendarDateIsRejected(){assertThrows(Exception.class,()->MarketAnalysisScenarioSchema.date("2026-02-30"));}
 @Test void nonCanonicalDateIsRejected(){assertThrows(Exception.class,()->MarketAnalysisScenarioSchema.date("2026-2-01"));}
 @Test void observationMustRemainInFixedPeriod(){var n=tree();((ObjectNode)n.required("observations").get(0)).put("period_start","2025-01-01");assertThrows(Exception.class,()->schema.read(n));}
 @Test void everyInputHasExactlyOnePeriod(){var n=tree();((ObjectNode)n.required("observations").get(1)).put("input_key","a");assertThrows(Exception.class,()->schema.read(n));}
 @Test void noMethodologySourceMeansIncompleteSelection(){var n=tree();n.putArray("context");assertThrows(Exception.class,()->schema.read(n));}
 @Test void conclusionCannotSkipItsCalculationInputs(){var n=tree();((ObjectNode)n.required("conclusions").get(0)).putArray("input_keys").add("a");assertThrows(Exception.class,()->schema.read(n));}
 @Test void conclusionCannotReferToUnknownCalculation(){var n=tree();((ObjectNode)n.required("conclusions").get(0)).putArray("calculation_keys").add("invented");assertThrows(Exception.class,()->schema.read(n));}
 @Test void conclusionRulePinIsMandatory(){var n=tree();n.remove("conclusion_review_rule");assertThrows(Exception.class,()->schema.read(n));}
 @Test void arithmeticContextDoesNotRewriteMarketSelection()throws Exception{var fixed=new PermissionSchema().object();fixed.set("selection",tree());fixed.set("specification",schema.tree(basis(80)));var original=fixed.deepCopy();var calculated=MarketAnalysisScenarioPipeline.calculations(fixed);assertEquals("0.30",calculated.required("calculations").get(0).required("value").asText());assertEquals(original,fixed);assertEquals("MARKET_ANALYSIS",fixed.required("selection").required("scenario_kind").asText());}
 @Test void undefinedRatioProducesUnknownNotZero()throws Exception{var selection=tree();((ObjectNode)selection.required("inputs").get(1)).put("decimal_literal","0");((ObjectNode)selection.required("calculations").get(0)).put("operation","RATIO");var fixed=new PermissionSchema().object();fixed.set("selection",selection);var result=MarketAnalysisScenarioPipeline.calculations(fixed);assertEquals("UNKNOWN",result.required("calculations").get(0).required("status").asText());assertFalse(result.required("calculations").get(0).has("value"));}
 @Test void registrationCarriesTypedResultAndCannotLeaveCoverageTrue()throws Exception{
  var f=new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.CANDIDATE,id(60),"a".repeat(64),id(70),List.of(),List.of(),List.of(),List.of(),List.of(),true);
  var original=new TaskSourceVerifierNormalizer.Normalized(f,new PermissionSchema().object().put("coverage_complete",true));var result=new PermissionSchema().object().put("blocked",true);
  var attached=MarketAnalysisScenarioPipeline.attach(original,result);assertFalse(attached.findings().coverageComplete());assertFalse(attached.pins().required("coverage_complete").asBoolean());assertEquals(result,attached.pins().required("market_analysis_scenario"));assertFalse(original.pins().has("market_analysis_scenario"));assertTrue(original.findings().coverageComplete());
 }
 @Test void reportAndTranslationKeepTheirDefensiveCopyProvenance()throws Exception{
  var f=new ArtifactProtocol.ReviewFindings(ArtifactProtocol.TargetKind.CANDIDATE,id(60),"a".repeat(64),id(70),List.of(),List.of(),List.of(),List.of(),List.of(),true);
  var original=new TaskSourceVerifierNormalizer.Normalized(f,new PermissionSchema().object().put("coverage_complete",true));var result=new PermissionSchema().object().put("blocked",true);
  var report=ReportScenarioPipeline.attach(original,result);var translation=TranslationScenarioPipeline.attach(original,result);assertEquals(result,report.pins().required("report_scenario"));assertEquals(result,translation.pins().required("translation_scenario"));assertFalse(report.pins().required("coverage_complete").asBoolean());assertFalse(translation.pins().required("coverage_complete").asBoolean());assertTrue(original.pins().required("coverage_complete").asBoolean());
 }
}

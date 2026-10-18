package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic, offline document fixtures. No test asserts production permission or statistical independence. */
final class EvaluationAcceptanceInputSchemaTest {
 final PermissionSchema json=new PermissionSchema();final EvaluationSchema evaluations=new EvaluationSchema(json);final AgentPublicationSchema publications=new AgentPublicationSchema(json);
 final EvaluationAcceptanceInputSchema checker=new EvaluationAcceptanceInputSchema(json,evaluations,publications);
 ObjectNode dataset,design,policy;ArrayNode manifest;
 EvaluationAcceptanceInputSchemaTest()throws Exception{
  manifest=json.object().putArray("unused");
  for(int i=0;i<3;i++){var expected=List.of(new EvaluationProtocol.Expectation("EVAL-01",List.of("item-A","item-B"),List.of()));var sample=new EvaluationProtocol.Sample(id(100+i),id(i<2?200:201),"ACCEPTANCE","PTR","REAL",new PermissionProtocol.Basis(id(300+i),org.apache.commons.codec.digest.DigestUtils.sha256Hex("synthetic input "+i)),basis(400+i),expected);var fixed=new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),new PermissionProtocol.Basis(sample.expected_ref().version_id(),evaluations.hash(evaluations.expectedItems(sample))),expected);manifest.add(json.tree(fixed));}
  dataset=json.object().put("schema_version","1.0.0");var entries=dataset.putArray("samples");for(int i=0;i<3;i++){var m=manifest.get(i);var entry=entries.addObject();for(String key:List.of("sample_id","source_family","input_ref","expected_ref"))entry.set(key,m.required(key).deepCopy());entry.putArray("source_versions").add(json.tree(basis(i<2?500:501)));}
  policy=json.object().put("schema_version","1.0.0").put("project_id",id(1)).put("runtime_manifest_sha256","b".repeat(64)).put("scope_sha256","c".repeat(64)).put("familywise_confidence","0.95").put("reason","Synthetic input consistency fixture");policy.set("rule_revision",json.tree(basis(10)));policy.set("dataset",json.tree(basis(11)));policy.set("sample_manifest_artifact",json.tree(new PermissionProtocol.Basis(id(12),evaluations.hash(manifest))));policy.set("sampling_design_evidence",json.tree(basis(13)));policy.putArray("candidate_refs").add(json.tree(basis(14)));policy.putArray("dimensions").addObject().put("dimension","EVAL-01").put("method","SOURCE_FAMILY_HOEFFDING_V1").put("direction","MIN").put("threshold","0.5").put("repeat_count",2).put("minimum_families",2);
  design=json.object().put("schema_version","1.0.0").put("method","SOURCE_FAMILY_INDEPENDENT_CLUSTERING_V1");design.set("dataset",policy.required("dataset").deepCopy());design.set("sample_manifest_artifact",policy.required("sample_manifest_artifact").deepCopy());var families=design.putArray("families");families.addObject().put("source_family",id(200)).putArray("sample_ids").add(id(100)).add(id(101));families.addObject().put("source_family",id(201)).putArray("sample_ids").add(id(102));design.putArray("independence_evidence_sources").add(json.tree(basis(600)));
 }
 static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}static PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),"a".repeat(64));}
 EvaluationAcceptanceInputSchema.CheckedInputs verify()throws Exception{return checker.verify(dataset,manifest,design,policy);}
 ObjectNode sample(int index){return (ObjectNode)manifest.get(index);}ObjectNode entry(int index){return (ObjectNode)dataset.required("samples").get(index);}ObjectNode family(int index){return (ObjectNode)design.required("families").get(index);}
 void reseal()throws Exception{((ObjectNode)policy.required("sample_manifest_artifact")).put("content_sha256",evaluations.hash(manifest));design.set("sample_manifest_artifact",policy.required("sample_manifest_artifact").deepCopy());}
 void refreshExpected(int index)throws Exception{var s=new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(sample(index),EvaluationProtocol.Sample.class);((ObjectNode)sample(index).required("expected_ref")).put("content_sha256",evaluations.hash(evaluations.expectedItems(s)));entry(index).set("expected_ref",sample(index).required("expected_ref").deepCopy());reseal();}
 void rejects(){var failure=assertThrows(RuntimeException.class,()->verify());assertTrue(failure instanceof SchemaRejection||failure instanceof TenantAccessException,"Must reject through the contract, not incidental "+failure.getClass().getSimpleName());}
 @Test void exactRealAcceptanceMappingReturnsAllRequiredFixedRefs()throws Exception{var c=verify();assertEquals(3,c.samples().size());assertEquals(2,c.familySamples().size());assertEquals(List.of(basis(500),basis(501)),c.sourceVersions());assertEquals(List.of(basis(600)),c.samplingEvidenceSources());assertEquals(3,c.inputRefs().size());assertEquals(3,c.expectedRefs().size());assertEquals(basis(11),c.dataset());assertEquals(basis(13),c.samplingDesign());assertEquals(evaluations.hash(manifest),c.sampleManifestCanonicalSha256());assertNotEquals(c.dataset().content_sha256(),c.datasetCanonicalSha256());}
 @Test void outputsAreImmutableAndSnapshotsDoNotChangeWithCallerTrees()throws Exception{var c=verify();String before=c.canonicalDataset();entry(0).put("sample_id",id(999));assertEquals(before,c.canonicalDataset());assertThrows(UnsupportedOperationException.class,()->c.samples().clear());assertThrows(UnsupportedOperationException.class,()->c.familySamples().clear());assertThrows(UnsupportedOperationException.class,()->c.familySamples().get(id(200)).clear());assertThrows(UnsupportedOperationException.class,()->c.sourceVersions().clear());}
 @Test void exactCoverageDoesNotDependOnDatasetOrFamilyArrayOrder()throws Exception{ArrayNode entries=(ArrayNode)dataset.required("samples");JsonNode first=entries.remove(0);entries.add(first);ArrayNode families=(ArrayNode)design.required("families");first=families.remove(0);families.add(first);assertEquals(3,verify().samples().size());}
 @Test void sameFamilyMayReuseAnInputWithoutCreatingAnotherIndependentFamily()throws Exception{sample(1).set("input_ref",sample(0).required("input_ref").deepCopy());entry(1).set("input_ref",sample(1).required("input_ref").deepCopy());refreshExpected(1);assertEquals(2,verify().inputRefs().size());assertEquals(2,verify().familySamples().size());}
 @Test void sourceRawShaAndCanonicalDocumentShaAreExplicitlyDifferentIdentities()throws Exception{var raw=(" \n"+json.canonical(dataset)+"\n").getBytes(StandardCharsets.UTF_8);var parsed=checker.parse("FormalEvaluationDataset",raw);assertEquals(json.canonical(dataset),json.canonical(parsed));assertNotEquals(org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw),verify().datasetCanonicalSha256());}
 @Test void rejectsOldDesignOnlyDatasetShape(){dataset=json.object().put("schema_version","1.0.0").put("dataset_version","design-only");rejects();}
 @Test void rejectsUnknownDatasetField(){dataset.put("production_eligible",true);rejects();}
 @Test void rejectsUnknownDatasetSampleField(){entry(0).put("approved",true);rejects();}
 @Test void rejectsUnknownSamplingDesignField(){design.put("independence_proven",true);rejects();}
 @Test void rejectsUnsupportedSchemaVersion(){dataset.put("schema_version","2.0.0");rejects();}
 @Test void rejectsUnsupportedSamplingMethod(){design.put("method","SELF_ATTESTED_INDEPENDENCE");rejects();}
 @Test void rejectsMalformedUuidOrUppercaseDigest(){entry(0).put("sample_id","not-an-id");rejects();}
 @Test void rejectsEmptyDataset(){((ArrayNode)dataset.required("samples")).removeAll();rejects();}
 @Test void rejectsEmptyManifest(){manifest.removeAll();rejects();}
 @Test void rejectsMissingDatasetSample(){((ArrayNode)dataset.required("samples")).remove(0);rejects();}
 @Test void rejectsExtraDatasetSample(){var extra=entry(0).deepCopy().put("sample_id",id(999));((ArrayNode)dataset.required("samples")).add(extra);rejects();}
 @Test void rejectsDuplicateDatasetSampleIdWithDifferentBody(){var extra=entry(0).deepCopy();extra.putArray("source_versions").add(json.tree(basis(550)));((ArrayNode)dataset.required("samples")).add(extra);rejects();}
 @Test void rejectsDatasetInputOrExpectedMismatch(){entry(0).set("input_ref",json.tree(basis(999)));rejects();}
 @Test void rejectsDatasetFamilyMismatch(){entry(0).put("source_family",id(201));rejects();}
 @Test void rejectsSourceVersionReuseAcrossFamilies(){entry(2).putArray("source_versions").add(json.tree(basis(500)));rejects();}
 @Test void rejectsInputReuseAcrossFamilies()throws Exception{sample(2).set("input_ref",sample(0).required("input_ref").deepCopy());entry(2).set("input_ref",sample(2).required("input_ref").deepCopy());refreshExpected(2);rejects();}
 @Test void rejectsCopiedInputBytesUnderDifferentArtifactIdsAcrossFamilies()throws Exception{((ObjectNode)sample(2).required("input_ref")).put("content_sha256",sample(0).required("input_ref").required("content_sha256").asText());entry(2).set("input_ref",sample(2).required("input_ref").deepCopy());refreshExpected(2);rejects();}
 @Test void rejectsDatasetExpectedReferenceMismatch(){entry(0).set("expected_ref",json.tree(basis(999)));rejects();}
 @Test void rejectsUppercaseDigest(){((ObjectNode)entry(0).required("source_versions").get(0)).put("content_sha256","A".repeat(64));rejects();}
 @Test void rejectsExtraFieldsOnFrozenSampleManifest(){sample(0).put("formal_approval",true);rejects();}
 @Test void rejectsExtraFieldsInsideEvidenceBasis(){((ObjectNode)design.required("independence_evidence_sources").get(0)).put("approved",true);rejects();}
 @Test void rejectsEmptySourceVersions(){entry(0).putArray("source_versions");rejects();}
 @Test void rejectsSameSourceIdentityTwiceEvenWithChangedHash(){var sources=(ArrayNode)entry(0).required("source_versions");sources.add(json.tree(new PermissionProtocol.Basis(id(500),"b".repeat(64))));rejects();}
 @Test void rejectsSameSourceIdentityWithDifferentHashWithinFamily(){entry(1).putArray("source_versions").add(json.tree(new PermissionProtocol.Basis(id(500),"b".repeat(64))));rejects();}
 @Test void rejectsMissingFamily(){((ArrayNode)design.required("families")).remove(0);rejects();}
 @Test void rejectsForeignFamily(){family(0).put("source_family",id(999));rejects();}
 @Test void rejectsDuplicateFamilyIdentity(){((ArrayNode)design.required("families")).add(family(0).deepCopy().set("sample_ids",json.object().putArray("ids").add(id(999))));rejects();}
 @Test void rejectsSampleInWrongFamily(){family(0).putArray("sample_ids").add(id(100)).add(id(102));family(1).putArray("sample_ids").add(id(101));rejects();}
 @Test void rejectsExtraOrMissingSampleInDesign(){family(0).putArray("sample_ids").add(id(100));rejects();}
 @Test void rejectsDatasetFixedRefMismatch(){((ObjectNode)design.required("dataset")).put("content_sha256","b".repeat(64));rejects();}
 @Test void rejectsManifestFixedRefMismatch(){((ObjectNode)design.required("sample_manifest_artifact")).put("version_id",id(999));rejects();}
 @Test void rejectsChangedManifestWithUnchangedHash(){sample(0).put("document_type","OTHER");rejects();}
 @Test void rejectsChangedExpectationsEvenAfterRehashingManifest()throws Exception{((ArrayNode)sample(0).required("expectations").get(0).required("applicable_item_ids")).add("fabricated-item");reseal();rejects();}
 @Test void rejectsSimulationSampleEvenIfHashesMatch()throws Exception{sample(0).put("evidence_class","SIMULATION");reseal();rejects();}
 @Test void rejectsDevelopmentOrTuningSampleEvenIfHashesMatch()throws Exception{sample(0).put("split","DEVELOPMENT");reseal();rejects();}
 @Test void rejectsNoActualIndependenceEvidenceRef(){design.putArray("independence_evidence_sources");rejects();}
 @Test void rejectsDuplicateEvidenceIdentityWithDifferentHash(){((ArrayNode)design.required("independence_evidence_sources")).add(json.tree(new PermissionProtocol.Basis(id(600),"b".repeat(64))));rejects();}
 @Test void rejectsConflictingHashAcrossDocumentRoles(){design.putArray("independence_evidence_sources").add(json.tree(new PermissionProtocol.Basis(id(500),"b".repeat(64))));rejects();}
 @Test void rejectsDuplicateSampleIdentityEvenAfterRehashingManifest()throws Exception{sample(1).put("sample_id",id(100));refreshExpected(1);rejects();}
 @Test void rejectsMissingRequiredDimension()throws Exception{((ObjectNode)policy.required("dimensions").get(0)).put("dimension","EVAL-03");rejects();}
 @Test void rejectsInsufficientFixedFamiliesForPredeclaredMinimum(){((ObjectNode)policy.required("dimensions").get(0)).put("minimum_families",3);rejects();}
 @Test void rejectsDuplicatePolicyDimension(){((ArrayNode)policy.required("dimensions")).add(((ObjectNode)policy.required("dimensions").get(0)).deepCopy().put("threshold","0.6"));rejects();}
 @Test void rejectsUnknownPolicyField(){policy.put("approved",true);rejects();}
 @Test void duplicateJsonKeysTrailingTokensMalformedAndOversizeAreRejected(){for(String value:List.of("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\",\"samples\":[]}","{} {}","{","null"))assertThrows(SchemaRejection.class,()->checker.parse("FormalEvaluationDataset",value.getBytes(StandardCharsets.UTF_8)));assertThrows(SchemaRejection.class,()->checker.parse("SamplingDesign",new byte[EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES+1]));assertThrows(SchemaRejection.class,()->checker.parse("SamplingDesign",null));}
 @Test void schemaReferenceNamesAndHashAreFixed(){assertEquals("urn:medical:evaluation-acceptance-inputs:1.0.0#/$defs/FormalEvaluationDataset",EvaluationAcceptanceInputSchema.DATASET_REF);assertEquals(64,checker.schemaSha256().length());assertThrows(IllegalArgumentException.class,()->checker.validate("SourceDataset",dataset));}
}

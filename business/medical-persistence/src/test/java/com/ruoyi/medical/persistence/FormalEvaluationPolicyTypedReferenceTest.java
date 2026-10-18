package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.persistence.mapper.*;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ruoyi.medical.persistence.AgentPublicationMixedPurposeTest.*;
/** Actual object reader and actual domain/dependency authorities. Wrong genuine domains must fail before transport or policy storage. */
class FormalEvaluationPolicyTypedReferenceTest {
 final EvaluationAcceptanceObjectReaderTest f=new EvaluationAcceptanceObjectReaderTest();final FormalEvaluationPolicySchema schema=new FormalEvaluationPolicySchema();
 final PermissionSchema json=f.f.json;final EvaluationSchema evaluations=new EvaluationSchema(json);final EvaluationAcceptanceInputSchema inputs=new EvaluationAcceptanceInputSchema(json,evaluations,f.schema);
 FormalEvaluationPolicyInputs service;ObjectNode request;
 FormalEvaluationPolicyTypedReferenceTest()throws Exception{}
 @BeforeEach void setup()throws Exception{f.prepare();var fixture=new EvaluationAcceptanceInputSchemaTest();request=json.object().put("schema_version","1.0.0").put("sampling_assessment","Explicit offline human-assessment fixture");fixture.policy.put("project_id",PROJECT);request.set("specification",fixture.policy);fixture.policy.putObject("expert_domains").put("EVAL-01","REGULATORY_DOCUMENT_REVIEW");((ObjectNode)request.required("specification")).set("dataset",json.tree(f.f.basis(PRIMARY)));request.putArray("root_uses").addObject().put("purpose",ROOT_PURPOSE).set("version",json.tree(f.f.basis(PRIMARY)));
  service=new FormalEvaluationPolicyInputs(f.access,f.reader,inputs,evaluations,f.schema,schema,f.f.dependencies,f.f.profiles,f.f.lineage,f.f.sources,f.f.artifacts,mock(FormalEvaluationInputMapper.class),mock(ObjectProvider.class),mock(FormalAgentCandidateAuthority.class),mock(FormalAgentCandidateDependencies.class),mock(AgentExecutionPolicyAuthority.class),mock(EvaluationRuleAuthority.class),mock(FormalEvaluationPolicyMapper.class));}
 @AfterEach void done()throws Exception{f.cleanup();}
 void fails(){assertThrows(Exception.class,()->service.prepare(f.subject,"1",request,true));}
 @Test void originalGenericCapabilityDatasetProbeIsRejectedBeforeGet(){var r=f.f.versions.get(PRIMARY);f.f.versions.put(PRIMARY,new ArtifactRegistryProtocol.VersionRow(r.id(),r.projectId(),"CAPABILITY_VERSION",r.contentSha256()));fails();assertEquals(0,f.requests.get());}
 @Test void genuineRuleDomainCannotMasqueradeAsDataset(){var r=f.f.versions.get(PRIMARY);f.f.versions.put(PRIMARY,new ArtifactRegistryProtocol.VersionRow(r.id(),r.projectId(),"RULE_VERSION",r.contentSha256()));fails();assertEquals(0,f.requests.get());}
 @Test void artifactDomainCannotMasqueradeAsDatasetSource(){var r=f.f.versions.get(PRIMARY);f.f.versions.put(PRIMARY,new ArtifactRegistryProtocol.VersionRow(r.id(),r.projectId(),"ARTIFACT",r.contentSha256()));fails();assertEquals(0,f.requests.get());}
 @Test void actualGetOfSourceStillRequiresClosedDatasetSchema(){fails();assertTrue(f.requests.get()>0);}
 @Test void noRootPurposeMeansNoGet(){request.putArray("root_uses");fails();assertEquals(0,f.requests.get());}
 @Test void rootPurposeCannotBeInferredFromSourceRights(){((ObjectNode)request.required("root_uses").get(0)).put("purpose","DIFFERENT_APPROVED_USE");fails();assertEquals(0,f.requests.get());}
 @Test void suppliedRawHashCannotOverrideOriginalSourceHash(){((ObjectNode)request.required("specification").required("dataset")).put("content_sha256","e".repeat(64));fails();assertEquals(0,f.requests.get());}
 @Test void duplicateRootBindingCannotCreateAmbiguousPurpose(){((ArrayNode)request.required("root_uses")).add(request.required("root_uses").get(0).deepCopy());fails();assertEquals(0,f.requests.get());}
 @Test void rawOriginalVersionReplacementCannotBecomePolicyInput(){f.onGet=()->{var r=f.original;f.original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),r.sha256(),r.objectKey(),"other-version",r.sizeBytes(),r.mediaType());};fails();assertEquals(1,f.requests.get());}
 @Test void sourceRevokedDuringActualGetCannotProduceTypedProof(){f.onGet=()->f.allowed.set(false);fails();assertEquals(1,f.requests.get());}
 @Test void publicProofTypesHaveNoPublicConstructor(){assertEquals(0,FormalEvaluationPolicyInputs.Prepared.class.getConstructors().length);assertEquals(0,EvaluationAcceptancePolicyService.PreparedPolicy.class.getConstructors().length);assertEquals(0,EvaluationAcceptancePolicyService.VerifiedPolicy.class.getConstructors().length);}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual historical entry, immutable090 ledger and registration validation.
 * Original083 DB rows are explicit fixtures; original evaluation/candidate/policy/context/read authorities are doubles.
 * No current execute admission, budget, model eligibility, registration grant or object transport is injected into the reader. */
class FormalEvaluationPlanPreparationReadServiceTest extends PlanPreparationFixture {
    final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
    final EnterpriseAccess access=mock(EnterpriseAccess.class);
    final EvaluationService evaluations=mock(EvaluationService.class);
    final EvaluationService.FormalDefinitionMetadata metadata=mock(EvaluationService.FormalDefinitionMetadata.class);
    final FormalAgentCandidateAuthority candidates=mock(FormalAgentCandidateAuthority.class);
    final FormalAgentCandidateAuthority.VerifiedCandidateIdentity candidate=mock(FormalAgentCandidateAuthority.VerifiedCandidateIdentity.class);
    final AgentExecutionPolicyAuthority policies=mock(AgentExecutionPolicyAuthority.class);
    final AgentExecutionPolicyAuthority.HistoricalPolicy policy=mock(AgentExecutionPolicyAuthority.HistoricalPolicy.class);
    final FormalEvaluationContextStore contexts=mock(FormalEvaluationContextStore.class);
    final FormalEvaluationContextReadAuthority contextReads=mock(FormalEvaluationContextReadAuthority.class);
    final FormalEvaluationContextSchema contextSchema=new FormalEvaluationContextSchema(json);
    final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
    final FormalEvaluationPlanPreparationDependencies provenance=mock(FormalEvaluationPlanPreparationDependencies.class);
    final FormalEvaluationPlanPreparationReadService service;
    final EvaluationProtocol.Definition definition;
    final String boundKey="original-bound-start-key";
    @TempDir Path directory;
    FormalEvaluationPlanPreparationReadServiceTest()throws Exception {
        when(access.execute(eq(subject),any())).thenAnswer(i->tx(()->((Function<EnterpriseAccess.Admission,Object>)i.getArgument(1)).apply(admission)));
        var sample=new EvaluationProtocol.Sample(id(6),id(7),"ACCEPTANCE","REGISTRATION_DIRECTORY","REAL",basis(20),basis(21),List.of());
        var create=new EvaluationProtocol.Create("1.0.0",project,basis(70),basis(71),List.of(sample),List.of(basis(10)),basis(72),List.of("accuracy"),new EvaluationProtocol.Variance(1,"fixture","fixture"),basis(73),"PRODUCTION","Explicit fixture");
        var manifest=json.object();manifest.putArray("template_versions").add(json.tree(basis(30))).add(json.tree(basis(32)));
        definition=new EvaluationProtocol.Definition("1.0.0",tenant,evaluation,create,List.of(new EvaluationProtocol.Candidate(id(10),hash('a'),id(74),actor,manifest,json.object(),"1.0.0",hash('b'))),List.of(),id(75),"1",actor,"2026-10-18T00:00:00Z");
        when(evaluations.requireFormalDefinitionMetadata(admission,evaluation)).thenReturn(metadata);
        when(metadata.definition()).thenReturn(definition);when(metadata.row()).thenReturn(new EvaluationService.FormalDefinitionIdentity(tenant,project,evaluation,hash('a'),"DRAFT","RESTRICTED",BigInteger.ONE));
        when(candidates.require(admission,basis(10),project,"EVALUATION_RESULTS")).thenReturn(candidate);when(candidate.executionPolicy()).thenReturn(basis(11));when(candidate.classification()).thenReturn("RESTRICTED");when(candidate.runtimeManifestSha256()).thenReturn(hash('b'));when(candidate.status()).thenReturn("DISABLED");when(candidate.manifestDefinition()).thenReturn(manifest);
        when(policies.requireHistoricalByVersion(admission,id(11),hash('a'),project,"RESTRICTED","EVALUATION_RESULTS")).thenReturn(policy);
        var d=mock(AgentExecutionPolicyProtocol.Definition.class);var binding=mock(AgentExecutionPolicyProtocol.NodeBinding.class);when(policy.definition()).thenReturn(d);when(policy.businessPurpose()).thenReturn("DOCUMENT_BODY");when(d.businessPurpose()).thenReturn("DOCUMENT_BODY");when(d.bindings()).thenReturn(List.of(binding));
        when(binding.nodeKind()).thenReturn("N01");when(binding.roleVersion()).thenReturn(basis(22));when(binding.modelVersion()).thenReturn(basis(23));when(binding.tokenizerVersion()).thenReturn(basis(24));when(binding.generationParameters()).thenReturn(json.object());
        var nodes=FormalEvaluationExecutionAdmission.matrix(definition,checked.original().request().start(),hash('b'),List.of(binding),json);
        checked=new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),checked.original(),checked.evaluation(),nodes.get(0),checked.contextBinding(),checked.bytes(),checked.evidence(),checked.stable(),checked.dependencies(),checked.preview(),true);
        var snapshot=json.object();snapshot.set("definition",json.tree(definition));snapshot.set("request",json.tree(checked.original().request().start()));snapshot.set("nodes",json.tree(nodes));snapshot.putObject("authorizations").put("fixture_original_only",true);
        var root=meta(json.manifestSha256());root.put("execution_id",execution);root.put("evaluation_id",evaluation);root.put("definition_sha256",hash('a'));root.put("candidate_version",id(10));root.put("candidate_sha256",hash('a'));root.put("policy_version",id(11));root.put("policy_sha256",hash('a'));root.put("runtime_manifest_sha256",hash('b'));root.put("canonical_request",schema.canonical(checked.original().request().start()));root.put("request_sha256",schema.hash(checked.original().request().start()));root.put("canonical_admission",schema.canonical(snapshot));root.put("admission_sha256",schema.hash(snapshot));root.put("idempotency_key",boundKey);root.put("expected_cells",1);root.put("expected_nodes",1);root.put("audit_id",id(90));root.put("event_id",id(91));rows("formal_evaluation_execution").add(root);
        journal(root,"evaluation.execution_prepared",FormalEvaluationExecutionLedger.event(root,json));
        var contextDefinition=json.object().put("schema_version","1.0.0").put("project_id",project);contextDefinition.set("candidate",json.tree(basis(10)));contextDefinition.set("execution_policy",json.tree(basis(11)));var cb=contextDefinition.putArray("node_bindings").addObject().put("node_kind","N01");cb.set("role",json.tree(basis(22)));
        var view=json.object();view.set("binding",json.tree(basis(40)));view.putObject("fixed").set("definition",contextDefinition);when(contexts.readView(admission,id(40),contextReads)).thenReturn(view);
        var link=meta(contextSchema.manifest());var content=json.object().put("schema_version","1.0.0").put("execution_id",execution).put("root_request_sha256",(String)root.get("request_sha256")).put("root_admission_sha256",(String)root.get("admission_sha256"));content.set("context_binding",json.tree(basis(40)));content.set("context_definition",contextDefinition);
        link.put("execution_id",execution);link.put("context_version",id(40));link.put("context_sha256",hash('a'));link.put("canonical_binding",schema.canonical(content));link.put("binding_sha256",schema.hash(content));link.put("audit_id",id(92));link.put("event_id",id(93));rows("formal_evaluation_execution_context").add(link);
        var event=json.object().put("schema_version","1.0.0").put("kind","EVALUATION_CONTEXT_BOUND").put("execution_id",execution).put("binding_sha256",schema.hash(content));event.set("context_binding",json.tree(basis(40)));journal(link,"evaluation.context_bound",event);
        when(provenance.require(eq(admission),eq(project),eq("RESTRICTED"),any())).thenReturn(checked.dependencies());
        originalFixture(basis(30),basis(30),"DOCUMENT_BODY");
        service=new FormalEvaluationPlanPreparationReadService(access,evaluations,candidates,policies,contexts,contextReads,contextSchema,resources,provenance,ledger,registrations,schema,json,db);
    }
    Map<String,Object> meta(String manifest){var m=new LinkedHashMap<String,Object>();m.put("tenant_id",tenant);m.put("project_id",project);m.put("created_by",actor);m.put("created_at",now);m.put("schema_version","1.0.0");m.put("schema_manifest_sha256",manifest);m.put("classification","RESTRICTED");return m;}
    void journal(Map<String,Object> row,String action,JsonNode payload)throws Exception{
        var audit=meta((String)row.get("schema_manifest_sha256"));audit.put("audit_id",row.get("audit_id"));audit.put("subject_id",actor);audit.put("action",action);audit.put("result","SUCCESS");audit.put("request_id",execution);audit.put("trace_id",execution);audit.put("resource_ref",schema.canonical(payload));rows("audit_event").add(audit);
        var event=new LinkedHashMap<String,Object>();event.put("tenant_id",tenant);event.put("event_id",row.get("event_id"));event.put("aggregate_id",execution);event.put("aggregate_revision",1);event.put("event_type",action);event.put("occurred_at",now);event.put("payload",schema.canonical(payload));rows("outbox_event").add(event);
    }
    byte[] recovery()throws Exception{return schema.canonical(new FormalEvaluationPlanPreparationSchema.Recovery("1.0.0",key,checked.original().request())).getBytes(StandardCharsets.UTF_8);}
    byte[] boundRecovery()throws Exception{return schema.canonical(new FormalEvaluationPlanPreparationSchema.BoundRecovery("1.0.0",boundKey,new FormalEvaluationContextProtocol.BoundStart("1.0.0",checked.original().request().start(),basis(40)))).getBytes(StandardCharsets.UTF_8);}
    JsonNode status()throws Exception{return service.status(subject,"1",recovery());}
    void complete()throws Exception {
        var attempt=prepare();Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));var writer=mock(S3ObjectWriter.class);
        when(writer.writeVerified(any(),any(),any())).thenAnswer(i->{var w=attempt.writeIntent();assertArrayEquals(checked.bytes(),Files.readAllBytes(i.getArgument(1)));return new InterfaceProtocol.ObjectRef(w.objectKey(),"fixture-object-version",w.sha256(),w.sizeBytes(),w.mediaType());});
        var physical=new RuntimeObjectTransport(()->writer,directory,1048576).transferVerified(attempt,()->{});tx(()->registrations.register(admission,proof,attempt,physical));
    }
    @Test void originalExecutionRecoveryUsesHistoricalReadersAndNeverExecutionOrBudgetAdmission()throws Exception{
        var r=service.recoverExecution(subject,"1",boundRecovery());assertEquals(execution,r.required("execution_id").asText());assertEquals(1,r.required("expected_nodes").intValue());assertEquals(schema.hash(checked.original().request().start()),r.required("request_sha256").asText());
        verify(policies,never()).requireByVersion(any(),anyString(),anyString(),anyString(),anyString(),anyString());verify(evaluations,never()).requireExecutionDefinition(any(),anyString());verifyNoInteractions(proof,resources,provenance);assertTrue(rows("operation_record").isEmpty());
    }
    @Test void metadataStatusNotFoundDoesNotWriteOrClaimCachedContent()throws Exception {var r=status();assertEquals("NOT_FOUND",r.required("state").asText());assertFalse(r.required("content_available").asBoolean());assertTrue(r.required("receipt").isNull());assertTrue(rows("operation_record").isEmpty());verifyNoInteractions(proof,resources,provenance);}
    @Test void pendingStatusDoesNotInvokeWriteProofEvenAfterItsRevocation()throws Exception{prepare();when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));clearInvocations(proof);var r=status();assertEquals("PENDING",r.required("state").asText());verifyNoInteractions(proof);verifyNoInteractions(resources);}
    @Test void preparedStatusUsesOriginalImmutableReceiptAndCurrentReadsOnly()throws Exception{complete();when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));clearInvocations(proof);var r=status();assertEquals("PREPARED",r.required("state").asText());assertFalse(r.required("content_available").asBoolean());assertFalse(r.required("receipt").has("preview"));assertFalse(r.required("receipt").required("professional_completion").asBoolean());verify(resources).require(eq(admission),argThat(s->s.resource_kind().equals("ARTIFACT")),eq("artifact.read"),eq("EVALUATION_RESULTS"));verifyNoInteractions(proof);}
    @Test void revokedCurrentArtifactReadNeverDisclosesPreparationIdentity()throws Exception{complete();when(resources.require(any(),any(),eq("artifact.read"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,this::status);}
    @Test void revokedCurrentSourceReadNeverDisclosesPendingIdentity()throws Exception{prepare();when(provenance.require(any(),anyString(),anyString(),any())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));assertThrows(TenantAccessException.class,this::status);}
    @Test void changedTransitiveClosureOrRightsCannotAttestOriginalPreparation()throws Exception {
        complete();var original=checked.dependencies();
        var added=new ArrayList<>(original.members());added.add(new ArtifactRegistryProtocol.MemberRow(id(999),id(50)));
        when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(original.roots(),added,original.classification(),original.authorizations()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());
        var changed=new ArrayList<>(original.members());changed.set(0,new ArtifactRegistryProtocol.MemberRow(changed.get(0).upstreamVersionId(),id(998)));
        when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(original.roots(),changed,original.classification(),original.authorizations()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());
        when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(original.roots(),original.members().subList(1,original.members().size()),original.classification(),original.authorizations()));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());
    }
    @Test void missingOriginalJournalsAndChangedMatrixCannotRecover()throws Exception {rows("audit_event").remove(0);assertThrows(TenantAccessException.class,()->service.recoverExecution(subject,"1",boundRecovery()));}
    @Test void originalNodeMatrixIsComparedToActualHistoricalPolicy()throws Exception{var root=only("formal_evaluation_execution");var snapshot=(com.fasterxml.jackson.databind.node.ObjectNode)schema.read((String)root.get("canonical_admission"));((com.fasterxml.jackson.databind.node.ObjectNode)snapshot.required("nodes").get(0).required("cell")).put("repeat_index",1);root.put("canonical_admission",schema.canonical(snapshot));root.put("admission_sha256",schema.hash(snapshot));assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());}
    @Test void staleContextAndCrossActorCannotReadOriginal()throws Exception {assertThrows(TenantAccessException.class,()->service.status(subject,"2",recovery()));when(admission.actorId()).thenReturn(id(999));assertThrows(TenantAccessException.class,this::status);}
    @Test void corrupt090LineageOrReceiptCannotBeReturned()throws Exception{complete();rows("lineage_edge").remove(0);assertThrows(TenantAccessException.class,this::status);}

    /** Coherent malformed original producer fixture: regenerate payload/intent/receipt/journals through actual090 methods.
     * Only the named upstream authority double permits the deliberately inconsistent original binding. */
    void originalFixture(PermissionProtocol.Basis requested,PermissionProtocol.Basis selected,String sourcePurpose)throws Exception {
        var body=(ObjectNode)schema.read(checked.original().body());body.set("template",json.tree(requested));
        var original=schema.request(schema.canonical(body).getBytes(StandardCharsets.UTF_8));
        var payload=(ObjectNode)schema.read(new String(checked.bytes(),StandardCharsets.UTF_8));payload.put("template_version",selected.version_id());
        byte[] bytes=schema.canonical(payload).getBytes(StandardCharsets.UTF_8);
        var evidence=(ObjectNode)checked.evidence();var preview=(ObjectNode)evidence.required("preview");
        var previous=preview.required("selected_template").required("version_id").asText();preview.set("selected_template",json.tree(selected));
        if(preview.has("plan_candidate"))preview.set("plan_candidate",payload);
        if(preview.has("templates"))for(var choice:preview.required("templates"))
            if(previous.equals(choice.required("template").required("version_id").asText()))((ObjectNode)choice).set("template",json.tree(selected));
        evidence.put("source_business_purpose",sourcePurpose).put("plan_sha256",WireValues.sha256(bytes));
        var prior=checked.dependencies();var roots=new ArrayList<PermissionProtocol.Basis>();
        for(var root:prior.roots())roots.add(previous.equals(root.version_id())?selected:root);
        var members=new ArrayList<ArtifactRegistryProtocol.MemberRow>();
        for(var member:prior.members())members.add(previous.equals(member.upstreamVersionId())?new ArtifactRegistryProtocol.MemberRow(selected.version_id(),member.rightsRevision()):member);
        roots.sort(Comparator.comparing(PermissionProtocol.Basis::version_id));members.sort(Comparator.comparing(ArtifactRegistryProtocol.MemberRow::upstreamVersionId));
        var deps=new FormalEvaluationPlanPreparationDependencies.Checked(roots,members,prior.classification(),prior.authorizations());
        var stable=(ObjectNode)FormalEvaluationPlanPreparationDependencies.stableEvidence(evidence);
        stable.set("roots",json.tree(deps.roots()));stable.set("members",json.tree(deps.members()));stable.set("dependencies",deps.authorizations());stable.put("classification",deps.classification());
        checked=new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),original,checked.evaluation(),checked.node(),checked.contextBinding(),bytes,evidence,schema.canonical(stable),deps,preview,true);
        when(provenance.require(eq(admission),eq(project),eq("RESTRICTED"),any())).thenReturn(deps);
    }
    @Test void alteredSourcePurposeCannotChooseWeakerCurrentReadBeforeDependencies()throws Exception {
        originalFixture(basis(30),basis(30),"EVALUATION_RESULTS");complete();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }
    @Test void selectedTemplateMustEqualExplicitOriginalRequestBeforeDependencies()throws Exception {
        originalFixture(basis(32),basis(30),"DOCUMENT_BODY");complete();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }
    @Test void selectedTemplateMustBelongToActualOriginalCandidateManifestBeforeDependencies()throws Exception {
        originalFixture(basis(99),basis(99),"DOCUMENT_BODY");complete();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }
    @Test void templateManifestMembershipIncludesExactContentHash()throws Exception {
        var changed=new PermissionProtocol.Basis(id(30),hash('f'));originalFixture(changed,changed,"DOCUMENT_BODY");prepare();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }
    @Test void automaticOriginalSelectionIsPreservedWithHistoricalPolicyPurpose()throws Exception {
        originalFixture(null,basis(30),"DOCUMENT_BODY");complete();clearInvocations(provenance,proof);
        assertEquals("PREPARED",status().required("state").asText());
        verify(provenance,times(2)).require(eq(admission),eq(project),eq("RESTRICTED"),argThat(uses->uses.size()==3&&uses.get(0).purpose().equals("DOCUMENT_BODY")&&uses.get(1).purpose().equals("EVALUATION_RESULTS")&&uses.get(2).purpose().equals("EVALUATION_RESULTS")));
        verifyNoInteractions(proof);verify(policies,never()).requireByVersion(any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void automaticOriginalSelectionStillRequiresCandidateManifestMembership()throws Exception {
        originalFixture(null,basis(99),"DOCUMENT_BODY");prepare();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }

    @Test void selectedTemplateMustAppearExactlyOnceInOriginalCandidateManifest()throws Exception {
        ((ObjectNode)definition.candidate_snapshots().get(0).dependency_manifest()).withArray("template_versions").add(json.tree(basis(30)));
        var root=only("formal_evaluation_execution");var snapshot=(ObjectNode)schema.read((String)root.get("canonical_admission"));snapshot.set("definition",json.tree(definition));
        root.put("canonical_admission",schema.canonical(snapshot));root.put("admission_sha256",schema.hash(snapshot));replaceJournal(root,FormalEvaluationExecutionLedger.event(root,json));
        var link=only("formal_evaluation_execution_context");var binding=(ObjectNode)schema.read((String)link.get("canonical_binding"));binding.put("root_admission_sha256",(String)root.get("admission_sha256"));link.put("canonical_binding",schema.canonical(binding));link.put("binding_sha256",schema.hash(binding));
        var event=json.object().put("schema_version","1.0.0").put("kind","EVALUATION_CONTEXT_BOUND").put("execution_id",execution).put("binding_sha256",schema.hash(binding));event.set("context_binding",json.tree(basis(40)));replaceJournal(link,event);
        prepare();clearInvocations(provenance,contexts);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,this::status).code());verifyNoInteractions(provenance,contexts);
    }
    void replaceJournal(Map<String,Object> row,JsonNode event)throws Exception {
        for(var a:rows("audit_event"))if(a.get("audit_id").equals(row.get("audit_id")))a.put("resource_ref",schema.canonical(event));
        for(var e:rows("outbox_event"))if(e.get("event_id").equals(row.get("event_id")))e.put("payload",schema.canonical(event));
    }
}

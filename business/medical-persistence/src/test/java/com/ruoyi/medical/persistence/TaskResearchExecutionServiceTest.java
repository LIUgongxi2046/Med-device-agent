package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real candidate/output factories under explicit session, C07 terminal and source authority
 * doubles. The model, isolation and evaluator are not executed or claimed by this fixture. */
class TaskResearchExecutionServiceTest extends TaskProfessionalClosedRegistrationTest {
    final TaskInvocationService.Terminal terminal=mock(TaskInvocationService.Terminal.class);
    final TaskResearchSourceReader sources=mock(TaskResearchSourceReader.class);
    final TaskResearchSourceReader.VerifiedReceipts reads=mock(TaskResearchSourceReader.VerifiedReceipts.class);
    final TaskResearchRetrievalAuthority retrievals=mock(TaskResearchRetrievalAuthority.class);
    final TaskResearchRetrievalAuthority.Verified retrieved=mock(TaskResearchRetrievalAuthority.Verified.class);
    final TaskResearchCompletionEvaluation.Evaluated evaluated=mock(TaskResearchCompletionEvaluation.Evaluated.class);
    final TaskResearchExecutionService outputs=new TaskResearchExecutionService(json,professionalSchema,sources,retrievals);
    final TaskResearchEvidenceContract evidenceContracts=mock(TaskResearchEvidenceContract.class);
    final SchemaCatalogue evidenceCatalogue=SchemaCatalogue.fromClasspath();
    final SchemaCatalogue.PublishedSchema evidencePublication=evidenceCatalogue.requirePublished(evidenceCatalogue.schemaRef("artifacts","EvidenceBundle"),evidenceCatalogue.schemaDigest("artifacts"));
    final JsonNode projection;
    TaskResearchExecutionServiceTest()throws Exception {
        // Explicit synthetic role-publication authority; real published v1 schemas still validate payloads.
        outputs.evidenceContracts(evidenceContracts);
        when(evidenceContracts.require(tenant,actor,project,professionalInput.manifest())).thenReturn(evidencePublication);
        when(professionalInput.questionId()).thenReturn(id(86));
        var ref=new DatasetProtocol.EvidenceRef("1.0.0",tenant,project,id(20),id(21),id(22),id(23),DatasetProtocol.LocatorKind.TXT,new DatasetProtocol.TextLocator("TXT","UTF-8","PRESERVE",0,1,1,1),hash('a'),id(24));
        var value=json.object();value.putArray("retrieval_ids");value.set("selected_refs",json.tree(List.of(ref)));value.putArray("conflict_refs");value.putArray("missing_requirement_ids");value.putArray("read_receipt_ids").add(id(99));
        var question=value.putArray("question_coverage").addObject().put("question_id",id(86));question.set("evidence_links",json.tree(List.of(new ArtifactProtocol.EvidenceLink(ref,ArtifactProtocol.Relation.SUPPORT))));question.putArray("missing_requirement_ids");projection=value;
        when(terminal.output()).thenReturn(result(projection));when(terminal.invocationIds()).thenReturn(List.of(id(98)));when(terminal.canonicalPins()).thenReturn(json.object().arrayNode().add(json.object().put("invocation_id",id(98)).put("accounting_sha256",hash('a')).put("result_registration_sha256",hash('b')).put("capture_registration_sha256",hash('c'))));
        when(sources.requireReceipts(eq(admission),eq(professionalInput),anyList(),anyList())).thenReturn(reads);when(reads.canonicalPins()).thenReturn(json.object().arrayNode().add(json.object().put("read_receipt_id",id(99)).put("receipt_sha256",hash('a'))));
        when(retrievals.require(eq(closedSession),anyList(),eq(false))).thenReturn(retrieved);when(retrieved.canonicalPins()).thenReturn(json.object().arrayNode());
        when(evaluated.candidateSha256()).thenReturn(WireValues.sha256(ContentReadAuthorization.canonicalBytes(projection)));when(evaluated.questionStatus()).thenReturn(ArtifactProtocol.QuestionStatus.VERIFIED);when(evaluated.completionCheckIds()).thenReturn(List.of(id(97)));when(evaluated.outcome()).thenReturn("SUCCEEDED");when(evaluated.missingRequirementIds()).thenReturn(List.of());
    }
    JsonNode result(JsonNode projection)throws Exception{var result=json.object();result.putArray("blocks").addObject().put("type","text").put("text",json.canonical(projection));return result;}
    TaskResearchExecutionService.Candidate candidate()throws Exception{return tx(()->outputs.candidate(closedSession,closed,terminal));}
    @Test void genuineTerminalAndReadReceiptsProduceCandidateBeforeEvaluatorTruth()throws Exception {
        var candidate=candidate();assertTrue(FormalTaskNodeAuthority.sameEvidence(projection,tx(candidate::projection)));verify(terminal).requireFor(closedSession,closed);verify(closed).requireSuccessfulExit();
        var output=tx(()->outputs.evidenceBundle(candidate,evaluated));var bundle=tx(()->TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,professionalSchema.read(output.kind(),output.bytes())).read());
        assertEquals(evidencePublication.schemaRef(),output.schemaRef());assertEquals(evidencePublication.schemaSha256(),output.schemaSha256());verify(retrievals).require(eq(closedSession),eq(List.of()),eq(false));
        assertEquals(ArtifactProtocol.QuestionStatus.VERIFIED,bundle.questionCoverage().get(0).status());assertEquals(List.of(id(97)),bundle.questionCoverage().get(0).completionCheckIds());assertEquals("POST_CLOSE",tx(()->output.provenance().required("phase").textValue()));
    }
    @Test void missingPublicationAuthorityCannotProduceCandidate()throws Exception {
        outputs.evidenceContracts(null);
        assertEquals(ErrorCode.DEPENDENCY_UNAVAILABLE,assertThrows(TenantAccessException.class,this::candidate).code());
    }
    @Test void revokedOrChangedPublicationCannotReuseCandidate()throws Exception {
        var candidate=candidate();
        when(evidenceContracts.require(tenant,actor,project,professionalInput.manifest())).thenReturn(evidenceCatalogue.requirePublished(evidenceCatalogue.schemaRef("artifacts-v2","EvidenceBundle"),evidenceCatalogue.schemaDigest("artifacts-v2")));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated))).code());
        when(evidenceContracts.require(tenant,actor,project,professionalInput.manifest())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated))).code());
    }
    @Test void rawTerminalWrongSessionFailedExitAndMissingReadProofCannotProduce()throws Exception {
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(terminal).requireFor(closedSession,closed);assertThrows(TenantAccessException.class,this::candidate);doNothing().when(terminal).requireFor(closedSession,closed);
        doThrow(new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(closed).requireSuccessfulExit();assertThrows(TenantAccessException.class,this::candidate);doNothing().when(closed).requireSuccessfulExit();
        when(sources.requireReceipts(eq(admission),eq(professionalInput),anyList(),anyList())).thenThrow(new TenantAccessException(ErrorCode.CONTENT_MISMATCH));assertThrows(TenantAccessException.class,this::candidate);
    }
    @Test void modelCannotSupplyStatusChecksUnknownFieldsOrExtraBlocks()throws Exception {
        var changed=(com.fasterxml.jackson.databind.node.ObjectNode)projection.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)changed.required("question_coverage").get(0)).put("status","VERIFIED");var status=changed;assertThrows(TenantAccessException.class,()->TaskResearchExecutionService.candidateProjection(result(status)));
        changed=(com.fasterxml.jackson.databind.node.ObjectNode)projection.deepCopy();changed.put("success",true);var unknown=changed;assertThrows(TenantAccessException.class,()->TaskResearchExecutionService.candidateProjection(result(unknown)));
        var extra=(com.fasterxml.jackson.databind.node.ObjectNode)result(projection);((com.fasterxml.jackson.databind.node.ArrayNode)extra.required("blocks")).addObject().put("type","tool-call");assertThrows(TenantAccessException.class,()->TaskResearchExecutionService.candidateProjection(extra));
    }
    @Test void changedResultReadRightsOrEvaluatorCannotReusePrivateCandidate()throws Exception {
        var candidate=candidate();when(terminal.output()).thenReturn(result(json.object()));assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated)));when(terminal.output()).thenReturn(result(projection));
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(reads).requireUnchanged();assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated)));doNothing().when(reads).requireUnchanged();when(evaluated.candidateSha256()).thenReturn(hash('f'));assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated)));
    }
    @Test void waitingNeedsRealWaitingIdentityAndCannotSayVerifiedSuccess()throws Exception {
        var candidate=candidate();when(evaluated.outcome()).thenReturn("WAITING_INPUT");assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated)));
        when(evaluated.waitingItemId()).thenReturn(id(101));when(evaluated.questionStatus()).thenReturn(ArtifactProtocol.QuestionStatus.INSUFFICIENT);var output=tx(()->outputs.evidenceBundle(candidate,evaluated));assertEquals(id(101),tx(()->output.provenance().required("producing_receipts").required("waiting_item_id").textValue()));
        when(evaluated.outcome()).thenReturn("SUCCEEDED");assertThrows(TenantAccessException.class,()->tx(()->outputs.evidenceBundle(candidate,evaluated)));
    }
    @Test void physicalStepResultRejectsWrongRegisteredEvidenceAndExport()throws Exception {
        var candidate=candidate();var evidence=mock(TaskProfessionalObjectRegistration.Registered.class);var export=mock(TaskProfessionalObjectRegistration.Registered.class);when(evidence.kind()).thenReturn("STEP_ENVELOPE");
        assertThrows(TenantAccessException.class,()->tx(()->outputs.stepResult(candidate,evaluated,evidence,export)));
    }
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SimulationAuthorizedUseRightsVerifierTest {
    private static PermissionSchema json;
    private static SourceRightsDecisionSchema decisions;
    private static SourceRightsSchema rightsSchema;
    private static final String TENANT=id(1),SOURCE=id(2),SOURCE_ID=id(3),RIGHTS=id(4),BASE=id(5),ACTOR=id(6),
        OPERATION=id(7),DECISION=id(8),SESSION=id(9),ARTIFACT=id(10),REQUEST=id(11),ACCESS_SET=id(12);
    private static final String NOW="2026-10-18T15:00:00Z",HASH="a".repeat(64),SOURCE_HASH="b".repeat(64);
    private static final ObjectRef STORAGE=new ObjectRef("tenant/actual-license.pdf","immutable-version-17",HASH,"37","application/pdf");

    @BeforeAll static void schemas() throws Exception {
        json=new PermissionSchema(); decisions=new SourceRightsDecisionSchema(json); rightsSchema=new SourceRightsSchema();
    }

    @Test void verifiesActualApprovalAndReturnsExactInternalStorageIdentityWithoutPublicLocation() throws Exception {
        var fixture=new Fixture(); var verified=fixture.verify();
        assertEquals(DECISION,verified.snapshot().path("decision_id").textValue());
        assertEquals(new PermissionProtocol.Basis(ARTIFACT,HASH),verified.evidence().get(0).ref());
        assertEquals(STORAGE,verified.evidence().get(0).object());
        assertEquals("1",verified.snapshot().path("evidence_count").textValue());
        assertFalse(verified.snapshot().has("evidence"));
        assertFalse(verified.snapshot().toString().contains("object_key"));
        assertFalse(verified.snapshot().toString().contains("object_version"));
        assertFalse(verified.snapshot().toString().contains(STORAGE.objectKey()));
        verified.snapshot().put("decision_id",id(20));
        ((ObjectNode)verified.evidence().get(0).manifest()).put("artifact_kind","UNTRUSTED");
        ((ObjectNode)verified.evidence().get(0).authorization().path("binding")).put("artifact_id",id(20));
        ((ObjectNode)verified.reviewPolicy()).put("region","CHANGED");
        assertEquals(DECISION,verified.snapshot().path("decision_id").textValue());
        assertEquals("AUTHORIZATION_EVIDENCE",verified.evidence().get(0).manifest().path("artifact_kind").textValue());
        assertEquals(ARTIFACT,verified.evidence().get(0).authorization().path("binding").path("artifact_id").textValue());
        assertEquals("cn-test",verified.reviewPolicy().path("region").textValue());
        assertThrows(UnsupportedOperationException.class,() -> verified.evidence().clear());
    }

    @Test void publicSnapshotContainsNoPrivateEvidenceMetadataOrSourceIdentities() throws Exception {
        var f=new Fixture(); var authorization=(ObjectNode)f.ledger.at("/decision/reviewed_manifest/evidence_authorizations/0");
        String privateSource=id(70),privateRights=id(71),privateVersion=id(72);
        authorization.putObject("sources").putObject(privateSource).put("source_version",privateSource).put("rights_revision",privateRights);
        authorization.putObject("versions").putObject(privateVersion).put("id",privateVersion).put("schema_ref","private-schema-location");
        f.resealReview(); var verified=f.verify(); String visible=verified.snapshot().toString();
        for(String secret:List.of(ARTIFACT,ACCESS_SET,privateSource,privateRights,privateVersion,SOURCE,ACTOR,OPERATION,STORAGE.objectKey(),STORAGE.objectVersion(),
                "AUTHORIZATION_EVIDENCE","application/pdf","rights-authorization/1.0.0","private-schema-location",
                "object_key","object_version","artifact_kind","schema_ref","dependency_manifest","sources","versions","policy","cn-test"))
            assertFalse(visible.contains(secret),secret);
        assertEquals(authorization,verified.evidence().get(0).authorization());
        assertEquals(java.util.Set.of("rights","decision_id","witness_sha256","evidence_count"),fieldNames(verified.snapshot()));
    }

    @Test void opaqueWitnessHashesExactReviewedAuthorizationsAndByteProofs() throws Exception {
        var f=new Fixture(); var verified=f.verify();
        var witness=json.object().put("schema_version","1.0.0");
        witness.set("evidence_authorizations",f.ledger.at("/decision/reviewed_manifest/evidence_authorizations"));
        witness.set("evidence_proof",f.ledger.at("/decision/evidence_proof"));
        assertEquals(hash(witness),verified.snapshot().path("witness_sha256").textValue());
        assertEquals(verified.snapshot(),f.verify().snapshot());
        ((ObjectNode)f.ledger.at("/decision/evidence_proof/0")).put("verified_at","2026-10-18T14:59:58Z");
        assertNotEquals(verified.snapshot().path("witness_sha256"),f.verify().snapshot().path("witness_sha256"));
    }

    @Test void opaqueWitnessChangesWhenReviewedPrivateMetadataChangesAndCannotBeMutatedThroughReturnedCopies() throws Exception {
        var f=new Fixture(); var original=f.verify();
        ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/evidence_authorizations/0/versions"))
            .putObject(id(73)).put("content_sha256","e".repeat(64));
        f.resealReview(); var changed=f.verify();
        assertNotEquals(original.snapshot().path("witness_sha256"),changed.snapshot().path("witness_sha256"));
        assertFalse(original.evidence().get(0).authorization().path("versions").has(id(73)));
        ((ObjectNode)changed.evidence().get(0).authorization().path("versions")).removeAll();
        assertTrue(changed.evidence().get(0).authorization().path("versions").has(id(73)));
    }

    private static java.util.Set<String> fieldNames(JsonNode value) {
        var result=new java.util.HashSet<String>(); value.fieldNames().forEachRemaining(result::add); return result;
    }

    @Test void acceptsDatabaseMicrosecondTimestampFormatting() throws Exception {
        var f=new Fixture(); var expected=f.verify().snapshot(); for(String path:List.of("/state","/decision","/operation"))
            ((ObjectNode)f.ledger.at(path)).put(path.equals("/state")?"updated_at":"created_at","2026-10-18T15:00:00.000000Z");
        assertEquals(expected,f.verify().snapshot());
    }

    @Test void rejectsBootstrapApprovedRightsWithoutActualDecision() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.path("state")).putNull("last_decision_id");
        ((ObjectNode)f.ledger.path("state")).put("revision","1"); f.ledger.putNull("decision"); f.ledger.putNull("operation");
        rejected(f);
    }

    @Test void rejectsMissingOrDuplicatedAuditAndOutboxWitnesses() throws Exception {
        for(String name:List.of("audits","outbox")) {
            var missing=new Fixture(); ((ArrayNode)missing.ledger.get(name)).removeAll(); rejected(missing);
            var duplicate=new Fixture(); var array=(ArrayNode)duplicate.ledger.get(name); array.add(array.get(0).deepCopy()); rejected(duplicate);
        }
    }

    @Test void rejectsRevokedCurrentRightsEvenWithOriginalApprovedReceipt() throws Exception {
        var f=new Fixture(); var r=f.rights;
        f.rights=new SourceReadProtocol.RightsRow(r.tenantId(),r.rightsRevision(),r.sourceVersion(),r.purpose(),r.allowedActions(),
            r.providers(),r.regions(),r.validFrom(),r.validTo(),"REVOKED",r.evidenceManifest(),r.contentSha256(),r.schemaVersion(),
            r.schemaManifest(),r.classification(),r.registeredSha256(),r.registeredProjectId()); rejected(f);
    }

    @Test void rejectsCurrentSourceRegistrationMismatch() throws Exception {
        var f=new Fixture(); var s=f.source;
        f.source=new SourceReadProtocol.SourceRow(s.tenantId(),s.sourceId(),s.sourceVersion(),s.ownerProjectId(),s.ownership(),
            s.classification(),s.rightsRevision(),s.originalSha256(),HASH,s.registeredProjectId()); rejected(f);
    }

    @Test void rejectsReviewedProposalEvenAfterAttackerRecalculatesEveryRecordedHash() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/proposal")).put("purpose","UNAPPROVED_PURPOSE");
        ((ObjectNode)f.ledger.at("/decision/request_manifest/body/proposal")).put("purpose","UNAPPROVED_PURPOSE");
        f.resealReview(); rejected(f);
    }

    @Test void rejectsReviewedSourceEvenAfterAttackerRecalculatesEveryRecordedHash() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/source")).put("original_sha256",HASH);
        f.resealReview(); rejected(f);
    }

    @Test void rejectsChangedHistoricalRightsPayloadDespiteResealedReviewAndRequest() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/rights")).put("purpose","ALTERED_BASE_RIGHTS");
        f.resealReview(); rejected(f);
    }

    @Test void rejectsHistoricalRightsSchemaDriftDespiteResealedReviewAndRequest() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/reviewed_manifest")).put("rights_schema_manifest_sha256",rightsSchema.manifestSha256());
        f.resealReview(); rejected(f);
    }

    @Test void rejectsDifferentStorageObjectWithSameContentHash() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/evidence_proof/0/object_ref")).put("object_key","tenant/unrelated.pdf");
        ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/evidence_authorizations/0/object_ref")).put("object_key","tenant/unrelated.pdf");
        f.resealReview(); rejected(f);
    }

    @Test void rejectsWrongReviewedArtifactBindingEvenWhenReviewHashIsConsistent() throws Exception {
        var f=new Fixture(); ((ObjectNode)f.ledger.at("/decision/reviewed_manifest/evidence_authorizations/0/binding")).put("artifact_id",id(99));
        f.resealReview(); rejected(f);
    }

    @ParameterizedTest(name="rejects ledger mutation {0}") @MethodSource("tamperPaths")
    void rejectsAlteredIndependentLedgerFields(String pointer) throws Exception {
        var f=new Fixture(); int separator=pointer.lastIndexOf('/');
        var parent=(ObjectNode)f.ledger.at(pointer.substring(0,separator)); String name=pointer.substring(separator+1);
        var value=parent.get(name);
        if(value.isTextual()) {
            String old=value.textValue();
            parent.put(name,old.matches("[0-9a-f]{64}")?"c".repeat(64):old.matches("[0-9a-f-]{36}")?id(99):
                old.matches("[0-9]+")?"99":old.endsWith("Z")?"2026-10-17T15:00:00Z":"ALTERED");
        } else if(value.isNumber()) parent.put(name,500); else if(value.isNull()) parent.put(name,"unexpected");
        else parent.putNull(name);
        rejected(f);
    }

    static Stream<String> tamperPaths() {
        return Stream.of(
            "/state/tenant_id","/state/source_version","/state/rights_revision","/state/last_decision_id","/state/revision",
            "/state/schema_manifest_sha256","/state/updated_at","/state/classification",
            "/decision/decision_id","/decision/operation_id","/decision/from_rights_revision","/decision/to_rights_revision",
            "/decision/decision","/decision/rights_status","/decision/expected_revision","/decision/resulting_revision",
            "/decision/reason","/decision/reviewed_manifest_sha256","/decision/created_by","/decision/created_at",
            "/decision/schema_manifest_sha256","/decision/receipt_ref/data/rights/content_sha256",
            "/operation/tenant_id","/operation/operation_name","/operation/subject_id","/operation/request_sha256",
            "/operation/state","/operation/http_status","/operation/target_id","/operation/error_code","/operation/created_by",
            "/operation/created_at","/operation/response_ref/data/decision_id",
            "/decision/request_manifest/session_id","/decision/request_manifest/path","/decision/request_manifest/context_header",
            "/decision/request_manifest/body/base_rights/content_sha256",
            "/decision/reviewed_manifest/source/source_id","/decision/reviewed_manifest/context/tenant_id",
            "/decision/evidence_proof/0/full_sha256","/decision/evidence_proof/0/evidence/artifact/version_id",
            "/decision/evidence_proof/0/object_ref/object_version","/decision/evidence_proof/0/object_ref/media_type",
            "/audits/0/tenant_id","/audits/0/project_id","/audits/0/subject_id","/audits/0/action","/audits/0/result",
            "/audits/0/reason_code","/audits/0/request_id","/audits/0/trace_id","/audits/0/schema_manifest_sha256",
            "/audits/0/created_by","/audits/0/resource_ref/rights/content_sha256",
            "/outbox/0/tenant_id","/outbox/0/aggregate_id","/outbox/0/aggregate_revision","/outbox/0/event_type",
            "/outbox/0/payload/schema_manifest_sha256","/outbox/0/payload/decision/decision_id");
    }

    private static void rejected(Fixture fixture) {
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,fixture::verify).code());
    }
    private static String id(int value) { return "00000000-0000-4000-8000-%012d".formatted(value); }
    private static String hash(JsonNode value) throws Exception { return DigestUtils.sha256Hex(json.canonical(value)); }

    static final class Fixture {
        SourceReadProtocol.SourceRow source;
        SourceReadProtocol.RightsRow rights;
        final ObjectNode ledger;

        Fixture() throws Exception {
            if(json==null) schemas();
            source=new SourceReadProtocol.SourceRow(TENANT,SOURCE_ID,SOURCE,null,"ENTERPRISE","INTERNAL",RIGHTS,SOURCE_HASH,SOURCE_HASH,null);
            var evidenceManifest=json.object(); evidenceManifest.put("owner","Actual rights owner"); evidenceManifest.put("license","Approved evaluation use");
            evidenceManifest.putArray("environments").add("SIM"); evidenceManifest.putArray("classifications").add("INTERNAL");
            evidenceManifest.putArray("authorization_evidence").add(json.tree(STORAGE));
            var payload=json.object(); payload.put("schema_version","1.0.0"); payload.put("tenant_id",TENANT);
            payload.put("source_version",SOURCE); payload.put("rights_revision",RIGHTS); payload.put("purpose","SIMULATION_EVALUATION");
            payload.putArray("allowed_actions").add("STORE").add("READ").add("ENTERPRISE_USE"); payload.putArray("providers");
            payload.putArray("regions").add("cn-test"); payload.putNull("valid_from"); payload.putNull("valid_to");
            payload.put("classification","INTERNAL"); payload.set("evidence_manifest",evidenceManifest);
            String rightsHash=hash(payload);
            rights=new SourceReadProtocol.RightsRow(TENANT,RIGHTS,SOURCE,"SIMULATION_EVALUATION",json.canonical(payload.get("allowed_actions")),"[]",
                "[\"cn-test\"]",null,null,"APPROVED",json.canonical(evidenceManifest),rightsHash,"1.0.0",rightsSchema.manifestSha256(),
                "INTERNAL",rightsHash,null);
            assertEquals(payload,rightsSchema.verify(rights));
            var previous=payload.deepCopy(); previous.put("rights_revision",BASE);
            ((ObjectNode)previous.get("evidence_manifest")).putArray("authorization_evidence");
            previous.putArray("allowed_actions"); String baseHash=hash(previous);
            var base=new PermissionProtocol.Basis(BASE,baseHash); var ref=new PermissionProtocol.Basis(ARTIFACT,HASH);
            var proposal=new SourceRightsProtocol.Proposal("SIMULATION_EVALUATION",List.of("STORE","READ","ENTERPRISE_USE"),List.of(),List.of("cn-test"),
                null,null,new SourceRightsProtocol.EvidenceManifest("Actual rights owner","Approved evaluation use",List.of("SIM"),List.of("INTERNAL"),List.of(ref)));
            var publicEvidence=json.object(); publicEvidence.set("artifact",json.tree(ref)); publicEvidence.put("artifact_kind","AUTHORIZATION_EVIDENCE");
            publicEvidence.put("classification","INTERNAL"); publicEvidence.put("media_type","application/pdf"); publicEvidence.put("size_bytes","37");
            publicEvidence.put("schema_ref","rights-authorization/1.0.0"); publicEvidence.put("schema_document_sha256","d".repeat(64));
            var dependencies=json.object(); dependencies.put("schema_version","1.0.0"); dependencies.put("tenant_id",TENANT);
            dependencies.putNull("project_id"); dependencies.put("access_set_id",ACCESS_SET); dependencies.putArray("members");
            var binding=json.object(); binding.put("tenant_id",TENANT); binding.putNull("project_id"); binding.put("artifact_id",ARTIFACT);
            binding.put("resource_kind","SOURCE"); binding.put("resource_id",SOURCE_ID); binding.put("schema_ref","rights-authorization/1.0.0");
            binding.put("schema_document_sha256","d".repeat(64)); binding.put("access_set_id",ACCESS_SET);
            binding.put("dependency_manifest_sha256",hash(dependencies));
            var authorization=json.object(); authorization.set("evidence",publicEvidence); authorization.set("object_ref",json.tree(STORAGE));
            authorization.set("binding",binding); authorization.set("dependency_manifest",dependencies);
            authorization.putObject("versions"); authorization.putObject("sources");
            var reviewed=json.object(); reviewed.put("schema_version","1.0.0"); reviewed.set("source",json.tree(source));
            ((ObjectNode)reviewed.get("source")).put("rights_revision",BASE); reviewed.set("rights",previous);
            reviewed.put("rights_status","PENDING_REVIEW"); reviewed.put("rights_schema_manifest_sha256",rightsSchema.pendingManifestSha256());
            reviewed.put("revision","1"); reviewed.put("lifecycle","ACTIVE"); reviewed.put("actor_id",ACTOR); reviewed.put("session_id",SESSION);
            reviewed.put("schema_manifest_sha256",decisions.manifestSha256()); reviewed.put("decision","APPROVE"); reviewed.set("proposal",json.tree(proposal));
            reviewed.set("policy",json.tree(new SourceRightsPolicy(true,id(90),"SIM","cn-test",false,64,1024,100,100,
                List.of(new SourceRightsPolicy.EvidenceProfile("AUTHORIZATION","AUTHORIZATION_EVIDENCE","SOURCE","source.read",
                    "rights-authorization/1.0.0","d".repeat(64),"e".repeat(64))))));
            reviewed.putArray("evidence_authorizations").add(authorization);
            var context=json.object(); context.put("tenant_id",TENANT); context.put("context_revision","3"); context.put("auth_epoch","2"); context.put("governance_epoch","0");
            reviewed.set("context",context); String reviewedHash=hash(reviewed);
            var input=new SourceRightsProtocol.DecisionInput("1.0.0","1",base,"APPROVE",proposal,reviewedHash,"Verified license bytes");
            var request=decisions.requestManifest(TENANT,SOURCE,input,"3",SESSION);
            var result=json.object(); result.put("schema_version","1.0.0"); result.put("operation_id",OPERATION); result.put("decision_id",DECISION);
            result.put("source_version",SOURCE); result.set("base_rights",json.tree(base)); result.set("rights",json.tree(new PermissionProtocol.Basis(RIGHTS,rightsHash)));
            result.put("decision","APPROVE"); result.put("rights_status","APPROVED"); result.put("revision","2"); result.put("reviewed_manifest_sha256",reviewedHash);
            result.putArray("evidence").add(publicEvidence); result.put("actor_id",ACTOR); result.put("decided_at",NOW);
            var after=context.deepCopy(); after.put("context_revision","4"); after.put("governance_epoch","1"); result.set("current_context",after);
            var receipt=decisions.envelope("Receipt",result,REQUEST,"2");
            var proof=json.object(); proof.put("schema_version","1.0.0"); proof.set("evidence",publicEvidence); proof.set("object_ref",json.tree(STORAGE));
            proof.put("full_sha256",HASH); proof.put("verified_at","2026-10-18T14:59:59Z");
            var state=row(rightsSchema.manifestSha256()); state.put("source_version",SOURCE); state.put("rights_revision",RIGHTS);
            state.put("last_decision_id",DECISION); state.put("revision","2"); state.put("updated_at",NOW);
            var decision=row(decisions.manifestSha256()); decision.put("source_version",SOURCE); decision.put("decision_id",DECISION); decision.put("operation_id",OPERATION);
            decision.put("from_rights_revision",BASE); decision.put("to_rights_revision",RIGHTS); decision.put("decision","APPROVE"); decision.put("rights_status","APPROVED");
            decision.put("expected_revision","1"); decision.put("resulting_revision","2"); decision.put("reason",input.reason());
            decision.set("request_manifest",request); decision.set("reviewed_manifest",reviewed); decision.put("reviewed_manifest_sha256",reviewedHash);
            decision.putArray("evidence_proof").add(proof); decision.set("receipt_ref",receipt); decision.put("created_by",ACTOR); decision.put("created_at",NOW);
            var operation=row(decisions.manifestSha256()); operation.put("operation_id",OPERATION); operation.put("subject_id",ACTOR); operation.put("operation_name","KN-RIGHTS-01");
            operation.put("request_sha256",hash(request)); operation.put("state","COMPLETED"); operation.put("http_status",200); operation.set("response_ref",receipt);
            operation.put("target_id",SOURCE); operation.putNull("error_code"); operation.put("created_by",ACTOR); operation.put("created_at",NOW);
            var audit=row(decisions.manifestSha256()); audit.put("audit_id",id(13)); audit.putNull("project_id"); audit.put("subject_id",ACTOR);
            audit.put("action","source.rights_decided"); audit.set("resource_ref",result); audit.put("result","SUCCESS"); audit.putNull("reason_code");
            audit.put("request_id",REQUEST); audit.put("trace_id",REQUEST); audit.put("created_by",ACTOR);
            var event=json.object(); event.put("event_id",id(14)); event.put("tenant_id",TENANT); event.put("aggregate_id",SOURCE);
            event.put("aggregate_revision","2"); event.put("event_type","source.rights_decided");
            var eventPayload=json.object(); eventPayload.put("schema_version","1.0.0"); eventPayload.put("schema_manifest_sha256",decisions.manifestSha256());
            eventPayload.set("decision",result); event.set("payload",eventPayload);
            var root=json.object(); root.set("state",state); root.set("decision",decision); root.set("operation",operation);
            root.putArray("audits").add(audit); root.putArray("outbox").add(event); ledger=root.deepCopy();
        }
        private static ObjectNode row(String schemaManifest) {
            var row=json.object(); row.put("tenant_id",TENANT); row.put("schema_version","1.0.0");
            row.put("schema_manifest_sha256",schemaManifest); row.put("classification","INTERNAL"); return row;
        }
        SimulationAuthorizedUseRightsVerifier.Verified verify() throws Exception {
            return SimulationAuthorizedUseRightsVerifier.verify(json,decisions,rightsSchema,source,rights,ledger);
        }
        void resealReview() throws Exception {
            String digest=hash(ledger.at("/decision/reviewed_manifest"));
            for(String path:List.of("/decision","/decision/request_manifest/body","/decision/receipt_ref/data", "/operation/response_ref/data", "/audits/0/resource_ref", "/outbox/0/payload/decision"))
                ((ObjectNode)ledger.at(path)).put("reviewed_manifest_sha256",digest);
            ((ObjectNode)ledger.path("operation")).put("request_sha256",hash(ledger.at("/decision/request_manifest")));
        }
    }
}

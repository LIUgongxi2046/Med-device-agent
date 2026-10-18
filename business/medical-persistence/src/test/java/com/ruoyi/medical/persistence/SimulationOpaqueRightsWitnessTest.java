package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.AuthenticatedIdentityBinding;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.ruoyi.medical.persistence.mapper.SourceReadMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real read/purpose/rights/lineage authorities; mapper and authenticated identity-proof boundaries are explicit doubles. */
class SimulationOpaqueRightsWitnessTest {
    private static final String TENANT=id(1),REVIEWER=id(6),ARTIFACT=id(10),ACCESS_SET=id(12),LICENSE=id(51),LICENSE_ID=id(52),LICENSE_RIGHTS=id(53),WORKER=id(60);
    private static final String SIM="SIMULATION_EVALUATION",REVIEW="SOURCE_RIGHTS_REVIEW";

    @Test void reviewerCanReadLicenseButWorkerCanOnlyConsumeOpaqueWitnessAndBusinessSource() throws Exception {
        var f=new Fixture();
        assertEquals(f.licenseRights,f.in(f.reviewer,() -> f.reads.require(f.reviewer,f.requirement(f.license,REVIEW))).rights());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.resources.require(f.worker,f.scope(f.license),"source.read",REVIEW))).code());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.resources.require(f.worker,f.scope(f.license),"source.read",SIM))).code());
        clearInvocations(f.permissions);
        assertDoesNotThrow(() -> f.in(f.worker,() -> { f.witness.require(f.worker,f.business.verify()); return null; }));
        verify(f.permissions,never()).resourceGrants(anyString(),anyString());
        assertEquals(f.business.rights,f.in(f.worker,() -> f.reads.require(f.worker,f.requirement(f.business.source,SIM))).rights());
        assertFalse(f.business.verify().snapshot().toString().contains(LICENSE));
    }

    @Test void addingLicenseReadGrantCannotChangeLicensePurposeOrWorkerPurposeBoundary() throws Exception {
        var f=new Fixture(); var inappropriateGrant=f.grant(WORKER,LICENSE_ID,SIM);
        when(f.permissions.resourceGrants(WORKER,"source.read")).thenReturn(List.of(inappropriateGrant));
        assertEquals(inappropriateGrant,f.in(f.worker,() -> f.resources.require(f.worker,f.scope(f.license),"source.read",SIM)));
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.reads.require(f.worker,f.requirement(f.license,SIM)))).code());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.resources.require(f.worker,f.scope(f.license),"source.read",REVIEW))).code());
    }

    @Test void revokedLicenseRightsInvalidateOpaqueWitnessWithoutGrantingContentRead() throws Exception {
        var f=new Fixture(); var old=f.licenseRights;
        f.licenseRights=new SourceReadProtocol.RightsRow(old.tenantId(),old.rightsRevision(),old.sourceVersion(),old.purpose(),old.allowedActions(),
            old.providers(),old.regions(),old.validFrom(),old.validTo(),"REVOKED",old.evidenceManifest(),old.contentSha256(),old.schemaVersion(),
            old.schemaManifest(),old.classification(),old.registeredSha256(),old.registeredProjectId());
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void changedCurrentVersionInvalidatesOpaqueWitness() throws Exception {
        var f=new Fixture(); when(f.artifacts.version(LICENSE)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(LICENSE,null,"SOURCE_VERSION","f".repeat(64))));
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void replacingCurrentLicenseRightsRevisionInvalidatesHistoricalWitness() throws Exception {
        var f=new Fixture(); var s=f.license;
        when(f.sources.source(LICENSE)).thenReturn(List.of(new SourceReadProtocol.SourceRow(s.tenantId(),s.sourceId(),s.sourceVersion(),s.ownerProjectId(),
            s.ownership(),s.classification(),id(98),s.originalSha256(),s.registeredSha256(),s.registeredProjectId())));
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void inactiveCurrentLicenseGateInvalidatesOpaqueWitness() throws Exception {
        var f=new Fixture(); when(f.sources.gates(LICENSE)).thenReturn(List.of(new SourceReadProtocol.GateRow(id(80),"RECEIVED","APPROVED","RECYCLED",BigInteger.ONE)));
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void sameHashAtDifferentObjectLocationInvalidatesOpaqueWitness() throws Exception {
        var f=new Fixture(); var old=f.artifact;
        var alternate=new com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef("private/different-license.pdf",old.objectVersion(),old.sha256(),old.sizeBytes().toString(),old.mediaType());
        f.artifact=new ArtifactRegistryProtocol.ArtifactRow(old.tenantId(),old.projectId(),old.artifactId(),old.artifactKind(),StoredObjectReferences.encode(alternate),
            alternate.objectKey(),alternate.objectVersion(),old.sha256(),old.sizeBytes(),old.mediaType(),old.accessSetId(),old.contentSha256(),old.schemaVersion(),old.schemaManifest(),old.classification());
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void alteredAuditOrOutboxRejectsApprovalBeforeOpaqueWitnessConsumption() throws Exception {
        for(String path:List.of("/audits/0/resource_ref","/outbox/0/payload/decision")) {
            var f=new Fixture(); ((ObjectNode)f.business.ledger.at(path)).put("actor_id",WORKER);
            assertThrows(TenantAccessException.class,() -> f.check());
        }
    }

    @Test void derivedOrNestedLicenseClosureFailsClosed() throws Exception {
        var f=new Fixture(); when(f.sources.parents(LICENSE)).thenReturn(List.of(new SourceReadProtocol.ParentRow(LICENSE,id(70),"DERIVED_FROM")));
        when(f.artifacts.version(id(70))).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(id(70),null,"SOURCE_VERSION","f".repeat(64))));
        assertThrows(TenantAccessException.class,() -> f.check());
    }

    @Test void changedReviewPolicyAndMissingBusinessReadGrantFailClosed() throws Exception {
        var f=new Fixture(); var p=f.policy;
        var changed=new SourceRightsPolicy(p.enabled(),id(99),p.environment(),p.region(),p.separateReviewer(),p.maximumEvidence(),p.maximumEvidenceBytes(),p.maximumVersions(),p.maximumRelations(),p.artifactProfiles());
        var witness=new SimulationOpaqueRightsWitness(f.json,changed,f.rights,f.permissions,f.sources,f.artifacts,f.originals,f.manifests,f.lineage);
        assertThrows(TenantAccessException.class,() -> f.in(f.worker,() -> { witness.require(f.worker,f.business.verify());return null; }));
        when(f.permissions.resourceGrants(WORKER,"source.read")).thenReturn(List.of());
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.reads.require(f.worker,f.requirement(f.business.source,SIM)))).code());
    }

    /** Composition test: the document-registration adapter is doubled, but its callback runs the real rights verifier and opaque validator. */
    @Test void sharedHistoricalLegalUseGateRechecksRealWitnessAndRejectsLaterLicenseRevocation() throws Exception {
        var f=new Fixture(); var legal=new LegalUseFixture(f);
        assertSame(legal.approvedRule,f.in(f.worker,() -> SimulationEvaluationLegalUseAuthority.require(f.worker,legal.request,legal.rules,legal.documentAdapter)));
        verify(legal.ruleStore).requireEvaluation(f.worker,legal.request.rule_revision(),true);
        assertEquals(f.business.rights,f.in(f.worker,() -> f.reads.require(f.worker,f.requirement(f.business.source,SIM))).rights());
        var current=f.licenseRights;
        f.licenseRights=new SourceReadProtocol.RightsRow(current.tenantId(),current.rightsRevision(),current.sourceVersion(),current.purpose(),current.allowedActions(),
            current.providers(),current.regions(),current.validFrom(),current.validTo(),"REVOKED",current.evidenceManifest(),current.contentSha256(),current.schemaVersion(),
            current.schemaManifest(),current.classification(),current.registeredSha256(),current.registeredProjectId());
        clearInvocations(legal.ruleStore);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> SimulationEvaluationLegalUseAuthority.require(f.worker,legal.request,legal.rules,legal.documentAdapter))).code());
        verifyNoInteractions(legal.ruleStore);
        assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> f.resources.require(f.worker,f.scope(f.license),"source.read",REVIEW))).code());
    }

    @Test void sharedHistoricalLegalUseGateRequiresStrictApprovalThroughRealEvaluationRuleRouting() throws Exception {
        var f=new Fixture(); var legal=new LegalUseFixture(f);
        when(legal.ruleStore.requireEvaluation(f.worker,legal.request.rule_revision(),true)).thenThrow(new TenantAccessException(ErrorCode.SOURCE_NOT_READY));
        assertEquals(ErrorCode.SOURCE_NOT_READY,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> SimulationEvaluationLegalUseAuthority.require(f.worker,legal.request,legal.rules,legal.documentAdapter))).code());
        verify(legal.documentAdapter).require(f.worker,legal.request);
        verify(legal.ruleStore).requireEvaluation(f.worker,legal.request.rule_revision(),true);
        verify(legal.ruleStore,never()).requireEvaluation(any(),any(),eq(false));
    }

    @Test void sharedHistoricalLegalUseGateRejectsUnavailableSimulationRuleStore() throws Exception {
        var f=new Fixture(); var legal=new LegalUseFixture(f); when(legal.ruleProvider.getIfAvailable()).thenReturn(null);
        assertEquals(ErrorCode.SCHEMA_UNSUPPORTED,assertThrows(TenantAccessException.class,() -> f.in(f.worker,
            () -> SimulationEvaluationLegalUseAuthority.require(f.worker,legal.request,legal.rules,legal.documentAdapter))).code());
        verify(legal.documentAdapter).require(f.worker,legal.request); verifyNoInteractions(legal.ruleStore);
    }

    /** Real EvaluationRuleAuthority dispatch; rule-store and public-document registration are explicit composition boundaries.
     * This does not claim actual DB registration, settled-result schema traversal, or rule-decision reconstruction.
     * Existing SimulationArtifactReadGuardTest separately covers COMPLETED result reads through this shared gate.
     */
    private static final class LegalUseFixture {
        final SimulationRuleVersionStore ruleStore=mock(SimulationRuleVersionStore.class);
        @SuppressWarnings("unchecked") final org.springframework.beans.factory.ObjectProvider<SimulationRuleVersionStore> ruleProvider=mock(org.springframework.beans.factory.ObjectProvider.class);
        final SimulationAuthorizedUseEvidenceAuthority documentAdapter=mock(SimulationAuthorizedUseEvidenceAuthority.class);
        final EvaluationRuleAuthority rules;
        final EvaluationProtocol.Create request;
        final RuleVersionProtocol.Validated approvedRule;
        LegalUseFixture(Fixture f)throws Exception {
            var evaluationSchema=new EvaluationSchema(f.json);
            var expected=new PermissionProtocol.Basis(id(111),"a".repeat(64));
            var sample=new EvaluationProtocol.Sample(id(110),id(112),"ACCEPTANCE","PTR","SIMULATION",
                new PermissionProtocol.Basis(f.business.source.sourceVersion(),f.business.source.originalSha256()),expected,
                List.of(new EvaluationProtocol.Expectation("EVAL-02",List.of("required-0"),List.of())));
            sample=new EvaluationProtocol.Sample(sample.sample_id(),sample.source_family(),sample.split(),sample.document_type(),sample.evidence_class(),sample.input_ref(),
                new PermissionProtocol.Basis(expected.version_id(),evaluationSchema.hash(evaluationSchema.expectedItems(sample))),sample.expectations());
            var provisional=new EvaluationProtocol.Create("1.0.0",null,new PermissionProtocol.Basis(id(113),"a".repeat(64)),
                new PermissionProtocol.Basis(id(114),evaluationSchema.hash(List.of(sample))),List.of(sample),List.of(new PermissionProtocol.Basis(id(115),"b".repeat(64))),
                new PermissionProtocol.Basis(id(116),"c".repeat(64)),List.of("EVAL-02"),new EvaluationProtocol.Variance(1,"ALL_REQUIRED_RUNS",null),
                new PermissionProtocol.Basis(id(117),f.hash(f.business.verify().snapshot())),"SIMULATION","Current legal use for a fixed historical result");
            request=evaluationSchema.create(f.json.canonical(f.json.tree(provisional)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var ruleSchema=new RuleVersionSchema(f.json,new TemplateApplicabilitySchema(f.json),new RuleExpression());
            rules=new EvaluationRuleAuthority(mock(com.ruoyi.medical.persistence.mapper.RuleVersionMapper.class),f.resources,ruleSchema,f.json,
                new com.ruoyi.medical.identity.IdentityDirectoryPolicy("INTERNAL"),ruleProvider);
            when(ruleProvider.getIfAvailable()).thenReturn(ruleStore);
            approvedRule=new RuleVersionProtocol.Validated(null,"{}",request.rule_revision().content_sha256(),List.of());
            when(ruleStore.requireEvaluation(f.worker,request.rule_revision(),true)).thenReturn(approvedRule);
            doAnswer(call -> {f.witness.require(call.getArgument(0),f.business.verify());return null;})
                .when(documentAdapter).require(f.worker,request);
        }
    }

    private static String id(int n) { return "00000000-0000-4000-8000-%012d".formatted(n); }
    @FunctionalInterface private interface Checked<T> { T call() throws Exception; }

    private static final class Fixture {
        final PermissionSchema json=new PermissionSchema(); final SourceRightsSchema rights=new SourceRightsSchema();
        final DependencyManifestSchema manifests=new DependencyManifestSchema();
        final PermissionMapper permissions=mock(PermissionMapper.class); final SourceReadMapper sources=mock(SourceReadMapper.class);
        final ArtifactRegistryMapper artifacts=mock(ArtifactRegistryMapper.class); final SimulationFixtureMapper originals=mock(SimulationFixtureMapper.class);
        final PermissionAuthority permissionAuthority=new PermissionAuthority(json);
        final ResourceReadAuthority resources=new ResourceReadAuthority(permissions,permissionAuthority,json);
        final SourceReadAuthority reads=new SourceReadAuthority(sources,permissions,resources,rights);
        final DependencyLineage lineage=new DependencyLineage(sources,artifacts);
        final SimulationAuthorizedUseRightsVerifierTest.Fixture business=new SimulationAuthorizedUseRightsVerifierTest.Fixture();
        final SourceRightsPolicy policy; final SimulationOpaqueRightsWitness witness;
        final SourceReadProtocol.SourceRow license;
        SourceReadProtocol.RightsRow licenseRights;
        ArtifactRegistryProtocol.ArtifactRow artifact;
        final EnterpriseAccess.Admission reviewer,worker;
        final LocalDateTime now=LocalDateTime.parse("2026-10-18T16:00:00");

        Fixture() throws Exception {
            var evidence=business.verify().evidence().get(0); var storage=evidence.object();
            license=new SourceReadProtocol.SourceRow(TENANT,LICENSE_ID,LICENSE,null,"ENTERPRISE","INTERNAL",LICENSE_RIGHTS,storage.sha256(),storage.sha256(),null);
            var payload=rights.verify(business.rights).deepCopy(); payload.put("source_version",LICENSE); payload.put("rights_revision",LICENSE_RIGHTS); payload.put("purpose",REVIEW);
            payload.putArray("allowed_actions").add("STORE").add("READ");
            String licenseHash=hash(payload);
            licenseRights=new SourceReadProtocol.RightsRow(TENANT,LICENSE_RIGHTS,LICENSE,REVIEW,json.canonical(payload.get("allowed_actions")),"[]","[\"cn-test\"]",null,null,"APPROVED",
                json.canonical(payload.get("evidence_manifest")),licenseHash,"1.0.0",rights.manifestSha256(),"INTERNAL",licenseHash,null);
            var catalogue=SchemaCatalogue.fromClasspath(); String schemaRef=catalogue.schemaRef("interfaces","ObjectRef"),schemaHash=catalogue.schemaDigest("interfaces");
            policy=new SourceRightsPolicy(true,id(90),"SIM","cn-test",false,64,1024,100,100,List.of(new SourceRightsPolicy.EvidenceProfile(
                "AUTHORIZATION","AUTHORIZATION_EVIDENCE","SOURCE","source.read",schemaRef,schemaHash,"e".repeat(64))));
            var member=new ArtifactRegistryProtocol.MemberRow(LICENSE,LICENSE_RIGHTS);
            var manifest=new ArtifactRegistryProtocol.DependencyManifest("1.0.0",TENANT,null,ACCESS_SET,List.of(member)); String manifestHash=hash(json.tree(manifest));
            var binding=new ArtifactRegistryProtocol.BindingRow(TENANT,null,ARTIFACT,"SOURCE",LICENSE_ID,schemaRef,schemaHash,ACCESS_SET,manifestHash);
            artifact=new ArtifactRegistryProtocol.ArtifactRow(TENANT,null,ARTIFACT,"AUTHORIZATION_EVIDENCE",StoredObjectReferences.encode(storage),storage.objectKey(),storage.objectVersion(),
                storage.sha256(),new BigInteger(storage.sizeBytes()),storage.mediaType(),ACCESS_SET,storage.sha256(),"1.0.0","e".repeat(64),"INTERNAL");
            var artifactVersion=new ArtifactRegistryProtocol.VersionRow(ARTIFACT,null,"ARTIFACT",storage.sha256());
            var sourceVersion=new ArtifactRegistryProtocol.VersionRow(LICENSE,null,"SOURCE_VERSION",storage.sha256());
            for(String path:List.of("/decision/reviewed_manifest/evidence_authorizations/0/evidence","/decision/evidence_proof/0/evidence",
                    "/decision/receipt_ref/data/evidence/0","/operation/response_ref/data/evidence/0","/audits/0/resource_ref/evidence/0","/outbox/0/payload/decision/evidence/0")) {
                var node=(ObjectNode)business.ledger.at(path); node.put("schema_ref",schemaRef); node.put("schema_document_sha256",schemaHash);
            }
            var reviewed=(ObjectNode)business.ledger.at("/decision/reviewed_manifest"); reviewed.set("policy",json.tree(policy));
            var authorization=(ObjectNode)reviewed.at("/evidence_authorizations/0"); authorization.set("binding",json.tree(binding)); authorization.set("dependency_manifest",json.tree(manifest));
            var versions=authorization.putObject("versions"); versions.putObject(ARTIFACT).set("version",json.tree(artifactVersion)); versions.putObject(LICENSE).set("version",json.tree(sourceVersion));
            var prior=authorization.putObject("sources").putObject(LICENSE); prior.set("source",json.tree(license)); prior.set("rights",rights.verify(licenseRights)); business.resealReview();
            when(permissions.databaseTime()).thenReturn(now);
            when(permissions.resourceOwner("SOURCE",LICENSE_ID)).thenReturn(List.of(new PermissionProtocol.OwnerRow(LICENSE_ID,null)));
            when(permissions.resourceOwner("SOURCE",business.source.sourceId())).thenReturn(List.of(new PermissionProtocol.OwnerRow(business.source.sourceId(),null)));
            when(permissions.resourceGrants(REVIEWER,"source.read")).thenReturn(List.of(grant(REVIEWER,LICENSE_ID,REVIEW)));
            when(permissions.resourceGrants(WORKER,"source.read")).thenReturn(List.of(grant(WORKER,business.source.sourceId(),SIM)));
            when(sources.source(LICENSE)).thenAnswer(call -> List.of(license)); when(sources.source(business.source.sourceVersion())).thenAnswer(call -> List.of(business.source));
            when(sources.rights(LICENSE_RIGHTS)).thenAnswer(call -> List.of(licenseRights)); when(sources.rights(business.rights.rightsRevision())).thenAnswer(call -> List.of(business.rights));
            var gate=new SourceReadProtocol.GateRow(id(80),"RECEIVED","APPROVED","ACTIVE",BigInteger.ONE);
            when(sources.gates(LICENSE)).thenReturn(List.of(gate)); when(sources.gates(business.source.sourceVersion())).thenReturn(List.of(gate));
            when(sources.parents(ARTIFACT)).thenReturn(List.of(new SourceReadProtocol.ParentRow(ARTIFACT,LICENSE,"SOURCE_ORIGINAL")));
            when(artifacts.version(ARTIFACT)).thenReturn(List.of(artifactVersion)); when(artifacts.version(LICENSE)).thenReturn(List.of(sourceVersion));
            when(artifacts.version(ACCESS_SET)).thenReturn(List.of(new ArtifactRegistryProtocol.VersionRow(ACCESS_SET,null,"DEPENDENCY_ACCESS_SET",manifestHash)));
            when(artifacts.artifact(ARTIFACT)).thenAnswer(call -> List.of(artifact)); when(artifacts.binding(ARTIFACT)).thenReturn(List.of(binding));
            when(artifacts.members(ACCESS_SET)).thenReturn(List.of(member));
            when(artifacts.manifest(ACCESS_SET)).thenReturn(List.of(new ArtifactRegistryProtocol.ManifestRow(TENANT,null,ACCESS_SET,json.canonical(json.tree(manifest)),manifestHash,
                manifestHash,BigInteger.ONE,"1.0.0",manifests.manifestSha256())));
            when(originals.original(anyMap())).thenReturn(List.of(new SimulationInvocationSources.Original(TENANT,LICENSE,storage.sha256(),storage.objectKey(),storage.objectVersion(),
                new BigInteger(storage.sizeBytes()),storage.mediaType())));
            // Identity authentication is outside this unit test; no read, permission, rights or lineage authority is mocked.
            var subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
            when(subject.user()).thenReturn(new BusinessSessionProtocol.User(REVIEWER,"reviewer","Reviewer",null));
            when(subject.binding().view()).thenReturn(new AuthenticatedIdentityBinding.View("local",REVIEWER,null,null));
            var epochs=new TenantWatermarks(BigInteger.ONE,BigInteger.ONE); var context=new BusinessSessionProtocol.Context(TENANT,"1","1","1");
            reviewer=new EnterpriseAccess.Admission(subject,id(81),context,epochs);
            var proof=mock(SimulationWorkerAccess.Proof.class); when(proof.actorId()).thenReturn(WORKER);
            when(proof.bindingView()).thenReturn(new AuthenticatedIdentityBinding.View("local",WORKER,null,null)); worker=new EnterpriseAccess.Admission(proof,context,epochs);
            witness=new SimulationOpaqueRightsWitness(json,policy,rights,permissions,sources,artifacts,originals,manifests,lineage);
        }
        String hash(com.fasterxml.jackson.databind.JsonNode value) throws Exception { return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(value)); }
        PermissionProtocol.Scope scope(SourceReadProtocol.SourceRow source) { return new PermissionProtocol.Scope(TENANT,null,"SOURCE",source.sourceId()); }
        SourceReadProtocol.ReadRequirement requirement(SourceReadProtocol.SourceRow source,String purpose) {
            return new SourceReadProtocol.ReadRequirement(source.sourceVersion(),source.rightsRevision(),null,null,null,null,SourceReadProtocol.Use.REVIEW,purpose,"SIM","cn-test",null);
        }
        PermissionProtocol.GrantRow grant(String actor,String resource,String purpose) throws Exception {
            var condition=new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of(purpose),new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),List.of(),List.of()));
            return new PermissionProtocol.GrantRow(id(82),actor,null,"SOURCE",resource,"source.read",json.canonicalObject(condition),"ACTIVE",now.minusSeconds(1),null,id(83),"1.0.0",json.manifestSha256(),BigInteger.ONE);
        }
        <T>T in(EnterpriseAccess.Admission admission,Checked<T> work)throws Exception {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try(var scope=TenantScope.open(new TenantScope.Identity(TENANT,admission.actorId(),BigInteger.ONE,BigInteger.ONE,BigInteger.ONE))) {
                scope.verifyTransaction(admission.watermarks());try {return work.call();} finally {scope.finishTransaction();}
            } finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        }
        void check()throws Exception {in(worker,() -> {witness.require(worker,business.verify());return null;});}
    }
}

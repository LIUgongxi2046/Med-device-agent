package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** Human consumer of actual N07 results. Historical bytes are readable only under current authority.
 * A VERIFIED read is not a professional approval; validity and substantive findings remain distinct. */
public final class ProfessionalCheckService {
    private final EnterpriseAccess access;private final JdbcTemplate db;private final DocumentReadService documents;
    private final DocumentReviewAuthority target;private final DocumentReviewRules rules;private final DocumentReviewExecutionService jobs;
    private final ResourceReadAuthority resources;private final SourceUploadTransport transport;private final DocumentUploadPolicy policy;
    private final DependencyLineage lineage;private final VersionResourceAuthority versions;private final ArtifactRegistryMapper artifacts;
    private final DependencyManifestSchema manifests;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();private final SchemaCatalogue schemas;
    public ProfessionalCheckService(DataSource data,EnterpriseAccess access,DocumentReadService documents,DocumentReviewAuthority target,
            DocumentReviewRules rules,DocumentReviewExecutionService jobs,ResourceReadAuthority resources,SourceUploadTransport transport,
            DocumentUploadPolicy policy,DependencyLineage lineage,VersionResourceAuthority versions,ArtifactRegistryMapper artifacts,DependencyManifestSchema manifests)throws Exception{
        db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.documents=documents;this.target=target;this.rules=rules;this.jobs=jobs;
        this.resources=resources;this.transport=transport;this.policy=policy;this.lineage=lineage;this.versions=versions;this.artifacts=artifacts;this.manifests=manifests;schemas=SchemaCatalogue.fromClasspath();
    }
    record Snapshot(Map<String,Object> row,DocumentReadService.FixedRead document,JsonNode receipt,Map<String,InterfaceProtocol.ObjectRef> objects,String findingsRef,String findingsSha,String digest){}
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String check)throws Exception{return verified(subject,context,check).detail();}
    Verified verified(ApplicationSessionIdentity.Verified subject,String context,String check)throws Exception{
        WireValues.uuid(check);PermissionSchema.revision(context);policy.requireConfigured();
        var row=tx(subject,a->{context(a,context);return row(a,check);});
        // Candidate and technical consumers are intentionally not promoted into N07.
        if(!"DOCUMENT".equals(str(row,"target_kind"))||!"N07".equals(json.read(str(row,"result_ref")).path("node_kind").asText()))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var proof=documents.readFixed(subject,context,str(row,"target_id"),str(row,"target_revision"));
        var fixed=tx(subject,a->snapshot(a,context,check,proof));var bytes=new HashMap<String,byte[]>();
        for(var entry:fixed.objects().entrySet()){
            S3ObjectReader.Admission guard=()->tx(subject,a->{same(fixed.digest(),snapshot(a,context,check,proof).digest());return null;});
            byte[] value=transport.readDocument(subject,proof.header().tenantId(),policy.maximumBytes().intValueExact(),guard,(reader,current)->{
                var ref=entry.getValue();long count=Long.parseLong(ref.sizeBytes());if(count<1||count>policy.maximumBytes().longValueExact())throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
                try(var object=reader.readVerified(ref,current)){byte[] result=new byte[(int)count];for(int start=0;start<result.length;){current.requireCurrent();int n=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,result.length-start);byte[] part=object.chunk(start,n);if(part.length!=n)bad();System.arraycopy(part,0,result,start,n);start+=n;}same(ref.sha256(),WireValues.sha256(result));current.requireCurrent();return result;}
            });bytes.put(entry.getKey(),value);
        }
        var artifact=ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,json.read(bytes.get("findings")),fixed.findingsRef(),fixed.findingsSha()).read();
        if(!(artifact.output() instanceof ArtifactProtocol.ReviewOutput output))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var findings=output.content();var coverage=json.read(bytes.get("coverage"));validateCoverage(coverage);
        same(json.hash(artifact.output()),artifact.contentSha256());same(DocumentReviewResultService.accessSet(str(row,"action_id")),artifact.dependencyAccessSetId());same(ArtifactProtocol.ExecutionKind.SYSTEM_JOB,artifact.executionKind());same(proof.header().tenantId(),artifact.tenantId());same(proof.header().projectId(),artifact.projectId());same(str(row,"run_id"),artifact.runId());same(str(row,"step_id"),artifact.stepId());same(str(row,"input_sha256"),artifact.inputManifestSha256());
        same("DOCUMENT",findings.targetKind().name());same(str(row,"target_revision"),findings.targetRevision());same(proof.header().bodySha256(),findings.targetSha256());same(str(row,"rule_manifest_id"),findings.ruleRevision());
        same(str(row,"input_sha256"),text(coverage,"input_sha256"));same(findings.targetRevision(),text(coverage,"target_revision"));same(findings.targetSha256(),text(coverage,"target_sha256"));same(findings.ruleRevision(),text(coverage,"rule_revision"));same(WireValues.sha256(bytes.get("findings")),text(coverage,"findings_sha256"));
        same(json.tree(findings.requiredClaimIds()),coverage.required("required_claim_ids"));same(json.tree(findings.requiredRuleIds()),coverage.required("required_rule_ids"));same(findings.coverageComplete(),coverage.required("coverage_complete").booleanValue());
        boolean blocked=!findings.coverageComplete()||!findings.issueProposals().isEmpty()||findings.claimChecks().stream().anyMatch(c->c.supportCheck()==ArtifactProtocol.SupportCheck.CONTRADICTED||c.supportCheck()==ArtifactProtocol.SupportCheck.INSUFFICIENT)||findings.ruleChecks().stream().anyMatch(r->r.result()==ArtifactProtocol.RuleResult.FAIL||r.result()==ArtifactProtocol.RuleResult.BLOCKED);same(blocked?"WAITING_INPUT":"SUCCEEDED",str(row,"status"));
        checkCoverage(findings);var proposed=new HashSet<String>();for(var proposal:findings.issueProposals())proposed.add(json.hash(proposal));var recorded=new HashSet<String>();fixed.receipt().required("issue_effects").forEach(effect->recorded.add(text(effect,"finding_sha256")));same(proposed,recorded);
        return tx(subject,a->{var current=snapshot(a,context,check,proof);same(fixed.digest(),current.digest());String invalid=validity(a,current,findings);var result=new Verified(subject,context,current,findings,coverage,invalid);return result;});
    }
    final class Verified {
        private final ApplicationSessionIdentity.Verified subject;private final String context;private final Snapshot snapshot;private final ArtifactProtocol.ReviewFindings findings;private final JsonNode coverage;private final String invalid;
        private Verified(ApplicationSessionIdentity.Verified s,String c,Snapshot p,ArtifactProtocol.ReviewFindings f,JsonNode coverage,String invalid){subject=s;context=c;snapshot=p;findings=f;this.coverage=coverage;this.invalid=invalid;}
        ArtifactProtocol.ReviewFindings findings(){return findings;}Snapshot snapshot(){return snapshot;}String invalidation(){return invalid;}
        void requireCurrent(EnterpriseAccess.Admission a)throws Exception{same(snapshot.digest(),ProfessionalCheckService.this.snapshot(a,context,str(snapshot.row(),"check_id"),snapshot.document()).digest());same(invalid,validity(a,snapshot,findings));}
        void requireValid(EnterpriseAccess.Admission a)throws Exception{requireCurrent(a);if(invalid!=null||!findings.coverageComplete())throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
        /** Only the private persisted B08 proof can explain its own evidence transition.
         * All other input, issue, rule and coverage branches are independently re-evaluated. */
        void requireValidForHumanEvidence(EnterpriseAccess.Admission a,HumanEvidenceReviewService.CurrentBatch batch)throws Exception{
            requireCurrent(a);if(batch==null)throw new TenantAccessException(ErrorCode.REVIEW_STALE);
            batch.requirePrecedingCheck(a,this);String remaining=validity(a,snapshot,findings,true);
            if(remaining!=null||!findings.coverageComplete())throw new TenantAccessException(ErrorCode.REVIEW_STALE);
        }
        JsonNode detail()throws Exception {
            var r=snapshot.row();var ref=snapshot.objects().get("findings");String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(ref),0,Long.parseLong(ref.sizeBytes()));
            var full=new CommonProtocol.Coverage(range,range,true,null);var ids=tx(subject,a->{requireCurrent(a);var values=new ArrayList<String>();snapshot.receipt().required("issue_effects").forEach(e->values.add(text(e,"issue_id")));return List.copyOf(values);});
            // Wire Coverage describes byte delivery, not professional coverage. Never manufacture a filtered PASS.
            var detail=new ResourceProtocol.CheckDetail(str(r,"check_id"),ResourceProtocol.TargetKind.DOCUMENT,str(r,"target_id"),str(r,"target_revision"),str(r,"input_sha256"),str(r,"rule_manifest_id"),ResourceProtocol.CheckStatus.valueOf(str(r,"status")),full,new CommonProtocol.ArtifactRef(str(r,"findings_artifact_id"),ref.sha256(),snapshot.findingsRef(),full),ids,str(r,"evidence_watermark"),str(r,"issue_watermark"),invalid);
            return json.tree(TypedSchemaBindings.bind(schemas,ResourceBindings.CHECK_DETAIL,json.tree(detail)).read());
        }
    }
    private Snapshot snapshot(EnterpriseAccess.Admission a,String context,String check,DocumentReadService.FixedRead proof)throws Exception{
        context(a,context);documents.requireCurrent(a,proof);var h=proof.header();var r=row(a,check);same(h.projectId(),str(r,"project_id"));same(h.documentId(),str(r,"target_id"));same(h.documentRevision(),str(r,"target_revision"));
        resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId()),"document.read","DOCUMENT_BODY");
        var job=jobs.byRun(h.tenantId(),str(r,"run_id"));same(job.document(),h.documentId());same(job.documentRevision(),h.documentRevision());same(job.inputHash(),str(r,"input_sha256"));same(job.ruleId(),str(r,"rule_manifest_id"));same(job.scopeId(),str(r,"scope_manifest_id"));same(job.step(),str(r,"step_id"));same(job.action(),str(r,"action_id"));
        var result=one(db.queryForList("SELECT receipt_json,receipt_sha256 FROM document_review_result WHERE tenant_id=? AND project_id=? AND check_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),check,job.run(),job.step(),job.action()));var receipt=json.read(str(result,"receipt_json"));DocumentReviewResultService.validateResult(receipt);for(String k:List.of("issue_watermark","evidence_watermark"))same(str(r,k),receipt.required("output_watermarks").required(k).asText());same(json.hash(receipt),str(result,"receipt_sha256"));same(receipt,json.read(str(r,"result_ref")));same("N07",text(receipt,"node_kind"));same(job.inputHash(),text(receipt,"input_sha256"));same(h.bodySha256(),text(receipt,"body_sha256"));same(json.read(job.baselineJson()),json.read(str(r,"input_watermarks")));same(json.hash(json.read(job.baselineJson())),text(receipt,"baseline_sha256"));same(str(r,"status"),text(receipt,"status"));
        var seenIssues=new HashSet<String>();int transitions=0;for(var effect:receipt.required("issue_effects")){String issueId=text(effect,"issue_id");if(!seenIssues.add(issueId))bad();var issue=one(db.queryForList("SELECT o.finding_sha256,o.effect,o.semantic_sha256,i.semantic_sha256 AS issue_semantic FROM professional_issue_occurrence o JOIN issue i ON i.tenant_id=o.tenant_id AND i.project_id=o.project_id AND i.issue_id=o.issue_id WHERE o.tenant_id=? AND o.project_id=? AND o.issue_id=? AND o.document_revision=? AND o.check_id=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),issueId,h.documentRevision(),check));same(text(effect,"finding_sha256"),str(issue,"finding_sha256"));same(text(effect,"effect"),str(issue,"effect"));same(str(issue,"semantic_sha256"),str(issue,"issue_semantic"));if(!"UNCHANGED".equals(text(effect,"effect")))transitions++;}
        same(receipt.required("input_watermarks").required("evidence_watermark"),receipt.required("output_watermarks").required("evidence_watermark"));same(receipt.required("input_watermarks").required("issue_watermark").bigIntegerValue().add(java.math.BigInteger.valueOf(transitions)),receipt.required("output_watermarks").required("issue_watermark").bigIntegerValue());
        var ruleManifest=json.read(str(r,"rule_manifest"));same(json.hash(ruleManifest),text(receipt,"rules_sha256"));same(ruleManifest,json.read(job.admissionJson()).required("rules_manifest"));
        var objects=new LinkedHashMap<String,InterfaceProtocol.ObjectRef>();for(String kind:List.of("findings","coverage"))objects.put(kind,object(a,proof,r,receipt,kind));
        var audit=one(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND project_id=? AND audit_id=? AND subject_id=? AND result='SUCCESS' AND action='document.professional_review.recorded' LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),text(receipt,"audit_id"),job.actor()));same(receipt,json.read(str(audit,"resource_ref")));same(receipt,json.read(str(one(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND event_type='document.professional_review.recorded' LIMIT 2 FOR UPDATE",h.tenantId(),text(receipt,"event_id"),check)),"payload")));
        var declaration=one(artifacts.binding(str(r,"findings_artifact_id")));
        return new Snapshot(Collections.unmodifiableMap(r),proof,receipt,Map.copyOf(objects),declaration.schemaRef(),declaration.schemaDocumentSha256(),json.hash(Map.of("row",r,"document",proof.digest(),"objects",objects,"findings_schema",declaration)));
    }
    private InterfaceProtocol.ObjectRef object(EnterpriseAccess.Admission a,DocumentReadService.FixedRead proof,Map<String,Object> check,JsonNode result,String kind)throws Exception{
        var h=proof.header();String id=text(result,kind+"_artifact_id");same(id,str(check,kind+"_artifact_id"));
        resources.require(a,new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"ARTIFACT",id),"artifact.read","DOCUMENT_BODY");
        var object=one(db.queryForList("SELECT * FROM document_review_object WHERE tenant_id=? AND project_id=? AND artifact_id=? AND run_id=? AND step_id=? AND action_id=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),id,str(check,"run_id"),str(check,"step_id"),str(check,"action_id")));
        var receipt=json.read(str(object,"receipt_json"));same(json.hash(receipt),str(object,"receipt_sha256"));same(id,text(receipt,"artifact_id"));same(str(check,"input_sha256"),text(receipt,"input_sha256"));
        var intent=one(db.queryForList("SELECT * FROM document_review_object_intent WHERE tenant_id=? AND write_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",h.tenantId(),str(object,"write_id"),id));same(json.hash(json.read(str(intent,"intent_json"))),str(intent,"intent_sha256"));same(str(intent,"intent_sha256"),text(receipt,"intent_sha256"));same(json.read(str(intent,"intent_json")).required("provenance"),receipt.required("provenance"));same(str(check,"input_sha256"),text(receipt.required("provenance"),"input_sha256"));same(text(result,"baseline_sha256"),text(receipt.required("provenance"),"baseline_sha256"));same(text(result,"rules_sha256"),text(receipt.required("provenance"),"rule_manifest_sha256"));
        same(str(intent,"intent_sha256"),str(one(db.queryForList("SELECT intent_sha256 FROM document_review_object_attempt WHERE tenant_id=? AND write_id=? LIMIT 2 FOR UPDATE",h.tenantId(),str(object,"write_id"))),"intent_sha256"));
        var ar=one(artifacts.artifact(id));var binding=one(artifacts.binding(id));same(h.tenantId(),ar.tenantId());same(h.projectId(),ar.projectId());same("REVIEW_"+kind.toUpperCase(Locale.ROOT),ar.artifactKind());same("DOCUMENT",binding.resourceKind());same(h.documentId(),binding.resourceId());same(ar.accessSetId(),binding.accessSetId());
        String schema;
        if(kind.equals("findings")) {
            var publication=schemas.requirePublished(binding.schemaRef(),binding.schemaDocumentSha256());
            if(!Set.of("artifacts","artifacts-v2").contains(publication.name())||publication.definition()!=null)bad();
            schema=schemas.schemaDigest(publication.name());boolean v2="artifacts-v2".equals(publication.name());var provenance=receipt.required("provenance");
            var bodyDeclaration=proof.bodyIntent().artifact();var bodyPublication=schemas.requirePublished(bodyDeclaration.schemaRef(),bodyDeclaration.schemaDocumentSha256());
            same(v2?"document-body-v2":"interfaces",bodyPublication.name());same("DocumentBody",bodyPublication.definition());
            if(v2){same("2.0.0",text(provenance,"evidence_contract"));same(binding.schemaRef(),text(provenance,"findings_schema_ref"));same(binding.schemaDocumentSha256(),text(provenance,"findings_schema_sha256"));same(bodyDeclaration.schemaRef(),text(provenance,"body_schema_ref"));same(bodyDeclaration.schemaDocumentSha256(),text(provenance,"body_schema_sha256"));}
            else if(provenance.has("evidence_contract"))bad();
            ArtifactEvidenceBindings.bind(schemas,ArtifactBindings.ARTIFACT,json.read(str(intent,"canonical_payload")),binding.schemaRef(),binding.schemaDocumentSha256()).read();
        } else {schema=DocumentReviewResultService.coverageManifest();same(DocumentReviewResultService.COVERAGE_REF,binding.schemaRef());}
        same(schema,ar.schemaManifest());same(schema,binding.schemaDocumentSha256());
        same(text(result,kind+"_sha256"),ar.sha256());same(ar.sha256(),ar.contentSha256());same(str(intent,"payload_sha256"),ar.sha256());same(WireValues.sha256(str(intent,"canonical_payload").getBytes(java.nio.charset.StandardCharsets.UTF_8)),ar.sha256());same(json.tree(ar.reference()),receipt.required("object_ref"));StoredObjectReferences.decode(ar.storedObject(),ar.reference());
        var manifestRow=one(artifacts.manifest(ar.accessSetId()));same(h.tenantId(),manifestRow.tenantId());same(h.projectId(),manifestRow.projectId());same(binding.dependencyManifestSha256(),manifestRow.manifestSha256());var manifest=manifests.verify(manifestRow,artifacts.members(ar.accessSetId()));
        var graph=lineage.require(id,manifest.members(),policy.limits());same(ar.sha256(),graph.versions().get(id).contentSha256());
        // Authorize every reachable original dependency under today's rights. Output schemas are private local contracts.
        var authorized=new HashSet<String>();authorized.add(id);for(var member:manifest.members()){
            if(member.upstreamVersionId().equals(id))bad();if(member.rightsRevision()!=null){boolean found=proof.fixedSources().values().stream().anyMatch(s->member.rightsRevision().equals(s.rights().rightsRevision())&&graph.reachableFrom(member.upstreamVersionId()).contains(s.source().sourceVersion()));if(!found)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
            var sub=lineage.inspect(member.upstreamVersionId(),policy.limits());var req=new HashMap<String,ArtifactRegistryProtocol.ReadRequirement>();for(var version:sub.versions().values())if("ARTIFACT".equals(version.entityKind())){
                if(version.id().equals(h.bodyArtifactId())){req.put(version.id(),proof.bodyIntent().artifact());continue;}var b=one(artifacts.binding(version.id()));var profiles=policy.artifactProfiles().stream().filter(p->p.resourceKind().equals(b.resourceKind())&&p.schemaRef().equals(b.schemaRef())).toList();var p=one(profiles);req.put(version.id(),new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(h.tenantId(),b.projectId(),b.resourceKind(),b.resourceId()),p.action(),"DOCUMENT_BODY",p.schemaRef(),p.schemaDocumentSha256(),p.artifactSchemaManifest(),policy.limits()));
            }
            var verified=versions.require(a,sub,"DOCUMENT_BODY",proof.bodyIntent().context(),req);for(var v:verified.values())if(v.owner().sourceVersion()!=null&&!proof.fixedSources().containsKey(v.owner().sourceVersion()))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);authorized.addAll(verified.keySet());
        }
        if(!authorized.containsAll(graph.versions().keySet()))bad();return ar.reference();
    }
    private String validity(EnterpriseAccess.Admission a,Snapshot s,ArtifactProtocol.ReviewFindings findings)throws Exception{return validity(a,s,findings,false);}
    private String validity(EnterpriseAccess.Admission a,Snapshot s,ArtifactProtocol.ReviewFindings findings,boolean recordedEvidenceTransition)throws Exception{
        var r=s.row();var h=s.document().header();if(r.get("invalidation_reason")!=null)return str(r,"invalidation_reason");if(!h.currentDocumentRevision().equals(h.documentRevision()))return "TARGET_REVISION_CHANGED";
        var marks=s.document().watermarks();if(!recordedEvidenceTransition&&!marks.evidenceWatermark().toString().equals(str(r,"evidence_watermark")))return "EVIDENCE_WATERMARK_CHANGED";if(!marks.issueWatermark().toString().equals(str(r,"issue_watermark")))return "ISSUE_WATERMARK_CHANGED";
        if(!"ACTIVE".equals(h.projectStatus()))return "PROJECT_NOT_ACTIVE";
        for(var rule:json.read(str(r,"rule_manifest")).required("rules")){var actual=one(db.queryForList("SELECT status,content_sha256 FROM rule_version WHERE tenant_id=? AND rule_version=? LIMIT 2 FOR UPDATE",h.tenantId(),text(rule,"rule_id")));if(!"APPROVED".equals(str(actual,"status"))||!text(rule,"sha256").equals(str(actual,"content_sha256")))return "RULE_MANIFEST_CHANGED";}
        var current=target.requireForRead(a,a.context().context_revision(),s.document());var old=(ObjectNode)json.read(str(r,"input_watermarks"));var now=(ObjectNode)current.baseline();old.remove("watermarks");now.remove("watermarks");if(!old.equals(now))return "INPUT_BASELINE_CHANGED";
        var currentRules=rules.require(a,current).manifest();var previousRules=json.read(str(r,"rule_manifest"));var expectedIds=new HashSet<String>();currentRules.required("rules").forEach(rule->expectedIds.add(text(rule,"rule_id")));if(!expectedIds.equals(new HashSet<>(findings.requiredRuleIds())))return "RULE_COVERAGE_CHANGED";
        var currentContent=(ObjectNode)currentRules.deepCopy();var previousContent=(ObjectNode)previousRules.deepCopy();currentContent.remove("authorizations");previousContent.remove("authorizations");if(!currentContent.equals(previousContent))return "RULE_MANIFEST_CHANGED";
        if(!new HashSet<>(findings.requiredClaimIds()).equals(current.claims().keySet()))return "CLAIM_COVERAGE_CHANGED";
        if(!findings.coverageComplete())return "PROFESSIONAL_COVERAGE_INCOMPLETE";return null;
    }
    static void checkCoverage(ArtifactProtocol.ReviewFindings f){var claims=new HashSet<String>();for(var c:f.claimChecks())if(!claims.add(c.claimId()))bad();var rules=new HashSet<String>();for(var r:f.ruleChecks())if(!rules.add(r.ruleId()))bad();if(f.coverageComplete()&&(!claims.equals(new HashSet<>(f.requiredClaimIds()))||!rules.equals(new HashSet<>(f.requiredRuleIds()))))bad();}
    private void validateCoverage(JsonNode n)throws Exception{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/document-review-coverage.schema.json"))){var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(json.read(in.readAllBytes()));if(!schema.validate(n).isEmpty())bad();}}
    private Map<String,Object> row(EnterpriseAccess.Admission a,String id){return one(db.queryForList("SELECT * FROM check_run WHERE tenant_id=? AND check_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),id));}
    private <T>T tx(ApplicationSessionIdentity.Verified s,Work<T> work){return access.execute(s,a->ThrowingSupplier.of(()->work.run(a)).get());}private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
    static void context(EnterpriseAccess.Admission a,String c){if(!a.context().context_revision().equals(c))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}static String str(Map<String,Object> r,String k){return Objects.requireNonNull(r.get(k)).toString();}static String text(JsonNode n,String k){if(!n.required(k).isTextual())bad();return n.required(k).textValue();}static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

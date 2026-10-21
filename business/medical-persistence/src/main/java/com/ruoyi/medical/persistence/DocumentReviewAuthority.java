package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.DocumentReadMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** N07 actual saved-document authority. A candidate receipt or technical check is not a body proof. */
@Component
public final class DocumentReviewAuthority {
    private final DocumentReadService documents;
    private final DocumentReadMapper rows;
    private final ResourceReadAuthority resources;
    private final org.springframework.jdbc.core.JdbcTemplate db;private final DependencyLineage lineage;private final DocumentUploadPolicy policy;
    private final TaskSourceVerifierSchema json = new TaskSourceVerifierSchema();
    public DocumentReviewAuthority(DocumentReadService documents,DocumentReadMapper rows,ResourceReadAuthority resources,@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") javax.sql.DataSource data,DependencyLineage lineage,DocumentUploadPolicy policy){this.documents=documents;this.rows=rows;this.resources=resources;this.db=new org.springframework.jdbc.core.JdbcTemplate(data);db.setQueryTimeout(5);this.lineage=lineage;this.policy=policy;}
    DocumentReadService.FixedRead read(ApplicationSessionIdentity.Verified subject,String context,String document,String revision)throws Exception {
        return documents.readFixed(subject,context,document,revision);
    }
    DocumentReadService.FixedRead read(EvidenceReadAccess reads,String context,String document,String revision)throws Exception {
        return documents.readFixed(reads,context,document,revision);
    }
    Verified require(EnterpriseAccess.Admission admission,String context,DocumentReadService.FixedRead proof)throws Exception {
        documents.requireCurrent(admission,proof);
        return new Verified(context,proof,baseline(admission,context,proof,true),true);
    }
    /** Read-only current assessment consumer; never grants a new check execution. */
    Verified requireForRead(EnterpriseAccess.Admission admission,String context,DocumentReadService.FixedRead proof)throws Exception {
        documents.requireCurrent(admission,proof);return new Verified(context,proof,baseline(admission,context,proof,false),false);
    }
    private JsonNode baseline(EnterpriseAccess.Admission a,String context,DocumentReadService.FixedRead proof,boolean checking)throws Exception {
        if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
        var h=proof.header();var scope=new PermissionProtocol.Scope(h.tenantId(),h.projectId(),"DOCUMENT",h.documentId());
        resources.require(a,scope,"document.read","DOCUMENT_BODY");if(checking)resources.require(a,scope,"document.check","DOCUMENT_BODY");
        var metadata=documents.authorizeMetadata(a,context,h.documentId(),h.documentRevision());var actual=metadata.header();
        if(!h.tenantId().equals(a.context().tenant_id())||!actual.documentRevision().equals(actual.currentDocumentRevision())||!"ACTIVE".equals(actual.projectStatus()))stale();
        if(!h.bodyArtifactId().equals(actual.bodyArtifactId())||!h.bodySha256().equals(actual.bodySha256())||!h.objectVersion().equals(actual.objectVersion())||!h.accessSetId().equals(actual.accessSetId())||!h.planRevision().equals(actual.planRevision())||!h.templateVersion().equals(actual.templateVersion())||!h.taskRevision().equals(actual.taskRevision()))stale();
        var marks=rows.watermarks(Map.of("userId",a.actorId(),"projectId",h.projectId(),"documentRevision",h.documentRevision()));if(marks.size()!=1)stale();
        var n=json.tree(Map.of("schema_version","1.0.0","node_kind","N07","target_kind","DOCUMENT","tenant_id",h.tenantId(),"project_id",h.projectId(),"document_id",h.documentId(),"document_revision",h.documentRevision(),"body_artifact_id",h.bodyArtifactId(),"body_sha256",h.bodySha256()));
        var o=(com.fasterxml.jackson.databind.node.ObjectNode)n;
        o.put("object_version",h.objectVersion()).put("body_schema_revision",h.bodySchemaRevision()).put("plan_revision",h.planRevision()).put("template_version",h.templateVersion()).put("task_id",h.taskId()).put("task_revision",h.taskRevision()).put("snapshot_id",h.snapshotId()).put("dependency_access_set_id",h.accessSetId()).put("auth_epoch",a.context().auth_epoch()).put("governance_epoch",a.context().governance_epoch());
        o.set("watermarks",json.tree(marks.get(0)));o.set("dependencies",json.tree(proof.dependencies()));
        var nodes=new ArrayList<Map<String,String>>();for(var node:json.read(proof.bodyBytes()).required("nodes"))nodes.add(Map.of("node_id",node.required("node_id").asText(),"sha256",json.hash(node)));o.set("node_manifest",json.tree(nodes));
        o.set("field_ids",json.tree(proof.body().fields().stream().map(InterfaceProtocol.FieldValue::fieldId).sorted().toList()));
        o.set("evidence_refs",json.tree(evidence(proof.body())));o.set("writer_session_ids",json.tree(writerSessions(h)));return n;
    }
    /** Cannot be constructed by a model or adapter. Every use rechecks current permissions and watermarks. */
    public final class Verified {
        private final String context;private final DocumentReadService.FixedRead proof;private final JsonNode baseline;private final boolean checking;
        private Verified(String context,DocumentReadService.FixedRead proof,JsonNode baseline,boolean checking){this.context=context;this.proof=proof;this.baseline=baseline.deepCopy();this.checking=checking;}
        public void requireCurrent(EnterpriseAccess.Admission admission)throws Exception {if(!baseline.equals(DocumentReviewAuthority.this.baseline(admission,context,proof,checking)))stale();}
        public JsonNode baseline(){return baseline.deepCopy();}
        public String baselineSha256()throws Exception{return json.hash(baseline);}
        public DocumentReadProtocol.Header header(){return proof.header();}
        ContentReadProtocol.ReadIntent bodyIntent(){return proof.bodyIntent();}
        void requireEvidenceContract(TaskReviewEvidenceContract contract)throws Exception {
            var catalogue=SchemaCatalogue.fromClasspath();var declared=proof.bodyIntent().artifact();
            var published=catalogue.requirePublished(declared.schemaRef(),declared.schemaDocumentSha256());
            if(!(contract.v2()?"document-body-v2":"interfaces").equals(published.name()) || !"DocumentBody".equals(published.definition()))
                throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            DocumentEvidenceBindings.bind(catalogue,InterfaceBindings.DOCUMENT_BODY,body(),declared.schemaRef(),declared.schemaDocumentSha256()).read();
        }
        String bodySchemaRef(){return proof.bodyIntent().artifact().schemaRef();}
        String bodySchemaSha(){return proof.bodyIntent().artifact().schemaDocumentSha256();}
        public InterfaceProtocol.ObjectRef objectReference(){return proof.bodyObject();}
        public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return proof.dependencies().members();}
        Map<String,SourceReadProtocol.VerifiedSource> fixedSources(){return proof.fixedSources();}
        public JsonNode body(){try{return json.read(proof.bodyBytes());}catch(Exception e){throw new IllegalStateException(e);}}
        public byte[] bodyBytes(){return proof.bodyBytes();}
        public Set<DatasetProtocol.EvidenceRef> evidenceRefs(){return evidence(proof.body());}
        public Set<String> writerSessionIds(){var ids=new LinkedHashSet<String>();baseline.required("writer_session_ids").forEach(n->ids.add(n.asText()));return Collections.unmodifiableSet(ids);}
        /** Host-derived full-content claims. Unreferenced content stays uncovered, never becomes TRANSITION. */
        public Map<String,ArtifactProtocol.ClaimRecord> claims()throws Exception {
            var claims=new LinkedHashMap<String,ArtifactProtocol.ClaimRecord>();var body=body();
            for(var node:body.required("nodes")){
                String id=node.required("node_id").asText(),kind=node.required("content").required("kind").asText();
                if(!Set.of("TEXT","TABLE","IMAGE","FORMULA","ATTACHMENT").contains(kind))continue;
                ArtifactProtocol.Anchor anchor;
                if("TEXT".equals(kind)){String text=node.required("content").required("text").asText();if(text.isBlank())continue;anchor=new ArtifactProtocol.TextAnchor("TEXT",id,0,text.codePointCount(0,text.length()),WireValues.sha256(text.getBytes(StandardCharsets.UTF_8)));}
                else anchor=new ArtifactProtocol.StructuredAnchor("STRUCTURED",id,null,null,json.hash(node));
                add(claims,id,anchor,links(id,null));
            }
            for(var field:body.required("fields")){String id=field.required("field_id").asText(),node=field.required("node_id").asText();var selected=new HashSet<String>();field.required("evidence_link_ids").forEach(e->selected.add(e.asText()));add(claims,id,new ArtifactProtocol.StructuredAnchor("STRUCTURED",node,null,id,json.hash(field)),links(node,selected));}
            return Collections.unmodifiableMap(claims);
        }
        private List<ArtifactProtocol.EvidenceLink> links(String node,Set<String> selected){return proof.body().evidenceLinks().stream().filter(e->e.nodeIds().contains(node)&&(selected==null||selected.contains(e.evidenceLinkId()))).map(e->new ArtifactProtocol.EvidenceLink(e.evidence(),switch(e.relation()){case SUPPORT->ArtifactProtocol.Relation.SUPPORT;case CONFLICT->ArtifactProtocol.Relation.CONFLICT;case BACKGROUND->ArtifactProtocol.Relation.BACKGROUND;})).toList();}
        private void add(Map<String,ArtifactProtocol.ClaimRecord> out,String key,ArtifactProtocol.Anchor anchor,List<ArtifactProtocol.EvidenceLink> refs){if(refs.isEmpty())return;String id=UUID.nameUUIDFromBytes(("N07/"+header().documentRevision()+"/"+key).getBytes(StandardCharsets.UTF_8)).toString();out.put(id,new ArtifactProtocol.ClaimRecord(id,anchor,ArtifactProtocol.ClaimKind.PRODUCT_FACT,refs));}
    }
    private Set<String> writerSessions(DocumentReadProtocol.Header h)throws Exception {
        var graph=lineage.inspect(h.documentRevision(),policy.limits());var sessions=new TreeSet<String>();
        for(var version:graph.versions().values())if("ARTIFACT".equals(version.entityKind())){
            var artifacts=db.queryForList("SELECT artifact_kind FROM artifact WHERE tenant_id=? AND artifact_id=? LIMIT 2 FOR UPDATE",h.tenantId(),version.id());
            if(artifacts.size()!=1)stale();String kind=artifacts.get(0).get("artifact_kind").toString();
            if(!Set.of("SECTION_BODY","SECTION_CANDIDATE").contains(kind))continue;
            var receipts=db.queryForList("SELECT receipt_json,receipt_sha256,run_id,step_id FROM task_section_writer_object WHERE tenant_id=? AND project_id=? AND artifact_id=? AND state='REGISTERED' LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),version.id());if(receipts.size()!=1)stale();var row=receipts.get(0);var receipt=json.read(row.get("receipt_json").toString());if(!json.hash(receipt).equals(row.get("receipt_sha256")))stale();
            var provenance=receipt.required("provenance");if(!"N04".equals(provenance.required("node_kind").asText())||!version.contentSha256().equals(provenance.required("payload_sha256").asText()))stale();String session=provenance.required("session_id").asText();WireValues.uuid(session);
            if(db.queryForList("SELECT session_id FROM professional_runtime_session WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND session_id=? LIMIT 2 FOR UPDATE",h.tenantId(),h.projectId(),row.get("run_id"),row.get("step_id"),session).size()!=1)stale();sessions.add(session);
        }
        if(sessions.isEmpty()){
            // Empty author-session set is valid only for a genuine completed human save, not missing generated provenance.
            var operations=db.queryForList("SELECT operation_id FROM operation_record WHERE tenant_id=? AND target_id=? AND operation_name='DC-02' AND state='COMPLETED' AND subject_id=created_by AND JSON_UNQUOTE(JSON_EXTRACT(response_ref,'$.data.document_revision'))=? LIMIT 2 FOR UPDATE",h.tenantId(),h.documentId(),h.documentRevision());if(operations.size()!=1)stale();
        }
        return Collections.unmodifiableSet(sessions);
    }
    private static Set<DatasetProtocol.EvidenceRef> evidence(InterfaceProtocol.DocumentBody body){var out=new LinkedHashSet<DatasetProtocol.EvidenceRef>();body.evidenceLinks().forEach(e->out.add(e.evidence()));return Collections.unmodifiableSet(out);}
    private static void stale(){throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
}

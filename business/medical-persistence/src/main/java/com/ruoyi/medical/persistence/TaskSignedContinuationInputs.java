package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;
/** Read-only signed predecessor. Actual bytes are independently read at admission and
 * again by the existing N04 physical context reader; no signature or body is rewritten. */
@Service
public final class TaskSignedContinuationInputs {
    private final EnterpriseAccess access;private final DocumentReadService documents;private final PermissionSchema json;private final JdbcTemplate db;
    public TaskSignedContinuationInputs(EnterpriseAccess access,DocumentReadService documents,PermissionSchema json,@Qualifier("masterDataSource")DataSource data){this.access=access;this.documents=documents;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
    Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,TaskService.VerifiedTask task)throws Exception {
        String revision=task.input().basedOnDocumentRevision();if(revision==null)return null;
        String id=access.execute(subject,a->ThrowingSupplier.of(()->{same(context,a.context().context_revision());return text(one(db.queryForList("SELECT document_id FROM document_revision_meta WHERE tenant_id=? AND project_id=? AND task_id=? AND document_revision=? LIMIT 2",a.context().tenant_id(),task.head().projectId(),task.head().taskId(),revision)),"document_id");}).get());
        return new Prepared(task.head().taskId(),revision,documents.readFixed(subject,context,id,revision));
    }
    static final class Prepared {private final String task,revision;private final DocumentReadService.FixedRead body;private Prepared(String task,String revision,DocumentReadService.FixedRead body){this.task=task;this.revision=revision;this.body=body;}}
    Verified require(EnterpriseAccess.Admission a,TaskService.VerifiedTask task,Prepared prepared,JsonNode principal)throws Exception {
        if(prepared==null){if(task.input().basedOnDocumentRevision()!=null)bad();return null;}
        same(task.head().taskId(),prepared.task);same(task.input().basedOnDocumentRevision(),prepared.revision);documents.requireCurrent(a,prepared.body);
        var h=prepared.body.header();var signed=signature(db,a.context().tenant_id(),h.projectId(),h.documentId(),h.documentRevision());
        same(h.bodySha256(),text(signed,"body_sha256"));if(!Arrays.equals(prepared.body.bodyBytes(),(byte[])signed.get("body_bytes")))bad();
        boolean allowed=false;for(var p:principal.required("permissions"))if("document.read".equals(p.required("action").asText())&&"DOCUMENT_BODY".equals(p.required("purpose").asText())&&"DOCUMENT".equals(p.required("scope").required("resource_kind").asText())&&h.documentId().equals(p.required("scope").required("resource_id").asText()))allowed=true;
        if(!allowed)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var witness=json.object().put("schema_version","1.0.0").put("kind","SIGNED_DOCUMENT_CONTEXT").put("document_id",h.documentId()).put("document_revision",h.documentRevision()).put("signature_id",text(signed,"signature_id")).put("signature_sha256",text(signed,"manifest_sha256")).put("body_artifact_id",h.bodyArtifactId()).put("body_sha256",h.bodySha256()).put("document_sha256",h.contentSha256());
        witness.set("body_object",json.tree(prepared.body.bodyObject()));
        var schema=SchemaCatalogue.fromClasspath();var prior=json.object().put("kind","DEPENDENT_SECTION");prior.set("version",json.tree(new PermissionProtocol.Basis(h.documentRevision(),h.contentSha256())));
        var body=json.tree(prepared.body.body());prior.set("input",json.object().put("schema_ref",prepared.body.bodyIntent().artifact().schemaRef()).put("schema_sha256",prepared.body.bodyIntent().artifact().schemaDocumentSha256()).put("content_sha256",h.bodySha256()).put("canonical_json",json.canonical(body)));
        return new Verified(this,a,task,prepared,principal,witness,prior,List.of(new PermissionProtocol.Basis(h.documentRevision(),h.contentSha256()),new PermissionProtocol.Basis(h.bodyArtifactId(),h.bodySha256()),new PermissionProtocol.Basis(text(signed,"signature_id"),text(signed,"manifest_sha256"))));
    }
    static final class Verified {
        private final TaskSignedContinuationInputs owner;private final EnterpriseAccess.Admission admission;private final TaskService.VerifiedTask task;private final Prepared prepared;private final JsonNode principal,witness,prior;private final List<PermissionProtocol.Basis> dependencies;
        private Verified(TaskSignedContinuationInputs o,EnterpriseAccess.Admission a,TaskService.VerifiedTask t,Prepared p,JsonNode principal,JsonNode witness,JsonNode prior,List<PermissionProtocol.Basis> deps){owner=o;admission=a;task=t;prepared=p;this.principal=principal.deepCopy();this.witness=witness.deepCopy();this.prior=prior.deepCopy();dependencies=List.copyOf(deps);}
        void require(EnterpriseAccess.Admission a,TaskService.VerifiedTask task)throws Exception{if(admission!=a||!this.task.head().equals(task.head())||!this.task.input().equals(task.input()))bad();var fresh=owner.require(a,task,prepared,principal);same(witness,fresh.witness);same(prior,fresh.prior);}
        JsonNode witness(){return witness.deepCopy();}JsonNode prior(){return prior.deepCopy();}List<PermissionProtocol.Basis> dependencies(){return dependencies;}
    }
    static JsonNode replayBody(JsonNode replay)throws Exception {
        String revision=replay.required("task_input").path("based_on_document_revision").textValue();if(revision==null)return null;
        var witness=new JsonMapper().readTree(replay.required("authorizations").required("canonical_witnesses").asText()).required("additional_authorizations").required("signed_document_context");
        same("SIGNED_DOCUMENT_CONTEXT",witness.required("kind").asText());same(revision,witness.required("document_revision").asText());
        var matches=new ArrayList<JsonNode>();for(var prior:replay.required("prior_inputs"))if(revision.equals(prior.required("version").required("version_id").asText()))matches.add(prior);var prior=one(matches);same("DEPENDENT_SECTION",prior.required("kind").asText());same(witness.required("document_sha256"),prior.required("version").required("content_sha256"));
        var serialized=prior.required("input");var schema=SchemaCatalogue.fromClasspath();var publication=schema.requirePublished(serialized.required("schema_ref").asText(),serialized.required("schema_sha256").asText());
        byte[] bytes=serialized.required("canonical_json").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8);same(WireValues.sha256(bytes),serialized.required("content_sha256").asText());same(witness.required("body_sha256"),serialized.required("content_sha256"));var body=new JsonMapper().readTree(bytes);DocumentEvidenceBindings.bind(schema,InterfaceBindings.DOCUMENT_BODY,body,publication.schemaRef(),publication.schemaSha256());if(!Arrays.equals(bytes,ContentReadAuthorization.canonicalBytes(body)))bad();return body;
    }
    static JsonNode requireRuntime(JdbcTemplate db,ResourceReadAuthority resources,TaskNodeProductionAuthority.Verified production)throws Exception {
        production.requireUnchanged();var replay=production.replay().content();var body=replayBody(replay);if(body==null)return null;
        var witness=new JsonMapper().readTree(replay.required("authorizations").required("canonical_witnesses").asText()).required("additional_authorizations").required("signed_document_context");
        resources.require(production.admission(),new PermissionProtocol.Scope(production.tenantId(),production.projectId(),"DOCUMENT",witness.required("document_id").asText()),"document.read","DOCUMENT_BODY");
        var signature=signature(db,production.tenantId(),production.projectId(),witness.required("document_id").asText(),witness.required("document_revision").asText());same(text(signature,"signature_id"),witness.required("signature_id").asText());same(text(signature,"manifest_sha256"),witness.required("signature_sha256").asText());same(text(signature,"body_sha256"),witness.required("body_sha256").asText());if(!Arrays.equals((byte[])signature.get("body_bytes"),ContentReadAuthorization.canonicalBytes(body)))bad();return witness;
    }
    private static Map<String,Object> signature(JdbcTemplate db,String tenant,String project,String document,String revision)throws Exception {
        var row=one(db.queryForList("SELECT s.signature_id,s.manifest_sha256,c.body_bytes,c.body_sha256,c.manifest_json,c.manifest_sha256 AS saved_manifest_sha256 FROM signature_record s JOIN document_signed_content c ON c.tenant_id=s.tenant_id AND c.project_id=s.project_id AND c.signature_id=s.signature_id AND c.document_revision=s.document_revision WHERE s.tenant_id=? AND s.project_id=? AND s.document_id=? AND s.document_revision=? LIMIT 2 FOR UPDATE",tenant,project,document,revision));
        same(WireValues.sha256((byte[])row.get("body_bytes")),text(row,"body_sha256"));same(text(row,"manifest_sha256"),text(row,"saved_manifest_sha256"));same(WireValues.sha256(text(row,"manifest_json").getBytes(java.nio.charset.StandardCharsets.UTF_8)),text(row,"manifest_sha256"));return row;
    }
    private static String text(Map<String,Object> row,String key){var v=row.get(key);if(v==null)bad();return v.toString();}private static <T>T one(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

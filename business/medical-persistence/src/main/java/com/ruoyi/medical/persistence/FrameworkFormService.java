package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Immutable authoring and independent approval. Approval is the publication record, never configuration. */
@Component
public final class FrameworkFormService {
    private final EnterpriseAccess access; private final ResourceReadAuthority authority; private final FrameworkBindingService bindings;
    private final PermissionMapper permissions; private final PermissionSchema json; private final FrameworkFormSchema schema;
    private final Environment environment; private final JdbcTemplate db;
    public FrameworkFormService(EnterpriseAccess access,ResourceReadAuthority authority,FrameworkBindingService bindings,
            PermissionMapper permissions,PermissionSchema json,FrameworkFormSchema schema,Environment environment,@Qualifier("masterDataSource") DataSource data){
        this.access=access;this.authority=authority;this.bindings=bindings;this.permissions=permissions;this.json=json;this.schema=schema;this.environment=environment;db=new JdbcTemplate(data);db.setQueryTimeout(5);
    }
    public JsonNode list(ApplicationSessionIdentity.Verified subject,String context){return access.execute(subject,a->ThrowingSupplier.of(()->{
        gate(a,context,"list","GET","/api/tool/build/schemas",false);grant(a,"management.discover");
        var result=json.object();result.set("items",json.tree(db.queryForList("SELECT f.form_id,f.content_sha256,f.created_by,f.created_at,CASE WHEN p.approval_id IS NULL THEN 'DRAFT' ELSE 'PUBLISHED' END AS status FROM framework_form_schema f LEFT JOIN framework_form_approval p ON p.tenant_id=f.tenant_id AND p.form_id=f.form_id WHERE f.tenant_id=? ORDER BY f.created_at DESC,f.form_id LIMIT 100",a.context().tenant_id())));result.put("limit",100);result.put("schema_sha256",schema.digest);return envelope(result);
    }).get());}
    public JsonNode draft(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
        var request=schema.read(bytes);schema.validate("Draft",request);schema.form(request.required("form"));
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var binding=gate(a,context,"draft","POST","/api/tool/build/schemas",true);grant(a,"management.configure");var prior=replay(a,key,"DRAFT",request);if(prior!=null)return prior;
            String id=UUID.randomUUID().toString(),hash=schema.hash(request.required("form"));register(a,id,"FRAMEWORK_FORM",hash);
            one(db.update("INSERT INTO framework_form_schema(tenant_id,form_id,form_json,content_sha256,reason,binding_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')",a.context().tenant_id(),id,json.canonical(request.required("form")),hash,text(request,"reason"),binding.manifestSha256(),schema.digest,a.actorId()));
            return finish(a,key,"DRAFT",request,view(stored(a,id),"DRAFT"));
        }).get());
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id,boolean published){uuid(id);return access.execute(subject,a->ThrowingSupplier.of(()->{
        gate(a,context,published?"consume":"read","GET","/api/tool/build/schemas/"+id+(published?"/published":""),false);grant(a,"management.discover");var row=stored(a,id);
        if(published)verifyApproval(a,row);
        return envelope(view(row,published?"PUBLISHED":"DRAFT_VIEW"));
    }).get());}
    public JsonNode approve(ApplicationSessionIdentity.Verified subject,String context,String id,String key,byte[] bytes)throws Exception{
        uuid(id);var request=schema.read(bytes);schema.validate("Approve",request);var effective=json.object().put("form_id",id);effective.set("approval",request);
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            var binding=gate(a,context,"approve","POST","/api/tool/build/schemas/"+id+"/approvals",true);var witness=grant(a,"management.approve");var prior=replay(a,key,"APPROVE",effective);if(prior!=null)return prior;
            var row=stored(a,id);if(a.actorId().equals(str(row,"created_by")))fail(ErrorCode.ACCESS_DENIED);same(text(request,"content_sha256"),str(row,"content_sha256"));
            artifact(a,text(request,"schema_artifact_id"),str(row,"content_sha256"));
            if(!db.queryForList("SELECT approval_id FROM framework_form_approval WHERE tenant_id=? AND form_id=?",a.context().tenant_id(),id).isEmpty())fail(ErrorCode.REVISION_CONFLICT);
            String approval=UUID.randomUUID().toString();var proof=json.object().put("approval_id",approval).put("form_id",id).put("content_sha256",str(row,"content_sha256")).put("schema_artifact_id",text(request,"schema_artifact_id")).put("reason",text(request,"reason")).put("created_by",a.actorId()).put("binding_sha256",binding.manifestSha256()).put("schema_sha256",schema.digest);
            proof.set("authority",AgentPublicationWitness.tree(witness));String hash=schema.hash(proof);register(a,approval,"FRAMEWORK_FORM_APPROVAL",hash);
            one(db.update("INSERT INTO framework_form_approval(tenant_id,approval_id,form_id,schema_artifact_id,approval_json,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')",a.context().tenant_id(),approval,id,text(request,"schema_artifact_id"),json.canonical(proof),hash,schema.digest,a.actorId()));
            verifyApproval(a,row);return finish(a,key,"APPROVE",effective,view(row,"PUBLISHED").put("approval_id",approval));
        }).get());
    }
    private FrameworkBindingAuthority.Binding gate(EnterpriseAccess.Admission a,String context,String operation,String method,String path,boolean design)throws Exception{
        if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);
        if(!"PLATFORM_ADMIN".equals(a.subject().user().platform_role()))fail(ErrorCode.ACCESS_DENIED);
        if(design&&!Set.of("DESIGN","DEVELOPMENT").contains(environment.getProperty("medical.framework.environment","")))fail(ErrorCode.ACCESS_DENIED);
        return bindings.requireWithin(a,context,"framework.form."+operation,method,path);
    }
    private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,String action)throws Exception{return authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"ENTERPRISE",a.context().tenant_id()),action,"MANAGEMENT_BINDING");}
    private Map<String,Object> stored(EnterpriseAccess.Admission a,String id)throws Exception{
        var row=single(db.queryForList("SELECT * FROM framework_form_schema WHERE tenant_id=? AND form_id=? FOR UPDATE",a.context().tenant_id(),id));var form=schema.read(str(row,"form_json"));schema.form(form);same(schema.hash(form),str(row,"content_sha256"));metadata(row);registry(a,id,"FRAMEWORK_FORM",str(row,"content_sha256"));return row;
    }
    private com.fasterxml.jackson.databind.node.ObjectNode view(Map<String,Object> row,String status)throws Exception{return json.object().put("form_id",str(row,"form_id")).put("content_sha256",str(row,"content_sha256")).put("schema_sha256",schema.digest).put("created_by",str(row,"created_by")).put("status",status).set("form",schema.read(str(row,"form_json")));}
    private void verifyApproval(EnterpriseAccess.Admission a,Map<String,Object> row)throws Exception{
        var p=single(db.queryForList("SELECT * FROM framework_form_approval WHERE tenant_id=? AND form_id=?",a.context().tenant_id(),str(row,"form_id")));metadata(p);var proof=schema.read(str(p,"approval_json"));same(schema.hash(proof),str(p,"content_sha256"));same(str(p,"approval_id"),text(proof,"approval_id"));same(str(row,"form_id"),text(proof,"form_id"));same(str(row,"content_sha256"),text(proof,"content_sha256"));same(str(p,"created_by"),text(proof,"created_by"));same(str(p,"schema_artifact_id"),text(proof,"schema_artifact_id"));same(schema.digest,text(proof,"schema_sha256"));if(str(row,"created_by").equals(str(p,"created_by")))fail(ErrorCode.ACCESS_DENIED);registry(a,str(p,"approval_id"),"FRAMEWORK_FORM_APPROVAL",str(p,"content_sha256"));artifact(a,str(p,"schema_artifact_id"),str(row,"content_sha256"));
    }
    private void artifact(EnterpriseAccess.Admission a,String id,String hash)throws Exception{
        authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"ARTIFACT",id),"artifact.read","MANAGEMENT_BINDING");
        var row=single(db.queryForList("SELECT a.sha256,a.content_sha256,a.media_type,v.content_sha256 AS registry_sha256 FROM artifact a JOIN version_registry v ON v.tenant_id=a.tenant_id AND v.version_id=a.artifact_id WHERE a.tenant_id=? AND a.artifact_id=? AND a.project_id IS NULL AND v.owner_project_id IS NULL AND v.entity_kind='ARTIFACT'",a.context().tenant_id(),id));same(hash,str(row,"sha256"));same(hash,str(row,"content_sha256"));same(hash,str(row,"registry_sha256"));same("application/json",str(row,"media_type"));
    }
    private void metadata(Map<String,Object> row){same("1.0.0",str(row,"schema_version"));same(schema.digest,str(row,"schema_manifest_sha256"));same("INTERNAL",str(row,"classification"));}
    private void register(EnterpriseAccess.Admission a,String id,String kind,String hash){one(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,NULL,?,?,?)",a.context().tenant_id(),id,kind,hash));}
    private void registry(EnterpriseAccess.Admission a,String id,String kind,String hash){same(hash,str(single(db.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? AND owner_project_id IS NULL AND entity_kind=?",a.context().tenant_id(),id,kind)),"content_sha256"));}
    private JsonNode replay(EnterpriseAccess.Admission a,String key,String action,JsonNode request)throws Exception{json.headers(a.context().context_revision(),key);var rows=db.queryForList("SELECT request_sha256,response_json FROM framework_form_operation WHERE tenant_id=? AND actor_id=? AND idempotency_key=? AND action=? FOR UPDATE",a.context().tenant_id(),a.actorId(),key,action);if(rows.isEmpty())return null;var row=single(rows);same(schema.hash(request),str(row,"request_sha256"));return schema.read(str(row,"response_json"));}
    private JsonNode finish(EnterpriseAccess.Admission a,String key,String action,JsonNode request,JsonNode data)throws Exception{var response=envelope(data);one(permissions.saveAudit(a.actorId(),null,UUID.randomUUID().toString(),"management.form."+action.toLowerCase(Locale.ROOT),json.canonical(request),text(response,"request_id"),schema.digest,"INTERNAL"));one(db.update("INSERT INTO framework_form_operation(tenant_id,actor_id,idempotency_key,action,request_sha256,response_json,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')",a.context().tenant_id(),a.actorId(),key,action,schema.hash(request),json.canonical(response),schema.digest,a.actorId()));return response;}
    private JsonNode envelope(JsonNode data){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",data));}
    private static String text(JsonNode n,String key){return n.required(key).asText();}private static String str(Map<String,Object> n,String key){return Objects.requireNonNull(n.get(key)).toString();}
    private static Map<String,Object> single(List<Map<String,Object>> rows){if(rows.size()!=1)fail(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}private static void same(String a,String b){if(!Objects.equals(a,b))fail(ErrorCode.CONTENT_MISMATCH);}private static void one(int n){if(n!=1)fail(ErrorCode.CONTENT_MISMATCH);}private static void fail(ErrorCode code){throw new TenantAccessException(code);}private static void uuid(String id){if(id==null||!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))FrameworkFormSchema.bad();}
}

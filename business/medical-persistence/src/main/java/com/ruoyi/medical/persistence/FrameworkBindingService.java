package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.FrameworkBindingAuthority;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Author -> independent human approval -> actual existing release row. No seed approval or implicit promotion. */
@Component
@EnableConfigurationProperties(FrameworkBindingPolicy.class)
public final class FrameworkBindingService {
    private final EnterpriseAccess access;private final ResourceReadAuthority authority;private final PermissionMapper permissions;
    private final PermissionSchema json;private final FrameworkBindingSchema schema;private final FrameworkBindingCatalogue catalogue;
    private final FrameworkBindingPolicy policy;private final Environment environment;private final JdbcTemplate db;
    public FrameworkBindingService(EnterpriseAccess access,ResourceReadAuthority authority,PermissionMapper permissions,PermissionSchema json,
            FrameworkBindingSchema schema,FrameworkBindingCatalogue catalogue,FrameworkBindingPolicy policy,Environment environment,
            @Qualifier("masterDataSource") DataSource data) {
        this.access=access;this.authority=authority;this.permissions=permissions;this.json=json;this.schema=schema;this.catalogue=catalogue;this.policy=policy;this.environment=environment;db=new JdbcTemplate(data);db.setQueryTimeout(5);
    }
    public JsonNode catalogue(ApplicationSessionIdentity.Verified subject,String context) {
        return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);grant(a,"management.discover");var result=json.object();
            result.put("schema_ref",FrameworkBindingCatalogue.REF+"Manifest");result.put("schema_sha256",schema.schemaSha256);
            result.put("action_catalogue_sha256",schema.actionDigest());result.put("configuration_sha256",schema.hash(configuration()));result.set("configuration",configuration());
            result.set("operations",catalogue.document(json));return envelope(result);}).get());
    }
    public JsonNode readDraft(ApplicationSessionIdentity.Verified subject,String context,String release) {
        WireValues.uuid(release);return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);grant(a,"management.discover");var row=stored(a,release);var manifest=schema.read(str(row,"manifest_json"));verifyDraft(row,manifest);
            var states=db.queryForList("SELECT status FROM management_binding_release WHERE tenant_id=? AND release_id=?",a.context().tenant_id(),release);
            var result=json.object().put("release_id",release).put("manifest_sha256",str(row,"manifest_sha256")).put("created_by",str(row,"created_by")).put("status",states.isEmpty()?"DRAFT":str(single(states),"status"));
            result.set("manifest",manifest);result.put("manifest_canonical",json.canonical(manifest));return envelope(result);}).get());
    }
    public JsonNode draft(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        var request=schema.read(bytes);schema.validate("Draft",request);var manifest=request.required("manifest");schema.manifest(manifest);
        return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);grant(a,"management.configure");String hash=schema.hash(manifest),release=text(manifest,"release_id");
            var prior=replay(a,key,"DRAFT",request);if(prior!=null)return prior;
            register(a,release,"MANAGEMENT_RELEASE",hash);
            one(db.update("""
                INSERT INTO framework_binding_manifest(tenant_id,release_id,environment_id,manifest_json,manifest_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                VALUES(?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')
                """,a.context().tenant_id(),release,text(manifest,"environment_id"),json.canonical(manifest),hash,schema.schemaSha256,a.actorId()));
            var result=json.object().put("release_id",release).put("manifest_sha256",hash).put("status","DRAFT");result.set("manifest",manifest);result.put("manifest_canonical",json.canonical(manifest));result.put("created_by",a.actorId());
            return finish(a,key,"DRAFT",request,result);
        }).get());
    }
    public JsonNode approve(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        var request=schema.read(bytes);schema.validate("Approve",request);
        return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);var witness=grant(a,"management.approve");var prior=replay(a,key,"APPROVE",request);if(prior!=null)return prior;
            String release=text(request,"release_id");var draft=stored(a,release);var manifest=schema.read(str(draft,"manifest_json"));verifyDraft(draft,manifest);
            if(a.actorId().equals(str(draft,"created_by")))deny();same(text(request,"manifest_sha256"),str(draft,"manifest_sha256"));
            artifact(a,text(request,"manifest_artifact_id"),str(draft,"manifest_sha256"));
            // Lock the tenant environment before selecting a head: concurrent approvals cannot both be active.
            single(db.queryForList("SELECT tenant_id FROM tenant WHERE tenant_id=? FOR UPDATE",a.context().tenant_id()));
            if(!db.queryForList("SELECT release_id FROM management_binding_release WHERE tenant_id=? AND environment_id=? AND status='ACTIVE' FOR UPDATE",a.context().tenant_id(),text(manifest,"environment_id")).isEmpty())fail(ErrorCode.REVISION_CONFLICT);
            String approval=UUID.randomUUID().toString();var proof=json.object().put("approval_id",approval).put("release_id",release).put("manifest_sha256",str(draft,"manifest_sha256"))
                    .put("manifest_artifact_id",text(request,"manifest_artifact_id")).put("created_by",a.actorId()).put("reason",text(request,"reason"));
            proof.set("authority",AgentPublicationWitness.tree(witness));String digest=schema.hash(proof);register(a,approval,"MANAGEMENT_APPROVAL",digest);
            one(db.update("""
                INSERT INTO framework_binding_approval(tenant_id,approval_id,release_id,approval_json,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification)
                VALUES(?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')
                """,a.context().tenant_id(),approval,release,json.canonical(proof),digest,schema.schemaSha256,a.actorId()));
            one(db.update("""
                INSERT INTO management_binding_release(tenant_id,project_id,release_id,environment_id,source_commit,manifest_artifact_id,manifest_sha256,approval_id,status,created_by,created_at,classification,revision,updated_at)
                VALUES(?,NULL,?,?,?,?,?,?,'ACTIVE',?,UTC_TIMESTAMP(6),'INTERNAL',1,UTC_TIMESTAMP(6))
                """,a.context().tenant_id(),release,text(manifest,"environment_id"),text(manifest,"source_commit"),text(request,"manifest_artifact_id"),str(draft,"manifest_sha256"),approval,a.actorId()));
            return finish(a,key,"APPROVE",request,json.object().put("release_id",release).put("approval_id",approval).put("manifest_sha256",str(draft,"manifest_sha256")).put("status","ACTIVE"));
        }).get());
    }
    public JsonNode revoke(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception {
        var request=schema.read(bytes);schema.validate("Revoke",request);
        return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);grant(a,"management.approve");var prior=replay(a,key,"REVOKE",request);if(prior!=null)return prior;
            one(db.update("UPDATE management_binding_release SET status='REVOKED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND release_id=? AND status='ACTIVE'",a.context().tenant_id(),text(request,"release_id")));
            return finish(a,key,"REVOKE",request,json.object().put("release_id",text(request,"release_id")).put("status","REVOKED"));}).get());
    }
    public JsonNode discover(ApplicationSessionIdentity.Verified subject,String context) {
        return access.execute(subject,a->ThrowingSupplier.of(()->{human(a,context);grant(a,"management.discover");var m=current(a);var result=json.object().put("release_id",text(m,"release_id")).put("environment_id",text(m,"environment_id")).put("manifest_sha256",schema.hash(m));result.set("modules",m.required("modules"));return envelope(result);}).get());
    }
    public FrameworkBindingAuthority.Binding require(ApplicationSessionIdentity.Verified subject,String context,String id,String method,String path) {
        return access.execute(subject,a->ThrowingSupplier.of(()->requireWithin(a,context,id,method,path)).get());
    }
    FrameworkBindingAuthority.Binding requireWithin(EnterpriseAccess.Admission a,String context,String id,String method,String path)throws Exception {
        human(a,context);grant(a,"management.discover");var m=current(a);var declared=catalogue.require(id);
        if(!catalogue.matches(declared,method,path))deny();boolean found=false;
        for(var module:m.required("modules"))for(var action:module.required("operations"))if(id.equals(text(action,"operation_id")))found=true;
        if(!found)deny();return new FrameworkBindingAuthority.Binding(text(m,"release_id"),text(m,"environment_id"),schema.hash(m));
    }
    private JsonNode current(EnterpriseAccess.Admission a)throws Exception {
        if(!policy.isEnabled()||policy.getReleaseId()==null||policy.getEnvironmentId()==null||policy.getSourceCommit()==null)fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var row=single(db.queryForList("SELECT * FROM management_binding_release WHERE tenant_id=? AND release_id=? AND environment_id=? AND project_id IS NULL AND status='ACTIVE' FOR UPDATE",a.context().tenant_id(),policy.getReleaseId(),policy.getEnvironmentId()));
        var draft=stored(a,policy.getReleaseId());var m=schema.read(str(draft,"manifest_json"));verifyDraft(draft,m);
        same(policy.getEnvironmentId(),text(m,"environment_id"));same(policy.getSourceCommit(),text(m,"source_commit"));same(policy.getRuntimeEnvironment(),text(m,"runtime_environment"));same(schema.hash(configuration()),text(m,"configuration_sha256"));
        same(str(row,"manifest_sha256"),schema.hash(m));same(str(row,"source_commit"),text(m,"source_commit"));
        var approval=single(db.queryForList("SELECT * FROM framework_binding_approval WHERE tenant_id=? AND approval_id=? AND release_id=?",a.context().tenant_id(),str(row,"approval_id"),str(row,"release_id")));
        var proof=schema.read(str(approval,"approval_json"));same(schema.hash(proof),str(approval,"content_sha256"));same(schema.schemaSha256,str(approval,"schema_manifest_sha256"));
        same(str(row,"manifest_sha256"),text(proof,"manifest_sha256"));same(str(row,"release_id"),text(proof,"release_id"));same(str(row,"approval_id"),text(proof,"approval_id"));
        same(str(row,"manifest_artifact_id"),text(proof,"manifest_artifact_id"));same(str(approval,"created_by"),text(proof,"created_by"));
        same("1.0.0",str(approval,"schema_version"));same("INTERNAL",str(approval,"classification"));same(str(row,"created_by"),str(approval,"created_by"));if(str(approval,"created_by").equals(str(draft,"created_by")))deny();registry(a,str(row,"approval_id"),"MANAGEMENT_APPROVAL",str(approval,"content_sha256"));
        artifact(a,str(row,"manifest_artifact_id"),str(row,"manifest_sha256"));deployment(m);return m;
    }
    private void deployment(JsonNode manifest) {
        for(var module:manifest.required("modules")) {
            if(!"BOUND".equals(text(module,"binding_status")))continue;
            if(!"local".equals(environment.getProperty("medical.identity.mode")))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            String id=text(module,"module_id");
            if("CODE_GENERATOR".equals(id)&&(!environment.getProperty("medical.framework.generator.enabled",Boolean.class,false)
                ||!"DEVELOPMENT".equals(environment.getProperty("medical.framework.environment"))))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            for(var operation:module.required("operations"))if("DESIGN_OR_DEVELOPMENT".equals(catalogue.require(text(operation,"operation_id")).environment())&&!Set.of("DESIGN","DEVELOPMENT").contains(environment.getProperty("medical.framework.environment","")))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
            for(var operation:module.required("operations"))if(text(operation,"operation_id").startsWith("framework.job.")
                &&!environment.getProperty("medical.framework.job-dispatch.enabled",Boolean.class,false))fail(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
    }
    private JsonNode configuration(){var c=json.object();c.put("identity_mode",environment.getProperty("medical.identity.mode","oidc"));c.put("job_dispatch_enabled",environment.getProperty("medical.framework.job-dispatch.enabled",Boolean.class,false));c.put("generator_enabled",environment.getProperty("medical.framework.generator.enabled",Boolean.class,false));c.put("runtime_environment",policy.getRuntimeEnvironment());c.put("framework_environment",environment.getProperty("medical.framework.environment","PRODUCTION"));return c;}
    private Map<String,Object> stored(EnterpriseAccess.Admission a,String release){return single(db.queryForList("SELECT * FROM framework_binding_manifest WHERE tenant_id=? AND release_id=? FOR UPDATE",a.context().tenant_id(),release));}
    private void verifyDraft(Map<String,Object> draft,JsonNode m)throws Exception{schema.manifest(m);same(schema.hash(m),str(draft,"manifest_sha256"));same(text(m,"release_id"),str(draft,"release_id"));same(text(m,"environment_id"),str(draft,"environment_id"));same(schema.schemaSha256,str(draft,"schema_manifest_sha256"));same("1.0.0",str(draft,"schema_version"));same("INTERNAL",str(draft,"classification"));
        var rows=db.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? AND owner_project_id IS NULL AND entity_kind='MANAGEMENT_RELEASE'",str(draft,"tenant_id"),str(draft,"release_id"));same(str(single(rows),"content_sha256"),str(draft,"manifest_sha256"));}
    private void artifact(EnterpriseAccess.Admission a,String id,String hash)throws Exception {
        authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"ARTIFACT",id),"artifact.read","MANAGEMENT_BINDING");
        var r=single(db.queryForList("SELECT a.sha256,a.content_sha256,a.media_type,v.content_sha256 AS registry_sha256 FROM artifact a JOIN version_registry v ON v.tenant_id=a.tenant_id AND v.version_id=a.artifact_id WHERE a.tenant_id=? AND a.artifact_id=? AND a.project_id IS NULL AND v.owner_project_id IS NULL AND v.entity_kind='ARTIFACT'",a.context().tenant_id(),id));
        same(hash,str(r,"sha256"));same(hash,str(r,"content_sha256"));same(hash,str(r,"registry_sha256"));same("application/json",str(r,"media_type"));
    }
    private void register(EnterpriseAccess.Admission a,String id,String kind,String hash){one(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,NULL,?,?,?)",a.context().tenant_id(),id,kind,hash));}
    private void registry(EnterpriseAccess.Admission a,String id,String kind,String hash){same(hash,str(single(db.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? AND entity_kind=? AND owner_project_id IS NULL",a.context().tenant_id(),id,kind)),"content_sha256"));}
    private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,String action)throws Exception{return authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"ENTERPRISE",a.context().tenant_id()),action,"MANAGEMENT_BINDING");}
    private void human(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);if(a.simulationWorker()||a.formalWorker()||!"PLATFORM_ADMIN".equals(a.subject().user().platform_role()))deny();}
    private JsonNode replay(EnterpriseAccess.Admission a,String key,String action,JsonNode request)throws Exception{json.headers(a.context().context_revision(),key);var rows=db.queryForList("SELECT request_sha256,response_json FROM framework_binding_operation WHERE tenant_id=? AND actor_id=? AND idempotency_key=? AND action=? FOR UPDATE",a.context().tenant_id(),a.actorId(),key,action);if(rows.isEmpty())return null;var r=single(rows);same(schema.hash(request),str(r,"request_sha256"));return schema.read(str(r,"response_json"));}
    private JsonNode finish(EnterpriseAccess.Admission a,String key,String action,JsonNode request,JsonNode data)throws Exception {var response=envelope(data);String requestId=text(response,"request_id");
        one(permissions.saveAudit(a.actorId(),null,UUID.randomUUID().toString(),"management.binding."+action.toLowerCase(Locale.ROOT),json.canonical(request),requestId,schema.schemaSha256,"INTERNAL"));
        one(db.update("INSERT INTO framework_binding_operation(tenant_id,actor_id,idempotency_key,action,request_sha256,response_json,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'INTERNAL')",a.context().tenant_id(),a.actorId(),key,action,schema.hash(request),json.canonical(response),schema.schemaSha256,a.actorId()));return response;}
    private JsonNode envelope(JsonNode value){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),"1",value));}
    private static String text(JsonNode n,String key){return n.required(key).asText();}private static String str(Map<String,Object> n,String key){return Objects.requireNonNull(n.get(key)).toString();}
    private static Map<String,Object> single(List<Map<String,Object>> rows){if(rows.size()!=1)fail(ErrorCode.OBJECT_UNAVAILABLE);return rows.get(0);}private static void same(String a,String b){if(!Objects.equals(a,b))fail(ErrorCode.CONTENT_MISMATCH);}private static void one(int n){if(n!=1)fail(ErrorCode.CONTENT_MISMATCH);}private static void one(int n,boolean ignore){if(n!=0&&n!=1)fail(ErrorCode.CONTENT_MISMATCH);}private static void deny(){fail(ErrorCode.ACCESS_DENIED);}private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

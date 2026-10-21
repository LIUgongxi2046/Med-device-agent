package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.PlatformMemberIdentityStatus;
import com.ruoyi.medical.persistence.mapper.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Two-person deployment-scope authority. Declaration is not proof; independent approval probes
 * actual adapters. Unsupported external/offline copies remain INCOMPLETE and can never authorize purge. */
@Component
public final class SourceCopyAuthority {
    private final JdbcTemplate db;
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionAuthority grants;
    private final PermissionMapper permissions;
    private final SourceGovernanceMapper governance;
    private final SourceDeletionMapper deletion;
    private final PermissionSchema json;
    private final SourceGovernanceSchema parser;
    private final PlatformMemberIdentityStatus identities;
    private final ObjectProvider<SourceDeletionPolicy> policies;
    private final ObjectProvider<SourceDeletionStores> stores;
    private final JsonSchema declarationSchema,decisionSchema;
    private final String manifest;
    @org.springframework.beans.factory.annotation.Autowired
    private ExternalErasureRegistry external;

    public SourceCopyAuthority(@Qualifier("masterDataSource") DataSource data,EnterpriseAccess access,
            ResourceReadAuthority resources,PermissionAuthority grants,PermissionMapper permissions,
            SourceGovernanceMapper governance,SourceDeletionMapper deletion,PermissionSchema json,
            SourceGovernanceSchema parser,PlatformMemberIdentityStatus identities,
            ObjectProvider<SourceDeletionPolicy> policies,ObjectProvider<SourceDeletionStores> stores)throws Exception {
        db=new JdbcTemplate(data);this.access=access;this.resources=resources;this.grants=grants;this.permissions=permissions;
        this.governance=governance;this.deletion=deletion;this.json=json;this.parser=parser;this.identities=identities;
        this.policies=policies;this.stores=stores;
        byte[] bytes;try(var stream=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("source-copy-authority-v2.schema.json"))){bytes=stream.readAllBytes();}
        manifest=DigestUtils.sha256Hex(bytes);var document=parser.read(new String(bytes,StandardCharsets.UTF_8));
        var config=SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
        declarationSchema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document.required("$defs").required("Declare"),config);
        decisionSchema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(document.required("$defs").required("Decide"),config);
    }

    public JsonNode configuration(ApplicationSessionIdentity.Verified actor,String context,String source) {
        return access.execute(actor,a->ThrowingSupplier.of(()->{
            current(a,context);var state=state(a,source);authorize(a,state);var policy=policy();
            var view=json.object().put("schema_version","2.0.0").put("source_version",source)
                .put("configuration_sha256",configurationHash(policy)).put("configuration_revision",policy.revision());
            view.set("configured_scopes",topology(policy));view.put("declaration_is_proof",false);
            view.set("external_protocol_authorities",external.topology());view.put("unsupported_external_scopes","unregistered scopes and OTHER");return view;
        }).get());
    }

    public JsonNode command(ApplicationSessionIdentity.Verified actor,String context,String source,String key,byte[] body) {
        RuntimeRetentionSchema.key(key);WireValues.uuid(source);
        if(body==null||body.length>65536)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return access.execute(actor,a->ThrowingSupplier.of(()->{
            current(a,context);var state=state(a,source);var grant=authorize(a,state);
            var request=parser.read(new String(body,StandardCharsets.UTF_8));String kind=request.path("kind").asText();
            var validator="DECLARE".equals(kind)?declarationSchema:decisionSchema;
            if(!validator.validate(request).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            String tenant=a.context().tenant_id(),requestHash=hash(json.tree(Map.of("source_version",source,"request",request)));
            var prior=db.query("SELECT fixed_json,content_sha256 FROM source_copy_authority WHERE tenant_id=? AND created_by=? AND idempotency_key=?",
                (r,n)->verified(r.getString(1),r.getString(2)),tenant,a.actorId(),key);
            if(prior.size()>1)fail();
            if(!prior.isEmpty()) {if(!requestHash.equals(prior.get(0).path("request_sha256").asText()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return receipt(prior.get(0));}
            var policy=policy();Instant now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
            var fixed=json.object().put("schema_version","2.0.0").put("authority_id",UUID.randomUUID().toString())
                .put("tenant_id",tenant).put("source_version",source).put("created_by",a.actorId()).put("created_at",now.toString())
                .put("request_sha256",requestHash).put("configuration_sha256",configurationHash(policy)).put("configuration_revision",policy.revision())
                .put("decision_kind",kind).put("classification",state.classification()).put("schema_manifest_sha256",manifest);
            fixed.set("project_id",json.tree(state.ownerProjectId()));fixed.set("request",request);
            fixed.put("audit_id",UUID.randomUUID().toString()).put("event_id",UUID.randomUUID().toString()).put("source_revision",state.revision().toString());
            fixed.set("grant",json.tree(Map.of("grant_id",grant.grantId(),"revision",grant.revision().toString())));
            if("DECLARE".equals(kind)) {
                if(!fixed.path("configuration_sha256").equals(request.path("configuration_sha256")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
                Instant until=WireValues.utcTime(request.path("valid_until").asText());
                requireWindow(now,until);
                var scopeIds=new HashSet<String>();for(var scope:request.path("external_scopes"))if(!scopeIds.add(scope.path("scope_id").asText()))fail();
                fixed.putNull("declaration_id");fixed.put("status","DECLARED");fixed.set("configured_scopes",topology(policy));
            } else {
                var original=load(tenant,request.path("declaration_id").asText());
                if(!"DECLARE".equals(original.path("decision_kind").asText())||!source.equals(original.path("source_version").asText())
                        ||!request.path("declaration_sha256").asText().equals(hash(original))
                        ||!original.path("project_id").equals(fixed.path("project_id")))fail();
                fixed.put("declaration_id",original.path("authority_id").asText());
                var decisions=db.queryForList("SELECT decision_kind FROM source_copy_authority WHERE tenant_id=? AND declaration_id=? FOR UPDATE",
                    String.class,tenant,original.path("authority_id").asText());
                if("REVOKE".equals(kind)) {
                    if(decisions.contains("REVOKE"))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
                    fixed.put("status","REVOKED");
                } else {
                    requireIndependent(original.path("created_by").asText(),a.actorId());
                    if(!decisions.isEmpty())throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
                    if(!original.path("configuration_sha256").equals(fixed.path("configuration_sha256"))
                            ||!WireValues.utcTime(original.path("request").path("valid_until").asText()).isAfter(now))
                        throw new TenantAccessException(ErrorCode.SCOPE_STALE);
                    currentAuthor(original,state,now);
                    var observed=probe(a,state,policy,original);
                    fixed.set("inventory",observed);fixed.put("inventory_sha256",hash(observed));
                    boolean complete=observed.path("complete").asBoolean(false);
                    fixed.put("status",complete?"APPROVED":"INCOMPLETE");fixed.put("decision_kind",complete?"APPROVE":"INCOMPLETE");
                }
            }
            if(!grant.equals(authorize(a,state)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            String id=fixed.path("authority_id").asText(),declaration=fixed.path("declaration_id").isNull()?null:fixed.path("declaration_id").asText();
            String decision=fixed.path("decision_kind").asText(),digest=hash(fixed);
            var event=json.object().put("schema_version","2.0.0").put("authority_id",id).put("content_sha256",digest).put("status",fixed.path("status").asText());
            if(permissions.saveAudit(a.actorId(),state.ownerProjectId(),fixed.path("audit_id").asText(),"source.copy_authority_decided",
                    json.canonical(event),id,manifest,state.classification())!=1)fail();
            if(permissions.saveEvent(a.actorId(),fixed.path("event_id").asText(),source,state.revision().toString(),
                    "source.copy_authority_decided",json.canonical(event))!=1)fail();
            int inserted=db.update("""
                INSERT INTO source_copy_authority(tenant_id,authority_id,source_version,project_id,declaration_id,decision_kind,
                  fixed_json,content_sha256,request_sha256,idempotency_key,created_by,created_at,classification,schema_version,schema_manifest_sha256,audit_id,event_id)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'2.0.0',?,?,?)
                """,tenant,id,source,state.ownerProjectId(),declaration,decision,json.canonical(fixed),digest,requestHash,key,a.actorId(),
                LocalDateTime.ofInstant(now,ZoneOffset.UTC),state.classification(),manifest,fixed.path("audit_id").asText(),fixed.path("event_id").asText());
            if(inserted!=1)fail();
            return receipt(fixed);
        }).get());
    }

    public JsonNode read(ApplicationSessionIdentity.Verified actor,String context,String source,String id) {
        return access.execute(actor,a->ThrowingSupplier.of(()->{
            current(a,context);authorize(a,state(a,source));var saved=load(a.context().tenant_id(),id);
            if(!source.equals(saved.path("source_version").asText()))fail();
            var view=(ObjectNode)receipt(saved);view.set("request",saved.path("request").deepCopy());
            view.put("created_by",saved.path("created_by").asText()).put("created_at",saved.path("created_at").asText());
            if(saved.has("configured_scopes"))view.set("configured_scopes",saved.path("configured_scopes").deepCopy());
            if(saved.has("inventory_sha256"))view.put("inventory_sha256",saved.path("inventory_sha256").asText());
            return view;
        }).get());
    }

    public JsonNode recover(ApplicationSessionIdentity.Verified actor,String context,String source,String key) {
        RuntimeRetentionSchema.key(key);return access.execute(actor,a->ThrowingSupplier.of(()->{
            current(a,context);authorize(a,state(a,source));
            var found=db.query("SELECT fixed_json,content_sha256 FROM source_copy_authority WHERE tenant_id=? AND created_by=? AND source_version=? AND idempotency_key=?",
                (r,n)->verified(r.getString(1),r.getString(2)),a.context().tenant_id(),a.actorId(),source,key);
            if(found.size()>1)fail();return found.isEmpty()?json.object().put("schema_version","2.0.0").put("status","NOT_FOUND"):receipt(found.get(0));
        }).get());
    }

    /** Called before any destructive worker action, and again before final confirmation. */
    JsonNode requireCurrent(String source,SourceDeletionPolicy policy)throws Exception {
        String tenant=TenantScope.requireCurrent().tenantId();
        var state=single(governance.lifecycleStateForUpdate(Map.of("userId",TenantScope.requireCurrent().subjectId(),"sourceVersion",source)));
        Instant now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
        var candidates=db.query("""
            SELECT a.fixed_json,a.content_sha256 FROM source_copy_authority a
            WHERE a.tenant_id=? AND a.source_version=? AND a.decision_kind='APPROVE'
              AND NOT EXISTS(SELECT 1 FROM source_copy_authority r WHERE r.tenant_id=a.tenant_id
                AND r.declaration_id=a.declaration_id AND r.decision_kind='REVOKE')
            ORDER BY a.authority_id LIMIT 101
            """,(r,n)->verified(r.getString(1),r.getString(2)),tenant,source);
        if(candidates.size()>100)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var valid=new ArrayList<JsonNode>();
        for(var approval:candidates) {
            var declaration=load(tenant,approval.path("declaration_id").asText());
            if(!source.equals(approval.path("source_version").asText())||!source.equals(declaration.path("source_version").asText())
                    ||!"DECLARE".equals(declaration.path("decision_kind").asText())
                    ||!hash(declaration).equals(approval.path("request").path("declaration_sha256").asText()))fail();
            if(!WireValues.utcTime(declaration.path("request").path("valid_until").asText()).isAfter(now))continue;
            if(!configurationHash(policy).equals(approval.path("configuration_sha256").asText())
                    ||!approval.path("configuration_sha256").equals(declaration.path("configuration_sha256")))continue;
            if(approval.path("created_by").equals(declaration.path("created_by"))||!approval.path("inventory").path("complete").asBoolean(false)
                    ||!hash(approval.path("inventory")).equals(approval.path("inventory_sha256").asText()))fail();
            
            currentAuthor(declaration,state,now);currentAuthor(approval,state,now);valid.add(approval);
        }
        if(valid.size()!=1)throw new TenantAccessException(valid.isEmpty()?ErrorCode.DEPENDENCY_UNAVAILABLE:ErrorCode.REVISION_CONFLICT);
        // Recheck dynamically discoverable external model copies, including WITHDRAWN operations.
        // Local journal erasure does not prove the provider erased its copy.
        var selected=valid.get(0);var declared=load(tenant,selected.path("declaration_id").asText());
        requireExternal(declared,selected,state,now,true);
        var chosen=valid.get(0);return json.object().put("authority_id",chosen.path("authority_id").asText())
            .put("content_sha256",hash(chosen)).put("configuration_sha256",configurationHash(policy));
    }

    private ObjectNode probe(EnterpriseAccess.Admission a,SourceGovernanceProtocol.LifecycleStateRow state,
            SourceDeletionPolicy policy,JsonNode declaration)throws Exception {
        var result=json.object().put("complete",true);var gaps=result.putArray("gaps");
        
        String tenant=a.context().tenant_id(),source=state.sourceVersion();
        try {
            var guard=new com.ruoyi.medical.persistence.erasure.ExternalErasure.Authority(){
                public <T>T current(com.ruoyi.medical.persistence.erasure.ExternalErasure.Approval approval,com.ruoyi.medical.persistence.erasure.ExternalErasure.Action<T> action)throws Exception {authorize(a,state);currentAuthor(declaration,state,now());return action.run();}
                public Instant now(){return permissions.databaseTime().toInstant(ZoneOffset.UTC);}
            };
            result.set("external_inventory",external.observe(declaration.path("request").path("external_scopes"),tenant,source,
                declaration.path("authority_id").asText(),declaration.path("created_by").asText(),a.actorId(),
                WireValues.utcTime(declaration.path("request").path("valid_until").asText()),guard,externalOperationIds(tenant,source),false));
        } catch(Exception unavailable) {gaps.addObject().put("scope_id","EXTERNAL").put("status","EXTERNAL_AUTHORITY_INCOMPLETE");}
        if(!"PURGE_PENDING".equals(state.status()))gaps.addObject().put("scope_id","SOURCE").put("status","NOT_FROZEN_FOR_PURGE");
        var jobIds=db.queryForList("SELECT deletion_id FROM deletion_job WHERE tenant_id=? AND target_version=? AND status IN ('QUEUED','RUNNING')",String.class,tenant,source);
        if(jobIds.size()!=1)gaps.addObject().put("scope_id","SOURCE").put("status","DELETION_SCOPE_UNAVAILABLE");
        var adapter=stores.getIfAvailable();
        if(adapter==null)gaps.addObject().put("scope_id","STORAGE").put("status","ADAPTER_UNAVAILABLE");
        if(gaps.isEmpty()) {
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("deletionId",jobIds.get(0));values.put("sourceVersion",source);
            var targets=governance.deletionItems(values);if(targets.isEmpty()||targets.size()>policy.maximumEntries())fail();
            var guard=new SourceDeletionStores.Guard(){
                public <T>T call(SourceDeletionStores.Action<T> action)throws Exception{authorize(a,state);return action.run();}
                public Instant now(){return permissions.databaseTime().toInstant(ZoneOffset.UTC);}
            };
            try {
                var observations=adapter.observeAuthority(tenant,targets,guard,deletion,values);result.set("storage_inventory",observations);
                if(!observations.path("complete").asBoolean(false))gaps.addObject().put("scope_id","STORAGE").put("status","INVENTORY_INCOMPLETE");
            } catch(Exception unavailable) {
                // No exception/provider payload is exposed as evidence, and no success is inferred.
                gaps.addObject().put("scope_id","STORAGE").put("status","OBSERVATION_UNAVAILABLE");
            }
        }
        result.put("complete",gaps.isEmpty());result.put("observed_at",permissions.databaseTime().toInstant(ZoneOffset.UTC).toString());return result;
    }

    private java.util.Set<String> externalOperationIds(String tenant,String source){
        return new java.util.HashSet<>(db.queryForList("SELECT o.operation_id FROM dense_operation o JOIN dense_operation_source s ON s.tenant_id=o.tenant_id AND s.operation_id=o.operation_id WHERE s.tenant_id=? AND s.source_version=? AND o.status<>'PREPARED'",String.class,tenant,source));
    }
    private void requireExternal(JsonNode declaration,JsonNode approval,SourceGovernanceProtocol.LifecycleStateRow state,Instant now,boolean absent)throws Exception {
        var guard=new com.ruoyi.medical.persistence.erasure.ExternalErasure.Authority(){
            public <T>T current(com.ruoyi.medical.persistence.erasure.ExternalErasure.Approval ignored,com.ruoyi.medical.persistence.erasure.ExternalErasure.Action<T> action)throws Exception {currentAuthor(declaration,state,now());currentAuthor(approval,state,now());return action.run();}
            public Instant now(){return permissions.databaseTime().toInstant(ZoneOffset.UTC);}
        };
        try { external.observe(declaration.path("request").path("external_scopes"),state.tenantId(),state.sourceVersion(),
            approval.path("authority_id").asText(),declaration.path("created_by").asText(),approval.path("created_by").asText(),
            WireValues.utcTime(declaration.path("request").path("valid_until").asText()),guard,externalOperationIds(state.tenantId(),state.sourceVersion()),absent);
        }catch(Exception unavailable){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
    }

    /** Revalidates the genuine independent source authority for each external operation. Unlike
     * SOURCE purge, external erase must be possible before final external absence is established. */
    <T>T externalOperation(ApplicationSessionIdentity.Verified actor,String context,String source,String approvalId,String scopeId,
            com.ruoyi.medical.persistence.erasure.ExternalErasure.ActionWithApproval<T> action) {
        return access.execute(actor,a->ThrowingSupplier.of(()->{
            current(a,context);var state=state(a,source);authorize(a,state);
            if(!"PURGE_PENDING".equals(state.status()))throw new TenantAccessException(ErrorCode.ACTION_STALE);
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("sourceVersion",source);
            var jobs=db.queryForList("SELECT deletion_id FROM deletion_job WHERE tenant_id=? AND target_version=? AND status IN ('QUEUED','RUNNING')",String.class,a.context().tenant_id(),source);
            if(jobs.size()!=1)throw new TenantAccessException(ErrorCode.ACTION_STALE);
            values.put("deletionId",jobs.get(0));var job=single(governance.deletionJob(values));values.put("policyRevision",job.policyRevision());
            var retention=single(governance.retentionPolicy(values));var local=permissions.databaseTime();
            if(!policy().tenantIds().contains(a.context().tenant_id()) || !"SOURCE".equals(retention.category()) || !"SOURCE_CREATED".equals(retention.startEvent())
                || !"APPROVED".equals(retention.status()) || retention.effectiveFrom().isAfter(local) || !"VERIFIED_DELETE".equals(retention.purgeMethod())
                || retention.retentionDuration().signum()<=0 || retention.backupExpiry().signum()<=0)
                throw new TenantAccessException(ErrorCode.RETENTION_UNCONFIGURED);
            if(state.sourceCreatedAt().plusSeconds(retention.retentionDuration().longValueExact()).isAfter(local))throw new TenantAccessException(ErrorCode.ACTION_STALE);
            if(governance.blockingReferenceCount(values).signum()!=0 || deletion.additionalReferenceCount(values).signum()!=0)throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
            var approved=load(a.context().tenant_id(),approvalId);var declaration=load(a.context().tenant_id(),approved.path("declaration_id").asText());
            Instant now=permissions.databaseTime().toInstant(ZoneOffset.UTC);
            if(!source.equals(approved.path("source_version").asText())||!"APPROVE".equals(approved.path("decision_kind").asText())
                ||!hash(declaration).equals(approved.path("request").path("declaration_sha256").asText())
                ||!configurationHash(policy()).equals(approved.path("configuration_sha256").asText())
                ||!WireValues.utcTime(declaration.path("request").path("valid_until").asText()).isAfter(now)
                ||!approved.path("inventory").path("complete").asBoolean(false)
                ||db.queryForObject("SELECT COUNT(*) FROM source_copy_authority WHERE tenant_id=? AND declaration_id=? AND decision_kind='REVOKE'",Long.class,a.context().tenant_id(),declaration.path("authority_id").asText())!=0)
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            currentAuthor(declaration,state,now);currentAuthor(approved,state,now);
            JsonNode selected=null;for(var item:approved.path("inventory").path("external_inventory").path("scopes"))if(scopeId.equals(item.path("scope_id").asText()))selected=item;
            if(selected==null||!"AUTHORITATIVE".equals(selected.path("proof").asText()))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            var scopeApproval=external.approval(a.context().tenant_id(),source,scopeId,approvalId,
                selected.path("inventory").path("snapshot").asText(),declaration.path("created_by").asText(),approved.path("created_by").asText(),
                WireValues.utcTime(declaration.path("request").path("valid_until").asText()));
            return action.run(scopeApproval);
        }).get());
    }

    private void currentAuthor(JsonNode fixed,SourceGovernanceProtocol.LifecycleStateRow state,Instant now)throws Exception {
        if(!fixed.path("project_id").equals(json.tree(state.ownerProjectId()))||!state.sourceVersion().equals(fixed.path("source_version").asText()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        String actor=fixed.path("created_by").asText();var member=single(permissions.targetMember(actor));var binding=single(permissions.targetBinding(actor));
        var local=LocalDateTime.ofInstant(now,ZoneOffset.UTC);
        if(!actor.equals(member.userId())||!actor.equals(binding.userId())||!"ACTIVE".equals(member.status())||!"ACTIVE".equals(binding.status())||!member.tenantId().equals(TenantScope.requireCurrent().tenantId())
                ||member.validFrom().isAfter(local)||member.validTo()!=null&&!member.validTo().isAfter(local)
                ||!Set.of("local","oidc").contains(binding.mode())
                ||"local".equals(binding.mode())&&!actor.equals(binding.localUserId())
                ||"oidc".equals(binding.mode())&&binding.localUserId()!=null
                ||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(binding.mode(),binding.userId(),binding.issuer(),binding.subject())))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        var grant=single(permissions.currentGrant(fixed.path("grant").path("grant_id").asText()));
        if(!grant.revision().toString().equals(fixed.path("grant").path("revision").asText()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        grants.requireResource(scope(state),actor,binding.mode(),"source.purge","SOURCE_PURGE",List.of(grant),local);
    }
    private SourceGovernanceProtocol.LifecycleStateRow state(EnterpriseAccess.Admission a,String source){WireValues.uuid(source);return single(governance.lifecycleStateForUpdate(Map.of("userId",a.actorId(),"sourceVersion",source)));}
    private PermissionProtocol.GrantRow authorize(EnterpriseAccess.Admission a,SourceGovernanceProtocol.LifecycleStateRow state)throws Exception{return resources.require(a,scope(state),"source.purge","SOURCE_PURGE");}
    private static PermissionProtocol.Scope scope(SourceGovernanceProtocol.LifecycleStateRow s){return new PermissionProtocol.Scope(s.tenantId(),s.ownerProjectId(),"SOURCE",s.sourceId());}
    private SourceDeletionPolicy policy(){var result=policies.getIfAvailable();if(result==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return result;}
    private JsonNode load(String tenant,String id){WireValues.uuid(id);return single(db.query("SELECT fixed_json,content_sha256 FROM source_copy_authority WHERE tenant_id=? AND authority_id=? FOR UPDATE",(r,n)->verified(r.getString(1),r.getString(2)),tenant,id));}
    private JsonNode verified(String stored,String digest){return ThrowingSupplier.of(()->{
        var value=parser.read(stored);String tenant=TenantScope.requireCurrent().tenantId();
        if(!hash(value).equals(digest)||!manifest.equals(value.path("schema_manifest_sha256").asText())||!tenant.equals(value.path("tenant_id").asText()))fail();
        var witness=json.object().put("schema_version","2.0.0").put("authority_id",value.path("authority_id").asText())
            .put("content_sha256",digest).put("status",value.path("status").asText());
        var audit=single(db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND action='source.copy_authority_decided' AND request_id=? AND schema_manifest_sha256=?",
            String.class,tenant,value.path("audit_id").asText(),value.path("created_by").asText(),value.path("authority_id").asText(),manifest));
        var event=single(db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=? AND event_type='source.copy_authority_decided'",
            String.class,tenant,value.path("event_id").asText(),value.path("source_version").asText(),value.path("source_revision").asText()));
        if(!parser.read(audit).equals(witness)||!parser.read(event).equals(witness))fail();return value;
    }).get();}
    private JsonNode receipt(JsonNode fixed)throws Exception{var value=json.object().put("schema_version","2.0.0").put("authority_id",fixed.path("authority_id").asText()).put("source_version",fixed.path("source_version").asText()).put("status",fixed.path("status").asText()).put("content_sha256",hash(fixed));if(fixed.has("inventory"))value.set("gaps",fixed.path("inventory").path("gaps"));return value;}
    private String hash(JsonNode value)throws Exception{return DigestUtils.sha256Hex(json.canonical(value));}
    String configurationHash(SourceDeletionPolicy policy)throws Exception{return hash(topology(policy));}
    private JsonNode topology(SourceDeletionPolicy p){var root=json.object().put("revision",p.revision()).put("cache_namespace",p.cacheNamespace());var objects=root.putArray("objects");for(var s:p.originals())object(objects.addObject(),"ORIGINAL",s);object(objects.addObject(),"PARSED",p.parsed());object(objects.addObject(),"DIAGNOSTIC",p.diagnostics());for(var s:p.backups())object(objects.addObject(),"BACKUP",s.store());var vectors=root.putArray("vectors");for(var s:p.vectors())vectors.addObject().put("host",s.host()).put("port",s.port()).put("tls",s.tls()).put("collection",s.collection());if(external!=null&&!external.topology().isEmpty())root.set("external_protocol_authorities",external.topology());return root;}
    private static void object(ObjectNode node,String kind,SourceDeletionPolicy.ObjectStore s){node.put("kind",kind).put("store_id",s.id()).put("endpoint",s.endpoint().toString()).put("bucket",s.bucket()).put("prefix",s.prefix());}
    private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static <T>T single(List<T> values){if(values.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);return values.get(0);}
    static void requireIndependent(String declarer,String approver){
        if(declarer==null||approver==null||declarer.equals(approver))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    static void requireWindow(Instant now,Instant until){
        if(!until.isAfter(now)||until.isAfter(now.plus(Duration.ofHours(24))))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    private static void fail(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

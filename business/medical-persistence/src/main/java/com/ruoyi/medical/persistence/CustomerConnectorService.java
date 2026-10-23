package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** CT-01/02/03 application pipeline. Customer-dependent physical protocol is deliberately
 * absent until an approved exact-spec adapter is registered. BLOCKED is a persisted result,
 * not a fabricated successful connection. External calls never run inside a DB transaction. */
@Component
public final class CustomerConnectorService {
    private final EnterpriseAccess access;
    private final ResourceReadAuthority resources;
    private final PermissionAuthority grants;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final JdbcTemplate db;
    private final ObjectProvider<CustomerConnectorAdapter> adapters;
    private final ObjectProvider<CustomerConnectorCredentials> credentials;
    private final SourceUploadService uploads;
    private final SourceUploadTransport transport;
    private final CustomerConnectorBusinessProfiles profiles;
    public CustomerConnectorService(EnterpriseAccess access,ResourceReadAuthority resources,PermissionAuthority grants,
            PermissionMapper permissions,PermissionSchema json,@Qualifier("masterDataSource")DataSource data,
            ObjectProvider<CustomerConnectorAdapter> adapters,ObjectProvider<CustomerConnectorCredentials> credentials,SourceUploadService uploads,SourceUploadTransport transport,CustomerConnectorBusinessProfiles profiles){
        this.access=access;this.resources=resources;this.grants=grants;this.permissions=permissions;this.json=json;
        this.db=new JdbcTemplate(data);db.setQueryTimeout(5);this.adapters=adapters;this.credentials=credentials;this.uploads=uploads;this.transport=transport;this.profiles=profiles;
    }
    public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes){
        return author(subject,context,key,bytes,false);
    }
    public JsonNode authorBusiness(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes){
        return author(subject,context,key,bytes,true);
    }
    private JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes,boolean business){
        return access.execute(subject,a->checked(()->{
            context(a,context);json.headers(context,key);var request=business?profiles.input(bytes):null;var profile=request==null?null:request.required("profile");var body=request==null?parse(bytes):request.required("definition");definition(body);manage(a,"connector.configure");
            String project=text(body,"project_id"),connector=text(body,"connector_id");project(a,project,"source.write");
            String revision=id(tenant(a),"CONFIG",a.actorId(),key),canonical=json.canonical(body),digest=hash(body);
            var old=db.queryForList("SELECT * FROM customer_connector_definition WHERE tenant_id=? AND config_revision=? FOR UPDATE",tenant(a),revision);
            if(!old.isEmpty()){
                var original=one(old);if(!digest.equals(str(original,"definition_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                var savedProfile=profiles.read(a,original);if(profile==null?savedProfile!=null:savedProfile==null||!profile.equals(savedProfile.required("profile")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return view(a,revision);
            }
            var heads=db.queryForList("SELECT * FROM customer_connector_head WHERE tenant_id=? AND connector_id=? FOR UPDATE",tenant(a),connector);
            if(heads.isEmpty()){
                if(!"0".equals(text(body,"expected_revision")))stale();
                changed(db.update("INSERT INTO customer_connector_head(tenant_id,project_id,connector_id,current_revision,revision,created_by,created_at,updated_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,NULL,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),project,connector,a.actorId(),text(body,"classification"),MANIFEST));
            }else{var h=metadata(one(heads));if(!project.equals(str(h,"project_id"))||!text(body,"expected_revision").equals(str(h,"revision"))||!text(body,"classification").equals(str(h,"classification")))stale();}
            changed(db.update("INSERT INTO customer_connector_definition(tenant_id,project_id,connector_id,config_revision,definition_json,definition_sha256,state,revision,created_by,created_at,updated_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,'DRAFT',1,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),project,connector,revision,canonical,digest,a.actorId(),text(body,"classification"),MANIFEST));
            changed(db.update("UPDATE customer_connector_head SET current_revision=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND connector_id=? AND revision=?",revision,tenant(a),connector,text(body,"expected_revision")));
            changed(db.update("INSERT INTO customer_connector_cursor(tenant_id,project_id,config_revision,cursor_json,cursor_sha256,revision,created_by,created_at,updated_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,'null',?,0,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),project,revision,hash(json.tree(null)),a.actorId(),text(body,"classification"),MANIFEST));
            if(profile!=null)profiles.save(a,row(a,revision),profile);
            audit(a,project,"connector.configuration_created",revision,body,text(body,"classification"));return view(a,revision);
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String revision){return access.execute(subject,a->checked(()->{context(a,context);return view(a,revision);}));}
    public JsonNode lookup(ApplicationSessionIdentity.Verified subject,String context,String kind,String key){
        if(!Set.of("CREATE","DECIDE","VERIFY","SYNC").contains(kind))bad();json.headers(context,key);
        return access.execute(subject,a->checked(()->{
            context(a,context);manage(a,"connector.read");var result=json.object().put("schema_version","1.0.0").put("kind",kind).put("state","NOT_FOUND");result.putNull("record").putNull("configuration");
            if("CREATE".equals(kind)){
                String revision=id(tenant(a),"CONFIG",a.actorId(),key);var rows=db.queryForList("SELECT * FROM customer_connector_definition WHERE tenant_id=? AND config_revision=? AND created_by=?",tenant(a),revision,a.actorId());if(rows.isEmpty())return result;
                var row=metadata(one(rows));var profile=profiles.read(a,row);var original=json.object().put("schema_version","1.0.0");original.set("definition",stored(row));original.set("profile",json.tree(profile==null?null:profile.required("profile")));var record=json.object();record.set("request",original);result.set("record",record);result.set("configuration",view(a,revision));
            }else if("DECIDE".equals(kind)){
                String decision=id(tenant(a),"DECISION",a.actorId(),key);var rows=db.queryForList("SELECT * FROM customer_connector_decision WHERE tenant_id=? AND decision_id=? AND actor_id=?",tenant(a),decision,a.actorId());if(rows.isEmpty())return result;
                var row=metadata(one(rows));var definition=row(a,str(row,"config_revision"));var profile=profiles.read(a,definition);var original=JSON.readTree(str(row,"request_json"));if(!profiles.decisionHash(original,profile).equals(str(row,"request_sha256")))bad();var record=json.object().put("decision_id",decision).put("decision",str(row,"decision")).put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(row.get("created_at"))));record.set("request",original);record.set("profile_sha256",profile==null?json.tree(null):profile.required("profile_sha256"));result.set("record",record);result.set("configuration",view(a,str(row,"config_revision")));
            }else{
                String run=id(tenant(a),kind,a.actorId(),key);var rows=db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND run_id=? AND kind=? AND created_by=?",tenant(a),run,kind,a.actorId());if(rows.isEmpty())return result;
                var row=metadata(one(rows));var original=JSON.readTree(str(row,"request_json"));if(!hash(original).equals(str(row,"request_sha256")))bad();var record=json.object();record.set("request",original);record.set("run",runView(a,run));result.set("record",record);result.set("configuration",view(a,str(row,"config_revision")));
            }
            return result.put("state","FOUND");
        }));
    }
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String revision,String key,byte[] bytes){
        return decide(subject,context,revision,key,bytes,null);
    }
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String revision,String key,byte[] bytes,String profileHash){
        return access.execute(subject,a->checked(()->{
            context(a,context);json.headers(context,key);var body=parse(bytes);closed(body,"decision","definition_sha256","expected_revision","reason");
            String decision=text(body,"decision"),reason=text(body,"reason");bounded(reason,4096);if(!Set.of("APPROVED","REVOKED").contains(decision))bad();
            var row=row(a,revision);var grant=manage(a,"connector.approve");if(!text(body,"definition_sha256").equals(str(row,"definition_sha256")))bad();
            var profile=profiles.approval(a,row,profileHash);
            String decisionId=id(tenant(a),"DECISION",a.actorId(),key),requestHash=profiles.decisionHash(body,profile);
            var existing=db.queryForList("SELECT * FROM customer_connector_decision WHERE tenant_id=? AND decision_id=?",tenant(a),decisionId);
            if(!existing.isEmpty()){var prior=metadata(one(existing));if(!revision.equals(str(prior,"config_revision"))||!requestHash.equals(str(prior,"request_sha256")))bad();return view(a,revision);}
            if(!text(body,"expected_revision").equals(str(row,"revision")))stale();
            if("APPROVED".equals(decision)){
                if(profile!=null&&!profiles.ownerActive(a,profile))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
                currentHead(a,row);if(!"DRAFT".equals(str(row,"state")))stale();var bodyDefinition=stored(row);
                if(!missing(bodyDefinition).isEmpty())throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                var credentialDirectory=credentials.getIfAvailable();if(credentialDirectory==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                credentialDirectory.requireReference(tenant(a),text(bodyDefinition,"credential_reference_id"),text(bodyDefinition,"credential_revision"));
                mappings(bodyDefinition);if(!permissions.databaseTime().toInstant(ZoneOffset.UTC).isBefore(Instant.parse(text(bodyDefinition,"expires_at"))))stale();
            }else if(!"APPROVED".equals(str(row,"state")))stale();
            changed(db.update("INSERT INTO customer_connector_decision(tenant_id,project_id,config_revision,decision_id,decision,definition_sha256,request_json,request_sha256,actor_id,identity_mode,grant_id,grant_revision,created_by,created_at,updated_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),str(row,"project_id"),revision,decisionId,decision,str(row,"definition_sha256"),json.canonical(body),requestHash,a.actorId(),a.bindingView().mode(),grant.grantId(),grant.revision(),a.actorId(),str(row,"classification"),MANIFEST));
            changed(db.update("UPDATE customer_connector_definition SET state=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND config_revision=? AND revision=?",decision,tenant(a),revision,text(body,"expected_revision")));
            audit(a,str(row,"project_id"),"connector."+decision.toLowerCase(Locale.ROOT),revision,body,str(row,"classification"));return view(a,revision);
        }));
    }
    JsonNode view(EnterpriseAccess.Admission a,String revision)throws Exception{
        manage(a,"connector.read");var r=row(a,revision);var b=stored(r);var result=json.object().put("schema_version","1.0.0").put("config_revision",revision)
            .put("definition_sha256",str(r,"definition_sha256")).put("approval_state",str(r,"state")).put("revision",str(r,"revision")).put("connected",false);
        result.set("definition",b);var profile=profiles.read(a,r);result.set("business_profile",json.tree(profile));var blockers=result.putArray("blockers");missing(b).forEach(blockers::add);
        if(profile!=null&&!profiles.ownerActive(a,profile))blockers.add("BUSINESS_PROFILE_OWNER_INACTIVE");
        if(!"APPROVED".equals(str(r,"state")))blockers.add("CONFIGURATION_NOT_APPROVED");
        if(missing(b).isEmpty()&&adapter(b)==null)blockers.add("CUSTOMER_PROTOCOL_ADAPTER_MISSING");
        if(credentials.getIfAvailable()==null)blockers.add("APPROVED_CREDENTIAL_DIRECTORY_MISSING");
        var cursor=cursor(a,revision);var cursorValue=JSON.readTree(str(cursor,"cursor_json"));if(!hash(cursorValue).equals(str(cursor,"cursor_sha256")))bad();
        result.put("cursor_revision",str(cursor,"revision"));result.set("cursor",cursorValue);
        if(blockers.isEmpty()){
            try{approved(a,r);}catch(TenantAccessException unavailable){blockers.add("CURRENT_APPROVAL_OR_MAPPING_NOT_VALID");}
        }
        result.put("state",blockers.isEmpty()?"READY_FOR_VERIFICATION":"BLOCKED");
        var verification=db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND config_revision=? AND kind='VERIFY' ORDER BY created_at DESC,run_id DESC LIMIT 1",tenant(a),revision);
        if(!verification.isEmpty()){
            var actual=metadata(one(verification));var observed=json.object().put("operation_id",str(actual,"run_id")).put("state",str(actual,"state"))
                .put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(actual.get("created_at")))).put("updated_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(actual.get("updated_at"))));
            if(actual.get("result_json")!=null){var receipt=JSON.readTree(str(actual,"result_json"));if(!hash(receipt).equals(str(actual,"result_sha256")))bad();observed.put("result_sha256",str(actual,"result_sha256"));
                if("VERIFIED".equals(str(actual,"state"))){observed.set("actual_scope",receipt.required("actualScope"));observed.set("external_account_id",receipt.required("externalAccountId"));}}
            result.set("last_verification",observed);
            if(blockers.isEmpty()&&"VERIFIED".equals(str(actual,"state"))){
                try{var pins=JSON.readTree(str(actual,"execution_request"));if(!hash(pins).equals(str(actual,"execution_sha256")))bad();credential(JSON.treeToValue(pins,CustomerConnectorAdapter.Request.class));result.put("state","VERIFIED").put("connected",true);}
                catch(TenantAccessException unavailable){blockers.add("CURRENT_CREDENTIAL_BINDING_NOT_VALID");result.put("state","BLOCKED");}
            }
        }
        var sync=db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND config_revision=? AND kind='SYNC' ORDER BY created_at DESC,run_id DESC LIMIT 1",tenant(a),revision);
        if(!sync.isEmpty()){var actual=metadata(one(sync));result.set("last_sync",json.object().put("operation_id",str(actual,"run_id")).put("state",str(actual,"state"))
            .put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(actual.get("created_at")))).put("updated_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(actual.get("updated_at")))));}
        result.put("connection_status_note","Connectivity is the last actual verification observation, not a claim of continuous availability.");
        return result;
    }
    public JsonNode run(ApplicationSessionIdentity.Verified subject,String context,String revision,String key,byte[] bytes,String kind,boolean recoveryOnly)throws Exception{
        if(!Set.of("VERIFY","SYNC").contains(kind))bad();json.headers(context,key);var input=parse(bytes);
        if("VERIFY".equals(kind))closed(input,"definition_sha256");else closed(input,"definition_sha256","expected_cursor_revision","maximum_items");
        WireValues.hash(text(input,"definition_sha256"));if("SYNC".equals(kind)){if(!text(input,"expected_cursor_revision").matches("0|[1-9][0-9]{0,18}"))bad();var n=input.required("maximum_items");if(!n.isIntegralNumber()||!n.canConvertToInt()||n.intValue()<1||n.intValue()>MAX_ITEMS)bad();}
        var work=access.execute(subject,a->checked(()->start(a,context,revision,key,input,kind)));
        if(work.blocked()||work.result()!=null)return work.result();
        var adapter=adapter(work.request().definition());if(adapter==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        CustomerConnectorAdapter.Current current=()->access.execute(subject,a->checked(()->{context(a,context);unchanged(a,work);return null;}));
        try{
            current.require();JsonNode original;
            if("VERIFY".equals(kind)){
                var verification=adapter.verify(work.request(),recoveryOnly||work.replay(),current);exchanges(verification.exchanges());
                if(verification.verified()){
                    account(work.request().definition(),verification.externalAccountId());
                    if(!verification.actualScope().equals(work.request().definition().required("ownership_scope").required("external_scope"))
                        ||verification.exchanges().stream().anyMatch(e->!e.complete()||e.responseStatus()<200||e.responseStatus()>299))deny();
                }
                original=json.tree(verification);
            }else{var page=adapter.readPage(work.request(),recoveryOnly||work.replay(),current);page(work.request(),page);original=json.tree(page);}
            current.require();return access.execute(subject,a->checked(()->finish(a,context,work,original)));
        }catch(Exception failure){
            try{access.execute(subject,a->{context(a,context);db.update("UPDATE customer_connector_run SET state='UNKNOWN',failure='NO_COMMITTED_COMPLETE_RESULT',updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE tenant_id=? AND run_id=? AND state='STARTED' AND result_json IS NULL",tenant(a),work.request().operationId());return null;});}catch(Exception stale){failure.addSuppressed(stale);}throw failure;
        }
    }
    private record Work(CustomerConnectorAdapter.Request request,String kind,String cursorRevision,boolean blocked,boolean replay,JsonNode result){}
    private Work start(EnterpriseAccess.Admission a,String context,String revision,String key,JsonNode input,String kind)throws Exception{
        context(a,context);manage(a,"SYNC".equals(kind)?"connector.sync":"connector.configure");var r=row(a,revision);var definition=stored(r);
        project(a,str(r,"project_id"),"source.write");if(!text(input,"definition_sha256").equals(str(r,"definition_sha256")))stale();
        String operation=id(tenant(a),kind,a.actorId(),key),requestHash=hash(input);var prior=db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(a),operation);
        var cursor=cursor(a,revision);boolean replay=!prior.isEmpty();
        String cursorRevision="SYNC".equals(kind)?text(input,"expected_cursor_revision"):str(cursor,"revision");
        JsonNode cursorValue=JSON.readTree(str(cursor,"cursor_json"));if(!hash(cursorValue).equals(str(cursor,"cursor_sha256")))bad();
        if(replay){var saved=metadata(one(prior));if(!revision.equals(str(saved,"config_revision"))||!kind.equals(str(saved,"kind"))||!requestHash.equals(str(saved,"request_sha256"))||!a.actorId().equals(str(saved,"created_by")))bad();
            var originalRequest=JSON.readTree(str(saved,"execution_request"));if(!hash(originalRequest).equals(str(saved,"execution_sha256")))bad();
            var request=JSON.treeToValue(originalRequest,CustomerConnectorAdapter.Request.class);
            if(saved.get("result_json")!=null){if(!"BLOCKED".equals(str(saved,"state")))approved(a,r);return new Work(request,kind,str(saved,"cursor_revision"),"BLOCKED".equals(str(saved,"state")),true,runView(a,operation));}
            approved(a,r);return new Work(request,kind,str(saved,"cursor_revision"),false,true,null);
        }
        var blockers=new ArrayList<>(missing(definition));if(!"APPROVED".equals(str(r,"state")))blockers.add("CONFIGURATION_NOT_APPROVED");
        if(blockers.isEmpty()&&adapter(definition)==null)blockers.add("CUSTOMER_PROTOCOL_ADAPTER_MISSING");
        if(credentials.getIfAvailable()==null)blockers.add("APPROVED_CREDENTIAL_DIRECTORY_MISSING");
        if(blockers.isEmpty())approved(a,r);
        if("SYNC".equals(kind)&&blockers.isEmpty()){
            if(!cursorRevision.equals(str(cursor,"revision")))stale();
            if(!db.queryForList("SELECT run_id FROM customer_connector_run WHERE tenant_id=? AND config_revision=? AND kind='SYNC' AND state IN ('STARTED','UNKNOWN') LIMIT 1",tenant(a),revision).isEmpty())throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
            var verified=db.queryForList("SELECT state FROM customer_connector_run WHERE tenant_id=? AND config_revision=? AND kind='VERIFY' ORDER BY created_at DESC,run_id DESC LIMIT 1",tenant(a),revision);
            if(verified.isEmpty()||!"VERIFIED".equals(str(one(verified),"state")))blockers.add("ACTUAL_VERIFICATION_REQUIRED");
        }
        var request=new CustomerConnectorAdapter.Request(tenant(a),str(r,"project_id"),operation,revision,str(r,"definition_sha256"),definition,cursorValue,"SYNC".equals(kind)?input.required("maximum_items").intValue():1);
        if(blockers.isEmpty()){
            try{credential(request);}catch(TenantAccessException unavailable){blockers.add("APPROVED_CREDENTIAL_BINDING_UNAVAILABLE");}
        }
        JsonNode blocked=null;if(!blockers.isEmpty()){var b=json.object().put("state","BLOCKED").put("connected",false);b.set("blockers",json.tree(blockers));blocked=b;}
        changed(db.update("INSERT INTO customer_connector_run(tenant_id,project_id,config_revision,run_id,kind,state,request_json,request_sha256,execution_request,execution_sha256,cursor_revision,result_json,result_sha256,created_by,created_at,updated_at,classification,revision,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,1,'1.0.0',?)",tenant(a),request.projectId(),revision,operation,kind,blocked==null?"STARTED":"BLOCKED",json.canonical(input),requestHash,json.canonicalObject(request),hash(request),cursorRevision,blocked==null?null:json.canonical(blocked),blocked==null?null:hash(blocked),a.actorId(),str(r,"classification"),MANIFEST));
        audit(a,request.projectId(),"connector."+kind.toLowerCase(Locale.ROOT)+"_requested",operation,input,str(r,"classification"));
        return new Work(request,kind,cursorRevision,blocked!=null,false,blocked==null?null:runView(a,operation));
    }
    private void unchanged(EnterpriseAccess.Admission a,Work work)throws Exception{
        credential(work.request());var r=row(a,work.request().configurationRevision());approved(a,r);manage(a,"SYNC".equals(work.kind())?"connector.sync":"connector.configure");project(a,work.request().projectId(),"source.write");
        if(!str(r,"definition_sha256").equals(work.request().definitionSha256()))stale();var operation=runRow(a,work.request().operationId());
        if(!a.actorId().equals(str(operation,"created_by"))||!hash(work.request()).equals(str(operation,"execution_sha256")))deny();
        if("SYNC".equals(work.kind())&&!work.cursorRevision().equals(str(cursor(a,work.request().configurationRevision()),"revision")))stale();
    }
    private JsonNode finish(EnterpriseAccess.Admission a,String context,Work work,JsonNode original)throws Exception{
        context(a,context);var existing=runRow(a,work.request().operationId());if(existing.get("result_json")!=null){approved(a,row(a,work.request().configurationRevision()));if(!hash(original).equals(str(existing,"result_sha256")))bad();return runView(a,work.request().operationId());}
        unchanged(a,work);String state;
        if("VERIFY".equals(work.kind()))state=original.required("verified").booleanValue()?"VERIFIED":"VERIFICATION_FAILED";
        else{
            var page=JSON.treeToValue(original,CustomerConnectorAdapter.Page.class);page(work.request(),page);boolean conflict=false;
            for(var item:page.items())if(capture(a,work,item))conflict=true;
            state=conflict?"CONFLICT":"CAPTURED_PENDING_GOVERNANCE";
            if(!conflict)changed(db.update("UPDATE customer_connector_cursor SET cursor_json=?,cursor_sha256=?,revision=revision+1,last_run_id=?,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND config_revision=? AND revision=?",new String(CustomerConnectorProtocol.canonicalBytes(page.nextCursor()),StandardCharsets.UTF_8),hash(page.nextCursor()),work.request().operationId(),tenant(a),work.request().configurationRevision(),work.cursorRevision()));
        }
        changed(db.update("UPDATE customer_connector_run SET state=?,result_json=?,result_sha256=?,failure=NULL,updated_at=UTC_TIMESTAMP(6),revision=revision+1 WHERE tenant_id=? AND run_id=? AND result_json IS NULL AND state IN ('STARTED','UNKNOWN')",state,json.canonical(original),hash(original),tenant(a),work.request().operationId()));
        audit(a,work.request().projectId(),"connector."+state.toLowerCase(Locale.ROOT),work.request().operationId(),json.object().put("result_sha256",hash(original)),text(work.request().definition(),"classification"));return runView(a,work.request().operationId());
    }
    private boolean capture(EnterpriseAccess.Admission a,Work work,CustomerConnectorAdapter.Item item)throws Exception{
        String external=hash(item.externalId()),version=hash(item.externalVersion()),content=WireValues.sha256(item.original());
        var old=db.queryForList("SELECT * FROM customer_connector_item WHERE tenant_id=? AND config_revision=? AND external_id_sha256=? AND external_version_sha256=? FOR UPDATE",tenant(a),work.request().configurationRevision(),external,version);
        if(item.revoked())db.update("UPDATE customer_connector_item SET state='REVOKED',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND config_revision=? AND external_id_sha256=? AND state<>'REVOKED'",tenant(a),work.request().configurationRevision(),external);
        if(!old.isEmpty()){
            var prior=metadata(one(old));if(item.revoked())return false;
            // Reusing the same external version with different bytes is a conflict, never an overwrite.
            if(!content.equals(str(prior,"original_sha256"))||!sameExternalRevision(storedItem(prior),item)||"REVOKED".equals(str(prior,"state")))return true;
            return false;
        }
        String itemId=id(tenant(a),"ITEM",work.request().configurationRevision(),external+":"+version);
        changed(db.update("INSERT INTO customer_connector_item(tenant_id,project_id,config_revision,run_id,item_id,external_id_sha256,external_version_sha256,item_json,item_sha256,original_sha256,state,revision,created_by,created_at,updated_at,classification,schema_version,schema_manifest_sha256) VALUES(?,?,?,?,?,?,?,?,?,?,?,1,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,'1.0.0',?)",tenant(a),work.request().projectId(),work.request().configurationRevision(),work.request().operationId(),itemId,external,version,json.canonicalObject(item),hash(item),content,item.revoked()?"REVOKED":"PENDING_RIGHTS",a.actorId(),text(work.request().definition(),"classification"),MANIFEST));return false;
    }
    public JsonNode runRead(ApplicationSessionIdentity.Verified subject,String context,String run){return access.execute(subject,a->checked(()->{context(a,context);manage(a,"connector.read");return runView(a,run);}));}
    public JsonNode history(ApplicationSessionIdentity.Verified subject,String context,String revision,String kind,String after){
        if(!Set.of("DECIDE","VERIFY","SYNC").contains(kind))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(after!=null)uuid(after);
        return access.execute(subject,a->checked(()->{
            context(a,context);manage(a,"connector.read");var configuration=row(a,revision);project(a,str(configuration,"project_id"),"project.read");var profile=profiles.read(a,configuration);
            var rows="DECIDE".equals(kind)?db.queryForList("SELECT * FROM customer_connector_decision WHERE tenant_id=? AND config_revision=? AND (? IS NULL OR decision_id>?) ORDER BY decision_id LIMIT 26",tenant(a),revision,after,after)
                :db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND config_revision=? AND kind=? AND (? IS NULL OR run_id>?) ORDER BY run_id LIMIT 26",tenant(a),revision,kind,after,after);
            var result=json.object().put("schema_version","1.0.0").put("config_revision",revision).put("kind",kind).put("complete",rows.size()<=25);var items=result.putArray("items");
            for(var source:rows.subList(0,Math.min(25,rows.size()))){var r=metadata(source);var original=JSON.readTree(str(r,"request_json"));boolean decision="DECIDE".equals(kind);if(!(decision?profiles.decisionHash(original,profile):hash(original)).equals(str(r,"request_sha256")))bad();
                var item=json.object().put("operation_id",str(r,decision?"decision_id":"run_id")).put("state",str(r,decision?"decision":"state")).put("actor_id",str(r,decision?"actor_id":"created_by"))
                    .put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(r.get("created_at")))).put("updated_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(r.get("updated_at"))));item.set("request",original);item.set("run",decision?json.tree(null):runView(a,str(r,"run_id")));items.add(item);}
            if(rows.size()>25)result.put("next_after",items.get(24).required("operation_id").asText());else result.putNull("next_after");return result;
        }));
    }
    private JsonNode runView(EnterpriseAccess.Admission a,String id)throws Exception{
        var r=runRow(a,id);project(a,str(r,"project_id"),"source.read");var out=json.object().put("schema_version","1.0.0").put("operation_id",id).put("config_revision",str(r,"config_revision")).put("state",str(r,"state")).put("formal_source_approval",false);
        out.set("failure",json.tree(r.get("failure")));out.set("request",JSON.readTree(str(r,"request_json")));out.set("result_sha256",json.tree(r.get("result_sha256")));
        if(r.get("result_json")!=null){var result=JSON.readTree(str(r,"result_json"));if(!hash(result).equals(str(r,"result_sha256")))bad();
            boolean revoked="SYNC".equals(str(r,"kind"))&&!db.queryForList("SELECT item_id FROM customer_connector_item WHERE tenant_id=? AND config_revision=? AND state='REVOKED' LIMIT 1",tenant(a),str(r,"config_revision")).isEmpty();
            if(revoked)out.put("originals_withheld","OWNERSHIP_REVOCATION_REQUIRES_FILTERED_REVIEW");else out.set("original_result",result);
        }
        var items=out.putArray("items");for(var row:db.queryForList("SELECT item_id,state,original_sha256,upload_id FROM customer_connector_item WHERE tenant_id=? AND run_id=? ORDER BY item_id LIMIT 26",tenant(a),id))items.add(json.tree(row));
        return out;
    }
    /** Import is a normal governed upload, not a source-rights approval. No provenance,
     * license, owner or retention declaration is fabricated from a successful API call. */
    public JsonNode ingest(ApplicationSessionIdentity.Verified subject,String context,String key,String itemId,byte[] declaration)throws Exception{
        json.headers(context,key);var initial=access.execute(subject,a->checked(()->{
            context(a,context);var item=itemRow(a,itemId);approved(a,row(a,str(item,"config_revision")));manage(a,"connector.sync");project(a,str(item,"project_id"),"source.write");
            if("REVOKED".equals(str(item,"state")))deny();var capture=storedItem(item);var body=parse(declaration);
            if(!Set.of("application/pdf","text/plain","text/html").contains(capture.mediaType()))throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
            if(!str(item,"classification").equals(text(body,"classification"))||!str(item,"project_id").equals(text(body,"project_id"))||!"PROJECT".equals(text(body,"ownership"))
                ||!"NEW_SOURCE".equals(text(body,"version_intent"))||!capture.mediaType().equals(text(body,"media_type"))
                ||!str(item,"original_sha256").equals(text(body,"sha256"))||!Integer.toString(capture.original().length).equals(text(body,"size_bytes")))bad();
            var provenance=body.required("provenance");if(!capture.originalUrl().equals(text(provenance,"original_url"))||!capture.originalUrl().equals(text(provenance,"final_url"))
                ||!capture.acquiredAt().equals(text(provenance,"acquired_at"))||!str(item,"created_by").equals(text(provenance,"acquired_by"))||!"CUSTOMER_CONNECTOR".equals(text(provenance,"acquisition_method")))bad();
            String digest=hash(body);if(item.get("import_sha256")!=null&&(!digest.equals(str(item,"import_sha256"))||!a.actorId().equals(str(item,"import_actor_id"))))bad();
            if(item.get("import_sha256")==null)changed(db.update("UPDATE customer_connector_item SET state='IMPORTING',import_request=?,import_sha256=?,import_actor_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND item_id=? AND import_sha256 IS NULL AND state='PENDING_RIGHTS'",json.canonical(body),digest,a.actorId(),tenant(a),itemId));
            return capture;
        }));
        String createKey=id(subject.user().user_id(),"CONNECTOR_UPLOAD",itemId,"CREATE");
        var created=uploads.create(subject,context,createKey,declaration);String upload=text(created.required("data"),"upload_id");
        access.execute(subject,a->checked(()->{
            context(a,context);var item=itemRow(a,itemId);approved(a,row(a,str(item,"config_revision")));project(a,str(item,"project_id"),"source.write");if("REVOKED".equals(str(item,"state")))deny();
            if(item.get("upload_id")!=null&&!upload.equals(str(item,"upload_id")))bad();
            if(item.get("upload_id")==null)changed(db.update("UPDATE customer_connector_item SET upload_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND item_id=? AND upload_id IS NULL",upload,tenant(a),itemId));return null;
        }));
        var registration=uploads.read(subject,context,upload,0,100);boolean received=false;
        for(var part:registration.required("data").required("parts"))if(part.required("part_no").intValue()==1&&"STORED".equals(text(part,"state"))&&WireValues.sha256(initial.original()).equals(text(part,"sha256")))received=true;
        if(!received)transport.receiveCapturedSource(subject,context,id(subject.user().user_id(),"CONNECTOR_UPLOAD",itemId,"PART"),upload,initial.original());
        var latest=uploads.read(subject,context,upload,0,100);var completion=json.object().put("schema_version","1.0.0").put("expected_revision",text(latest,"resource_revision"))
            .put("version_intent","NEW_SOURCE").put("size_bytes",Integer.toString(initial.original().length)).put("sha256",WireValues.sha256(initial.original()));
        completion.putArray("parts").addObject().put("part_no",1).put("size_bytes",Integer.toString(initial.original().length)).put("sha256",WireValues.sha256(initial.original()));
        var frozen=access.execute(subject,a->checked(()->{
            context(a,context);var item=itemRow(a,itemId);approved(a,row(a,str(item,"config_revision")));project(a,str(item,"project_id"),"source.write");if("REVOKED".equals(str(item,"state")))deny();
            if(item.get("completion_request")!=null)return JSON.readTree(str(item,"completion_request"));
            changed(db.update("UPDATE customer_connector_item SET completion_request=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND item_id=? AND completion_request IS NULL",json.canonical(completion),tenant(a),itemId));return (JsonNode)completion;
        }));
        var receipt=uploads.requestCompletion(subject,context,id(subject.user().user_id(),"CONNECTOR_UPLOAD",itemId,"COMPLETE"),upload,ContentReadAuthorization.canonicalBytes(frozen));
        return access.execute(subject,a->checked(()->{
            context(a,context);var item=itemRow(a,itemId);project(a,str(item,"project_id"),"source.read");
            if("IMPORTING".equals(str(item,"state")))changed(db.update("UPDATE customer_connector_item SET state='UPLOAD_PENDING_GOVERNANCE',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND item_id=? AND state='IMPORTING'",tenant(a),itemId));
            // A concurrent actual ownership revocation remains REVOKED; never restore it.
            var out=json.object().put("item_id",itemId).put("upload_id",upload).put("state",str(itemRow(a,itemId),"state")).put("formal_source_approval",false);
            out.set("completion",receipt);out.put("source_lifecycle_url","/api/uploads/"+upload+"/status");return out;
        }));
    }
    private Map<String,Object> itemRow(EnterpriseAccess.Admission a,String id){uuid(id);return metadata(one(db.queryForList("SELECT * FROM customer_connector_item WHERE tenant_id=? AND item_id=? FOR UPDATE",tenant(a),id)));}
    private static boolean sameExternalRevision(CustomerConnectorAdapter.Item a,CustomerConnectorAdapter.Item b){
        return a.externalId().equals(b.externalId())&&a.externalVersion().equals(b.externalVersion())&&a.externalAccountId().equals(b.externalAccountId())
            &&a.ownerProjectId().equals(b.ownerProjectId())&&a.mediaType().equals(b.mediaType())&&a.originalUrl().equals(b.originalUrl())&&a.metadata().equals(b.metadata());
    }
    private CustomerConnectorAdapter.Item storedItem(Map<String,Object> item)throws Exception{
        var original=JSON.readTree(str(item,"item_json"));if(!hash(original).equals(str(item,"item_sha256")))bad();
        var capture=JSON.treeToValue(original,CustomerConnectorAdapter.Item.class);if(!WireValues.sha256(capture.original()).equals(str(item,"original_sha256")))bad();return capture;
    }

    private void credential(CustomerConnectorAdapter.Request request)throws Exception{
        var directory=credentials.getIfAvailable();var implementation=adapter(request.definition());
        if(directory==null||implementation==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        directory.require(request,implementation.credentialTarget(request));
    }
    private CustomerConnectorAdapter adapter(JsonNode definition)throws Exception{
        if(!missing(definition).isEmpty())return null;String specification=hash(definition.required("interface_spec"));
        var found=adapters.orderedStream().filter(a->specification.equals(a.specificationSha256())).toList();if(found.size()>1)bad();return found.isEmpty()?null:found.get(0);
    }
    private JsonNode approved(EnterpriseAccess.Admission a,Map<String,Object> row)throws Exception{
        currentHead(a,row);if(!"APPROVED".equals(str(row,"state")))deny();var body=stored(row);if(!missing(body).isEmpty())deny();mappings(body);
        if(!permissions.databaseTime().toInstant(ZoneOffset.UTC).isBefore(Instant.parse(text(body,"expires_at"))))stale();
        var decision=metadata(one(db.queryForList("SELECT * FROM customer_connector_decision WHERE tenant_id=? AND config_revision=? AND decision='APPROVED' LIMIT 2",tenant(a),str(row,"config_revision"))));
        var profile=profiles.read(a,row);if(profile!=null&&!profiles.ownerActive(a,profile))deny();
        if(!profiles.decisionHash(JSON.readTree(str(decision,"request_json")),profile).equals(str(decision,"request_sha256")))bad();
        if(!str(row,"definition_sha256").equals(str(decision,"definition_sha256")))bad();
        String actor=str(decision,"actor_id");var now=permissions.databaseTime();var member=one(permissions.targetMember(actor));var binding=one(permissions.targetBinding(actor));
        if(!"ACTIVE".equals(member.status())||member.validFrom()==null||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!binding.mode().equals(str(decision,"identity_mode")))deny();
        var scope=new PermissionProtocol.Scope(tenant(a),null,"ENTERPRISE",tenant(a));
        var grant=grants.requireResource(scope,actor,binding.mode(),"connector.approve","CUSTOMER_CONNECTOR",permissions.currentGrant(str(decision,"grant_id")),now);
        if(!grant.revision().toString().equals(str(decision,"grant_revision")))stale();return body;
    }
    private void mappings(JsonNode definition){
        for(var mapping:definition.required("account_mapping")){var member=one(permissions.targetMember(text(mapping,"user_id")));var now=permissions.databaseTime();if(!"ACTIVE".equals(member.status())||member.validFrom()==null||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now))deny();}
    }
    private void currentHead(EnterpriseAccess.Admission a,Map<String,Object> row){var head=metadata(one(db.queryForList("SELECT * FROM customer_connector_head WHERE tenant_id=? AND connector_id=? FOR UPDATE",tenant(a),str(row,"connector_id"))));if(!str(row,"config_revision").equals(str(head,"current_revision"))||!str(row,"project_id").equals(str(head,"project_id")))stale();}
    private JsonNode stored(Map<String,Object> row)throws Exception{var body=parse(str(row,"definition_json").getBytes(StandardCharsets.UTF_8));definition(body);if(!hash(body).equals(str(row,"definition_sha256")))bad();return body;}
    private Map<String,Object> row(EnterpriseAccess.Admission a,String revision){uuid(revision);return metadata(one(db.queryForList("SELECT * FROM customer_connector_definition WHERE tenant_id=? AND config_revision=? FOR UPDATE",tenant(a),revision)));}
    private Map<String,Object> cursor(EnterpriseAccess.Admission a,String revision){return metadata(one(db.queryForList("SELECT * FROM customer_connector_cursor WHERE tenant_id=? AND config_revision=? FOR UPDATE",tenant(a),revision)));}
    private Map<String,Object> runRow(EnterpriseAccess.Admission a,String id){uuid(id);return metadata(one(db.queryForList("SELECT * FROM customer_connector_run WHERE tenant_id=? AND run_id=? FOR UPDATE",tenant(a),id)));}
    private PermissionProtocol.GrantRow manage(EnterpriseAccess.Admission a,String action)throws Exception{return resources.require(a,new PermissionProtocol.Scope(tenant(a),null,"ENTERPRISE",tenant(a)),action,"CUSTOMER_CONNECTOR");}
    private PermissionProtocol.GrantRow project(EnterpriseAccess.Admission a,String project,String action)throws Exception{return resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,"CUSTOMER_CONNECTOR");}
    private void audit(EnterpriseAccess.Admission a,String project,String action,String target,JsonNode body,String classification)throws Exception{
        var payload=json.object().put("schema_version","1.0.0").put("target_id",target).put("context_revision",a.context().context_revision());payload.set("request",body);
        changed(permissions.saveAudit(a.actorId(),project,UUID.randomUUID().toString(),action,json.canonical(payload),UUID.randomUUID().toString(),MANIFEST,classification));
        changed(permissions.saveEvent(a.actorId(),UUID.randomUUID().toString(),target,"1",action,json.canonical(payload)));
    }
    private static Map<String,Object> metadata(Map<String,Object> row){if(!"1.0.0".equals(str(row,"schema_version"))||!MANIFEST.equals(str(row,"schema_manifest_sha256")))bad();return row;}
    private static void context(EnterpriseAccess.Admission a,String context){PermissionSchema.revision(context);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);if(a.formalWorker()||a.simulationWorker()||a.evaluationWorker())deny();}
    private static String tenant(EnterpriseAccess.Admission a){String tenant=a.context().tenant_id();if(!tenant.equals(TenantScope.requireCurrent().tenantId()))deny();return tenant;}
    private static <T>T checked(ThrowingSupplier<T> call){return call.get();}
}

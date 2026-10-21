package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Fixed approved source plus separately recorded human choices. Never mutates a template or rule. */
@Component
public final class TemplateApplicationService {
    private static final String PURPOSE = "TEMPLATE_APPLICATION";
    private final EnterpriseAccess access;
    private final TemplateApplicabilityService applicability;
    private final TemplateApplicabilitySchema schema;
    private final TemplateMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;
    private final PermissionSchema json;
    private final IdentityDirectoryPolicy policy;
    private final JsonMapper reader = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final String manifest;

    public TemplateApplicationService(EnterpriseAccess access, TemplateApplicabilityService applicability,
            TemplateApplicabilitySchema schema, TemplateMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority authority, PermissionSchema json, IdentityDirectoryPolicy policy) throws IOException {
        this.access=access; this.applicability=applicability; this.schema=schema; this.mapper=mapper;
        this.permissions=permissions; this.authority=authority; this.json=json; this.policy=policy;
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/template-application.schema.json"))) {
            manifest=DigestUtils.sha256Hex(in.readAllBytes());
        }
    }

    JsonNode query(Map<String,String[]> parameters) {
        var q=json.object(); parameters.forEach((key,value)->{if(value.length!=1)invalid();q.put(key,value[0]);});
        schema.verifyApplication("FixedQuery",q); return q;
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String template,JsonNode query) {
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,context); var target=target(a,template,query); var values=values(a,template,query);
            var history=TemplateApplicationHistory.load(mapper,values,json,schema,manifest,policy.classification());
            String revision=history.revision(); var decisions=json.tree(history.decisions());
            var data=json.object().put("schema_version","1.0.0").put("project_id",text(query,"project_id"))
                .put("profile_revision",text(query,"profile_revision")).put("profile_sha256",target.profile().sha256())
                .put("baseline_sha256",hash(json.tree(TemplateApplicabilityService.machineView(target.view())))).put("decision_revision",revision)
                .put("professional_approval",false);
            data.set("template",json.tree(target.view())); data.set("decisions",decisions);
            schema.verifyApplication("FixedView",data); return envelope(data,UUID.randomUUID().toString(),revision);
        }).get());
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String key,String template,byte[] body) {
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            context(a,context); var request=reader.readTree(body); schema.verifyApplication("HumanDecisionInput",request);
            ProjectSchema.text(text(request,"reason"),2000); PermissionSchema.revision(text(request,"expected_revision"));
            var target=target(a,template,request); var grant=authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),
                text(request,"project_id"),"PROJECT",text(request,"project_id")),"project.manage",PURPOSE);
            var values=values(a,template,request); values.put("key",key);
            var identity=json.object().put("template_version",template);
            identity.set("request",request); values.put("requestHash",hash(identity)); values.put("reason",text(request,"reason"));
            var prior=mapper.applicationOperation(values); if(prior.size()>1)bad();
            if(!prior.isEmpty()) {
                var operation=prior.get(0);
                if(!operation.requestSha256().equals(values.get("requestHash")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                if(!operation.state().equals("COMPLETED")||!Objects.equals(operation.httpStatus(),200)
                    ||!operation.targetId().equals(template)||!operation.schemaVersion().equals("1.0.0")
                    ||!TemplateApplicabilitySchema.applicationManifestMatches(operation.schemaManifestSha256(),manifest))bad();
                var original=reader.readTree(operation.responseRef()); var d=verifyReceipt(original,template,request);
                if(!d.required("request").equals(request)||!d.required("operation_id").asText().equals(operation.operationId())
                    ||!d.required("actor_id").asText().equals(a.actorId()))bad();
                values.put("operationManifest",operation.schemaManifestSha256());
                values.put("operationId",operation.operationId()); verifySaved(values,original); return original;
            }
            validateTarget(template,request,TemplateApplicabilityService.machineView(target.view()));
            if(!hash(json.tree(TemplateApplicabilityService.machineView(target.view()))).equals(text(request,"baseline_sha256")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var history=TemplateApplicationHistory.load(mapper,values,json,schema,manifest,policy.classification());
            String revision=history.revision();
            if(!revision.equals(text(request,"expected_revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            if(revision.equals("0"))one(mapper.insertApplicationHead(values));
            String resulting=new BigInteger(revision).add(BigInteger.ONE).toString(); PermissionSchema.revision(resulting);
            values.put("expected",revision);values.put("resulting",resulting);
            if(mapper.advanceApplicationHead(values)!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            String operation=UUID.randomUUID().toString(),decision=UUID.randomUUID().toString(),requestId=UUID.randomUUID().toString();
            var d=json.object().put("decision_id",decision).put("operation_id",operation).put("template_version",template)
                .put("resulting_revision",resulting).put("actor_id",a.actorId()).put("created_at",DatabaseTimestamp.format((java.time.LocalDateTime)values.get("now")))
                .put("authority_grant_id",grant.grantId()).put("authority_revision",grant.revision().toString());
            d.set("request",request); d.set("baseline",json.tree(TemplateApplicabilityService.machineView(target.view())));
            var data=json.object().put("schema_version","1.0.0").put("operation_id",operation).put("professional_approval",false);data.set("decision",d);
            schema.verifyApplication("HumanDecisionReceipt",data);var response=envelope(data,requestId,resulting);
            values.put("operationId",operation);values.put("decisionId",decision);values.put("response",json.canonical(response));values.put("responseHash",hash(response));
            one(mapper.saveApplicationOperation(values));one(mapper.saveApplicationDecision(values));
            one(permissions.saveAudit(a.actorId(),text(request,"project_id"),UUID.randomUUID().toString(),"template.application_decided",
                json.canonical(data),requestId,manifest,policy.classification()));
            one(permissions.saveEvent(a.actorId(),UUID.randomUUID().toString(),template,resulting,"template.application_decided",json.canonical(data)));
            verifySaved(values,response);
            if(!TemplateApplicabilityService.machineView(target.view()).equals(TemplateApplicabilityService.machineView(target(a,template,request).view()))||!grant.equals(authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),
                text(request,"project_id"),"PROJECT",text(request,"project_id")),"project.manage",PURPOSE)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            return response;
        }).get());
    }
    private void verifySaved(Map<String,Object> values,JsonNode response)throws IOException {
        values.put("responseHash",hash(response));values.put("requestId",text(response,"request_id"));
        if(!reader.readTree(single(mapper.applicationDecision(values))).equals(response)
            ||!reader.readTree(single(mapper.applicationAudit(values))).equals(response.required("data"))
            ||!reader.readTree(single(mapper.applicationEvent(values))).equals(response.required("data")))bad();
    }
    private JsonNode verifyReceipt(JsonNode receipt,String template,JsonNode query)throws IOException {
        schema.verifyEnvelope(receipt);var data=receipt.required("data");schema.verifyApplication("HumanDecisionReceipt",data);var d=data.required("decision");var request=d.required("request");
        if(!text(d,"template_version").equals(template)||!text(request,"project_id").equals(text(query,"project_id"))
            ||!text(request,"profile_revision").equals(text(query,"profile_revision"))||!text(request,"document_type").equals(text(query,"document_type"))
            ||!text(data,"operation_id").equals(text(d,"operation_id"))||!text(receipt,"revision").equals(text(d,"resulting_revision"))
            ||!new BigInteger(text(request,"expected_revision")).add(BigInteger.ONE).toString().equals(text(d,"resulting_revision"))
            ||!hash(d.required("baseline")).equals(text(request,"baseline_sha256"))
            ||!text(d.required("baseline").required("template"),"content_sha256").equals(text(request,"template_sha256")))bad();
        return d;
    }
    static void validateTarget(String template,JsonNode request,TemplateApplicabilityProtocol.TemplateView view) {
        if(!view.template().content_sha256().equals(text(request,"template_sha256")))throw new TenantAccessException(ErrorCode.RESULT_VERSION_MISMATCH);
        String id=text(request,"target_id");
        String machine;
        if(text(request,"scope").equals("TEMPLATE")) {if(!template.equals(id))invalid();machine=view.applicability();}
        else {
            var node=view.template().content().nodes().stream().filter(n->n.node_id().equals(id)).findFirst().orElseThrow(()->new SchemaRejection(ErrorCode.SCHEMA_INVALID));
            // Human application decisions cannot modify the approved template's required-node contract.
            if(node.required()!=request.required("required").booleanValue())invalid();
            machine=view.machine_conditions().stream().filter(c->c.scope().equals("NODE")&&c.target_id().equals(id)).findFirst().orElseThrow().status();
            if(node.required()&&text(request,"applicability").equals("NOT_APPLICABLE"))invalid();
        }
        if(!machine.equals("UNKNOWN")&&!machine.equals(text(request,"applicability")))invalid();
    }
    private TemplateApplicabilityService.Target target(EnterpriseAccess.Admission a,String template,JsonNode q)throws IOException {
        return applicability.inspectTarget(a,text(q,"project_id"),text(q,"profile_revision"),text(q,"document_type"),template,PURPOSE);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission a,String template,JsonNode q) {
        var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("projectId",text(q,"project_id"));v.put("profileId",text(q,"profile_revision"));
        v.put("templateId",template);v.put("operationManifest",manifest);v.put("classification",policy.classification());v.put("now",Objects.requireNonNull(permissions.databaseTime()));return v;
    }
    private JsonNode envelope(JsonNode data,String request,String revision){var result=json.tree(new InterfaceProtocol.Envelope("1.0.0",request,revision,data));schema.verifyEnvelope(result);return result;}
    private String hash(JsonNode value)throws IOException{return DigestUtils.sha256Hex(json.canonical(value));}
    private static String text(JsonNode node,String key){return node.required(key).textValue();}
    private static void context(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static <T>T single(List<T> rows){if(rows.size()!=1)bad();return rows.get(0);}
    private static void one(int n){if(n!=1)bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}

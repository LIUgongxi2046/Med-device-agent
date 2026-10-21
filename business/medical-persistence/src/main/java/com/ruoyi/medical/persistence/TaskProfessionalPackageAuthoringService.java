package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.TaskProfessionalPackageAuthoringSchema.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.function.ThrowingSupplier;

/** User-session orchestration. Network I/O never runs in a tenant transaction. */
@Component
@ConditionalOnProperty(prefix="medical.task-professional-package-authoring",name="enabled",havingValue="true")
public final class TaskProfessionalPackageAuthoringService {
    private final EnterpriseAccess access;private final TaskProfessionalPackageAuthoringSchema schema;private final TaskProfessionalPackageAuthoringAuthority authority;
    private final TaskProfessionalPackageAuthoringLedger ledger;private final TaskProfessionalPackageAuthoringRegistration registrations;private final TaskProfessionalPackageAuthoringTransport transport;
    private final FormalEvaluationContextPackages packages;private final PermissionSchema json;
    @org.springframework.beans.factory.annotation.Autowired
    public TaskProfessionalPackageAuthoringService(EnterpriseAccess access,TaskProfessionalPackageAuthoringSchema schema,TaskProfessionalPackageAuthoringAuthority authority,TaskProfessionalPackageAuthoringLedger ledger,TaskProfessionalPackageAuthoringRegistration registrations,TaskProfessionalPackageAuthoringTransport transport,
            ArtifactContentService content,SourceUploadTransport objects,ArtifactRegistryMapper artifacts,SourceReadMapper sources,DependencyLineage lineage,AgentPublicationDependencies dependencies,EvaluationReferences profiles,EvaluationDependencyPolicy policy,PermissionSchema json){this.access=access;this.schema=schema;this.authority=authority;this.ledger=ledger;this.registrations=registrations;this.transport=transport;this.json=json;packages=new FormalEvaluationContextPackages(access,content,objects,artifacts,sources,lineage,dependencies,profiles,policy,json);}
    private TaskProfessionalPackageAuthoringService(TaskProfessionalPackageAuthoringService original,TaskProfessionalPackageAuthoringSchema selected){access=original.access;schema=selected;authority=original.authority.forSchema(selected);ledger=original.ledger.forSchema(selected);registrations=original.registrations.forSchema(ledger,selected);transport=original.transport.forSchema(selected);packages=original.packages;json=original.json;}
    TaskProfessionalPackageAuthoringService v2()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.v2());}
    TaskProfessionalPackageAuthoringService planDraft()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.planDraft());}
    TaskProfessionalPackageAuthoringService sectionWriter()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.sectionWriter());}
    TaskProfessionalPackageAuthoringService sourceVerifier()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.sourceVerifier());}
    TaskProfessionalPackageAuthoringService documentReview()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.documentReview());}
    TaskProfessionalPackageAuthoringService sectionWriterV2()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.sectionWriterV2());}
    TaskProfessionalPackageAuthoringService sourceVerifierV2()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.sourceVerifierV2());}
    TaskProfessionalPackageAuthoringService documentReviewV2()throws Exception{return new TaskProfessionalPackageAuthoringService(this,schema.documentReviewV2());}
    record Response(int httpStatus,JsonNode body,JsonNode rejection){Response{body=body==null?null:body.deepCopy();rejection=rejection==null?null:rejection.deepCopy();}}
    private record Prepared(JsonNode request,TaskProfessionalPackageAuthoringAuthority.Checked checked,List<FormalEvaluationContextPackages.Plan> plans,TaskProfessionalPackageAuthoringLedger.Original original,JsonNode rejection){}
    public Response preparation(ApplicationSessionIdentity.Verified subject,String context,String project){outside(subject);return tx(subject,a->response(200,authority.preparation(a,context,project),"1"));}
    public Response create(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] wire)throws Exception {
        outside(subject);WireValues.uuid(project);schema.key(key);PermissionSchema.revision(context);
        var prepared=tx(subject,a->{ledger.lock(a);if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);var original=ledger.byKey(a,key);if(original!=null){ledger.owner(a,project,original);ledger.sameRequest(original,schema.request(wire));return new Prepared(null,null,List.of(),original,null);}
            var rejected=ledger.rejection(a,key);if(rejected!=null)return new Prepared(null,null,List.of(),null,ledger.reject(a,context,project,key,wire,ErrorCode.valueOf(rejected.required("error_code").textValue())));
            try {var request=schema.request(wire);var prep=authority.preparation(a,context,project);if(!prep.required("preparation_sha256").equals(request.required("preparation_sha256")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);var checked=authority.check(a,context,project,request);var plans=new ArrayList<FormalEvaluationContextPackages.Plan>();for(var b:authority.packageReads(a,project,request)){String expected=plans.isEmpty()?schema.ref("PROMPT"):schema.ref("PROFILE");plans.add(packages.plan(a,context,project,b,expected,schema.packageHashForRef(expected)));}return new Prepared(request,checked,List.copyOf(plans),null,null);
            }catch(SchemaRejection e){return new Prepared(null,null,List.of(),null,ledger.reject(a,context,project,key,wire,e.code()));}catch(TenantAccessException e){if(!rejectable(e.code()))throw e;return new Prepared(null,null,List.of(),null,ledger.reject(a,context,project,key,wire,e.code()));}
        });
        if(prepared.rejection()!=null)return rejected(prepared.rejection());if(prepared.original()!=null)return original(subject,context,project,prepared.original(),true);
        var reads=new ArrayList<FormalEvaluationContextPackages.Read>();for(var plan:prepared.plans())reads.add(packages.read(subject,plan));
        var attempt=tx(subject,a->{ledger.lock(a);var prior=ledger.byKey(a,key);if(prior!=null){ledger.owner(a,project,prior);ledger.sameRequest(prior,prepared.request());return null;}
            var current=authority.check(a,context,project,prepared.request());if(!current.proof().equals(prepared.checked().proof()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var witnesses=json.object().putArray("reads");int index=0;for(var read:reads){read.requireCurrent(a);String definition=index++==0?"PromptPackage":"ProfilePackage";schema.validate(definition,schema.parse(read.bytes()));witnesses.add(read.witness());}
            return ledger.begin(a,context,project,key,prepared.request(),current,witnesses);
        });
        if(attempt==null)return original(subject,context,project,tx(subject,a->ledger.byKey(a,key)),true);
        return transfer(subject,context,project,attempt,true);
    }
    public Response read(ApplicationSessionIdentity.Verified subject,String context,String project,String operation)throws Exception{outside(subject);var original=tx(subject,a->{authority.require(a,context,project);return ledger.byOperation(a,project,operation);});return original(subject,context,project,original,false);}
    public Response recover(ApplicationSessionIdentity.Verified subject,String context,String project,byte[] wire)throws Exception {
        outside(subject);WireValues.uuid(project);var recovery=schema.parse(wire);schema.validate("Recovery",recovery);var request=recovery.required("original_request");schema.validateRequest(request);String key=recovery.required("original_key").textValue();
        var original=tx(subject,a->{ledger.lock(a);authority.require(a,context,project);var prior=ledger.byKey(a,key);if(prior!=null){ledger.owner(a,project,prior);ledger.sameRequest(prior,request);}return prior;});
        if(original==null)return tx(subject,a->{authority.require(a,context,project);return response(200,registrations.notFound(a,project),"1");});
        boolean complete=tx(subject,a->"COMPLETED".equals(ledger.state(a,original)));if(complete)return original(subject,context,project,original,false);
        var attempt=tx(subject,a->{current(a,context,original);return ledger.recover(a,original);});return transfer(subject,context,project,attempt,false);
    }
    private Response transfer(ApplicationSessionIdentity.Verified subject,String context,String project,TaskProfessionalPackageAuthoringLedger.Attempt attempt,boolean create)throws Exception {
        var o=attempt.original();S3ObjectReader.Admission guard=()->tx(subject,a->{current(a,context,o);var actual=ledger.byOperation(a,project,o.text("operation_id"));if(!o.digest().equals(actual.digest()))bad();return null;});
        final TaskProfessionalPackageAuthoringTransport.Verified physical;
        try {physical=transport.transfer(subject,attempt,guard);}catch(java.io.IOException|software.amazon.awssdk.core.exception.SdkException uncertain){return original(subject,context,project,o,create);}
        tx(subject,a->{var checked=current(a,context,o);registrations.register(a,physical,checked);return null;});return original(subject,context,project,o,create);
    }
    private Response original(ApplicationSessionIdentity.Verified subject,String context,String project,TaskProfessionalPackageAuthoringLedger.Original o,boolean create)throws Exception {
        var registered=tx(subject,a->{ledger.owner(a,project,o);var checked=current(a,context,o);return "COMPLETED".equals(ledger.state(a,o))?registrations.read(a,o,checked):null;});
        if(registered==null)return tx(subject,a->{current(a,context,o);var data=registrations.status(a,o,null);schema.validate("Status",data);return response(202,data,"2");});
        S3ObjectReader.Admission guard=()->tx(subject,a->{var checked=current(a,context,o);var actual=registrations.read(a,o,checked);if(!actual.reference().equals(registered.reference())||!actual.status().equals(registered.status()))bad();return null;});
        transport.readRegistered(subject,registered,guard);guard.requireCurrent();schema.validate("Status",registered.status());return response(create?201:200,registered.status(),"3");
    }
    private TaskProfessionalPackageAuthoringAuthority.Checked current(EnterpriseAccess.Admission a,String context,TaskProfessionalPackageAuthoringLedger.Original o)throws Exception {ledger.owner(a,o.text("project_id"),o);var actual=authority.check(a,context,o.text("project_id"),o.request());if(!actual.proof().equals(o.intent().required("authority"))||!json.tree(actual.content()).equals(o.intent().required("content")))throw new TenantAccessException(ErrorCode.SCOPE_STALE);return actual;}
    private Response response(int status,JsonNode data,String revision){return new Response(status,schema.envelope(data,revision),null);}private Response rejected(JsonNode proof){return new Response(errorStatus(ErrorCode.valueOf(proof.required("error_code").textValue())),null,proof);}
    static int errorStatus(ErrorCode code){return code==ErrorCode.SCHEMA_INVALID?422:code==ErrorCode.ACCESS_DENIED?403:409;}
    static boolean rejectable(ErrorCode code){return Set.of(ErrorCode.SCHEMA_INVALID,ErrorCode.ACCESS_DENIED,ErrorCode.REVISION_CONFLICT,ErrorCode.SCOPE_STALE).contains(code);}
    private static void outside(ApplicationSessionIdentity.Verified subject){DocumentUploadTransferService.requireOutsideTransaction(subject);}
    @FunctionalInterface private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
    private <T>T tx(ApplicationSessionIdentity.Verified subject,Work<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get());}
}

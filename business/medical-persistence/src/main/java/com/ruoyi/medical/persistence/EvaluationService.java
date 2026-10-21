package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.EvaluationMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class EvaluationService {
    private final org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> authorizedUses;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.beans.factory.ObjectProvider<OriginalScoreWitnessSink> originalScoreWitnessSinks;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private org.springframework.beans.factory.ObjectProvider<FormalEvaluationScoringGate> formalScoringGates;
    /** A synchronous sink participates in the original EnterpriseAccess transaction; failure must propagate. */
    public interface OriginalScoreWitnessSink { void capture(CreatedScoreWitness witness)throws IOException; }
    public static final class CreatedScoreWitness {
        private final EvaluationService owner;private final EnterpriseAccess.Admission admission;private final EvaluationProtocol.Score score;
        private final PermissionProtocol.GrantRow grant;private final EvaluationProtocol.Definition definition;private final String identityMode;private final JsonNode receipt;private final Thread thread=Thread.currentThread();private final TenantScope originalScope=TenantScope.requireCurrent();private boolean active=true;
        private CreatedScoreWitness(EvaluationService owner,EnterpriseAccess.Admission admission,EvaluationProtocol.Score score,PermissionProtocol.GrantRow grant,JsonNode receipt,EvaluationProtocol.Definition definition){this.definition=definition;this.owner=owner;this.admission=admission;this.score=score;this.grant=grant;identityMode=admission.bindingView().mode();this.receipt=receipt.deepCopy();}
        public EnterpriseAccess.Admission admission(){check();return admission;}public EvaluationProtocol.Score score(){check();return score;}
        public EvaluationProtocol.Definition definition(){check();return copyDefinition(definition);}public PermissionProtocol.GrantRow grant(){check();return grant;}public String identityMode(){check();return identityMode;}public JsonNode originalReceipt(){check();return receipt.deepCopy();}
        public void requireCurrent()throws IOException{check();var values=owner.values(admission);values.put("evaluationId",score.evaluation_id());var loaded=owner.load(admission,values,false);if(loaded.scores().stream().filter(s->s.score_id().equals(score.score_id())).filter(score::equals).count()!=1)mismatch();}
        private void check(){if(!active||thread!=Thread.currentThread())denied(ErrorCode.ACCESS_DENIED);var scope=TenantScope.requireCurrent();if(scope!=originalScope||!scope.tenantId().equals(admission.context().tenant_id())||!scope.subjectId().equals(admission.actorId()))denied(ErrorCode.ACCESS_DENIED);TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.EvaluationMapper.scores");}
    }
    private void captureOriginalScore(EnterpriseAccess.Admission a,EvaluationProtocol.Score score,PermissionProtocol.GrantRow grant,JsonNode receipt,EvaluationProtocol.Definition definition)throws IOException{
        if(originalScoreWitnessSinks==null)return;var sinks=originalScoreWitnessSinks.orderedStream().toList();if(sinks.size()>1)mismatch();
        if(!sinks.isEmpty()){var proof=new CreatedScoreWitness(this,a,score,grant,receipt,definition);try{sinks.get(0).capture(proof);}finally{proof.active=false;}}
    }
    private final EnterpriseAccess access;private final EvaluationMapper mapper;private final PermissionMapper permissions;
    private final ResourceReadAuthority authority;private final EvaluationReferences references;private final EvaluationSchema schema;
    private final EvaluationVerdict verdicts;private final PermissionSchema json;private final IdentityDirectoryPolicy policy;private final EvaluationRuleAuthority rules;
    public EvaluationService(EnterpriseAccess access,EvaluationMapper mapper,PermissionMapper permissions,ResourceReadAuthority authority,
            EvaluationReferences references,EvaluationSchema schema,EvaluationVerdict verdicts,PermissionSchema json,IdentityDirectoryPolicy policy,EvaluationRuleAuthority rules,
            org.springframework.beans.factory.ObjectProvider<SimulationAuthorizedUseEvidenceAuthority> authorizedUses) {
        this.access=access;this.mapper=mapper;this.permissions=permissions;this.authority=authority;this.references=references;
        this.schema=schema;this.verdicts=verdicts;this.json=json;this.policy=policy;this.rules=rules;this.authorizedUses=authorizedUses;
    }
    public JsonNode create(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] body) {
        return access.execute(subject,a->checked(()->{
            var request=schema.create(body);var values=values(a);operation(values,"EV-01","evaluation.created",201,key,
                schema.requestHash("/api/evaluation-batches",request,context,subject.sessionId()));
            var witness=require(a,request.project_id(),null,"evaluation.create");var previous=mapper.currentOperation(values);
            if(previous.size()>1)mismatch();
            if(!previous.isEmpty())return replay(a,values,previous.get(0),request,null);
            current(a,context);project(request.project_id(),true);var candidates=candidates(a,request,true);
            checkReferences(a,request,List.of());var approvedRule=rules.require(a,request.rule_revision(),true,EvaluationDependencyUse.forDefinition(request));String id=UUID.randomUUID().toString();values.put("evaluationId",id);values.put("projectId",request.project_id());
            var definition=new EvaluationProtocol.Definition("1.0.0",a.context().tenant_id(),id,request,candidates,rules.boundSamples(request,approvedRule),witness.grantId(),witness.revision().toString(),
                subject.user().user_id(),time((LocalDateTime)values.get("now")));
            schema.validate("Definition",json.tree(definition));String hash=schema.hash(definition);values.put("definitionHash",hash);values.put("definition",json.canonicalObject(definition));
            values.put("request",request);var projection=projection(request,List.of());
            values.put("sampleManifest",json.canonical(projection.required("sample_manifest")));values.put("candidates",json.canonical(projection.required("release_candidates")));
            values.put("authorizedUse",json.canonical(projection.required("authorized_use")));values.put("splitManifest",json.canonical(projection.required("split_manifest")));
            values.put("variance",json.canonical(projection.required("variance_policy")));
            one(mapper.insertVersion(values));one(mapper.insertBatch(values));one(mapper.insertDefinition(values));
            for(var ref:definitionRefs(request)){values.put("upstreamId",ref.version_id());one(mapper.insertLineage(values));}
            var stored=load(a,values);if(!stored.definition().equals(definition)||!stored.row().revision().equals(BigInteger.ONE)||!stored.scores().isEmpty())mismatch();
            return finish(a,values,stored,witness,null,null);
        }));
    }
    public JsonNode append(ApplicationSessionIdentity.Verified subject,String context,String key,String id,byte[] body) {
        var gate=formalScoringGates==null?null:formalScoringGates.getIfAvailable();
        var prepared=gate==null?null:checked(()->gate.prepare(subject,context,id,body));
        return access.execute(subject,a->checked(()->{
            json.requireId(id);var request=schema.append(body);var values=values(a);values.put("evaluationId",id);
            operation(values,"EV-02","evaluation.scored",201,key,schema.requestHash("/api/evaluation-batches/"+id+"/scores",request,context,subject.sessionId()));
            var target=load(a,values);var witness=require(a,target.row().projectId(),id,"evaluation.score");
            var previous=mapper.currentOperation(values);if(previous.size()>1)mismatch();if(!previous.isEmpty())return replay(a,values,previous.get(0),null,request);
            current(a,context);writable(target,request.expected_revision(),request.definition_sha256());project(target.row().projectId(),true);
            if("PRODUCTION".equals(target.definition().request().environment())){if(gate==null||prepared==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);checked(()->{gate.require(a,id,request,prepared);return null;});}
            candidates(a,target.definition().request(),true);rules.require(a,target.definition().request().rule_revision(),true,EvaluationDependencyUse.forDefinition(target.definition().request()));verdicts.requireAppend(target.definition(),target.row().contentHash(),target.scores(),request);
            var candidate=target.definition().candidate_snapshots().stream().filter(c->c.version_id().equals(request.cell().agent_version_id())).findFirst().orElseThrow();
            if(subject.user().user_id().equals(candidate.created_by()))denied(ErrorCode.ACCESS_DENIED);
            var newRefs=scoreRefs(request);for(var ref:newRefs)references.artifact(target.row().projectId(),ref);references.require(a,target.row().projectId(),newRefs,EvaluationDependencyUse.forDefinition(target.definition().request()).purpose());
            String scoreId=UUID.randomUUID().toString();var score=new EvaluationProtocol.Score("1.0.0",scoreId,id,target.row().contentHash(),target.definition().request().rule_revision(),request,
                subject.user().user_id(),witness.grantId(),witness.revision().toString(),time((LocalDateTime)values.get("now")));
            schema.validate("Score",json.tree(score));values.put("scoreId",scoreId);values.put("definitionHash",target.row().contentHash());values.put("expected",request.expected_revision());
            values.put("observation",request);values.put("ruleId",score.rule_revision().version_id());values.put("value",ratio(request));
            values.put("valueText",request.status().equals("SCORED")&&ratio(request)==null?"ZERO_DENOMINATOR":null);
            values.put("inputRef",json.canonicalObject(request.input_ref()));values.put("outputRef",request.output_ref()==null?null:json.canonicalObject(request.output_ref()));
            values.put("evidenceRefs",json.canonicalObject(request.evidence_refs()));values.put("scoreJson",json.canonicalObject(score));values.put("scoreHash",schema.hash(score));
            one(mapper.insertScore(values));one(mapper.insertObservation(values));values.put("nextStatus","RUNNING");values.put("missing","[]");one(mapper.advanceBatch(values));
            var after=load(a,values);if(after.scores().size()!=target.scores().size()+1||after.scores().stream().noneMatch(score::equals))mismatch();
            var response=finish(a,values,after,witness,scoreId,null);captureOriginalScore(a,score,witness,response,after.definition());
            if("PRODUCTION".equals(after.definition().request().environment()))checked(()->{gate.require(a,id,request,prepared);return null;});return response;
        }));
    }
    public JsonNode finalizeResult(ApplicationSessionIdentity.Verified subject,String context,String key,String id,byte[] body) {
        var gate=formalScoringGates==null?null:formalScoringGates.getIfAvailable();
        var prepared=gate==null?null:checked(()->gate.prepareFinalization(subject,context,id,body));
        return access.execute(subject,a->checked(()->{
            json.requireId(id);var request=schema.finalizeRequest(body);var values=values(a);values.put("evaluationId",id);
            operation(values,"EV-03","evaluation.finalized",200,key,schema.requestHash("/api/evaluation-batches/"+id+"/finalize",request,context,subject.sessionId()));
            var target=load(a,values);var witness=require(a,target.row().projectId(),id,"evaluation.finalize");
            var previous=mapper.currentOperation(values);if(previous.size()>1)mismatch();if(!previous.isEmpty())return replay(a,values,previous.get(0),null,null);
            current(a,context);writable(target,request.expected_revision(),request.definition_sha256());project(target.row().projectId(),true);
            if("PRODUCTION".equals(target.definition().request().environment())){if(gate==null||prepared==null)denied(ErrorCode.DEPENDENCY_UNAVAILABLE);checked(()->{gate.requireFinalization(a,id,request,prepared);return null;});}
            candidates(a,target.definition().request(),true);rules.require(a,target.definition().request().rule_revision(),true,EvaluationDependencyUse.forDefinition(target.definition().request()));var result=verdicts.compute(target.definition(),target.row().contentHash(),target.scores());
            values.put("definitionHash",target.row().contentHash());values.put("expected",request.expected_revision());values.put("resulting",PermissionSchema.next(request.expected_revision()));
            values.put("nextStatus",batchStatus(result.verdict()));values.put("missing",json.canonicalObject(result.missing_requirements()));
            schema.validate("Result",json.tree(result));values.put("resultJson",json.canonicalObject(result));values.put("resultHash",schema.hash(result));values.put("verdict",result.verdict());
            values.put("authorityGrant",witness.grantId());values.put("authorityRevision",witness.revision().toString());
            one(mapper.advanceBatch(values));
            // Operation is inserted before its immutable result ledger FK. Both belong to the same EnterpriseAccess transaction.
            var response=finish(a,values,target,witness,null,result);
            if("PRODUCTION".equals(target.definition().request().environment()))checked(()->{gate.requireFinalization(a,id,request,prepared);return null;});return response;
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id,boolean scoresOnly) {
        return access.execute(subject,a->checked(()->{
            json.requireId(id);current(a,context);var values=values(a);values.put("evaluationId",id);var stored=load(a,values);
            require(a,stored.row().projectId(),id,"evaluation.read");var data=json.object();data.put("schema_version","1.0.0");data.put("evaluation_id",id);
            data.put("content_sha256",stored.row().contentHash());data.put("status",stored.row().status());data.put("revision",stored.row().revision().toString());
            if(!scoresOnly)data.set("definition",json.tree(stored.definition()));data.set("score_history",json.tree(stored.scores()));
            data.set("result",stored.result()==null?json.tree(null):json.tree(stored.result()));data.set("result_sha256",json.tree(stored.row().resultHash()));
            data.put("production_eligible",false);data.set("allowed_actions",json.tree(List.of()));
            return schema.envelope(data,UUID.randomUUID().toString(),stored.row().revision().toString());
        }));
    }
    /** Current fixed evaluation authority for the restricted worker; never creates execution authority. */
    EvaluationProtocol.Definition requireSimulationDefinition(EnterpriseAccess.Admission admission,String id,String hash)throws IOException {
        json.requireId(id);com.ruoyi.medical.contracts.WireValues.hash(hash);
        var values=values(admission);values.put("evaluationId",id);
        var loaded=load(admission,values,true);
        if(!hash.equals(loaded.row().contentHash())||EvaluationDependencyUse.forDefinition(loaded.definition().request())!=EvaluationDependencyUse.SIMULATION
            ||loaded.result()!=null||!Set.of("QUEUED","RUNNING").contains(loaded.row().status()))mismatch();
        rules.require(admission,loaded.definition().request().rule_revision(),true,EvaluationDependencyUse.SIMULATION);
        return loaded.definition();
    }
    /** Internal read for the normal score API: no final result or execution authority is implied. */
    EvaluationProtocol.Definition scoringDefinition(EnterpriseAccess.Admission a,String id)throws IOException{json.requireId(id);var v=values(a);v.put("evaluationId",id);return copyDefinition(load(a,v).definition());}
    /** Exact current evaluation ledger. This is evidence of storage and read authority, not acceptance. */
    public static final class AcceptanceSnapshot {
        private final EvaluationProtocol.Stored row;
        private final EvaluationProtocol.Definition definition;
        private final List<EvaluationProtocol.Score> scores;
        private final EvaluationProtocol.Result result;
        private final EvaluationService owner;private final EnterpriseAccess.Admission admission;private final String canonical;private final TenantScope originalScope;private final FormalAgentAcceptanceEvidence.ReadPermit formalPermit;
        private AcceptanceSnapshot(EvaluationService owner,EnterpriseAccess.Admission admission,Loaded loaded)throws IOException {this(owner,admission,loaded,null);}
        private AcceptanceSnapshot(EvaluationService owner,EnterpriseAccess.Admission admission,Loaded loaded,FormalAgentAcceptanceEvidence.ReadPermit permit)throws IOException {formalPermit=permit;
            this.owner=owner;this.admission=admission;originalScope=TenantScope.requireCurrent();row=loaded.row();definition=loaded.definition();scores=List.copyOf(loaded.scores());result=loaded.result();canonical=owner.acceptanceIdentity(loaded);
        }
        public void requireConsumer(EnterpriseAccess.Admission a,String evaluationId)throws IOException{if(a!=admission||!row.evaluationId().equals(evaluationId))denied(ErrorCode.ACCESS_DENIED);requireUnchanged();}
        public void requireUnchanged()throws IOException{var scope=TenantScope.requireCurrent();if(scope!=originalScope)denied(ErrorCode.ACCESS_DENIED);if(!scope.tenantId().equals(admission.context().tenant_id())||!scope.subjectId().equals(admission.actorId()))denied(ErrorCode.ACCESS_DENIED);var values=owner.values(admission);values.put("evaluationId",row.evaluationId());if(!canonical.equals(owner.acceptanceIdentity(owner.load(admission,values,false,formalPermit))))mismatch();}
        public EvaluationProtocol.Stored row(){return row;}
        public EvaluationProtocol.Definition definition(){return copyDefinition(definition);}
        public List<EvaluationProtocol.Score> scores(){return scores;}
        public EvaluationProtocol.Result result(){return result;}
    }
    public AcceptanceSnapshot requireAcceptance(EnterpriseAccess.Admission admission,String id)throws IOException {
        json.requireId(id);var values=values(admission);values.put("evaluationId",id);
        var loaded=load(admission,values,false);
        if(loaded.result()==null)throw new TenantAccessException(ErrorCode.ACTION_STALE);
        return new AcceptanceSnapshot(this,admission,loaded);
    }
    /** Internal preparation metadata only. No score, result, source permission or acceptance proof is returned. */
    public record FormalDefinitionIdentity(String tenantId,String projectId,String evaluationId,String contentHash,String status,String classification,BigInteger revision){}
    public static final class FormalDefinitionMetadata {
        private final FormalDefinitionIdentity row;private final EvaluationProtocol.Definition definition;private final EvaluationService owner;private final EnterpriseAccess.Admission admission;private final TenantScope scope;
        private FormalDefinitionMetadata(EvaluationService owner,EnterpriseAccess.Admission admission,EvaluationProtocol.Stored row,EvaluationProtocol.Definition definition){this.owner=owner;this.admission=admission;scope=TenantScope.requireCurrent();this.row=new FormalDefinitionIdentity(row.tenantId(),row.projectId(),row.evaluationId(),row.contentHash(),row.status(),row.classification(),row.revision());this.definition=definition;}
        public FormalDefinitionIdentity row(){return row;}public EvaluationProtocol.Definition definition(){return copyDefinition(definition);}
        public void requireUnchanged()throws IOException{if(scope!=TenantScope.requireCurrent())denied(ErrorCode.ACCESS_DENIED);var current=owner.requireFormalDefinitionMetadata(admission,row.evaluationId());if(!row.equals(current.row)||!owner.schema.hash(definition).equals(owner.schema.hash(current.definition)))mismatch();}
    }
    public FormalDefinitionMetadata requireFormalDefinitionMetadata(EnterpriseAccess.Admission a,String id)throws IOException {
        json.requireId(id);var values=values(a);values.put("evaluationId",id);var row=single(mapper.definition(values),ErrorCode.OBJECT_UNAVAILABLE);require(a,row.projectId(),row.evaluationId(),"evaluation.read");
        var d=schema.definition(row.definition());String hash=schema.hash(d);
        if(!a.context().tenant_id().equals(row.tenantId())||!row.tenantId().equals(d.tenant_id())||!id.equals(row.evaluationId())||!id.equals(d.evaluation_id())||!Objects.equals(row.projectId(),d.request().project_id())||!hash.equals(row.contentHash())||!hash.equals(row.registryHash())||!"1.0.0".equals(row.schemaVersion())||!schema.manifestSha256().equals(row.manifest())||!row.classification().equals(policy.classification())||!d.created_by().equals(row.createdBy())||!d.created_at().equals(time(row.createdAt()))||row.revision().signum()<=0||!"PRODUCTION".equals(d.request().environment())||d.request().samples().isEmpty()||d.request().samples().stream().anyMatch(sample->!"REAL".equals(sample.evidence_class())))mismatch();
        if(!mapper.parents(values).equals(definitionRefs(d.request()).stream().map(PermissionProtocol.Basis::version_id).toList()))mismatch();
        return new FormalDefinitionMetadata(this,a,row,d);
    }
    public AcceptanceSnapshot requireFormalAcceptance(EnterpriseAccess.Admission a,String id,FormalAgentAcceptanceEvidence.ReadPermit permit)throws IOException {
        if(permit==null)denied(ErrorCode.ACCESS_DENIED);json.requireId(id);var values=values(a);values.put("evaluationId",id);var loaded=load(a,values,false,permit);if(loaded.result()==null)throw new TenantAccessException(ErrorCode.ACTION_STALE);return new AcceptanceSnapshot(this,a,loaded,permit);
    }
    private static EvaluationProtocol.Definition copyDefinition(EvaluationProtocol.Definition d){var c=d.candidate_snapshots().stream().map(x->new EvaluationProtocol.Candidate(x.version_id(),x.content_sha256(),x.agent_id(),x.created_by(),x.dependency_manifest().deepCopy(),x.limits().deepCopy(),x.schema_version(),x.schema_manifest_sha256())).toList();return new EvaluationProtocol.Definition(d.schema_version(),d.tenant_id(),d.evaluation_id(),d.request(),c,d.approved_expectation_samples(),d.authority_grant_id(),d.authority_revision(),d.created_by(),d.created_at());}
    private String acceptanceIdentity(Loaded loaded)throws IOException{var value=json.object();value.put("tenant",loaded.row().tenantId());value.put("evaluation_id",loaded.row().evaluationId());value.put("project_id",loaded.row().projectId());value.put("definition_hash",loaded.row().contentHash());value.put("result_hash",loaded.row().resultHash());value.put("status",loaded.row().status());value.put("revision",loaded.row().revision().toString());value.set("definition",json.tree(loaded.definition()));value.set("scores",json.tree(loaded.scores()));value.set("result",json.tree(loaded.result()));return json.canonical(value);}
    /** Execution preparation reads an original unfinished definition; it is not acceptance or model permission. */
    public static final class ExecutionDefinitionSnapshot {
        private final EvaluationService owner;
        private final EnterpriseAccess.Admission admission;
        private final EvaluationProtocol.Stored row;
        private final EvaluationProtocol.Definition definition;
        private final PermissionProtocol.GrantRow runGrant;
        private final String identity;
        private final TenantScope originalScope;
        private final Thread originalThread;
        private ExecutionDefinitionSnapshot(EvaluationService owner,EnterpriseAccess.Admission admission,Loaded loaded,PermissionProtocol.GrantRow grant)throws IOException {
            this.owner=owner;this.admission=admission;originalScope=TenantScope.requireCurrent();originalThread=Thread.currentThread();row=loaded.row();definition=loaded.definition();runGrant=grant;
            identity=owner.json.canonicalObject(List.of(row.tenantId(),row.evaluationId(),row.contentHash(),definition,grantIdentity(owner.json,grant)));
        }
        private static JsonNode grantIdentity(PermissionSchema json,PermissionProtocol.GrantRow g) {
            var x=json.object();x.put("grant_id",g.grantId());x.put("subject_id",g.subjectId());x.put("project_id",g.projectId());x.put("resource_kind",g.resourceKind());x.put("resource_id",g.resourceId());x.put("action",g.action());x.put("condition",g.condition());x.put("status",g.status());x.put("valid_from",g.validFrom()==null?null:g.validFrom().toString());x.put("valid_to",g.validTo()==null?null:g.validTo().toString());x.put("basis_version",g.basisVersion());x.put("schema_version",g.schemaVersion());x.put("manifest",g.manifest());x.put("revision",g.revision().toString());return x;
        }
        public EvaluationProtocol.Stored row(){return row;}
        public EvaluationProtocol.Definition definition(){return copyDefinition(definition);}
        public PermissionProtocol.GrantRow runGrant(){return runGrant;}
        public void requireConsumer(EnterpriseAccess.Admission a,String evaluationId,String definitionHash)throws IOException {
            if(a!=admission||!row.evaluationId().equals(evaluationId)||!row.contentHash().equals(definitionHash))denied(ErrorCode.ACCESS_DENIED);
            requireUnchanged();
        }
        public void requireUnchanged()throws IOException {
            if(Thread.currentThread()!=originalThread||TenantScope.requireCurrent()!=originalScope
                ||!originalScope.tenantId().equals(admission.context().tenant_id())||!originalScope.subjectId().equals(admission.actorId()))denied(ErrorCode.ACCESS_DENIED);
            var fresh=owner.requireExecutionDefinition(admission,row.evaluationId());
            if(!identity.equals(fresh.identity))denied(ErrorCode.SCOPE_STALE);
        }
    }
    public ExecutionDefinitionSnapshot requireExecutionDefinition(EnterpriseAccess.Admission admission,String id)throws IOException {
        json.requireId(id);var values=values(admission);values.put("evaluationId",id);
        var loaded=load(admission,values,false);
        if(admission.simulationWorker()||loaded.row().projectId()==null||loaded.result()!=null
                ||!Set.of("QUEUED","RUNNING").contains(loaded.row().status())
                ||"SIMULATION".equals(loaded.definition().request().environment())
                ||loaded.definition().request().samples().stream().anyMatch(s->!"REAL".equals(s.evidence_class())))
            denied(ErrorCode.ACTION_STALE);
        project(loaded.row().projectId(),true);
        var grant=require(admission,loaded.row().projectId(),id,"evaluation.run");
        return new ExecutionDefinitionSnapshot(this,admission,loaded,grant);
    }
    private record Loaded(EvaluationProtocol.Stored row,EvaluationProtocol.Definition definition,List<EvaluationProtocol.Score> scores,EvaluationProtocol.Result result) { }
    private Loaded load(EnterpriseAccess.Admission a,Map<String,Object> values)throws IOException {
        return load(a,values,false);
    }
    private Loaded load(EnterpriseAccess.Admission a,Map<String,Object> values,boolean simulationConsumption)throws IOException {return load(a,values,simulationConsumption,null);}
    private Loaded load(EnterpriseAccess.Admission a,Map<String,Object> values,boolean simulationConsumption,FormalAgentAcceptanceEvidence.ReadPermit formalPermit)throws IOException {
        if(simulationConsumption&&formalPermit!=null)denied(ErrorCode.ACCESS_DENIED);
        var row=single(mapper.definition(values),ErrorCode.OBJECT_UNAVAILABLE);
        // Authorize before parsing/returning potentially sensitive scoring metadata.
        if(simulationConsumption) {
            authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),row.projectId(),"EVALUATION",row.evaluationId()),
                "evaluation.read",EvaluationDependencyUse.SIMULATION.purpose());
        } else require(a,row.projectId(),row.evaluationId(),"evaluation.read");
        var definition=schema.definition(row.definition());String hash=schema.hash(definition);
        if(simulationConsumption&&EvaluationDependencyUse.forDefinition(definition.request())!=EvaluationDependencyUse.SIMULATION)mismatch();
        if(!a.context().tenant_id().equals(row.tenantId())||!row.tenantId().equals(definition.tenant_id())||!row.evaluationId().equals(values.get("evaluationId"))
            ||!row.evaluationId().equals(definition.evaluation_id())||!Objects.equals(row.projectId(),definition.request().project_id())||!hash.equals(row.contentHash())||!hash.equals(row.registryHash())
            ||!row.schemaVersion().equals("1.0.0")||!row.manifest().equals(schema.manifestSha256())||!row.classification().equals(policy.classification())
            ||!definition.created_by().equals(row.createdBy())||!definition.created_at().equals(time(row.createdAt()))||row.revision().signum()<=0)mismatch();
        var candidateSnapshots=candidates(a,definition.request(),false);if(!definition.candidate_snapshots().equals(candidateSnapshots))mismatch();
        if(!mapper.parents(values).equals(definitionRefs(definition.request()).stream().map(PermissionProtocol.Basis::version_id).toList()))mismatch();
        var raw=mapper.scores(values);if(raw.size()>50000)denied(ErrorCode.CONTEXT_LIMIT);var scores=new ArrayList<EvaluationProtocol.Score>();
        for(var r:raw) {
            if(r.scoreJson()==null)mismatch();var score=schema.score(r.scoreJson());
            if(!r.scoreId().equals(score.score_id())||!r.evaluationId().equals(score.evaluation_id())||!schema.hash(score).equals(r.contentHash())
                ||!r.schemaVersion().equals("1.0.0")||!r.manifest().equals(schema.manifestSha256())||!r.createdBy().equals(score.scorer_id())
                ||!r.createdAt().equals(LocalDateTime.ofInstant(java.time.Instant.parse(score.created_at()),ZoneOffset.UTC))||!r.classification().equals(policy.classification())
                ||!scoreProjection(score).equals(schema.read(r.projection())))mismatch();
            if(!score.observation().expected_revision().equals(Integer.toString(scores.size()+1)))mismatch();
            if(definition.candidate_snapshots().stream().anyMatch(c->c.version_id().equals(score.observation().cell().agent_version_id())&&c.created_by().equals(score.scorer_id())))mismatch();
            scores.add(score);
        }
        verdicts.effective(definition,hash,scores);var rule=formalPermit==null?checkReferences(a,definition.request(),scores):formalPermit.require(a,definition,List.copyOf(scores));
        if(!definition.approved_expectation_samples().equals(rules.boundSamples(definition.request(),rule)))mismatch();
        EvaluationProtocol.Result result=null;
        if(row.resultJson()!=null) {
            result=verdicts.compute(definition,hash,scores);
            schema.validate("Result",json.tree(result));
            if(!json.tree(result).equals(schema.read(row.resultJson()))||!schema.hash(result).equals(row.resultHash())||!batchStatus(result.verdict()).equals(row.status()))mismatch();
        }else if(row.resultHash()!=null||!Set.of("QUEUED","RUNNING").contains(row.status()))mismatch();
        var missing=result==null?List.<String>of():result.missing_requirements();
        if(!projection(definition.request(),missing).equals(schema.read(row.projection())))mismatch();
        if(row.revision().compareTo(BigInteger.valueOf(scores.size()+1L+(result==null?0L:1L)))!=0)mismatch();
        return new Loaded(row,definition,List.copyOf(scores),result);
    }
    private List<EvaluationProtocol.Candidate> candidates(EnterpriseAccess.Admission a,EvaluationProtocol.Create request,boolean writing)throws IOException {
        var result=new ArrayList<EvaluationProtocol.Candidate>();var values=values(a);
        for(var ref:request.candidates()) {
            values.put("candidateId",ref.version_id());var row=single(mapper.candidate(values),ErrorCode.OBJECT_UNAVAILABLE);
            if(!row.tenantId().equals(a.context().tenant_id())||!row.versionId().equals(ref.version_id())||!row.contentHash().equals(ref.content_sha256())
                ||!row.registryHash().equals(ref.content_sha256())||row.projectId()!=null&&!row.projectId().equals(request.project_id())||!row.schemaVersion().equals("1.0.0"))mismatch();
            if(!Set.of("DRAFT","VALIDATING","EVALUATING","REVIEW_PENDING","PUBLISHED","DISABLED").contains(row.status()))mismatch();
            if(writing&&row.status().equals("DISABLED"))denied(ErrorCode.SOURCE_NOT_READY);
            authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),row.projectId(),"CONFIGURATION",row.agentId()),"configuration.read",EvaluationDependencyUse.forDefinition(request).purpose());
            result.add(new EvaluationProtocol.Candidate(row.versionId(),row.contentHash(),row.agentId(),row.createdBy(),schema.read(row.dependencyManifest()),schema.read(row.limits()),row.schemaVersion(),row.manifest()));
        }
        return List.copyOf(result);
    }
    private RuleVersionProtocol.Validated checkReferences(EnterpriseAccess.Admission a,EvaluationProtocol.Create request,List<EvaluationProtocol.Score> scores)throws IOException {
        var rule=rules.require(a,request.rule_revision(),false,EvaluationDependencyUse.forDefinition(request));
        references.artifact(request.project_id(),request.sample_manifest_artifact(),EvaluationSchema.SAMPLE_MANIFEST_REF);
        for(var sample:request.samples())references.artifact(request.project_id(),sample.expected_ref(),EvaluationSchema.EXPECTED_ITEMS_REF);
        var refs=new ArrayList<>(definitionRefs(request));var artifactRefs=new ArrayList<PermissionProtocol.Basis>();
        artifactRefs.add(request.sample_manifest_artifact());artifactRefs.add(request.authorized_use_evidence());
        request.samples().forEach(s->{artifactRefs.add(s.input_ref());artifactRefs.add(s.expected_ref());});
        for(var score:scores)artifactRefs.addAll(scoreRefs(score.observation()));
        for(var ref:artifactRefs)references.artifact(request.project_id(),ref);refs.addAll(artifactRefs);
        var use=EvaluationDependencyUse.forDefinition(request);references.require(a,request.project_id(),refs,use.purpose());
        if(use==EvaluationDependencyUse.SIMULATION) {
            rule=SimulationEvaluationLegalUseAuthority.require(a,request,rules,
                authorizedUses==null?null:authorizedUses.getIfAvailable());
        }
        return rule;
    }
    private List<PermissionProtocol.Basis> definitionRefs(EvaluationProtocol.Create request) {
        var refs=new TreeMap<String,PermissionProtocol.Basis>();var all=new ArrayList<PermissionProtocol.Basis>();
        all.add(request.dataset());all.add(request.sample_manifest_artifact());all.add(request.rule_revision());all.add(request.authorized_use_evidence());all.addAll(request.candidates());
        request.samples().forEach(s->{all.add(s.input_ref());all.add(s.expected_ref());});
        for(var ref:all){var prior=refs.putIfAbsent(ref.version_id(),ref);if(prior!=null&&!prior.equals(ref))mismatch();}return List.copyOf(refs.values());
    }
    private static List<PermissionProtocol.Basis> scoreRefs(EvaluationProtocol.Append score) {
        var refs=new ArrayList<>(score.evidence_refs());refs.add(score.input_ref());if(score.output_ref()!=null)refs.add(score.output_ref());return refs;
    }
    private ObjectNode projection(EvaluationProtocol.Create request,List<String> missing) {
        var p=json.object();p.put("dataset_version",request.dataset().version_id());p.set("sample_manifest",json.tree(request.samples()));
        p.put("sample_manifest_artifact_id",request.sample_manifest_artifact().version_id());p.put("rule_revision",request.rule_revision().version_id());
        p.set("release_candidates",json.tree(request.candidates()));p.set("authorized_use",json.tree(request.authorized_use_evidence()));
        var splits=json.object();request.samples().forEach(s->splits.put(s.sample_id(),s.split()));p.set("split_manifest",splits);
        p.set("variance_policy",json.tree(request.variance_policy()));p.set("missing_requirements",json.tree(missing));return p;
    }
    private ObjectNode scoreProjection(EvaluationProtocol.Score score) {
        var r=score.observation();var p=json.object();p.put("sample_id",r.cell().sample_id());p.put("agent_version_id",r.cell().agent_version_id());p.put("dimension",r.cell().dimension());
        p.set("value",json.tree(ratio(r)));p.set("value_text",json.tree(r.status().equals("SCORED")&&ratio(r)==null?"ZERO_DENOMINATOR":null));p.set("numerator",json.tree(r.numerator()));p.set("denominator",json.tree(r.denominator()));
        p.put("rule_revision",score.rule_revision().version_id());p.set("input_ref",json.tree(r.input_ref()));p.set("output_ref",json.tree(r.output_ref()));p.set("evidence_ref",json.tree(r.evidence_refs()));
        p.put("scorer_id",score.scorer_id());p.set("supersedes",json.tree(r.supersedes()));p.put("status",r.status());p.put("repeat_index",r.cell().repeat_index());
        p.put("expected_revision",r.expected_revision());p.put("definition_sha256",score.definition_sha256());return p;
    }
    private JsonNode finish(EnterpriseAccess.Admission a,Map<String,Object> values,Loaded before,PermissionProtocol.GrantRow grant,String scoreId,EvaluationProtocol.Result result)throws IOException {
        String revision=result==null?before.row().revision().toString():(String)values.get("resulting");values.put("resulting",revision);values.put("projectId",before.row().projectId());
        String operationId=UUID.randomUUID().toString(),requestId=UUID.randomUUID().toString();values.put("operationId",operationId);values.put("requestId",requestId);
        var receipt=json.object();receipt.put("schema_version","1.0.0");receipt.put("operation_id",operationId);receipt.put("evaluation_id",before.row().evaluationId());
        receipt.put("definition_sha256",before.row().contentHash());receipt.put("revision",revision);receipt.put("status",result==null?before.row().status():batchStatus(result.verdict()));
        receipt.set("score_id",json.tree(scoreId));receipt.set("result_sha256",json.tree(result==null?null:schema.hash(result)));receipt.set("verdict",json.tree(result==null?null:result.verdict()));receipt.put("production_eligible",false);
        schema.validate("Receipt",receipt);var response=schema.envelope(receipt,requestId,revision);values.put("response",json.canonical(response));one(mapper.saveOperation(values));
        if(result!=null) {one(mapper.insertResult(values));var after=load(a,values);if(!result.equals(after.result()))mismatch();}
        var audit=json.object();audit.put("schema_version","1.0.0");audit.put("operation_id",operationId);audit.put("request_sha256",(String)values.get("requestHash"));
        audit.put("authority_grant_id",grant.grantId());audit.put("authority_revision",grant.revision().toString());audit.set("after",receipt);
        one(permissions.saveAudit(a.subject().user().user_id(),before.row().projectId(),UUID.randomUUID().toString(),(String)values.get("eventType"),json.canonical(audit),requestId,schema.manifestSha256(),policy.classification()));
        one(permissions.saveEvent(a.subject().user().user_id(),UUID.randomUUID().toString(),before.row().evaluationId(),revision,(String)values.get("eventType"),json.canonical(receipt)));
        verifyOperation(values,single(mapper.currentOperation(values),ErrorCode.CONTENT_MISMATCH),response);
        if(!schema.read(single(mapper.audit(values),ErrorCode.CONTENT_MISMATCH)).equals(audit)||!schema.read(single(mapper.event(values),ErrorCode.CONTENT_MISMATCH)).equals(receipt))mismatch();
        return response;
    }
    private JsonNode replay(EnterpriseAccess.Admission a,Map<String,Object> values,SelectionOperationRow operation,EvaluationProtocol.Create create,EvaluationProtocol.Append append)throws IOException {
        if(!operation.requestSha256().equals(values.get("requestHash")))denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        values.put("evaluationId",operation.targetId());var target=load(a,values);String action=switch((String)values.get("operationName")){case "EV-01"->"evaluation.create";case "EV-02"->"evaluation.score";default->"evaluation.finalize";};
        require(a,target.row().projectId(),create==null?target.row().evaluationId():null,action);var response=schema.receipt(operation.responseRef());verifyOperation(values,operation,response);var receipt=response.required("data");
        if(!receipt.required("definition_sha256").textValue().equals(target.row().contentHash())||!receipt.required("evaluation_id").textValue().equals(target.row().evaluationId())||receipt.required("production_eligible").asBoolean())mismatch();
        if(create!=null&&(!target.definition().request().equals(create)||!target.definition().created_by().equals(a.subject().user().user_id())))mismatch();
        if(append!=null) {
            var matching=target.scores().stream().filter(s->s.score_id().equals(receipt.required("score_id").textValue())).toList();
            if(matching.size()!=1||!matching.get(0).observation().equals(append)||!matching.get(0).scorer_id().equals(a.subject().user().user_id()))mismatch();
        }
        if(values.get("operationName").equals("EV-03")&&(target.result()==null||!target.row().resultHash().equals(receipt.required("result_sha256").textValue())||!target.result().verdict().equals(receipt.required("verdict").textValue())))mismatch();
        values.put("operationId",operation.operationId());values.put("projectId",target.row().projectId());values.put("requestId",response.required("request_id").textValue());values.put("resulting",receipt.required("revision").textValue());
        var audit=schema.read(single(mapper.audit(values),ErrorCode.CONTENT_MISMATCH));
        if(!audit.required("operation_id").textValue().equals(operation.operationId())||!audit.required("request_sha256").textValue().equals(operation.requestSha256())
            ||!audit.required("after").equals(receipt)||!schema.read(single(mapper.event(values),ErrorCode.CONTENT_MISMATCH)).equals(receipt))mismatch();return response;
    }
    private void verifyOperation(Map<String,Object> values,SelectionOperationRow operation,JsonNode response) throws IOException {
        if(!schema.receipt(operation.responseRef()).equals(response))mismatch();
        if(!operation.requestSha256().equals(values.get("requestHash"))||!operation.targetId().equals(values.get("evaluationId"))||!operation.state().equals("COMPLETED")
            ||!operation.httpStatus().equals(values.get("httpStatus"))||!operation.schemaVersion().equals("1.0.0")||!operation.schemaManifestSha256().equals(schema.manifestSha256())
            ||!operation.operationId().equals(response.required("data").required("operation_id").textValue())||!response.required("schema_version").textValue().equals("1.0.0")
            ||!response.required("resource_revision").equals(response.required("data").required("revision")))mismatch();
    }
    private PermissionProtocol.GrantRow require(EnterpriseAccess.Admission a,String project,String id,String action)throws IOException {
        return authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,id!=null?"EVALUATION":project==null?"ENTERPRISE":"PROJECT",id!=null?id:project==null?a.context().tenant_id():project),action,EvaluationReferences.PURPOSE);
    }
    private Map<String,Object> values(EnterpriseAccess.Admission a) {
        var v=new HashMap<String,Object>();v.put("userId",a.actorId());v.put("now",Objects.requireNonNull(permissions.databaseTime()));v.put("manifest",schema.manifestSha256());v.put("classification",policy.classification());return v;
    }
    private static void operation(Map<String,Object> v,String name,String event,int status,String key,String hash){v.put("operationName",name);v.put("eventType",event);v.put("httpStatus",status);v.put("key",key);v.put("requestHash",hash);}
    private void project(String id,boolean writing){if(id!=null&&!permissions.projectStatus(id).equals(List.of("ACTIVE")))denied(ErrorCode.OBJECT_UNAVAILABLE);}
    private static void writable(Loaded target,String revision,String hash){if(target.result()!=null||!Set.of("QUEUED","RUNNING").contains(target.row().status())||!revision.equals(target.row().revision().toString())||!hash.equals(target.row().contentHash()))denied(ErrorCode.REVISION_CONFLICT);}
    private static String ratio(EvaluationProtocol.Append r){return r.numerator()==null||r.denominator().equals("0")?null:new BigDecimal(r.numerator()).divide(new BigDecimal(r.denominator()),4,RoundingMode.HALF_UP).toPlainString();}
    private static String batchStatus(String verdict){return switch(verdict){case "PASS"->"COMPLETED";case "FAIL"->"FAILED";default->"INSUFFICIENT_EVIDENCE";};}
    private static String time(LocalDateTime time){return time.toInstant(ZoneOffset.UTC).toString();}
    private static void current(EnterpriseAccess.Admission a,String header){if(!a.context().context_revision().equals(header))denied(ErrorCode.CONTEXT_CHANGED);}
    private static <T>T checked(ThrowingSupplier<T> work){return work.get((message,failure)->failure instanceof com.fasterxml.jackson.core.JsonProcessingException?new SchemaRejection(ErrorCode.SCHEMA_INVALID):new IllegalStateException("EVALUATION_INVALID",failure));}
    private static <T>T single(List<T> rows,ErrorCode code){if(rows.size()!=1)denied(code);return Objects.requireNonNull(rows.get(0));}
    private static void one(int count){if(count!=1)denied(ErrorCode.REVISION_CONFLICT);}
    private static void mismatch(){denied(ErrorCode.CONTENT_MISMATCH);}
    private static void denied(ErrorCode code){throw new TenantAccessException(code);}
}

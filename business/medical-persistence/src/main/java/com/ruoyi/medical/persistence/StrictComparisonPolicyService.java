package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.StrictComparisonStore.*;

/** Separate authoring and independent approval consumers for typed comparison policies. */
public final class StrictComparisonPolicyService {
    static final String WRITE="strict_comparison_policy.write", READ="strict_comparison_policy.read", APPROVE="strict_comparison_policy.approve";
    private final EnterpriseAccess access;
    private final StrictComparisonStore store;
    private final StrictComparisonCodec codec;
    private final StrictComparisonAuthority authority;
    private final StrictComparisonRuntimePolicy capacity;
    StrictComparisonPolicyService(EnterpriseAccess access,StrictComparisonStore store,StrictComparisonCodec codec,StrictComparisonAuthority authority,StrictComparisonRuntimePolicy capacity) {
        this.access=access;this.store=store;this.codec=codec;this.authority=authority;this.capacity=capacity;
    }
    public JsonNode author(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes) throws Exception {
        capacity.requireConfigured();var request=codec.bind(bytes,StrictComparisonProtocol.Author.class);var tree=codec.tree(request);
        return access.execute(subject,a->checked(()->{
            StrictComparisonAuthority.human(a,context);var live=authority.require(a,project,request.scope(),WRITE);
            authority.definition(a,live,request,capacity.maximumCombinations());
            request.definition().requireImplementation(implementation());
            var old=store.replay(a,project,Kind.POLICY,key,tree);if(old!=null)return old.receipt();
            var row=store.append(a,project,Kind.POLICY,null,key,tree,tree,live.witness());
            one(store.db.update("INSERT INTO strict_comparison_policy_state(tenant_id,project_id,policy_revision,revision,status,decision_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) SELECT tenant_id,project_id,revision_id,1,'DRAFT',NULL,schema_version,schema_manifest_sha256,created_by,created_at,classification FROM strict_comparison_policy_revision WHERE tenant_id=? AND project_id=? AND revision_id=?",a.context().tenant_id(),project,row.id()));
            live.requireUnchanged();store.advance(a);return row.receipt();
        }));
    }
    public JsonNode context(ApplicationSessionIdentity.Verified subject,String context,String project) {
        return access.execute(subject,a->checked(()->{
            StrictComparisonAuthority.human(a,context);capacity.requireConfigured();
            authority.project(a,project,READ);authority.project(a,project,StrictDecimalEvidenceService.READ);
            return codec.tree(Map.of("schema_version","1.0.0","comparison_implementation",implementation(),"decimal_parser",StrictDecimalEvidenceService.implementation(),
                "operators",List.of("TEXT_EXACT","DECIMAL_EXACT"),"precision_modes",List.of("NOT_APPLICABLE","EXACT_SCALE"),"unit_modes",List.of("EXACT_DECLARED"),
                "unit_sources",List.of("SOURCE_SPAN","UNITLESS","UNKNOWN"),"coverage_mode","EXACT_DECLARED_COMBINATIONS","decimal_contract",StrictDecimalLexicalParser.CONTRACT,"assessment","IMPLEMENTATION_METADATA_NOT_APPROVAL"));
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String project,String id) {
        return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);authority.project(a,project,READ);var row=store.read(a,project,Kind.POLICY,id);var state=state(a,project,row);return codec.tree(Map.of("schema_version","1.0.0","definition",row.body(),"receipt",row.receipt(),"state",state));}));
    }
    public JsonNode baseline(ApplicationSessionIdentity.Verified subject,String context,String project,String id) {
        return access.execute(subject,a->checked(()->{StrictComparisonAuthority.human(a,context);var row=store.read(a,project,Kind.POLICY,id);var result=baseline(a,project,row);return codec.tree(Map.of("baseline",result,"baseline_sha256",codec.hash(result)));}));
    }
    public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String project,String id,String key,byte[] bytes) throws Exception {
        capacity.requireConfigured();var decision=codec.bind(bytes,StrictComparisonProtocol.Decide.class);
        var envelope=codec.tree(Map.of("policy_revision",id,"decision",decision));
        return access.execute(subject,a->checked(()->{
            StrictComparisonAuthority.human(a,context);authority.project(a,project,APPROVE);var row=store.read(a,project,Kind.POLICY,id);
            var previous=store.replay(a,project,Kind.DECISION,key,envelope);if(previous!=null){if(!id.equals(previous.parentId()))mismatch();return previous.receipt();}
            if(decision.decision()==StrictComparisonProtocol.Decision.APPROVE){
                requireIndependent(row.receipt().required("created_by").textValue(),a.actorId());
                requireCurrentAuthor(a,project,row);
            }
            var baseline=baseline(a,project,row);var before=state(a,project,row);
            if(!before.revision().equals(decision.expectedRevision()))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            if(!row.digest().equals(decision.definitionSha256())||!codec.hash(baseline).equals(decision.baselineSha256()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            var request=codec.bind(row.body(),StrictComparisonProtocol.Author.class);
            if(decision.decision()==StrictComparisonProtocol.Decision.APPROVE&&request.definition().applicability().state()!=StrictComparisonPolicyDefinition.Applicability.APPLICABLE)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
            String status=switch(decision.decision()){case APPROVE->"APPROVED";case REJECT->"REJECTED";case DISABLE->"DISABLED";};
            if(status.equals(before.status())||("DRAFT".equals(before.status())&&"DISABLED".equals(status)))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
            String next=PermissionSchema.next(before.revision());
            var body=codec.tree(Map.of("policy_revision",id,"definition_sha256",row.digest(),"previous_revision",before.revision(),"revision",next,"status",status,"baseline",baseline,"decision",decision));
            var saved=store.append(a,project,Kind.DECISION,id,key,envelope,body,baseline);
            one(store.db.update("UPDATE strict_comparison_policy_state SET revision=?,status=?,decision_id=? WHERE tenant_id=? AND project_id=? AND policy_revision=? AND revision=?",next,status,saved.id(),a.context().tenant_id(),project,id,before.revision()));
            var after=state(a,project,row);if(!next.equals(after.revision())||!status.equals(after.status())||!saved.id().equals(after.decisionId()))mismatch();
            store.advance(a);return saved.receipt();
        }));
    }
    /** Not exposed as a caller-mintable record. A Boolean rule approval cannot construct this. */
    public final class Verified {
        private final EnterpriseAccess.Admission admission;private final String project;private final Row row;private final State state;private final StrictComparisonProtocol.Author request;private final JsonNode witness;private final StrictComparisonRunScope.Verified run;
        private Verified(EnterpriseAccess.Admission a,String project,Row row,State state,StrictComparisonProtocol.Author request,JsonNode witness,StrictComparisonRunScope.Verified run){this.admission=a;this.project=project;this.row=row;this.state=state;this.request=request;this.witness=witness.deepCopy();this.run=run;}
        public StrictComparisonPolicyDefinition.Definition definition(){return request.definition();}
        public StrictComparisonProtocol.Scope scope(){return request.scope();}
        public String revisionId(){return row.id();}public String digest(){return row.digest();}public String decisionId(){return state.decisionId();}
        public void requireUnchanged() throws Exception {
            var now=run==null?requireApproved(admission,project,row.id(),row.digest()):requireApprovedFixedRun(run,row.id(),row.digest());if(!state.equals(now.state)||!witness.equals(now.witness))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
    }
    Verified requireApproved(EnterpriseAccess.Admission a,String project,String id,String digest) throws Exception {
        return approved(a,project,id,digest,null);
    }
    Verified requireApprovedFixedRun(StrictComparisonRunScope.Verified run,String id,String digest) throws Exception {
        return approved(run.admission(),run.project(),id,digest,run);
    }
    private Verified approved(EnterpriseAccess.Admission a,String project,String id,String digest,StrictComparisonRunScope.Verified run) throws Exception {
        capacity.requireConfigured();var row=store.read(a,project,Kind.POLICY,id);if(!row.digest().equals(digest))mismatch();
        var request=codec.bind(row.body(),StrictComparisonProtocol.Author.class);var live=run==null?authority.require(a,project,request.scope(),READ):authority.requireRun(run,request.scope(),READ);
        authority.definition(a,live,request,capacity.maximumCombinations());request.definition().requireImplementation(implementation());
        var state=state(a,project,row);if(!"APPROVED".equals(state.status()))throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        if(request.definition().applicability().state()!=StrictComparisonPolicyDefinition.Applicability.APPLICABLE)throw new TenantAccessException(ErrorCode.CONDITION_UNKNOWN);
        requireCurrentAuthor(a,project,row);
        var decision=store.read(a,project,Kind.DECISION,state.decisionId());
        var original=decision.body().required("baseline").required("authority");
        authority.requireCurrentActor(a,project,decision.receipt().required("created_by").textValue(),original.required("identity_mode").textValue(),original.required("grant"),APPROVE);
        return new Verified(a,project,row,state,request,live.witness(),run);
    }
    private JsonNode baseline(EnterpriseAccess.Admission a,String project,Row row) throws Exception {
        capacity.requireConfigured();var request=codec.bind(row.body(),StrictComparisonProtocol.Author.class);var live=authority.require(a,project,request.scope(),APPROVE);
        authority.definition(a,live,request,capacity.maximumCombinations());request.definition().requireImplementation(implementation());
        return codec.tree(Map.of("schema_version","1.0.0","policy_revision",row.id(),"content_sha256",row.digest(),"state",state(a,project,row),"authority",live.witness(),"implementation",implementation()));
    }
    private record State(String revision,String status,String decisionId) { }
    private State state(EnterpriseAccess.Admission a,String project,Row policy) throws Exception {
        var row=single(store.db.queryForList("SELECT s.revision,s.status,s.decision_id FROM strict_comparison_policy_state s JOIN strict_comparison_policy_revision p ON p.tenant_id=s.tenant_id AND p.project_id=s.project_id AND p.revision_id=s.policy_revision AND p.schema_version=s.schema_version AND p.schema_manifest_sha256=s.schema_manifest_sha256 AND p.created_by=s.created_by AND p.created_at=s.created_at AND p.classification=s.classification WHERE s.tenant_id=? AND s.project_id=? AND s.policy_revision=? FOR UPDATE",a.context().tenant_id(),project,policy.id()));
        var state=new State(text(row,"revision"),text(row,"status"),(String)row.get("decision_id"));
        if(state.decisionId()==null){if(!"1".equals(state.revision())||!"DRAFT".equals(state.status()))mismatch();}
        else {var decision=store.read(a,project,Kind.DECISION,state.decisionId());
            if(!policy.id().equals(decision.parentId())||!policy.id().equals(decision.body().required("policy_revision").asText())||!policy.digest().equals(decision.body().required("definition_sha256").asText())||!state.revision().equals(decision.body().required("revision").asText())||!state.status().equals(decision.body().required("status").asText()))mismatch();
            var supplied=codec.bind(decision.body().required("decision"),StrictComparisonProtocol.Decide.class);
            if(supplied.decision()==StrictComparisonProtocol.Decision.APPROVE)requireIndependent(policy.receipt().required("created_by").textValue(),decision.receipt().required("created_by").textValue());
            String expected=switch(supplied.decision()){case APPROVE->"APPROVED";case REJECT->"REJECTED";case DISABLE->"DISABLED";};
            if(!expected.equals(state.status())||!policy.digest().equals(supplied.definitionSha256())||!supplied.expectedRevision().equals(decision.body().required("previous_revision").asText())||!PermissionSchema.next(supplied.expectedRevision()).equals(state.revision())||!codec.hash(decision.body().required("baseline")).equals(supplied.baselineSha256()))mismatch();
        }return state;
    }
    private void requireCurrentAuthor(EnterpriseAccess.Admission a,String project,Row row)throws Exception {
        var original=row.authority();authority.requireCurrentActor(a,project,row.receipt().required("created_by").textValue(),
                original.required("identity_mode").textValue(),original.required("grant"),WRITE);
    }
    static void requireIndependent(String author,String approver){
        com.ruoyi.medical.contracts.WireValues.uuid(author);com.ruoyi.medical.contracts.WireValues.uuid(approver);
        if(author.equals(approver))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    static StrictComparisonPolicyDefinition.Version implementation() throws Exception {return StrictComparisonCodec.implementation(StrictFieldComparisonEngine.class,"STRICT_FIELD_COMPARISON_V1");}
    private static <T>T checked(ThrowingSupplier<T> action){return action.get();}
}

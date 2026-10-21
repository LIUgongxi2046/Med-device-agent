package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private current N06 input and deterministic-output proof. This does not authorize an
 * object write, candidate adoption, an initial body save, or N07 completion. */
public final class TaskCandidateAssemblyAuthority {
    private final FormalCapabilityIdentityAuthority roles;private final JdbcTemplate db;private final TaskSourceVerifierSchema json;
    private final TaskSourceVerifierCommittedReviewAuthority reviews;
    private final TaskSectionWriterInputAuthority writers;
    private final TemplateApplicabilityService applicability;
    private final TaskRunReplayContentAuthority contents;
    private final TaskSourceVerifierRules rules;
    private final TaskCandidateAssemblyNormalizer normalizer;private final TaskCandidateAssemblyCrossFileAuthority crossFiles;
    public TaskCandidateAssemblyAuthority(DataSource data,TaskSourceVerifierSchema json,TaskSourceVerifierCommittedReviewAuthority reviews,
            TaskSectionWriterInputAuthority writers,TemplateApplicabilityService applicability,TaskRunReplayContentAuthority contents,
            TaskSourceVerifierRules rules,TaskCandidateAssemblyNormalizer normalizer,TaskCandidateAssemblyCrossFileAuthority crossFiles,FormalCapabilityIdentityAuthority roles){this.roles=roles;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.json=json;this.reviews=reviews;this.writers=writers;this.applicability=applicability;this.contents=contents;this.rules=rules;this.normalizer=normalizer;this.crossFiles=crossFiles;}
    public Verified require(TaskRunActivityPreparation.Current current)throws Exception{return require(current,null);}
    public Verified require(TaskRunActivityPreparation.Current current,TaskCandidateAssemblyCrossFileAuthority.Verified crossFileReads)throws Exception{return new Verified(current,crossFileReads,inspect(current,crossFileReads));}
    private State inspect(TaskRunActivityPreparation.Current c,TaskCandidateAssemblyCrossFileAuthority.Verified crossFileReads)throws Exception {
        c.requireUnchanged();var p=c.production();var replay=p.replay().content();var graph=c.graph();
        if(!graph.requiredStages().contains("N06"))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var bindings=c.policy().definition().bindings().stream().filter(b->"N06".equals(b.nodeKind())).toList();if(bindings.size()!=1)bad();
        var role=roles.require(p.admission(),bindings.get(0).roleVersion(),"ROLE",p.projectId(),"RUN_CREATION");
        var contract=TaskCandidateAssemblyContracts.fromRole(role.canonicalDefinition().required("request"));
        var assigned=new ArrayList<String>();var requirements=new ArrayList<String>();
        for(var section:replay.required("plan").required("content").required("sections")){
            assigned.add(text(section,"section_id"));for(var node:section.required("requirement_node_ids"))requirements.add(node.asText());
        }
        if(assigned.isEmpty()||assigned.size()>512||new HashSet<>(assigned).size()!=assigned.size()||new HashSet<>(requirements).size()!=requirements.size())bad();
        var template=replay.required("template");var profile=replay.required("project_profile");
        var applicable=applicability.requireTarget(p.admission(),p.projectId(),text(profile,"profile_revision"),text(template.required("content"),"document_type"),text(template,"template_version"),c.policy().definition().businessPurpose());
        same(template.required("content"),json.tree(applicable.view().template().content()));
        same(new HashSet<>(requirements),new HashSet<>(applicable.view().template().content().nodes().stream().map(TemplateProtocol.Node::node_id).toList()));
        var closure=contents.require(p);var currentRules=rules.require(p);
        var rows=db.queryForList("SELECT c.candidate_id,c.target_sections FROM task_section_writer_submission s JOIN candidate c ON c.tenant_id=s.tenant_id AND c.project_id=s.project_id AND c.candidate_id=s.candidate_id WHERE c.tenant_id=? AND c.project_id=? AND c.task_id=? AND (c.run_id=? OR EXISTS (SELECT 1 FROM task_run_success_reuse r WHERE r.tenant_id=c.tenant_id AND r.project_id=c.project_id AND r.run_id=? AND r.stage_kind='N04' AND r.original_run_id=c.run_id AND r.original_step_id=c.step_id)) AND c.status='READY' ORDER BY c.candidate_id LIMIT 513 FOR UPDATE",p.tenantId(),p.projectId(),p.taskId(),p.runId(),p.runId());
        if(rows.isEmpty()||rows.size()>512)throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var bySection=new HashMap<String,String>();for(var row:rows){var section=json.read(str(row,"target_sections"));if(section.size()!=1||!section.get(0).isTextual()||bySection.putIfAbsent(section.get(0).asText(),str(row,"candidate_id"))!=null)bad();}
        if(!new HashSet<>(assigned).equals(bySection.keySet()))throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        var candidates=new ArrayList<TaskCandidateAssemblyNormalizer.Candidate>();var candidatePins=JsonNodeFactory.instance.arrayNode();
        JsonNode baseline=null;String base=null,bodySchema=null,document=null;
        var retainedReviews=new ArrayList<TaskSourceVerifierCommittedReviewAuthority.Verified>();var retainedWriters=new ArrayList<TaskSectionWriterInputAuthority.Verified>();
        for(String section:assigned){
            var review=reviews.require(p,c.publication(),c.policy(),bySection.get(section));var target=review.target();
            var writer=target.writerInput(c.publication(),c.policy());var wi=writer.input();same(contract,writer.evidenceContract());same(contract.bodySha(),wi.bodySchemaSha256());
            same(List.of(section),target.candidate().targetSections());same(List.of(section),wi.targetSections());
            if(baseline==null){baseline=writer.baselineBody();base=wi.baseDocumentRevision();bodySchema=wi.bodySchemaRevision();document=wi.documentId();}
            else {same(baseline,writer.baselineBody());same(base,wi.baseDocumentRevision());same(bodySchema,wi.bodySchemaRevision());same(document,wi.documentId());}
            same(text(replay,"plan_revision"),wi.planRevision());same(text(replay,"snapshot_id"),wi.snapshotId());
            // The consumer reader verifies committed N05 outputs and current rights. Its findings
            // remain candidate-bound; no conversion into a document check is performed here.
            candidates.add(new TaskCandidateAssemblyNormalizer.Candidate(target.candidateId(),target.candidate(),target.body(),target.bodySha256(),target.evidenceRefs(),review.checkId(),review.findingsArtifactId()));
            var pin=JsonNodeFactory.instance.objectNode().put("section_id",section).put("candidate_id",target.candidateId()).put("candidate_artifact_id",target.candidateArtifactId()).put("candidate_receipt_sha256",target.candidateReceiptSha256()).put("body_artifact_id",target.bodyArtifactId()).put("body_sha256",target.bodySha256()).put("writer_input_sha256",writer.inputSha256()).put("writer_input_receipt_sha256",writer.receiptSha256()).put("check_id",review.checkId()).put("findings_artifact_id",review.findingsArtifactId()).put("coverage_artifact_id",review.coverageArtifactId()).put("review_receipt_sha256",review.receiptSha256());pin.set("review_pins",review.pins());candidatePins.add(pin);
            retainedReviews.add(review);retainedWriters.add(writer);
        }
        var fixed=new TaskCandidateAssemblyNormalizer.Fixed(base,bodySchema,assigned,requirements,applicable.view(),baseline,derived("RULES",p.runId()),candidates,contract);
        var normalized=normalizer.normalize(fixed);
        if(crossFileReads==null&&!normalized.manifest().factVersions().isEmpty())normalized=normalizer.normalize(fixed,Map.of(),List.of("FACT_IMPACT_READ_REQUIRED"));
        if(crossFileReads!=null)normalized=normalizer.normalize(fixed,crossFileReads.evaluate(c,normalized.body()),crossFileReads.impactBlockers(c,normalized.body()));
        if(crossFileReads!=null){var report=(com.fasterxml.jackson.databind.node.ObjectNode)normalized.report();report.set("fact_impact_scan",crossFileReads.impactReport(c,normalized.body()));normalized=new TaskCandidateAssemblyNormalizer.Result(normalized.manifest(),normalized.body(),normalized.companion(),report,normalized.blockers());}
        // Fact relationships must be in the fixed, currently authorized dependency closure.
        var pinnedVersions=new HashSet<String>();replay.required("dependency_versions").forEach(v->pinnedVersions.add(text(v,"version_id")));
        for(String fact:normalized.manifest().factVersions())if(!pinnedVersions.contains(fact))throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var ruleManifest=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N06").put("revision_id",derived("RULES",p.runId()));ruleManifest.set("rules",json.tree(currentRules.rules()));
        var input=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0").put("node_kind","N06").put("tenant_id",p.tenantId()).put("project_id",p.projectId()).put("task_id",p.taskId()).put("run_id",p.runId()).put("step_id",derived("STEP",p.runId())).put("action_id",derived("ACTION",p.runId())).put("graph_id",graph.graphId()).put("graph_sha256",graph.graphSha256()).put("replay_sha256",p.replay().rawSha256()).put("plan_revision",text(replay,"plan_revision")).put("plan_sha256",text(replay.required("plan"),"content_sha256")).put("snapshot_id",text(replay,"snapshot_id")).put("document_id",document).put("base_document_revision",base).put("body_schema_revision",bodySchema).put("baseline_sha256",json.hash(baseline)).put("rule_revision",derived("RULES",p.runId())).put("rule_manifest_sha256",json.hash(ruleManifest));
        if(contract.v2())TaskCandidateAssemblyContracts.declare(input,contract);
        input.set("cross_file_inputs",crossFileReads==null?crossFiles.select(c).preflight():crossFileReads.pins());input.set("role_version",json.tree(bindings.get(0).roleVersion()));input.set("execution_policy_version",json.tree(new PermissionProtocol.Basis(c.policy().versionId(),c.policy().contentSha256())));input.set("assigned_section_ids",json.tree(assigned));input.set("requirement_node_ids",json.tree(requirements));input.set("candidates",candidatePins);input.set("applicability",json.tree(applicable.view()));input.set("content_roots",json.tree(closure.roots()));input.set("content_members",json.tree(closure.members()));
        var witness=JsonNodeFactory.instance.objectNode().put("production_authorizations_sha256",p.authorizationsSha256());witness.set("input",input);witness.set("rules",currentRules.witness());witness.set("manifest",json.tree(normalized.manifest()));witness.set("cross_file_report",normalized.report());witness.set("companion",normalized.companion());
        for(var r:retainedReviews)r.requireUnchanged();for(var w:retainedWriters)w.requireUnchanged();applicability.requireUnchanged(p.admission(),applicable);currentRules.requireUnchanged();closure.requireUnchanged();c.requireUnchanged();
        return new State(input,ruleManifest,normalized,closure.roots(),closure.members(),json.hash(witness));
    }
    private record State(JsonNode input,JsonNode rules,TaskCandidateAssemblyNormalizer.Result result,List<String> roots,List<ArtifactRegistryProtocol.MemberRow> members,String witness){}
    public final class Verified {
        private final TaskRunActivityPreparation.Current current;private final State state;private TaskCandidateAssemblyCheckService.Verified committedCheck;private final TaskCandidateAssemblyCrossFileAuthority.Verified crossFileReads;
        private Verified(TaskRunActivityPreparation.Current c,TaskCandidateAssemblyCrossFileAuthority.Verified crossFileReads,State state){current=c;this.crossFileReads=crossFileReads;this.state=state;}
        public void requireUnchanged()throws Exception{current.requireUnchanged();same(state.witness,inspect(current,crossFileReads).witness);if(committedCheck!=null)committedCheck.requireFor(this);}
        public TaskNodeProductionAuthority.Verified production(){current.production().admission();return current.production();}
        public JsonNode input(){production();return state.input.deepCopy();}public String inputSha256()throws Exception{return json.hash(input());}
        public String stepId(){return text(input(),"step_id");}public String actionId(){return text(input(),"action_id");}
        public String graphId(){return text(input(),"graph_id");}public String graphSha256(){return text(input(),"graph_sha256");}
        public String ruleRevision(){return text(input(),"rule_revision");}public JsonNode ruleManifest(){production();return state.rules.deepCopy();}
        public List<String> contentRoots(){production();return state.roots;}public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){production();return state.members;}
        public Verified withCommittedCheck(TaskCandidateAssemblyCheckService.Verified check)throws Exception{if(check==null)bad();check.requireFor(this);var result=new Verified(current,crossFileReads,state);result.committedCheck=check;return result;}
        public boolean hasCommittedCheck(){production();return committedCheck!=null;}public String crossFileCheckId(){if(committedCheck==null)bad();return committedCheck.checkId();}public JsonNode crossFileCheckReceipt(){if(committedCheck==null)bad();return committedCheck.receipt();}
        public ArtifactProtocol.AssemblyManifest manifest(){production();var m=state.result.manifest();if(committedCheck==null)return m;var ids=new ArrayList<>(m.checkIds());ids.add(committedCheck.checkId());return new ArtifactProtocol.AssemblyManifest(m.targetKind(),m.documentRevision(),m.sections(),m.requirementNodeIds(),m.factVersions(),m.evidenceRefs(),m.ruleRevision(),ids,m.reviewIds(),m.unresolvedIssueIds(),m.formatProfileRevision());}public JsonNode body(){production();return state.result.body();}
        public JsonNode companion(){production();return state.result.companion();}public JsonNode crossFileReport(){production();return state.result.report();}
        public boolean ready(){production();return state.result.ready();}public List<String> blockers(){production();return state.result.blockers();}
    }
    static String derived(String kind,String run){WireValues.uuid(run);return UUID.nameUUIDFromBytes(("N06:"+kind+":"+run).getBytes(StandardCharsets.UTF_8)).toString();}
    private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).asText();}
    private static String str(Map<String,Object> row,String key){return Objects.requireNonNull(row.get(key)).toString();}
    private static void same(Object a,Object b){if(!Objects.equals(a,b))bad();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

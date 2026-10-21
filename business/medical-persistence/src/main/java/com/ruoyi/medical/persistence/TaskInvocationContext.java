package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Genuine TASK context construction. Every producer capability has a private constructor.
 * 097 is input provenance, never the model exact-input artifact. Existing075 stores actual
 * observed/prepared bytes; existing context_manifest stores their immutable context, not a job. */
public final class TaskInvocationContext {
    public static final String EXACT_INPUT_REF=TaskInvocationContextSchema.EXACT_INPUT_REF;
    private final JdbcTemplate db;private final TaskNodeInputAuthority inputs;private final TaskProfessionalRuntimeService runtime;
    private final TaskProfessionalSupervisor supervisors;private final TaskProfessionalObjectRegistration objects;
    private final ModelConfigurationService models;private final ModelEligibilityAuthority eligibility;private final ResourceReadAuthority resources;
    private final SourceReadAuthority sources;private final FormalInvocationSourceOriginals originals;private final TaskInvocationPackages packages;
    private final TaskNodeProducerAuthority producers;private final TaskProfessionalContentAuthority contents;
    private final TaskInvocationResults results;private final TaskResearchToolReceipts toolReceipts;private final TaskProfessionalPredecessorEvidence predecessors;
    private final TaskInvocationOriginals history;private final RuntimeObjectTransport outputTransport;private final FormalInvocationSchema schema;private final TaskInvocationContextSchema exact;private final PermissionSchema json;
    private final com.fasterxml.jackson.databind.json.JsonMapper parser=com.fasterxml.jackson.databind.json.JsonMapper.builder()
        .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public TaskInvocationContext(DataSource data,TaskNodeInputAuthority inputs,TaskProfessionalRuntimeService runtime,
            TaskProfessionalSupervisor supervisors,TaskProfessionalObjectRegistration objects,ModelConfigurationService models,
            ModelEligibilityAuthority eligibility,ResourceReadAuthority resources,SourceReadAuthority sources,
            FormalInvocationSourceOriginals originals,TaskInvocationPackages packages,TaskNodeProducerAuthority producers,
            TaskProfessionalContentAuthority contents,RuntimeObjectTransport outputTransport,FormalInvocationSchema schema,TaskInvocationContextSchema exact,PermissionSchema json,TaskInvocationResults results,TaskResearchToolReceipts toolReceipts){
        this(data,inputs,runtime,supervisors,objects,models,eligibility,resources,sources,originals,packages,producers,contents,outputTransport,schema,exact,json,results,toolReceipts,null);
    }
    public TaskInvocationContext(DataSource data,TaskNodeInputAuthority inputs,TaskProfessionalRuntimeService runtime,
            TaskProfessionalSupervisor supervisors,TaskProfessionalObjectRegistration objects,ModelConfigurationService models,
            ModelEligibilityAuthority eligibility,ResourceReadAuthority resources,SourceReadAuthority sources,
            FormalInvocationSourceOriginals originals,TaskInvocationPackages packages,TaskNodeProducerAuthority producers,
            TaskProfessionalContentAuthority contents,RuntimeObjectTransport outputTransport,FormalInvocationSchema schema,TaskInvocationContextSchema exact,PermissionSchema json,TaskInvocationResults results,TaskResearchToolReceipts toolReceipts,TaskProfessionalPredecessorEvidence predecessors){
        this.predecessors=predecessors;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.inputs=inputs;this.runtime=runtime;this.supervisors=supervisors;this.objects=objects;
        this.models=models;this.eligibility=eligibility;this.resources=resources;this.sources=sources;this.originals=originals;this.packages=packages;
        this.producers=producers;this.contents=contents;this.results=Objects.requireNonNull(results);this.toolReceipts=Objects.requireNonNull(toolReceipts);history=new TaskInvocationOriginals(data,objects,producers,schema,exact,json);this.outputTransport=outputTransport;this.schema=schema;this.exact=exact;this.json=json;
    }
    private record State(ModelConfigurationProtocol.Fixed model,ModelEligibilityAuthority.Verified eligibility,JsonNode replay,
            JsonNode binding,JsonNode sources,List<String> requiredActions,String digest,String classification) { }
    /** Detached data for actual external tokenization; constructing this value is not production authority. */
    public static final class Compilation {
        private final byte[] bytes;private final PermissionProtocol.Basis price,tokenizer;private final AgentReleaseProtocol.SchemaBoundArtifact executable;private final JsonNode invocationHistory;
        private Compilation(byte[] bytes,PermissionProtocol.Basis price,PermissionProtocol.Basis tokenizer,AgentReleaseProtocol.SchemaBoundArtifact executable,JsonNode invocationHistory){this.bytes=bytes.clone();this.price=price;this.tokenizer=tokenizer;this.executable=executable;this.invocationHistory=invocationHistory.deepCopy();}
        public byte[] bytes(){return bytes.clone();}public PermissionProtocol.Basis priceBasis(){return price;}public PermissionProtocol.Basis tokenizerBasis(){return tokenizer;}
        public AgentReleaseProtocol.SchemaBoundArtifact tokenizerArtifact(){return executable;}
    }
    public Compilation compile(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervision,
            TaskProfessionalSupervisor.ModelRequest observed,TaskProfessionalPromptCompiler.CompiledPrompt prompt)throws Exception {
        requireSession(session,supervision,observed);prompt.requireCurrent(session.admission());
        var current=state(session.admission(),session.input());var projection=exact.readProjection(observed.bytes());
        if(!session.input().manifest().required("predecessors").isEmpty()&&!prompt.acceptsPredecessors())unavailable();
        if(!text(session.launch(),"prompt").equals(prompt.launchText())||!text(projection,"model").equals(text(session.deployment().value().required("sdk"),"model")))bad();
        if(!projection.required("parameters").equals(current.binding().required("generation_parameters")))bad();
        var ordered=results.orderedBefore(session,supervision,observed);var invocationHistory=historyData(session,ordered,observed.ordinal());
        var projected=TaskInvocationProjection.conversation(projection,session.sessionId(),prompt.launchText(),prompt.approvedSystemMessages(),approvedTools(session,projection.required("tools")),invocationHistory,observed.ordinal());
        byte[] bytes=ContentReadAuthorization.canonicalBytes(projected);exact.readExactInput(bytes);
        var tokenizer=json.basis(current.binding().required("tokenizer_version"));
        var executable=parser.treeToValue(current.binding().required("tokenizer_artifact"),AgentReleaseProtocol.SchemaBoundArtifact.class);
        return new Compilation(bytes,((ModelConfigurationProtocol.Definition)current.model().definition()).price_basis(),tokenizer,executable,invocationHistory);
    }
    public Prepared prepare(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervision,
            TaskProfessionalSupervisor.ModelRequest observed,TaskProfessionalPromptCompiler.CompiledPrompt prompt,
            FormalInvocationSourceOriginals.Read price,TaskInvocationPackages.Read executable,FormalTokenizerRunner.Result counted)throws Exception {
        var compilation=compile(session,supervision,observed,prompt);var a=session.admission();var current=state(a,session.input());
        price.requireCurrent(a);executable.requireCurrent(a);
        if(!compilation.priceBasis().version_id().equals(price.sourceVersion())||!compilation.priceBasis().content_sha256().equals(price.contentSha256()))bad();
        var artifact=compilation.tokenizerArtifact().artifact();
        if(!artifact.artifactId().equals(executable.artifact().version_id())||!artifact.sha256().equals(executable.artifact().content_sha256())
                ||!compilation.tokenizerArtifact().schema().schemaRef().equals(executable.schemaRef())||!compilation.tokenizerArtifact().schema().schemaSha256().equals(executable.schemaSha256()))bad();
        if(!counted.tokenizerVersion().equals(compilation.tokenizerBasis().version_id())||!counted.executableSha256().equals(artifact.sha256())
                ||!counted.executableSha256().equals(WireValues.sha256(executable.bytes()))||!counted.inputSha256().equals(WireValues.sha256(compilation.bytes())))bad();
        var profile=schema.read("PriceProfile",price.bytes(),TaskInvocationContextSchema.MAXIMUM_BYTES);bindPrice(current.model(),profile,compilation.priceBasis());
        var maximum=new BigInteger(text(current.replay().required("agent_release").required("limits"),"max_input_tokens"));
        if(counted.tokenCount().compareTo(maximum)>0)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        var policyRequest=json.object().put("maximum_input_tokens",maximum.toString());policyRequest.set("request_parameters",current.binding().required("generation_parameters"));
        FormalInvocationModelPolicy.require(json.tree(current.model()),policyRequest,current.classification(),counted.tokenCount());
        var content=contents.require(session.input());content.requireUnchanged();
        var promptTransmission=json.object().put("phase","PREPARATION_READ_ONLY");
        if(!new HashSet<>(content.members()).containsAll(prompt.contentMembers()))unavailable();
        var production=producers.requireLeased(a,session.input());var enterprise=production.enterpriseBudgetBasis();
        var parent=budgetBasis(a,text(current.replay(),"budget_revision"));if(parent.version_id().equals(enterprise.version_id()))bad();
        var prepared=new Prepared(session,supervision,observed,prompt,price,executable,counted,compilation,current,profile,
                enterprise,parent,content,promptTransmission);prepared.requireUnchanged();return prepared;
    }
    public final class Prepared {
        private final TaskProfessionalRuntimeService.Session session;private final TaskProfessionalSupervisor.Admitted supervision;
        private final TaskProfessionalSupervisor.ModelRequest observed;private final TaskProfessionalPromptCompiler.CompiledPrompt prompt;
        private final FormalInvocationSourceOriginals.Read price;private final TaskInvocationPackages.Read executable;private final FormalTokenizerRunner.Result counted;
        private final Compilation compiled;private final State original;private final JsonNode priceDocument,promptTransmission;
        private final PermissionProtocol.Basis enterprise,parent;private final TaskProfessionalContentAuthority.Verified content;
        private Prepared(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervision,TaskProfessionalSupervisor.ModelRequest observed,
                TaskProfessionalPromptCompiler.CompiledPrompt prompt,FormalInvocationSourceOriginals.Read price,TaskInvocationPackages.Read executable,
                FormalTokenizerRunner.Result counted,Compilation compiled,State original,JsonNode priceDocument,PermissionProtocol.Basis enterprise,
                PermissionProtocol.Basis parent,TaskProfessionalContentAuthority.Verified content,JsonNode promptTransmission){
            this.session=session;this.supervision=supervision;this.observed=observed;this.prompt=prompt;this.price=price;this.executable=executable;
            this.counted=counted;this.compiled=compiled;this.original=original;this.priceDocument=priceDocument.deepCopy();this.enterprise=enterprise;this.parent=parent;
            this.content=content;this.promptTransmission=promptTransmission.deepCopy();
        }
        public void requireUnchanged()throws Exception {
            requireSession(session,supervision,observed);prompt.requireCurrent(admission());price.requireCurrent(admission());executable.requireCurrent(admission());content.requireUnchanged();
            var fresh=compile(session,supervision,observed,prompt);if(!Arrays.equals(compiled.bytes(),fresh.bytes())||!FormalTaskNodeAuthority.sameEvidence(compiled.invocationHistory,fresh.invocationHistory)||!original.digest().equals(state(admission(),input()).digest()))stale();
            if(!"PREPARATION_READ_ONLY".equals(text(promptTransmission,"phase")))stale();
            if(!parent.equals(budgetBasis(admission(),parent.version_id())))stale();
        }
        public TaskProfessionalRuntimeService.Session session(){return session;}public TaskProfessionalSupervisor.Admitted supervision(){return supervision;}public TaskProfessionalSupervisor.Admitted admitted(){return supervision;}
        public EnterpriseAccess.Admission admission(){return session.admission();}public TaskNodeInputAuthority.Verified input(){return session.input();}
        public String runId(){return session.runId();}public String stepId(){return session.stepId();}public String actionId(){return session.actionId();}
        public String leaseGeneration(){return session.generation();}public String workerService(){return session.workerService();}public String inputId(){return input().inputId();}
        public String inputSha256(){return input().inputSha256();}public String sessionId(){return session.sessionId();}public String kind(){return "TASK_EXACT_INPUT";}
        public String schemaRef(){return EXACT_INPUT_REF;}public String schemaSha256(){return exact.sha256();}
        public byte[] bytes()throws Exception{requireUnchanged();return compiled.bytes();}public String payloadSha256()throws Exception{return WireValues.sha256(compiled.bytes());}
        public String modelRequestId(){return observed.requestId();}public String modelRequestSha256(){return observed.sha256();}public long modelRequestOrdinal(){return observed.ordinal();}
        public List<ArtifactRegistryProtocol.MemberRow> contentMembers(){return content.members();}public List<String> contentRoots(){return content.roots();}
        public JsonNode provenance()throws Exception {
            requireUnchanged();var p=json.object().put("schema_version","1.0.0").put("phase","PRE_SEND").put("session_id",sessionId()).put("payload_sha256",payloadSha256())
                .put("supervision_receipt_id",supervision.receiptId()).put("supervision_sha256",supervision.sha256()).put("deployment_sha256",supervision.deploymentSha256());
            if(observed.ordinal()>1)p.set("invocation_history",compiled.invocationHistory.deepCopy());
            p.set("model_request",observed.canonicalReceipt());p.set("model",json.tree(original.model()));p.set("model_eligibility",json.tree(original.eligibility().safeReplayBinding()));
            p.set("prompt_compilation",prompt.canonicalPins());p.set("source_authorizations",original.sources());p.set("tokenization",tokenization(counted));p.set("tokenizer_artifact",executable.canonicalPins());
            p.set("output_storage",json.tree(outputTransport.storageBinding()));p.set("output_retention",runRetention(admission(),original.replay()));p.set("price",priceDocument);p.put("price_authorization_sha256",price.authorizationSha256());p.set("enterprise_budget_basis",json.tree(enterprise));p.set("parent_budget_basis",json.tree(parent));return p;
        }
    }
    private JsonNode historyData(TaskProfessionalRuntimeService.Session session,TaskInvocationResults.OrderedHistory ordered,long ordinal)throws Exception {
        ordered.requireFor(session);var tools=toolReceipts.requireHistory(session,ordered);
        var data=TaskInvocationHistoryData.of(ordered.modelResults(),ordered.modelPins(),tools.canonicalResponses(),tools.canonicalPins());
        TaskInvocationHistoryData.require(data,ordinal);return data;
    }
    private State state(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input)throws Exception {
        TaskProfessionalRuntimeService.current(a);input.requireUnchanged();var i=a.formalProof().identity();
        if(!"TASK".equals(i.executionKind())||!i.tenant().equals(input.tenantId())||!i.project().equals(input.projectId())||!i.run().equals(input.runId())||!i.step().equals(input.stepId()))denied();
        var row=one(db.queryForList("SELECT replay_manifest,classification,budget_revision FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' AND status='RUNNING' AND dispatch_closed_at IS NULL AND ended_at IS NULL AND superseded_by_run_id IS NULL LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),i.actor()));
        var replay=schema.parse(str(row,"replay_manifest"));if(!text(input.manifest(),"replay_sha256").equals(schema.hash(replay)))bad();
        var policy=replay.required("execution_policy");var matches=new ArrayList<JsonNode>();for(var binding:policy.required("bindings"))if("N02".equals(text(binding,"node_kind")))matches.add(binding);var binding=one(matches);
        if(!binding.required("role_version").equals(input.manifest().required("role_version"))||!str(row,"budget_revision").equals(text(replay,"budget_revision")))bad();
        var modelBasis=json.basis(binding.required("model_version"));var model=models.requireCurrentExternal(a,modelBasis.version_id(),"RUN_CREATION",i.project());
        if(!modelBasis.content_sha256().equals(schema.hash(model)))bad();var d=(ModelConfigurationProtocol.Definition)model.definition();
        var enabled=eligibility.require(a,modelBasis.version_id(),"RUN_CREATION",i.project());if(!enabled.contentSha256().equals(modelBasis.content_sha256()))bad();
        var pinnedModels=new ArrayList<JsonNode>();for(var pinned:replay.required("model_bindings"))if(modelBasis.version_id().equals(text(pinned,"model_config_revision")))pinnedModels.add(pinned);if(!json.tree(enabled.safeReplayBinding()).equals(one(pinnedModels)))stale();
        enabled.requireDataUse(str(row,"classification"),text(policy,"region"),policy.required("training_allowed").booleanValue());enabled.requireParameters(binding.required("generation_parameters"));
        var witnesses=json.object().arrayNode();
        for(var fixed:input.fixedSources()){
            var read=sources.require(a,new SourceReadProtocol.ReadRequirement(fixed.sourceVersion(),fixed.rightsRevision(),i.project(),input.snapshotId(),fixed.parseVersion(),null,SourceReadProtocol.Use.FORMAL,fixed.purpose(),text(policy,"environment"),text(policy,"region"),null));
            if(read.selection()==null||!fixed.indexVersion().equals(read.selection().indexVersion())||!fixed.approvalRevision().equals(read.selection().approvalRevision())||!fixed.bindingRevision().equals(read.selection().bindingRevision()))bad();
            var members=new ArrayList<JsonNode>();for(var m:replay.required("scope_snapshot").required("members"))if(fixed.sourceVersion().equals(text(m,"source_version")))members.add(m);
            if(!text(one(members),"original_sha256").equals(read.source().originalSha256()))bad();witnesses.add(AgentPublicationWitness.tree(read));
        }
        var required=new TreeSet<String>();for(var permission:input.manifest().required("node_permissions")){String action=text(permission,"action");if(Set.of("model.send","configuration.read","project.read","source.read","source.external.process","artifact.read","budget.read").contains(action))required.add(action);}
        if(!required.contains("model.send")||!required.contains("configuration.read"))unavailable();
        var witness=json.object();witness.set("model",json.tree(model));witness.set("eligibility",json.tree(enabled.safeReplayBinding()));witness.set("eligibility_authorizations",json.tree(enabled.authorizations()));witness.set("sources",witnesses);witness.put("phase","PREPARATION_READ_ONLY");witness.put("replay_sha256",schema.hash(replay));
        enabled.requireUnchanged();input.requireUnchanged();return new State(model,enabled,replay,binding,witnesses,List.copyOf(required),schema.hash(witness),str(row,"classification"));
    }
    /** This path is reachable only after the actual registered context's independent
     * MODEL_CALL action is admitted. Preparation/read witnesses never substitute for it. */
    private JsonNode transmission(EnterpriseAccess.Admission a,TaskNodeInputAuthority.Verified input,State state,JsonNode prompt)throws Exception {
        var i=a.formalProof().identity();var model=state.model();var policy=state.replay().required("execution_policy");var d=(ModelConfigurationProtocol.Definition)model.definition();
        var grant=resources.require(a,new PermissionProtocol.Scope(i.tenant(),model.project_id(),"CONFIGURATION",model.config_id()),"model.send",text(policy,"business_purpose"));var sent=json.object().arrayNode();
        for(var fixed:input.fixedSources()){
            var read=sources.require(a,new SourceReadProtocol.ReadRequirement(fixed.sourceVersion(),fixed.rightsRevision(),i.project(),input.snapshotId(),fixed.parseVersion(),null,SourceReadProtocol.Use.MODEL_SEND,fixed.purpose(),text(policy,"environment"),text(policy,"region"),d.provider()));
            if(read.selection()==null||!fixed.indexVersion().equals(read.selection().indexVersion())||!fixed.approvalRevision().equals(read.selection().approvalRevision())||!fixed.bindingRevision().equals(read.selection().bindingRevision()))bad();
            var members=new ArrayList<JsonNode>();for(var m:state.replay().required("scope_snapshot").required("members"))if(fixed.sourceVersion().equals(text(m,"source_version")))members.add(m);if(!text(one(members),"original_sha256").equals(read.source().originalSha256()))bad();sent.add(AgentPublicationWitness.tree(read));
        }
        var result=json.object().put("phase","MODEL_CALL").put("action_id",i.action()).put("run_id",i.run()).put("step_id",i.step()).put("lease_generation",i.generation());result.set("model_send",json.tree(grant));result.set("sources",sent);
        result.set("prompt",packages.requireCurrentPins(a,i.project(),prompt.required("prompt_package"),text(policy,"business_purpose"),d.provider(),input));result.set("profile",packages.requireCurrentPins(a,i.project(),prompt.required("profile_package"),text(policy,"business_purpose"),d.provider(),input));if(TaskProfessionalPromptV2.COMPILER.equals(text(prompt,"compiler"))){if(predecessors==null)unavailable();result.set("predecessor_evidence",predecessors.requireCurrentPins(a,input,prompt.required("predecessor_evidence"),text(policy,"business_purpose"),d.provider()));}return result;
    }
    private void requireSession(TaskProfessionalRuntimeService.Session session,TaskProfessionalSupervisor.Admitted supervision,TaskProfessionalSupervisor.ModelRequest observed)throws Exception {
        session.requireUnchanged();supervision.requireUnchanged();observed.requireUnchanged(session.admission());
        if(!"OPEN".equals(session.state())||!session.sessionId().equals(supervision.sessionId())||!session.binding().equals(supervision.binding())||!session.sessionId().equals(observed.sessionId())||!session.binding().equals(observed.binding()))denied();
    }
    private JsonNode approvedTools(TaskProfessionalRuntimeService.Session session,JsonNode actual)throws Exception {
        String role=text(session.deployment().value(),"role_name");var names="Researcher".equals(role)?List.of("search_sources","read_source","read_artifact","load_skill","ask_user_question"):
            "NetworkResearcher".equals(role)?List.of("read_artifact","load_skill","ask_user_question","propose_web_acquisition"):List.<String>of();if(names.isEmpty())unavailable();
        var definitions=Map.of("search_sources",List.of("AT-01","检索已批准的资料"),"read_source",List.of("AT-02","读取指定原文及引用"),"read_artifact",List.of("AT-03","读取指定版本的成果"),"load_skill",List.of("AT-04","读取当前角色允许的能力"),"ask_user_question",List.of("AT-08","保存人工待办并结束当前执行"),"propose_web_acquisition",List.of("AT-09","提出已批准范围的采集任务"));
        if(!actual.isArray()||actual.size()!=names.size())bad();var hashes=session.deployment().value().required("expected_inventory").required("tool_schema_hashes");
        for(int n=0;n<names.size();n++){var tool=actual.get(n);var definition=definitions.get(names.get(n));if(!names.get(n).equals(text(tool,"name"))||!definition.get(1).equals(text(tool,"description"))||!text(hashes,names.get(n)).equals(schema.hash(tool.required("parameters"))))bad();}return actual.deepCopy();
    }
    private JsonNode runRetention(EnterpriseAccess.Admission a,JsonNode replay){
        var i=a.formalProof().identity();var created=one(db.query("SELECT created_at FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' LIMIT 2",(r,n)->r.getTimestamp(1).toInstant(),i.tenant(),i.project(),i.run(),i.actor()));
        return TaskInvocationOutputPolicy.forRun(i.run(),created,replay.required("retention"));
    }
    private PermissionProtocol.Basis budgetBasis(EnterpriseAccess.Admission a,String revision){
        WireValues.uuid(revision);var i=a.formalProof().identity();var row=one(db.queryForList("SELECT b.content_sha256,v.content_sha256 registry_sha256 FROM task_run r JOIN budget_revision b ON b.tenant_id=r.tenant_id AND b.project_id=r.project_id AND b.budget_revision=r.budget_revision JOIN version_registry v ON v.tenant_id=b.tenant_id AND v.version_id=b.budget_revision AND v.owner_project_id=b.project_id WHERE r.tenant_id=? AND r.project_id=? AND r.run_id=? AND r.budget_revision=? AND r.execution_kind='TASK' LIMIT 2 FOR UPDATE",i.tenant(),i.project(),i.run(),revision));
        if(!str(row,"content_sha256").equals(str(row,"registry_sha256")))bad();return new PermissionProtocol.Basis(revision,str(row,"content_sha256"));
    }
    private void bindPrice(ModelConfigurationProtocol.Fixed model,JsonNode price,PermissionProtocol.Basis basis)throws Exception {
        var d=(ModelConfigurationProtocol.Definition)model.definition();if(!d.price_basis().equals(basis))bad();schema.require("PriceProfile",price);var definition=json.tree(d);for(String key:List.of("provider","model_id","model_version","currency"))if(!definition.required(key).equals(price.required(key)))bad();
    }
    private JsonNode tokenization(FormalTokenizerRunner.Result result){return json.object().put("tokenizer_version",result.tokenizerVersion()).put("executable_sha256",result.executableSha256()).put("input_sha256",result.inputSha256()).put("output_sha256",result.outputSha256()).put("token_count",result.tokenCount().toString());}
    /** Registration creates only an immutable context. It does not bind/mint MODEL_CALL,
     * reserve money, dispatch, or complete a node. */
    public Registered register(Prepared prepared,TaskProfessionalObjectRegistration.Registered registered)throws Exception {
        prepared.requireUnchanged();registered.requireUnchanged();var a=prepared.admission();var i=a.formalProof().identity();
        if(!prepared.kind().equals(registered.kind())||!prepared.sessionId().equals(registered.sessionId())||!prepared.payloadSha256().equals(registered.reference().sha256())
            ||!EXACT_INPUT_REF.equals(registered.pin("schema_ref"))||!exact.sha256().equals(registered.pin("payload_schema_sha256"))
            ||!prepared.provenance().equals(registered.receipt().required("provenance")))bad();
        for(var pin:Map.of("tenant_id",i.tenant(),"project_id",i.project(),"run_id",i.run(),"step_id",i.step(),"action_id",i.action(),"created_by",i.actor(),"lease_generation",i.generation(),"worker_service_uri",i.workerService()).entrySet())if(!pin.getValue().equals(registered.pin(pin.getKey())))bad();
        String id=UUID.nameUUIDFromBytes(("TASK_MODEL_CONTEXT:"+prepared.sessionId()+":"+prepared.modelRequestId()+":"+prepared.modelRequestSha256()).getBytes(StandardCharsets.US_ASCII)).toString();
        var original=prepared.original;var node=new TaskInvocationProtocol.Node(i.tenant(),i.project(),prepared.input().taskId(),i.run(),i.principal(),i.step(),i.action(),prepared.sessionId(),i.generation(),i.workerService(),prepared.inputId(),prepared.inputSha256(),prepared.input().receiptSha256(),prepared.supervision.receiptId(),prepared.supervision.sha256(),original.classification());
        String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(registered.reference()),0,Long.parseLong(registered.reference().sizeBytes()));
        var ref=new CommonProtocol.ArtifactRef(registered.artifactId(),registered.reference().sha256(),EXACT_INPUT_REF,new CommonProtocol.Coverage(range,range,true,null));
        var rules=new ArrayList<PermissionProtocol.Basis>();for(var rule:original.replay().required("agent_release").required("rule_versions"))rules.add(json.basis(rule));
        var request=new TaskInvocationProtocol.Request("1.0.0","TASK","N02",node,original.model().model_config_revision(),text(original.replay().required("execution_policy"),"business_purpose"),ref,
            new PermissionProtocol.Basis(prepared.input().snapshotId(),text(prepared.input().manifest(),"snapshot_sha256")),prepared.compiled.tokenizerBasis(),json.basis(prepared.input().manifest().required("role_version")),prepared.prompt.promptBasis(),rules,prepared.enterprise,prepared.parent,text(original.replay().required("agent_release").required("limits"),"max_input_tokens"),original.binding().required("generation_parameters"));
        var dependencies=(ObjectNode)prepared.provenance();dependencies.put("exact_input_artifact_id",registered.artifactId()).put("exact_input_receipt_sha256",registered.receiptSha256()).put("state_sha256",original.digest());
        dependencies.set("exact_input_receipt",registered.receipt());dependencies.set("host_binding",prepared.session.binding());dependencies.set("required_actions",json.tree(original.requiredActions()));
        dependencies.put("replay_sha256",schema.hash(original.replay()));
        dependencies.set("prompt_preparation",prepared.promptTransmission);dependencies.set("launch_prompt",json.tree(prepared.prompt.launchText()));dependencies.set("approved_system_messages",json.tree(prepared.prompt.approvedSystemMessages()));
        var source=json.object().put("execution_kind","TASK");source.set("request",json.tree(request));source.set("dependencies",dependencies);
        String set=text(registered.receipt().required("content_manifest"),"access_set_id");var document=history.contextDocument(id,request,source,set,prepared.counted.tokenCount().toString());String digest=schema.hash(document);
        var found=db.queryForList("SELECT content_sha256 FROM context_manifest WHERE tenant_id=? AND context_manifest_id=? LIMIT 2 FOR UPDATE",i.tenant(),id);
        if(found.isEmpty()){
            var now=one(db.query("SELECT UTC_TIMESTAMP(6)",(r,n)->r.getTimestamp(1).toLocalDateTime()));
            insert("version_registry",Map.of("tenant_id",i.tenant(),"version_id",id,"owner_project_id",i.project(),"entity_kind","CONTEXT_MANIFEST","content_sha256",digest));
            var row=meta(i,original.classification(),now);row.put("context_manifest_id",id);row.put("exact_input_ref",json.canonical(json.tree(ref)));row.put("source_manifest",json.canonical(source));row.put("prompt_revision",request.prompt_basis().version_id());row.put("tokenizer_revision",request.tokenizer_basis().version_id());row.put("token_count",prepared.counted.tokenCount());row.put("dependency_access_set_id",set);row.put("content_sha256",digest);row.put("execution_mode","EXTERNAL");insert("context_manifest",row);
            var audit=meta(i,original.classification(),now);audit.put("audit_id",UUID.randomUUID().toString());audit.put("subject_id",i.actor());audit.put("action","task.model_context.registered");audit.put("resource_ref",json.canonical(document));audit.put("result","SUCCESS");audit.put("request_id",id);audit.put("trace_id",i.run());insert("audit_event",audit);
            insert("outbox_event",Map.of("tenant_id",i.tenant(),"event_id",id,"aggregate_id",id,"aggregate_revision",1,"event_type","task.model_context.registered","payload",json.canonical(document),"occurred_at",now));
        }else if(!digest.equals(str(one(found),"content_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        prepared.requireUnchanged();registered.requireUnchanged();return requireCurrent(a,id);
    }
    public Registered requireCurrent(EnterpriseAccess.Admission a,String context)throws Exception {
        TaskProfessionalRuntimeService.current(a);var stored=history.requireStored(a.context().tenant_id(),context);var node=stored.request().node();var identity=a.formalProof().identity();
        if(!stored.actorId().equals(a.actorId())||!identity.run().equals(node.run_id())||!identity.step().equals(node.step_id())||!identity.principal().equals(node.principal_id())||!identity.generation().equals(node.lease_generation())||!identity.workerService().equals(node.worker_service_uri()))denied();
        var input=inputs.require(a,node.run_id(),node.step_id());if(!node.node_input_artifact_id().equals(input.inputId())||!node.node_input_sha256().equals(input.inputSha256())||!node.node_input_receipt_sha256().equals(input.receiptSha256()))bad();
        var session=identity.action().equals(node.host_action_id())?runtime.requireSession(a,input,node.session_id()):runtime.requireCurrentSession(a,input,stored.dependencies().required("host_binding"),node.session_id(),context);
        if(!"OPEN".equals(session.state()))denied();var admitted=supervisors.requireAdmitted(session);admitted.requireUnchanged();
        if(!admitted.receiptId().equals(node.supervisor_receipt_id())||!admitted.sha256().equals(node.supervisor_receipt_sha256()))bad();
        var current=state(a,input);var deps=stored.dependencies();
        if(!current.digest().equals(text(deps,"state_sha256"))||!json.tree(current.model()).equals(deps.required("model"))||!json.tree(current.eligibility().safeReplayBinding()).equals(deps.required("model_eligibility"))||!schema.hash(current.replay()).equals(text(deps,"replay_sha256")))stale();
        if(!stored.request().request_parameters().equals(current.binding().required("generation_parameters"))||!stored.request().role_basis().equals(json.basis(current.binding().required("role_version")))||!stored.request().tokenizer_basis().equals(json.basis(current.binding().required("tokenizer_version"))))bad();
        var d=(ModelConfigurationProtocol.Definition)current.model().definition();bindPrice(current.model(),deps.required("price"),d.price_basis());
        if(!text(deps,"price_authorization_sha256").equals(originals.requireCurrentBasis(a,d.price_basis(),"MODEL_CONFIGURATION",node.project_id())))stale();
        packages.requireReviewedPins(a,node.project_id(),deps.required("tokenizer_artifact"));var prompt=deps.required("prompt_compilation");packages.requireReviewedPins(a,node.project_id(),prompt.required("role_package"));
        packages.requireReviewedPins(a,node.project_id(),prompt.required("prompt_package"));packages.requireReviewedPins(a,node.project_id(),prompt.required("profile_package"));
        if(TaskProfessionalPromptV2.COMPILER.equals(text(prompt,"compiler"))){if(predecessors==null)unavailable();predecessors.requireCurrentPins(a,input,prompt.required("predecessor_evidence"),null,null);}
        else if(!input.manifest().required("predecessors").isEmpty())unavailable();
        if(!"PREPARATION_READ_ONLY".equals(text(deps.required("prompt_preparation"),"phase"))||!text(deps,"launch_prompt").equals(text(session.launch(),"prompt"))||!prompt.equals(session.launch().required("prompt_provenance")))stale();
        var transmission=identity.action().equals(node.host_action_id())?json.object().put("phase","PREPARATION_READ_ONLY"):transmission(a,input,current,prompt);
        var content=contents.require(input);if(!json.tree(content.members()).equals(deps.required("exact_input_receipt").required("content_manifest").required("members")))bad();
        if(!budgetBasis(a,stored.request().parent_budget_basis().version_id()).equals(stored.request().parent_budget_basis()))stale();
        var requestNode=json.tree(stored.request());FormalInvocationModelPolicy.require(json.tree(current.model()),requestNode,node.classification(),new BigInteger(text(deps.required("tokenization"),"token_count")));
        if(!json.tree(outputTransport.storageBinding()).equals(deps.required("output_storage"))||!FormalTaskNodeAuthority.sameEvidence(runRetention(a,current.replay()),deps.required("output_retention")))stale();
        var projection=exact.readProjection(Base64.getDecoder().decode(text(deps.required("model_request"),"body_base64")));approvedTools(session,projection.required("tools"));
        var ordered=results.orderedBefore(session,admitted,stored);if(!FormalTaskNodeAuthority.sameEvidence(TaskInvocationHistoryData.fromDependencies(deps,stored.modelRequestOrdinal()),historyData(session,ordered,stored.modelRequestOrdinal())))stale();
        if(!identity.action().equals(node.host_action_id()))((ObjectNode)transmission).set("tool_content",toolReceipts.requireModelSend(a,input,TaskInvocationHistoryData.fromDependencies(deps,stored.modelRequestOrdinal()).required("tool_pins"),stored.request().business_purpose(),d.provider()));
        String digest=schema.hash(Map.of("context_sha256",stored.hash(),"current_state_sha256",current.digest(),"prompt_transmission",transmission));
        input.requireUnchanged();session.requireUnchanged();admitted.requireUnchanged();return new Registered(a,stored,current,digest,transmission);
    }
    public final class Registered {
        private final EnterpriseAccess.Admission admission;private final TaskInvocationOriginals.Stored original;private final State state;private final String digest;private final JsonNode transmission;
        private Registered(EnterpriseAccess.Admission a,TaskInvocationOriginals.Stored original,State state,String digest,JsonNode transmission){admission=a;this.original=original;this.state=state;this.digest=digest;this.transmission=transmission.deepCopy();}
        public void requireCurrent(EnterpriseAccess.Admission a)throws Exception {var current=TaskInvocationContext.this.requireCurrent(a,contextId());if(!digest.equals(current.digest)||!original.hash().equals(current.original.hash()))stale();}
        public String contextId(){return original.contextId();}public TaskInvocationProtocol.Request request(){return original.request();}public JsonNode dependencies(){return original.dependencies();}
        public JsonNode sendAuthorizations()throws Exception {requireCurrent(admission);if(!"MODEL_CALL".equals(text(transmission,"phase")))denied();return transmission.deepCopy();}
        public ModelConfigurationProtocol.Fixed model(){return state.model();}public JsonNode providerBody()throws Exception{
            requireCurrent(admission);var n=request().node();producers.requireInvocation(admission,inputs.require(admission,n.run_id(),n.step_id()),this).requireModelInputConsume();
            var body=FormalInvocationSendPreparation.providerBody(json.tree(model().definition()),request().request_parameters(),exact.readExactInput(original.original().bytes()));
            if(ContentReadAuthorization.canonicalBytes(body).length>TaskInvocationContextSchema.MAXIMUM_BYTES)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return body;
        }public String authorizationSha256(){return digest;}
        public FormalInvocationPricing.Schedule price()throws Exception{return original.price();}public String credentialReferenceId(){return state.eligibility().credentialReferenceId();}public String credentialRevision(){return state.eligibility().credentialRevision();}
        public JsonNode exactInputReceipt(){return original.original().receipt();}public String inputSizeBytes(){return original.original().reference().sizeBytes();}
        public List<String> requiredActions(){return state.requiredActions();}public String exactInputReceiptSha256(){return text(original.dependencies(),"exact_input_receipt_sha256");}
        public String modelRequestId(){return text(original.dependencies().required("model_request"),"request_id");}public String modelRequestSha256(){return schemaHash(original.dependencies().required("model_request"));}public long modelRequestOrdinal(){return original.modelRequestOrdinal();}
    }
    public JsonNode outputCommitment(EnterpriseAccess.Admission a,Registered context,String invocation)throws Exception {
        context.requireCurrent(a);return producers.requireInvocation(a,inputs.require(a,context.request().node().run_id(),context.request().node().step_id()),context).commitment(invocation);
    }
    void requireResultRead(EnterpriseAccess.Admission a,TaskInvocationOriginals.Stored stored)throws Exception {
        // Actual current task/source content authorization, independent of the original send
        // and of factual recovery. Closed session consumption is allowed, no new dispatch.
        TaskProfessionalRuntimeService.current(a);var node=stored.request().node();var i=a.formalProof().identity();if(!i.action().equals(node.host_action_id())||!i.run().equals(node.run_id())||!i.step().equals(node.step_id())||!i.actor().equals(stored.actorId()))denied();
        var input=inputs.require(a,node.run_id(),node.step_id());var session=runtime.requireSession(a,input,node.session_id());session.requireUnchanged();
        if(!node.node_input_artifact_id().equals(input.inputId())||!node.node_input_sha256().equals(input.inputSha256())||!node.node_input_receipt_sha256().equals(input.receiptSha256()))bad();
        resources.require(a,new PermissionProtocol.Scope(node.tenant_id(),node.project_id(),"TASK",node.task_id()),"task.read",stored.request().business_purpose());
        var current=contents.require(input);if(!current.members().equals(history.contentMembers(stored)))stale();current.requireUnchanged();stored.original().requireUnchanged();
    }
    private Map<String,Object> meta(FormalWorkerAccess.Identity i,String classification,LocalDateTime now){var result=new LinkedHashMap<String,Object>();result.put("tenant_id",i.tenant());result.put("project_id",i.project());result.put("schema_version","1.0.0");result.put("schema_manifest_sha256",exact.sha256());result.put("created_by",i.actor());result.put("created_at",now);result.put("classification",classification);return result;}
    private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());if(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray())!=1)bad();}
    private static String schemaHash(JsonNode value){try{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}catch(Exception e){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}}
    private static void requireTenant(String tenant){if(!TransactionSynchronizationManager.isActualTransactionActive()||!TenantScope.requireCurrent().tenantId().equals(tenant))denied();}
    private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
    private static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    private static <T>T one(List<T> values){if(values.size()!=1||values.get(0)==null)bad();return values.get(0);}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}

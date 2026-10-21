package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Closed AT-03/04/08 admission. Neither a tool name, a registered UUID, nor an SDK
 * request is an authority. Every checked value retains the actual C07 call proof.
 * The initial TASK implementation is deliberately N02-only.
 */
public final class TaskRegisteredToolAuthority {
    private final JdbcTemplate db;
    private final TaskProfessionalSupervisor supervisors;
    private final FormalRuntimeCapabilityIdentityAuthority capabilities;
    private final FormalCapabilityIdentityAuthority roles;
    private final ResourceReadAuthority resources;
    private final PermissionSchema json;
    private final SchemaCatalogue catalogue;
    private final com.fasterxml.jackson.databind.json.JsonMapper parser=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public TaskRegisteredToolAuthority(DataSource data,TaskProfessionalSupervisor supervisors,
            FormalRuntimeCapabilityIdentityAuthority capabilities,FormalCapabilityIdentityAuthority roles,
            ResourceReadAuthority resources,PermissionSchema json)throws Exception {
        this(new JdbcTemplate(data),supervisors,capabilities,roles,resources,json);
    }
    TaskRegisteredToolAuthority(JdbcTemplate db,TaskProfessionalSupervisor supervisors,
            FormalRuntimeCapabilityIdentityAuthority capabilities,FormalCapabilityIdentityAuthority roles,
            ResourceReadAuthority resources,PermissionSchema json)throws Exception {
        this.db=db;db.setQueryTimeout(5);this.supervisors=supervisors;this.capabilities=capabilities;
        this.roles=roles;this.resources=resources;this.json=json;catalogue=SchemaCatalogue.fromClasspath();
    }
    interface CallProof {void requireUnchanged()throws Exception;JsonNode canonicalPins()throws Exception;}
    private record ActualCall(TaskInvocationService.ToolCall call) implements CallProof {
        public void requireUnchanged()throws Exception{call.requireUnchanged();}
        public JsonNode canonicalPins()throws Exception{return call.canonicalPins();}
    }
    private record HistoricalCall(TaskInvocationResults.OrderedHistory history,String callId) implements CallProof {
        public void requireUnchanged()throws Exception{history.requireUnchanged();}
        public JsonNode canonicalPins()throws Exception{return history.toolCallPins(callId);}
    }
    public Checked require(TaskProfessionalRuntimeService.Session session,String tool,JsonNode request,
            TaskInvocationService.ToolCall call)throws Exception {
        return require(session,tool,request,new ActualCall(Objects.requireNonNull(call)));
    }
    Checked require(TaskProfessionalRuntimeService.Session session,String tool,JsonNode request,
            TaskInvocationResults.OrderedHistory history)throws Exception {
        history.requireFor(session);return require(session,tool,request,new HistoricalCall(history,text(request,"call_id")));
    }
    Checked require(TaskProfessionalRuntimeService.Session session,String tool,JsonNode request,CallProof call)throws Exception {
        return new Checked(session,tool,request.deepCopy(),call,inspect(session,tool,request,call,false),false);
    }
    Checked requireClosedWaiting(TaskProfessionalRuntimeService.Session session,JsonNode request,
            TaskInvocationService.WaitingTerminal terminal,TaskProfessionalSupervisor.Closed closure)throws Exception {
        terminal.requireFor(session,closure);
        var call=new CallProof(){public void requireUnchanged()throws Exception{terminal.requireFor(session,closure);}
            public JsonNode canonicalPins()throws Exception{return terminal.canonicalCallPins();}};
        return new Checked(session,"AT-08",request.deepCopy(),call,inspect(session,"AT-08",request,call,true),true);
    }
    private record State(JsonNode pins,PermissionProtocol.Basis content,JsonNode predecessor,
            String classification,java.math.BigInteger maximumCalls) {}
    private State inspect(TaskProfessionalRuntimeService.Session session,String tool,JsonNode request,CallProof call,boolean closed)throws Exception {
        validateRequest(tool,request,catalogue);session.requireUnchanged();call.requireUnchanged();
        if(!(closed?"CLOSED":"OPEN").equals(session.state())||closed&&!"AT-08".equals(tool))deny();
        var a=session.admission();var input=session.input();var fixed=new TaskNodeInputSchema().read(ContentReadAuthorization.canonicalBytes(input.manifest()));
        var waiting=db.queryForList("SELECT action_id FROM waiting_item WHERE tenant_id=? AND project_id=? AND run_id=? AND step_id=? AND reason_code='ASK_USER_QUESTION' LIMIT 2 FOR UPDATE",
                input.tenantId(),input.projectId(),input.runId(),input.stepId());
        if(!waiting.isEmpty()&&(!"AT-08".equals(tool)||waiting.size()!=1
                ||!id("TASK_REGISTERED_TOOL_ACTION",session.sessionId(),text(request,"call_id")).equals(str(waiting.get(0),"action_id"))))
            throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
        String roleName=text(session.deployment().value(),"role_name");requireNode(fixed.nodeKind(),roleName,tool);
        var supervision=supervisors.requireAdmitted(session);var callPins=call.canonicalPins();
        for(var e:Map.of("session_id",session.sessionId(),"supervision_receipt_id",supervision.receiptId(),
                "supervision_sha256",supervision.sha256(),"tool_call_id",text(request,"call_id"),
                "tool_name",text(request,"name"),"arguments_sha256",text(request,"arguments_sha256")).entrySet())
            same(e.getValue(),text(callPins,e.getKey()));
        var row=one(db.queryForList("SELECT replay_manifest,classification FROM task_run WHERE tenant_id=? AND project_id=? AND run_id=? AND created_by=? AND execution_kind='TASK' AND status='RUNNING' AND ended_at IS NULL AND dispatch_closed_at IS NULL AND superseded_by_run_id IS NULL LIMIT 2 FOR UPDATE",
                input.tenantId(),input.projectId(),input.runId(),a.actorId()));
        var replay=parser.readTree(str(row,"replay_manifest"));same(fixed.replaySha256(),hash(replay));var release=replay.required("agent_release");
        var role=roles.require(a,fixed.roleVersion(),"ROLE",input.projectId(),"AGENT_PUBLICATION");
        if(!contains(release.required("role_versions"),json.tree(fixed.roleVersion())))deny();
        var args=request.required("arguments");PermissionProtocol.Basis content=null;JsonNode predecessor=json.tree(null);
        var permissions=new ArrayList<RunTokenAuthoritySchema.Permission>();JsonNode skillPins=json.tree(null);
        if("AT-03".equals(tool)) {
            predecessor=exactPredecessor(input.manifest(),text(args,"artifact_id"));
            content=new PermissionProtocol.Basis(text(predecessor,"evidence_artifact_id"),text(predecessor,"evidence_sha256"));
            // TASK read is only the business admission. The bridge MUST additionally obtain
            // exact private predecessor/HOST_PREDECESSOR_CONSUME/source-rights proof before bytes.
            permissions.add(exactPermission(fixed,"TASK",input.taskId(),"task.read",text(replay.required("execution_policy"),"business_purpose")));
        } else if("AT-04".equals(tool)) {
            var selected=new ArrayList<PermissionProtocol.Basis>();
            for(var pin:release.required("skill_versions"))if(text(args,"skill_version").equals(text(pin,"version_id")))selected.add(json.basis(pin));
            var skill=capabilities.require(a,one(selected),"SKILL",input.projectId(),"AGENT_PUBLICATION");
            content=skill.sourceArtifact();var definition=skill.canonicalDefinition();
            permissions.add(exactPermission(fixed,"CONFIGURATION",text(definition,"config_id"),"configuration.read","AGENT_PUBLICATION"));
            // The capability's published scope may not introduce permissions absent from this role/node.
            requireSubset(definition.required("request").required("tool_scope").required("permissions"),
                    role.canonicalDefinition().required("request").required("tool_scope").required("permissions"));
            requireSubset(definition.required("request").required("tool_scope").required("permissions"),json.tree(fixed.nodePermissions()));
            skill.requireUnchanged();skillPins=json.object().set("version",json.tree(skill.version()));
            ((com.fasterxml.jackson.databind.node.ObjectNode)skillPins).set("definition",definition);
        } else {
            permissions.add(exactPermission(fixed,"TASK",input.taskId(),"task.run","TASK_EXECUTION"));
        }
        var versions=new ArrayList<FormalRuntimeCapabilityIdentityAuthority.VerifiedCapability>();
        for(var pin:release.required("tool_versions")) {
            var version=capabilities.require(a,json.basis(pin),"TOOL",input.projectId(),"AGENT_PUBLICATION");
            var definition=version.canonicalDefinition().required("request");
            if(catalogue.schemaRef("tool-inputs",definition(tool)).equals(text(definition.required("input_schema_ref"),"schema_ref"))) {
                same(catalogue.schemaDigest("tool-inputs"),text(definition.required("input_schema_ref"),"schema_sha256"));
                same(catalogue.schemaRef("tool-success",null),text(definition.required("output_schema_ref"),"schema_ref"));
                same(catalogue.schemaDigest("tool-success"),text(definition.required("output_schema_ref"),"schema_sha256"));
                for(var permission:permissions)if(!contains(definition.required("tool_scope").required("permissions"),json.tree(permission)))deny();
                versions.add(version);
            }
        }
        var version=one(versions);var grants=json.object().arrayNode();
        for(var permission:permissions) {
            if(!contains(role.canonicalDefinition().required("request").required("tool_scope").required("permissions"),json.tree(permission)))deny();
            grants.add(json.tree(resources.require(a,permission.scope(),permission.action(),permission.purpose())));
        }
        var pins=json.object().put("role_name",roleName).put("classification",str(row,"classification"));
        pins.set("role_version",json.tree(role.version()));pins.set("role_definition",role.canonicalDefinition());
        pins.set("tool_version",json.tree(version.version()));pins.set("tool_definition",version.canonicalDefinition());
        pins.set("permissions",json.tree(permissions));pins.set("current_grants",grants);pins.set("tool_call",callPins);
        pins.set("skill",skillPins);pins.set("predecessor",predecessor);pins.put("replay_sha256",fixed.replaySha256());
        version.requireUnchanged();role.requireUnchanged();call.requireUnchanged();session.requireUnchanged();
        return new State(pins,content,predecessor,str(row,"classification"),new java.math.BigInteger(text(release.required("limits"),"max_tool_calls")));
    }
    public final class Checked {
        private final TaskProfessionalRuntimeService.Session session;private final String tool;private final JsonNode request;
        private final CallProof call;private final State state;
        private final boolean closed;
        private Checked(TaskProfessionalRuntimeService.Session session,String tool,JsonNode request,CallProof call,State state,boolean closed){
            this.session=session;this.tool=tool;this.request=request;this.call=call;this.state=state;this.closed=closed;
        }
        public void requireUnchanged()throws Exception {
            var current=inspect(session,tool,request,call,closed);if(!state.equals(current))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        }
        public TaskProfessionalRuntimeService.Session session(){return session;}
        public String tool(){return tool;}public JsonNode request(){return request.deepCopy();}
        public JsonNode canonicalPins(){return state.pins().deepCopy();}
        public String classification(){return state.classification();}
        public String actionId(){return id("TASK_REGISTERED_TOOL_ACTION",session.sessionId(),text(request,"call_id"));}
        PermissionProtocol.Basis content(){return state.content();}
        JsonNode predecessor(){return state.predecessor().deepCopy();}
        java.math.BigInteger maximumCalls(){return state.maximumCalls();}
    }
    static void validateRequest(String tool,JsonNode request,SchemaCatalogue catalogue)throws Exception {
        if(!Set.of("AT-03","AT-04","AT-08","AT-09").contains(tool))deny();
        ProfessionalRuntimeObservations.closed(request,Set.of("call_id","name","arguments","arguments_sha256"));
        if(!name(tool).equals(text(request,"name"))||text(request,"call_id").isBlank()||text(request,"call_id").length()>256)deny();
        same(hash(request.required("arguments")),text(request,"arguments_sha256"));
        catalogue.validate("tool-inputs",request.required("arguments"),definition(tool));
        if(ContentReadAuthorization.canonicalBytes(request).length>65536)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if("AT-03".equals(tool)&&(!request.required("arguments").required("cursor").isNull()
                ||!request.required("arguments").required("node_ids").isEmpty()))throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
    }
    static JsonNode exactPredecessor(JsonNode input,String artifact) {
        var matches=new ArrayList<JsonNode>();for(var pin:input.required("predecessors"))
            if(artifact.equals(text(pin,"evidence_artifact_id")))matches.add(pin);
        if(matches.size()!=1)deny();return matches.get(0).deepCopy();
    }
    /** Closed integration entrance. New node kinds need their own real private context adapter, not a catalogue alias. */
    static void requireNode(String node,String role,String tool){if(!"N02".equals(node))deny();requireRole(role,tool);}
    static void requireRole(String role,String tool) {
        if("AT-09".equals(tool)&&!"NetworkResearcher".equals(role))deny();
        if(!Set.of("Researcher","NetworkResearcher").contains(role)||!Set.of("AT-03","AT-04","AT-08","AT-09").contains(tool))deny();
    }
    private RunTokenAuthoritySchema.Permission exactPermission(TaskNodeInputProtocol.Input input,String kind,String id,String action,String purpose) {
        return one(input.nodePermissions().stream().filter(p->kind.equals(p.scope().resource_kind())
                &&id.equals(p.scope().resource_id())&&action.equals(p.action())&&(purpose==null||purpose.equals(p.purpose()))).toList());
    }
    static String name(String tool){return switch(tool){case "AT-03"->"read_artifact";case "AT-04"->"load_skill";case "AT-08"->"ask_user_question";case "AT-09"->"propose_web_acquisition";default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};}
    static String definition(String tool){return switch(tool){case "AT-03"->"ArtifactArgs";case "AT-04"->"SkillArgs";case "AT-08"->"QuestionArgs";case "AT-09"->"WebArgs";default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};}
    static String tool(String name){return switch(name){case "read_artifact"->"AT-03";case "load_skill"->"AT-04";case "ask_user_question"->"AT-08";case "propose_web_acquisition"->"AT-09";default->throw new TenantAccessException(ErrorCode.ACCESS_DENIED);};}
    static String id(String kind,String session,String call){WireValues.uuid(session);return UUID.nameUUIDFromBytes((kind+"|"+session+"|"+call).getBytes(StandardCharsets.UTF_8)).toString();}
    private static boolean contains(JsonNode array,JsonNode value){for(var item:array)if(FormalTaskNodeAuthority.sameEvidence(item,value))return true;return false;}
    private static void requireSubset(JsonNode values,JsonNode allowed){if(!values.isArray()||values.isEmpty())deny();for(var value:values)if(!contains(allowed,value))deny();}
    static String hash(Object value)throws Exception{return WireValues.sha256(ContentReadAuthorization.canonicalBytes(value));}
    static String text(JsonNode n,String field){var value=n.required(field);if(!value.isTextual())bad();return value.textValue();}
    static String str(Map<String,Object> n,String field){var value=n.get(field);if(value==null)bad();return value.toString();}
    static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}
    static void same(Object expected,Object actual){if(expected instanceof JsonNode a&&actual instanceof JsonNode b){if(!FormalTaskNodeAuthority.sameEvidence(a,b))bad();}else if(!Objects.equals(expected,actual))bad();}
    static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

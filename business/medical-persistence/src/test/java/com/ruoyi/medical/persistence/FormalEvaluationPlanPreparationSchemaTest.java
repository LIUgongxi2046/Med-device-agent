package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual staged090 decoder/schema tests. Synthetic wire fixtures carry no authorization proof. */
class FormalEvaluationPlanPreparationSchemaTest {
    final PermissionSchema json = new PermissionSchema();
    final FormalEvaluationPlanPreparationSchema schema = new FormalEvaluationPlanPreparationSchema(json);
    FormalEvaluationPlanPreparationSchemaTest() throws Exception { }

    static String id(int n) { return String.format("00000000-0000-4000-8000-%012d", n); }
    static String hash() { return "a".repeat(64); }
    static PermissionProtocol.Basis basis(int n) { return new PermissionProtocol.Basis(id(n), hash()); }
    static FormalEvaluationExecutionProtocol.Start start() {
        return new FormalEvaluationExecutionProtocol.Start("1.0.0", id(1), hash(), "1", basis(2), basis(3), basis(4), basis(5));
    }
    static FormalEvaluationPlanPreparationSchema.Request request() {
        return new FormalEvaluationPlanPreparationSchema.Request("1.0.0", id(6), start(), id(7), 0, "N01", basis(8));
    }
    static FormalEvaluationContextProtocol.BoundStart boundStart() {
        return new FormalEvaluationContextProtocol.BoundStart("1.0.0", start(), basis(9));
    }
    static final String ORIGINAL_KEY = "original-N01-key:!@#123";
    byte[] bytes(Object value) throws Exception { return schema.canonical(value).getBytes(StandardCharsets.UTF_8); }
    ObjectNode tree(Object value) { return (ObjectNode)json.tree(value); }
    ObjectNode recovery() { return tree(new FormalEvaluationPlanPreparationSchema.Recovery("1.0.0", ORIGINAL_KEY, request())); }
    ObjectNode boundRecovery() { return tree(new FormalEvaluationPlanPreparationSchema.BoundRecovery("1.0.0", ORIGINAL_KEY, boundStart())); }
    interface Decode { Object read(byte[] bytes) throws Exception; }
    record Wire(String name, ObjectNode value, Decode decode) { }
    List<Wire> wires() {
        return List.of(new Wire("Request", tree(request()), schema::request), new Wire("Recovery", recovery(), schema::recovery),
                new Wire("BoundStart", tree(boundStart()), schema::boundStart), new Wire("BoundRecovery", boundRecovery(), schema::boundRecovery));
    }
    void rejected(Decode decoder, byte[] value, String description) {
        var failure=assertThrows(Exception.class, () -> decoder.read(value), description);
        if(failure instanceof SchemaRejection rejection) assertEquals(ErrorCode.SCHEMA_INVALID,rejection.code(),description);
        else assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class,failure,description);
    }

    @Test void roundTripsOriginalRequestAndPreservesExactWireBody() throws Exception {
        String raw = " \n\t" + schema.canonical(request()).replace("1.0.0", "\\u0031.0.0") + " \r\n";
        var decoded = schema.request(raw.getBytes(StandardCharsets.UTF_8));
        assertEquals(request(), decoded.request());
        assertEquals(raw, decoded.body());
        assertEquals(boundStart(), schema.boundStart(bytes(boundStart())));
        assertEquals(ORIGINAL_KEY, schema.recovery(bytes(recovery())).originalKey());
        assertEquals(request(), schema.recovery(bytes(recovery())).originalRequest());
        assertEquals(ORIGINAL_KEY, schema.boundRecovery(bytes(boundRecovery())).originalKey());
        assertEquals(boundStart(), schema.boundRecovery(bytes(boundRecovery())).originalRequest());
    }

    @Test void nullableTemplateIsExplicitAndDoesNotMeanOmitted() throws Exception {
        var node = tree(request()).putNull("template");
        assertNull(schema.request(bytes(node)).request().template());
        node.remove("template");
        rejected(schema::request, bytes(node), "nullable template remains required");
    }

    @Test void allEntrypointsRejectRootDuplicatesAndTrailingTokens() throws Exception {
        for (var wire : wires()) {
            String raw = schema.canonical(wire.value());
            rejected(wire.decode(), ("{\"schema_version\":\"1.0.0\"," + raw.substring(1)).getBytes(StandardCharsets.UTF_8), wire.name()+" duplicate");
            for (String suffix : List.of(" {}", " []", " true", " null", " garbage"))
                rejected(wire.decode(), (raw + suffix).getBytes(StandardCharsets.UTF_8), wire.name()+" trailing "+suffix);
        }
    }

    @Test void nestedDuplicateKeysCannotBeHiddenInsideOriginalRequests() throws Exception {
        for (var wire : wires()) {
            String raw = schema.canonical(wire.value()).replaceFirst("\\\"version_id\\\":", "\"version_id\":\""+id(2)+"\",\"version_id\":");
            rejected(wire.decode(), raw.getBytes(StandardCharsets.UTF_8), wire.name()+" duplicate nested basis");
        }
    }

    @Test void allEntrypointsEnforceStrictUtf8AndNonemptyBoundedBodies() throws Exception {
        for (var wire : wires()) {
            for (byte[] raw : new byte[][] {null, new byte[0], new byte[]{(byte)0xc3, 0x28},
                    new byte[]{(byte)0xff, (byte)0xfe, '{', 0, '}', 0}, new byte[FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES+1]})
                rejected(wire.decode(), raw, wire.name()+" malformed or oversized bytes");
            for (String raw : List.of(" ", "null", "[]", "{}", "\"text\"", "1", "{"))
                rejected(wire.decode(), raw.getBytes(StandardCharsets.UTF_8), wire.name()+" malformed shape "+raw);
        }
    }

    @Test void exactMaximumBodySizeIsAcceptedAndNextByteRejected() throws Exception {
        for (var wire : wires()) {
            byte[] raw = bytes(wire.value());
            byte[] maximum = Arrays.copyOf(raw, FormalEvaluationPlanPreparationSchema.MAXIMUM_BYTES);
            Arrays.fill(maximum, raw.length, maximum.length, (byte)' ');
            assertNotNull(wire.decode().read(maximum), wire.name());
            rejected(wire.decode(), Arrays.copyOf(maximum, maximum.length+1), wire.name()+" exceeds exact cap");
        }
    }

    @Test void requestObjectsAreClosedAtEveryNestedObject() throws Exception {
        for (var wire : wires()) {
            for (String path : objectPaths(wire.value())) {
                var bad = wire.value().deepCopy();
                ((ObjectNode)bad.at(path)).put("caller_supplied_authority", true);
                rejected(wire.decode(), bytes(bad), wire.name()+path+" must be closed");
            }
            for (String path : objectPaths(wire.value())) for (String field : fieldNames(wire.value().at(path))) {
                var bad = wire.value().deepCopy(); ((ObjectNode)bad.at(path)).remove(field);
                rejected(wire.decode(), bytes(bad), wire.name()+path+" missing "+field);
            }
        }
    }

    @Test void callersCannotSupplyPlanPayloadAuthorityOrCompletion() throws Exception {
        for (String field : List.of("plan_candidate", "plan", "payload", "artifact_registration", "authorization", "approved",
                "allowed", "action_summary", "context_binding", "source_snapshot", "profile_revision", "coverage", "runnable",
                "professional_completion", "object_version", "receipt_sha256", "tenant_id", "actor_id", "provider_receipt")) {
            var bad = tree(request()); bad.putObject(field).put("caller_claim", true);
            rejected(schema::request, bytes(bad), "Request forbids "+field);
            var wrapped = recovery(); wrapped.set("original_request", bad);
            rejected(schema::recovery, bytes(wrapped), "Recovery forbids "+field);
        }
        for (var wire : wires()) {
            var bad=wire.value().deepCopy();
            String prefix=wire.name().equals("Recovery")||wire.name().equals("BoundRecovery")?"/original_request/start":"/start";
            ((ObjectNode)bad.at(prefix)).put("approved",true);
            rejected(wire.decode(),bytes(bad),wire.name()+" start authority");
        }
    }

    @Test void requestRejectsWrongNodeUuidHashVersionRepeatAndRevision() throws Exception {
        for (String node : List.of("N02", "n01", "N01 ", "")) {
            var bad=tree(request()).put("node_kind",node); rejected(schema::request,bytes(bad),"node "+node);
        }
        for (int repeat : new int[]{-1,100}) {
            var bad=tree(request()).put("repeat_index",repeat); rejected(schema::request,bytes(bad),"repeat "+repeat);
        }
        for (ObjectNode bad : List.of(tree(request()).put("repeat_index",0.5),tree(request()).put("repeat_index","0"),
                tree(request()).put("schema_version","2.0.0"),tree(request()).put("execution_id","not-a-uuid"),tree(request()).putNull("sample_id")))
            rejected(schema::request,bytes(bad),"wrong request field type or format");
        for (String path : List.of("/start/candidate","/start/execution_policy","/start/enterprise_budget","/start/parent_budget","/template")) {
            var bad=tree(request());((ObjectNode)bad.at(path)).put("content_sha256","A".repeat(64));
            rejected(schema::request,bytes(bad),path+" uppercase digest");
            ((ObjectNode)bad.at(path)).put("content_sha256",hash()+"\n");
            rejected(schema::request,bytes(bad),path+" terminal newline in digest");
        }
        for (String revision : List.of("0","01","-1","1.0"," 1","1".repeat(21),"1\n")) {
            var bad=tree(request());((ObjectNode)bad.at("/start")).put("expected_evaluation_revision",revision);
            rejected(schema::request,bytes(bad),"invalid revision "+revision);
        }
        assertEquals(99,schema.request(bytes(tree(request()).put("repeat_index",99))).request().repeatIndex());
    }

    @Test void boundContextBasisCannotCarryMalformedDigest() throws Exception {
        for(String digest:List.of("A".repeat(64),hash()+"\n",hash()+"\r\n")) {
            var bound=tree(boundStart());((ObjectNode)bound.required("context_binding")).put("content_sha256",digest);
            rejected(schema::boundStart,bytes(bound),"bound context digest");
            var recovery=boundRecovery();recovery.set("original_request",bound);
            rejected(schema::boundRecovery,bytes(recovery),"recovery bound context digest");
        }
    }

    @Test void recoveryRequiresExactOriginalKeyAndTypedOriginalBody() throws Exception {
        for (String key : List.of("short", "x".repeat(129), "x".repeat(15), "x".repeat(16)+" ", "x".repeat(16)+"\n", "键".repeat(16))) {
            rejected(schema::recovery,bytes(recovery().put("original_key",key)),"recovery key "+key);
            rejected(schema::boundRecovery,bytes(boundRecovery().put("original_key",key)),"bound recovery key "+key);
            assertThrows(SchemaRejection.class,()->FormalEvaluationPlanPreparationSchema.key(key));
        }
        for (String key : List.of("x".repeat(16), "x".repeat(128), ORIGINAL_KEY)) {
            FormalEvaluationPlanPreparationSchema.key(key);
            assertEquals(key,schema.recovery(bytes(recovery().put("original_key",key))).originalKey());
        }
        assertThrows(SchemaRejection.class,()->FormalEvaluationPlanPreparationSchema.key(null));
        var wrong=recovery();wrong.set("original_request",tree(boundStart()));rejected(schema::recovery,bytes(wrong),"wrong original request type");
        wrong=boundRecovery();wrong.set("original_request",tree(request()));rejected(schema::boundRecovery,bytes(wrong),"wrong bound request type");
    }

    @Test void closedResponseDefinitionsAcceptExplicitNonRunnableFixtures() throws Exception {
        for (var entry : responses().entrySet()) assertSame(entry.getValue(),schema.validate(entry.getKey(),entry.getValue()),entry.getKey());
        var unselected=preview().putNull("selected_template").putNull("plan_candidate").put("selection_state","CHOICE_REQUIRED");
        assertSame(unselected,schema.validate("Preview",unselected));
        for (String state:List.of("NOT_FOUND","PENDING")) {
            var status=statusReceipt().put("state",state).putNull("receipt");assertSame(status,schema.validate("Status",status));
        }
    }

    @Test void responsesRejectUnknownPropertiesAtEveryNestedObject() {
        for (var entry : responses().entrySet()) for (String path : objectPaths(entry.getValue())) {
            var bad=entry.getValue().deepCopy();((ObjectNode)bad.at(path)).put("unexpected_authority",true);
            assertThrows(SchemaRejection.class,()->schema.validate(entry.getKey(),bad),entry.getKey()+path);
        }
    }

    @Test void responsesRequireEveryDeclaredFieldAtEveryNestingLevel() {
        for (var entry : responses().entrySet()) for (String path : objectPaths(entry.getValue())) for (String field : fieldNames(entry.getValue().at(path))) {
            var bad=entry.getValue().deepCopy();((ObjectNode)bad.at(path)).remove(field);
            assertThrows(SchemaRejection.class,()->schema.validate(entry.getKey(),bad),entry.getKey()+path+" requires "+field);
        }
    }

    @Test void responseDigestsAndQuestionKeysCannotCarryTerminalLineBreaks() {
        // Direct actual networknt validation, not Start record constructors or the recovery Java guard.
        for(var scalar:Map.of("Hash",hash(),"Revision","1","Key","question_key","KeyOriginal",ORIGINAL_KEY,"Id",id(1)).entrySet()) {
            schema.validate(scalar.getKey(),json.tree(scalar.getValue()));
            for(String suffix:List.of("\n","\r\n","\u2028","\u2029"))
                assertThrows(SchemaRejection.class,()->schema.validate(scalar.getKey(),json.tree(scalar.getValue()+suffix)),scalar.getKey()+" actual scalar regex/format");
        }
        for(var entry:responses().entrySet()) for(String path:objectPaths(entry.getValue())) for(String field:fieldNames(entry.getValue().at(path))) {
            if(!field.endsWith("sha256")&&!field.equals("question_key"))continue;
            for(String suffix:List.of("\n","\r\n")) {
                var bad=entry.getValue().deepCopy();var target=(ObjectNode)bad.at(path);
                target.put(field,target.required(field).asText()+suffix);
                assertThrows(SchemaRejection.class,()->schema.validate(entry.getKey(),bad),entry.getKey()+path+"/"+field+" terminal line break");
            }
        }
    }

    @Test void preparationResponsesCannotClaimRunnableOrProfessionalCompletion() {
        for (String type : List.of("Preview","Prepared","PreparationIdentity")) for (String field : List.of("runnable","professional_completion")) {
            var bad=responses().get(type).put(field,true);
            assertThrows(SchemaRejection.class,()->schema.validate(type,bad),type+" "+field);
        }
        for (String field : List.of("complete_evaluation","professional_completed_cells","selected_cells")) {
            var bad=preview();var coverage=(ObjectNode)bad.required("coverage");
            if(field.equals("complete_evaluation"))coverage.put(field,true);else coverage.put(field,2);
            assertThrows(SchemaRejection.class,()->schema.validate("Preview",bad),field);
        }
        var completed=identity().put("state","COMPLETED");assertThrows(SchemaRejection.class,()->schema.validate("PreparationIdentity",completed));
        var content=statusReceipt().put("content_available",true);assertThrows(SchemaRejection.class,()->schema.validate("Status",content));
        var receipt=statusReceipt();((ObjectNode)receipt.required("receipt")).set("preview",preview());
        assertThrows(SchemaRejection.class,()->schema.validate("Status",receipt),"metadata receipt cannot expose plan content");
    }

    @Test void requestHashUsesCanonicalOriginalAndSeparatesIdentityAndContext() throws Exception {
        String raw=schema.canonical(request()), first=schema.requestHash(id(20),id(21),"1",raw);
        assertTrue(schema.manifest().matches("[a-f0-9]{64}"));
        assertEquals(first,schema.requestHash(id(20),id(21),"1"," \n"+raw+"\t"));
        assertNotEquals(first,schema.requestHash(id(22),id(21),"1",raw));
        assertNotEquals(first,schema.requestHash(id(20),id(22),"1",raw));
        assertNotEquals(first,schema.requestHash(id(20),id(21),"2",raw));
        assertNotEquals(first,schema.requestHash(id(20),id(21),"1",schema.canonical(tree(request()).put("repeat_index",1))));
    }

    ObjectNode registration() { return json.object().put("schema_version","1.0.0").put("execution_id",id(6)).put("request_sha256",hash()).put("expected_cells",1).put("expected_nodes",1); }
    ObjectNode identity() { return json.object().put("schema_version","1.0.0").put("operation_id",id(30)).put("write_id",id(31)).put("attempt_id",id(32))
            .put("execution_id",id(6)).put("artifact_id",id(33)).put("artifact_sha256",hash()).put("object_version","fixture-object-version")
            .put("receipt_sha256",hash()).put("state","PREPARED").put("runnable",false).put("professional_completion",false); }
    ObjectNode preview() {
        var result=json.object().put("schema_version","1.0.0").put("preparation_kind","DETERMINISTIC_PLAN_DRAFT").put("execution_id",id(6))
                .put("evaluation_id",id(1)).put("profile_revision",id(10)).put("profile_sha256",hash()).put("sample_id",id(7)).put("repeat_index",0)
                .put("document_type","技术文档").put("selection_state","SELECTED").put("runnable",false).put("professional_completion",false);
        for(String name:List.of("candidate","execution_policy","context_binding","source_snapshot","selected_template"))result.set(name,tree(basis(8)));
        result.putObject("coverage").put("scope","SELECTED_CELL").put("expected_cells",1).put("expected_nodes",1).put("selected_cells",1).put("professional_completed_cells",0).put("complete_evaluation",false);
        result.putArray("templates").addObject().put("document_type","技术文档").put("applicability","APPLICABLE").put("eligible",true).set("template",tree(basis(8)));
        var plan=result.putObject("plan_candidate").put("template_version",id(8)).put("profile_revision",id(10));
        plan.putArray("requirement_node_ids").add(id(11));plan.putArray("pending_condition_ids").add(id(14));
        var section=plan.putArray("sections").addObject().put("section_id",id(12)).put("goal","Synthetic directory section").putNull("owner_id").put("method","HUMAN_PROVIDED");
        section.putArray("requirement_node_ids").add(id(11));section.putArray("depends_on_section_ids");section.putArray("completion_rule_ids").add(id(13));
        var question=plan.putArray("questions").addObject().put("question_key","Q_1").put("section_id",id(12)).put("question","Synthetic question?");
        question.putArray("requirement_ids").add(id(11));question.putArray("completion_rule_ids").add(id(13));question.putArray("depends_on_question_keys");
        var condition=result.putArray("conditions").addObject().put("scope","NODE").put("target_id",id(11)).put("engine_revision","fixture-1").put("status","UNKNOWN");
        condition.set("rule_revision",tree(basis(14)));condition.putArray("input_keys").add("device_type");condition.putArray("unknown_input_keys").add("device_type");
        result.putArray("blockers").addObject().put("code","OWNER_UNASSIGNED").putArray("target_ids").add(id(12));
        result.putArray("action_summary").addObject().put("action","artifact.register").put("resource_kind","EVALUATION").put("resource_id",id(1))
                .put("project_id",id(15)).put("purpose","EVALUATION_RESULTS").put("allowed",false);
        return result;
    }
    ObjectNode prepared() { var result=identity();result.set("preview",preview());return result; }
    ObjectNode statusReceipt() { var result=json.object().put("schema_version","1.0.0").put("state","PREPARED").put("content_available",false);result.set("receipt",identity());return result; }
    ObjectNode availability() {
        var result=json.object().put("schema_version","1.0.0").put("unavailable_reason","DEPENDENCY_UNAVAILABLE");
        var flags=result.putObject("features");for(String flag:List.of("bound_preparation","execution_recovery","plan_preview","plan_preparation","status","object_recovery"))flags.put(flag,false);
        return result;
    }
    Map<String,ObjectNode> responses() { return Map.of("Registration",registration(),"Preview",preview(),"Prepared",prepared(),"PreparationIdentity",identity(),"Status",statusReceipt(),"Availability",availability()); }
    static List<String> fieldNames(JsonNode node) { var result=new ArrayList<String>();node.fieldNames().forEachRemaining(result::add);return result; }
    static List<String> objectPaths(JsonNode node) { var result=new ArrayList<String>();collect(node,"",result);return result; }
    private static void collect(JsonNode node,String path,List<String> result) {
        if(node.isObject()) { result.add(path);node.fields().forEachRemaining(e->collect(e.getValue(),path+"/"+e.getKey().replace("~","~0").replace("/","~1"),result)); }
        else if(node.isArray())for(int i=0;i<node.size();i++)collect(node.get(i),path+"/"+i,result);
    }
}

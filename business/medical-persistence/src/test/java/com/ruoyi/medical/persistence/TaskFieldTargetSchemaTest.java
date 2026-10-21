package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Shape tests use synthetic data, never produce source, registration or approval authority. */
class TaskFieldTargetSchemaTest {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final String H="a".repeat(64);
    final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();
    TaskFieldTargetSchemaTest()throws Exception { }
    @Test void mappingAuthorIsClosedProposedDataWithoutOccurrenceOrValueAllocation()throws Exception {
        var request=mappingAuthor();assertEquals(request,schema.validate("MappingAuthor",request));
        for(String key:List.of("field_id","entry_id","candidate_value","raw_value","alias_value","tenant_id")) {
            var bad=request.deepCopy();mapping(bad).put(key,id(90));reject("MappingAuthor",bad);
        }
        var bad=request.deepCopy();bad.put("status","APPROVED");reject("MappingAuthor",bad);
    }
    @Test void mappingPinsAndCasAreRequiredAndNoAliasIsAccepted()throws Exception {
        for(String key:List.of("task_revision","task_input_sha256","plan_revision","plan_sha256","template_version","template_sha256","profile_revision","scope_snapshot","reason","expected_revision")) {
            var request=mappingAuthor();request.remove(key);reject("MappingAuthor",request);
        }
        var request=mappingAuthor();request.put("expected_revision","1");reject("MappingAuthor",request);
        request.put("base_mapping_id",id(88));assertNotNull(schema.validate("MappingAuthor",request));
        request.put("expected_revision","18446744073709551616");reject("MappingAuthor",request);
    }
    @Test void sourceKindsAreExactlyThePublishedFiveAndExplicitUnknownSurvives()throws Exception {
        for(String kind:TaskFieldTargetProtocol.SOURCE_EXACT_KINDS) {
            var request=mappingAuthor();mapping(request).set("source_exact",source().put("field_kind",kind));
            assertNotNull(schema.validate("MappingAuthor",request));
        }
        var request=mappingAuthor();mapping(request).set("source_exact",source().put("field_kind","NUMBER"));reject("MappingAuthor",request);
        request=mappingAuthor();mapping(request).putNull("source_exact").put("model_context","UNRESOLVED").put("applicability","UNKNOWN");
        assertNotNull(schema.validate("MappingAuthor",request));
    }
    @Test void contextsNeverSilentlyConvertEmptyToKnownScopedOrNonemptyToGlobal()throws Exception {
        var request=mappingAuthor();mapping(request).put("model_context","KNOWN_SCOPED");reject("MappingAuthor",request);
        mapping(request).putArray("model_ids").add(id(70));assertNotNull(schema.validate("MappingAuthor",request));
        mapping(request).put("model_context","KNOWN_GLOBAL");reject("MappingAuthor",request);
    }
    @Test void duplicateAuthorEntryKeysFailBeforeAnyOccurrenceExists()throws Exception {
        var request=mappingAuthor();request.withArray("mappings").add(mapping(request).deepCopy());reject("MappingAuthor",request);
    }
    @Test void explicitEmptyTemplateFieldInventoryIsPreservedWithoutClaimingChecks()throws Exception {
        var manifest=mappingManifest();manifest.putArray("mappings");manifest.putArray("inventory");
        assertNotNull(schema.validate("MappingManifest",manifest));
        var view=JSON.createObjectNode().put("schema_version","1.0.0").put("assignment_sha256",schema.hash(manifest)).put("revision","1").put("is_current",true);
        view.set("manifest",manifest);assertNotNull(schema.validate("MappingView",view));
        ((ObjectNode)view.required("manifest")).put("status","APPROVED");reject("MappingView",view);
    }
    @Test void optionalUnsupportedAndUnknownFieldsRemainInventoryNotTargetSuccess()throws Exception {
        var manifest=mappingManifest();var item=inventory(manifest);var field=(ObjectNode)item.required("field_definition");
        item.put("value_type","NUMBER").put("state","UNSUPPORTED_TYPE");field.put("value_type","NUMBER");
        assertNotNull(schema.validate("MappingManifest",manifest));assertFalse(item.required("required").booleanValue());
        item.put("state","MAPPED");reject("MappingManifest",manifest);
        manifest=mappingManifest();inventory(manifest).put("state","UNKNOWN_CONDITION");mapping(manifest).put("applicability","UNKNOWN");
        assertNotNull(schema.validate("MappingManifest",manifest));
    }
    @Test void templateFieldDefinitionCannotBeSwappedOrPartiallyProjected()throws Exception {
        var manifest=mappingManifest();((ObjectNode)inventory(manifest).required("field_definition")).put("field_id",id(99));reject("MappingManifest",manifest);
        manifest=mappingManifest();((ObjectNode)inventory(manifest).required("field_definition")).remove("evidence_rule");reject("MappingManifest",manifest);
        manifest=mappingManifest();inventory(manifest).putArray("entry_keys");reject("MappingManifest",manifest);
    }
    @Test void originalAndParsedReceiptsRequireActualFullRangeEqualityAndOriginalPurpose()throws Exception {
        var receipt=sourceReceipt();assertNotNull(schema.validate("SourceReadReceipt",receipt));
        for(String key:List.of("original_read","parsed_read")) {
            var bad=receipt.deepCopy();((ObjectNode)bad.required(key)).put("length","3");reject("SourceReadReceipt",bad);
            bad=receipt.deepCopy();((ObjectNode)bad.required(key)).put("delivered_sha256","b".repeat(64));reject("SourceReadReceipt",bad);
        }
        for(String purpose:List.of("STRICT_FIELD_PREPARATION","TASK_FIELD_TARGET_INPUT")){var bad=receipt.deepCopy();bad.put("purpose",purpose);reject("SourceReadReceipt",bad);}
        var bad=receipt.deepCopy();bad.put("actor_id",id(99));reject("SourceReadReceipt",bad);
        bad=receipt.deepCopy();bad.remove("processing_project_id");reject("SourceReadReceipt",bad);
    }
    @Test void preRunTargetIsTaskOwnedProposedTextWithExactEntryCoverage()throws Exception {
        var manifest=targetManifest();assertNotNull(schema.validate("TargetManifest",manifest));
        for(String key:List.of("run_id","document_revision")){var bad=manifest.deepCopy();bad.put(key,id(99));reject("TargetManifest",bad);}
        var bad=manifest.deepCopy();bad.put("owner_kind","RUN");reject("TargetManifest",bad);
        bad=manifest.deepCopy();bad.putArray("entry_bindings");reject("TargetManifest",bad);
        bad=manifest.deepCopy();((ObjectNode)bad.at("/target/entries/0/candidate_value")).put("unit","mm");reject("TargetManifest",bad);
        bad=manifest.deepCopy();((ObjectNode)bad.at("/target/entries/0/provenance/source")).putNull("source_exact");reject("TargetManifest",bad);
    }
    @Test void explicitEmptyTargetIsDistinctFromOriginalNonemptyComparisonContract()throws Exception {
        var manifest=targetManifest();manifest.putArray("inventory");manifest.putArray("entry_bindings");((ObjectNode)manifest.required("target")).putArray("entries");
        assertNotNull(schema.validate("TargetManifest",manifest));
        assertThrows(SchemaRejection.class,()->new StrictFieldComparisonSchema().target(manifest.required("target")));
    }
    @Test void sourceLineageCannotBeRelabeledAsGovernanceOrViceVersa()throws Exception {
        var target=targetManifest();target.withArray("source_lineage").add(version(id(50)));
        target.withArray("governance_pins").add(version(id(50)));reject("TargetManifest",target);
    }
    @Test void inputCapabilityPinsOnlyOneAlreadyRegisteredArtifactAndExactConsumer()throws Exception {
        var request=inputAuthor();assertNotNull(schema.validate("InputAuthor",request));
        for(String key:List.of("artifact_id","registration_id","producing_receipt_sha256","assignment_id","template_sha256","artifact_schema_sha256","occurrence_ids","maximum_read_bytes","object_ref")) {
            var bad=request.deepCopy();bad.remove(key);reject("InputAuthor",bad);
        }
        var bad=request.deepCopy();bad.put("artifact_id","*");reject("InputAuthor",bad);
        bad=request.deepCopy();bad.put("consumer_node_kind","N04");reject("InputAuthor",bad);
        bad=request.deepCopy();bad.put("approved",true);reject("InputAuthor",bad);
        bad=request.deepCopy();bad.put("maximum_read_bytes","1");reject("InputAuthor",bad);
        bad=request.deepCopy();bad.put("object_version","replaced");reject("InputAuthor",bad);
    }
    @Test void approvalIsSeparateFromAuthoringAndCannotApproveFutureOrWildcardInput()throws Exception {
        var decision=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",id(2)).put("expected_revision","1").put("capability_sha256",H).put("decision","APPROVE").put("reason","Independent review");
        assertNotNull(schema.validate("InputDecide",decision));decision.put("decision","ENABLE");reject("InputDecide",decision);
        decision.put("decision","DISABLE").put("artifact_id",id(10));reject("InputDecide",decision);
    }
    @Test void originalKeyRecoveryAcceptsNoReplacementRequestOrIds()throws Exception {
        var recovery=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",id(2)).put("original_idempotency_key","original-key-000001");
        assertNotNull(schema.validate("Recovery",recovery));recovery.put("new_key","replace-key-000001");reject("Recovery",recovery);
    }
    @Test void strictParserRejectsDuplicateTrailingInvalidUnicodeAndOversizedInputs()throws Exception {
        byte[] good=schema.canonical(mappingAuthor()).getBytes(StandardCharsets.UTF_8);assertNotNull(schema.request("MappingAuthor",good));
        assertThrows(SchemaRejection.class,()->schema.request("MappingAuthor","{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class,()->schema.request("MappingAuthor",(new String(good,StandardCharsets.UTF_8)+" {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(SchemaRejection.class,()->schema.request("MappingAuthor",new byte[]{(byte)0xc3,0x28}));
        assertThrows(SchemaRejection.class,()->schema.read("\"\ud800\""));
        assertThrows(SchemaRejection.class,()->schema.request("MappingAuthor",new byte[8_388_609]));
        assertThrows(SchemaRejection.class,()->schema.validate("UnknownType",mappingAuthor()));
    }
    @Test void defaultConfigurationFailsClosedAndCannotMintPermissions() {
        var policy=new TaskFieldTargetPolicy(0,0,0,0,0,null,null);assertThrows(TenantAccessException.class,policy::requireConfigured);
        assertThrows(IllegalArgumentException.class,()->new TaskFieldTargetPolicy(10001,1,1,1,1,"TEST","local"));
        assertEquals("task.field_mapping.write",TaskFieldTargetProtocol.MAPPING_WRITE);
        assertEquals("task.field_target.produce",TaskFieldTargetProtocol.TARGET_PRODUCE);
        assertEquals("TASK_FIELD_TARGET_INPUT",TaskFieldTargetProtocol.INPUT_PURPOSE);
    }
    @Test void validationReturnsIsolatedTreeAndHashesLiteralStringsWithoutNormalization()throws Exception {
        var input=mappingAuthor();var saved=schema.validate("MappingAuthor",input);input.put("reason","modified");assertNotEquals(input,saved);
        var a=JSON.createObjectNode().put("raw","é");var b=JSON.createObjectNode().put("raw","e\u0301");assertNotEquals(schema.hash(a),schema.hash(b));
    }
    private void reject(String type,JsonNode node){assertThrows(SchemaRejection.class,()->schema.validate(type,node));}
    static String id(int value){return String.format("00000000-0000-0000-0000-%012d",value);}
    static ObjectNode source(){return JSON.createObjectNode().put("source_version",id(21)).put("field_revision",id(27)).put("manifest_sha256",H).put("ordinal",0).put("field_kind","MODEL");}
    static ObjectNode version(String id){return JSON.createObjectNode().put("version_id",id).put("content_sha256",H);}
    static ObjectNode object(){return JSON.createObjectNode().put("object_key","task-fields/exact").put("object_version","v1").put("sha256",H).put("size_bytes","4").put("media_type","application/json");}
    static ObjectNode pins(){return JSON.createObjectNode().put("project_id",id(2)).put("task_revision",id(4)).put("task_input_sha256",H).put("plan_revision",id(5)).put("plan_sha256",H).put("template_version",id(6)).put("template_sha256",H).put("profile_revision",id(60)).put("scope_snapshot",id(61));}
    @org.junit.jupiter.api.Test void sameDefinitionInDistinctActualSectionsKeepsIndependentInventory()throws Exception{
        var manifest=mappingManifest();var second=inventory(manifest).deepCopy().put("section_id",id(80)).put("state","MISSING_MAPPING");second.putArray("entry_keys");
        ((com.fasterxml.jackson.databind.node.ArrayNode)manifest.required("inventory")).add(second);
        assertNotNull(schema.validate("MappingManifest",manifest));
        var wrong=manifest.deepCopy();((ObjectNode)wrong.at("/inventory/1/field_definition")).put("name","Changed definition");reject("MappingManifest",wrong);
        wrong=manifest.deepCopy();((ObjectNode)wrong.at("/inventory/1")).put("section_id",id(8));reject("MappingManifest",wrong);
        wrong=manifest.deepCopy();((ObjectNode)wrong.at("/mappings/0")).put("section_id",id(80));reject("MappingManifest",wrong);
    }
    static ObjectNode mappingAuthor(){
        var n=pins().put("schema_version","1.0.0").put("expected_revision","0").putNull("base_mapping_id").put("reason","Explicit proposed mapping");
        var e=n.putArray("mappings").addObject().put("entry_key",id(13)).put("field_definition_id",id(12)).put("section_id",id(8)).put("node_id",id(9)).putNull("row_id").put("model_context","KNOWN_GLOBAL").put("condition_context","KNOWN_GLOBAL").put("applicability","APPLICABLE");
        e.putArray("model_ids");e.putArray("condition_ids");e.putArray("question_ids");e.set("source_exact",source());return n;
    }
    static ObjectNode mapping(JsonNode n){return (ObjectNode)n.at("/mappings/0");}
    static ObjectNode inventory(JsonNode n){return (ObjectNode)n.at("/inventory/0");}
    static ObjectNode condition(){var n=JSON.createObjectNode().put("mode","ALWAYS").putNull("rule_revision").putNull("engine_revision");n.putArray("input_keys");return n;}
    static ObjectNode evidence(){var n=JSON.createObjectNode().put("mode","DIRECT_REFERENCE").putNull("calculation_rule_revision").put("require_original_locator",true).put("require_human_verification",true);for(String key:List.of("authority_source_types","verification_rule_revisions","input_field_ids"))n.putArray(key);return n;}
    static ObjectNode node(){
        var n=JSON.createObjectNode().put("node_id",id(9)).putNull("parent_id").put("official_number","1").put("title","An actual template node").put("ordinal",1).put("required",false).put("content_mode","DIRECT_REFERENCE").put("nature","OFFICIAL");
        n.set("condition",condition());n.set("evidence_rule",evidence());n.putArray("basis_refs");n.putArray("content_requirements");return n;
    }
    static ObjectNode field(){
        var n=JSON.createObjectNode().put("field_id",id(12)).put("node_id",id(9)).put("key","model").put("name","Model").put("description","Explicit template definition").put("value_type","TEXT").put("required",false).putNull("repeat_id").putNull("attachment_requirement_id");
        n.set("condition",condition());n.set("evidence_rule",evidence());n.putArray("basis_refs");
        var c=n.putObject("constraints");for(String key:List.of("max_length","precision","minimum","maximum","unit"))c.putNull(key);for(String key:List.of("range_basis","enum_options","nullable_reasons"))c.putArray(key);
        n.putObject("output_mapping").put("node_id",id(9)).putNull("repeat_id").putNull("column_key").put("internal_only",false).put("ordinal",1);return n;
    }
    static void inventoryEnvelope(ObjectNode n){
        n.putArray("template_nodes").add(node());n.putArray("repeat_definitions");n.putArray("attachment_requirements");
        var conditions=n.putArray("condition_results");for(String scope:List.of("NODE","FIELD")){
            var c=conditions.addObject().put("scope",scope).put("target_id",id(scope.equals("NODE")?9:12)).putNull("rule_revision").putNull("engine_revision").put("status","APPLICABLE");c.putArray("input_keys");c.putArray("unknown_input_keys");
        }
        var item=n.putArray("inventory").addObject().put("field_definition_id",id(12)).put("section_id",id(8)).put("node_id",id(9)).put("key","model").put("name","Model").put("value_type","TEXT").put("required",false).putNull("repeat_id").put("row_id_required",false).put("state","MAPPED");
        item.putArray("question_ids");item.putArray("entry_keys").add(id(13));item.set("condition",condition());item.set("field_definition",field());
    }
    static ObjectNode mappingManifest(){var n=pins().put("schema_version","1.0.0").put("tenant_id",id(1)).put("task_id",id(3)).put("assignment_id",id(7)).put("status","PROPOSED");n.set("mappings",mappingAuthor().required("mappings"));inventoryEnvelope(n);return n;}
    ObjectNode targetManifest()throws Exception {
        var n=JSON.createObjectNode().put("schema_version","1.0.0").put("artifact_kind","STRICT_FIELD_TARGET").put("owner_kind","TASK").putNull("run_id").putNull("document_revision").put("task_input_sha256",H).put("plan_sha256",H).put("profile_revision",id(60)).put("scope_snapshot",id(61));
        inventoryEnvelope(n);n.set("target",new StrictFieldComparisonEngineTest().target(" x😀 "));
        n.putArray("entry_bindings").addObject().put("entry_key",id(13)).put("entry_id",id(10)).put("field_id",id(11)).put("source_read_receipt_id",id(29));n.putArray("source_lineage");n.putArray("governance_pins");return n;
    }
    ObjectNode sourceReceipt(){
        var n=JSON.createObjectNode().put("schema_version","1.0.0");int i=1;
        for(String key:List.of("tenant_id","project_id","task_id","task_revision","plan_revision","snapshot_id","mapping_revision","entry_key","receipt_id","audit_id","parse_version","index_version","approval_revision","rights_revision","binding_revision","processing_attempt_id","processing_project_id"))n.put(key,id(i++));
        n.put("source_version",id(21)).put("actor_id",id(90)).put("created_by",id(90)).put("created_at","2026-10-20T00:00:00Z").put("classification","INTERNAL").put("purpose","DOCUMENT_BODY").put("environment","TEST").put("region","local");
        for(String key:List.of("mapping_sha256","request_sha256","processing_command_sha256","processing_result_sha256","authority_sha256"))n.put(key,H);
        n.set("source_exact",source());for(String key:List.of("original_read","parsed_read")){var r=n.putObject(key).put("start","0").put("length","4").put("full_sha256",H).put("delivered_sha256",H);r.set("object_ref",object());}return n;
    }
    ObjectNode inputAuthor(){
        var n=JSON.createObjectNode().put("schema_version","1.0.0").put("project_id",id(2)).put("task_id",id(3)).put("task_revision",id(4)).put("task_input_sha256",H).put("plan_revision",id(5)).put("plan_sha256",H).put("template_version",id(6)).put("template_sha256",H).put("assignment_id",id(7)).put("assignment_sha256",H).put("artifact_id",id(50)).put("artifact_sha256",H).put("object_version","v1").put("registration_id",id(51)).put("producing_receipt_sha256",H).put("release_id",id(52)).put("publication_approval_sha256",H).put("expected_revision","0").put("classification","INTERNAL").put("reason","Author exact existing input").put("artifact_schema_sha256",schema.manifest()).put("consumer_node_kind","N03").put("maximum_read_bytes","4");
        n.set("role_version",version(id(53)));n.set("execution_policy",version(id(54)));n.set("object_ref",object());n.putArray("occurrence_ids").add(id(11));return n;
    }
}

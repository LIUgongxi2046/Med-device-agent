package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;

/** Closed shape and consistency checks only. Neither JSON nor its digest proves a source read,
 * current rights, completeness of the genuine template, independent approval or registration.
 * Every mutating host boundary must separately require private current authority and real I/O. */
public final class TaskFieldTargetSchema {
    private static final int MAX_BYTES = 8_388_608;
    private static final BigInteger U64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    private static final Set<String> TYPES = Set.of("MappingAuthor", "MappingManifest", "MappingReceipt",
        "MappingView", "Preparation", "Produce", "Recovery", "SourceReadReceipt", "TargetManifest",
        "TargetIntent", "ProduceReceipt", "Status", "InputAuthor", "InputManifest", "InputDecide",
        "InputReceipt", "InputView", "InputContext");
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;
    private final String manifest;

    public TaskFieldTargetSchema() throws IOException {
        byte[] bytes;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/task-field-target.schema.json"))) {
            bytes = input.readAllBytes();
        }
        manifest = DigestUtils.sha256Hex(bytes);
        try { schema = SchemaCatalogue.fromClasspath().compileApiSchema(json.readTree(bytes)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IOException(failure); }
        for (String type : TYPES) definition(type).initializeValidators();
    }
    public String manifest() { return manifest; }
    public String schemaSha256() { return manifest; }
    public JsonNode tree(Object value) { return json.valueToTree(value); }
    public String canonical(Object value) throws IOException {
        var node = tree(value); bounded(node, 0, new long[]{0}); unicode(node);
        return new String(ContentReadAuthorization.canonicalBytes(node), StandardCharsets.UTF_8);
    }
    public String hash(Object value) throws IOException { return DigestUtils.sha256Hex(canonical(value)); }
    public JsonNode request(String type, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) invalid();
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return validate(type, read(value));
        } catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public JsonNode read(String value) throws IOException {
        if (value == null || value.length() > MAX_BYTES) invalid();
        try {
            StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) invalid();
            return json.readTree(value);
        } catch (IOException | IllegalArgumentException failure) { throw new SchemaRejection(ErrorCode.SCHEMA_INVALID); }
    }
    public JsonNode validate(String type, JsonNode value) throws IOException {
        if (!TYPES.contains(type) || value == null) invalid();
        bounded(value, 0, new long[]{0}); unicode(value); encodedSize(value);
        JsonNode saved = value.deepCopy();
        if (!definition(type).validate(saved).isEmpty()) invalid();
        numericStrings(saved);
        switch (type) {
            case "MappingAuthor" -> {
                mappings(saved.required("mappings"));
                if ("0".equals(text(saved,"expected_revision")) != saved.required("base_mapping_id").isNull()) invalid();
            }
            case "MappingManifest" -> mappingManifest(saved);
            case "MappingView" -> {
                validate("MappingManifest", saved.required("manifest"));
                if (!hash(saved.required("manifest")).equals(text(saved,"assignment_sha256"))) invalid();
            }
            case "Preparation" -> {
                inventory(saved);
                if (!saved.required("current_mapping").isNull()) validate("MappingView",saved.required("current_mapping"));
                validate("Status",saved.required("production"));
            }
            case "SourceReadReceipt" -> sourceReceipt(saved);
            case "TargetManifest" -> targetManifest(saved);
            case "TargetIntent" -> intent(saved);
            case "InputAuthor", "InputManifest" -> input(saved);
            case "InputReceipt" -> {
                if ("DRAFT".equals(text(saved,"status")) != saved.required("decision_id").isNull()) invalid();
                if (saved.required("decision_id").isNull() && !"1".equals(text(saved,"revision"))) invalid();
            }
            case "InputView" -> {
                validate("InputManifest", saved.required("manifest"));
                if (!hash(saved.required("manifest")).equals(text(saved,"capability_sha256"))) invalid();
                if ("DRAFT".equals(text(saved,"status")) != saved.required("latest_decision_id").isNull()) invalid();
            }
            case "InputContext" -> {
                for (var item : saved.required("candidates")) validate("InputAuthor",item);
                for (var item : saved.required("capabilities")) validate("InputView",item);
            }
            case "Status" -> {
                boolean registered = "REGISTERED".equals(text(saved,"state"));
                if (registered == saved.required("receipt").isNull()) invalid();
                if (registered) {
                    validate("ProduceReceipt",saved.required("receipt"));
                    for (String key : List.of("tenant_id","project_id","task_id","assignment_id","assignment_sha256","write_id","artifact_id"))
                        same(saved,key,saved.required("receipt"),key);
                } else if (!saved.required("artifact_id").isNull()) invalid();
            }
            default -> { }
        }
        if (saved.has("schema_manifest_sha256") && !manifest.equals(text(saved,"schema_manifest_sha256"))) invalid();
        return saved;
    }
    private void mappingManifest(JsonNode node) throws IOException {
        mappings(node.required("mappings")); var inventory = inventory(node);
        var expected = new HashMap<String,Set<String>>();
        for (var entry : node.required("mappings")) {
            String field = inventoryKey(entry); var item = inventory.get(field);
            if (item == null || item.required("section_id").isNull()) invalid();
            same(entry,"section_id",item,"section_id"); same(entry,"node_id",item,"node_id");
            if (!strings(item.required("question_ids")).containsAll(strings(entry.required("question_ids")))) invalid();
            expected.computeIfAbsent(field, ignored -> new HashSet<>()).add(text(entry,"entry_key"));
            if ("MAPPED".equals(text(item,"state")) && (!"TEXT".equals(text(item,"value_type"))
                    || entry.required("source_exact").isNull() || unresolved(entry)
                    || item.required("row_id_required").booleanValue() && entry.required("row_id").isNull())) invalid();
        }
        for (var item : inventory.values()) {
            if (!strings(item.required("entry_keys")).equals(expected.getOrDefault(inventoryKey(item),Set.of()))) invalid();
            if ("MAPPED".equals(text(item,"state")) && item.required("entry_keys").isEmpty()) invalid();
        }
    }
    private Map<String,JsonNode> inventory(JsonNode node) {
        var definitions = new HashMap<String,JsonNode>();
        var result = new HashMap<String,JsonNode>(); var nodes = unique(node.required("template_nodes"),"node_id");
        var repeats = unique(node.required("repeat_definitions"),"repeat_id");
        var attachments = unique(node.required("attachment_requirements"),"attachment_requirement_id");
        var conditions = new HashMap<String,JsonNode>();
        for(var condition:node.required("condition_results")) {
            if(conditions.put(text(condition,"scope")+":"+text(condition,"target_id"),condition)!=null) invalid();
            if(!strings(condition.required("input_keys")).containsAll(strings(condition.required("unknown_input_keys")))) invalid();
            if(!"UNKNOWN".equals(text(condition,"status")) && !condition.required("unknown_input_keys").isEmpty()) invalid();
        }
        for (var item : node.required("inventory")) {
            if (result.put(inventoryKey(item),item) != null) invalid();
            var field = item.required("field_definition");
            var previousDefinition=definitions.putIfAbsent(text(item,"field_definition_id"),field);
            if(previousDefinition!=null&&!previousDefinition.equals(field))invalid();
            same(item,"field_definition_id",field,"field_id");
            for (String key : List.of("node_id","key","name","value_type","required","condition","repeat_id")) same(item,key,field,key);
            if (!nodes.containsKey(text(item,"node_id"))) invalid();
            if (!field.required("repeat_id").isNull()) {
                var repeat = repeats.get(text(field,"repeat_id"));
                if (repeat == null || !strings(repeat.required("field_ids")).contains(text(field,"field_id"))) invalid();
                same(item,"row_id_required",repeat,"row_id_required");
            } else if (item.required("row_id_required").booleanValue()) invalid();
            if (!field.required("attachment_requirement_id").isNull()
                    && !attachments.containsKey(text(field,"attachment_requirement_id"))) invalid();
            if (item.required("section_id").isNull() != "UNASSIGNED_SECTION".equals(text(item,"state"))) invalid();
            if("MAPPED".equals(text(item,"state"))) {
                for(String key:List.of("FIELD:"+text(item,"field_definition_id"),"NODE:"+text(item,"node_id"))) {
                    var condition=conditions.get(key);
                    if(condition==null || !"APPLICABLE".equals(text(condition,"status"))) invalid();
                }
            }
        }
        return result;
    }
    private void targetManifest(JsonNode node) throws IOException {
        var fields = inventory(node); var target = node.required("target");
        var sections = unique(target.required("sections"),"section_id");
        var entries = unique(target.required("entries"),"entry_id");
        var occurrences = new HashSet<String>(); var bound = new HashSet<String>(); var keys = new HashSet<String>();
        for (var binding : node.required("entry_bindings")) {
            if (!bound.add(text(binding,"entry_id")) || !keys.add(text(binding,"entry_key"))) invalid();
            var entry = entries.get(text(binding,"entry_id"));
            if (entry == null) invalid(); same(entry,"field_id",binding,"field_id");
            var inventory = fields.get(inventoryKey(entry));
            if (inventory == null || !strings(inventory.required("entry_keys")).contains(text(binding,"entry_key"))) invalid();
            if (!"MAPPED".equals(text(inventory,"state"))) invalid();
            var provenance = entry.required("provenance");
            if (provenance.isNull() || provenance.required("read_receipt_ids").size()!=1
                    || !text(binding,"source_read_receipt_id").equals(provenance.required("read_receipt_ids").get(0).textValue())) invalid();
        }
        if (!bound.equals(entries.keySet())) invalid();
        var expectedKeys=new HashSet<String>();
        for(var field:fields.values()) if("MAPPED".equals(text(field,"state"))) expectedKeys.addAll(strings(field.required("entry_keys")));
        if(!expectedKeys.equals(keys)) invalid();
        for (var entry : entries.values()) {
            context(entry);
            if (unresolved(entry) || !occurrences.add(text(entry,"field_id"))
                    || entry.required("field_id").equals(entry.required("field_definition_id"))) invalid();
            var section = sections.get(text(entry,"section_id"));
            if (section==null || !strings(section.required("requirement_node_ids")).contains(text(entry,"node_id"))) invalid();
            var field = fields.get(inventoryKey(entry));
            if (field==null) invalid();
            same(entry,"section_id",field,"section_id"); same(entry,"node_id",field,"node_id");
            var value = entry.required("candidate_value");
            if (!"TEXT".equals(text(value,"value_kind")) || value.required("raw_text").isNull()
                    || !value.required("decimal_value").isNull() || !value.required("precision").isNull()
                    || !value.required("unit").isNull() || !value.required("object_ref").isNull()
                    || !value.required("selected_ids").isEmpty()) invalid();
            var provenance = entry.required("provenance"); var source = provenance.required("source");
            if (source.isNull() || !"SOURCE_EXACT_REVISION".equals(text(source,"kind")) || source.required("source_exact").isNull()
                    || source.required("evidence_ref").isNull() || !source.required("parsed_decimal").isNull()) invalid();
            if (provenance.required("end_codepoint").intValue()<=provenance.required("start_codepoint").intValue()
                    || !DigestUtils.sha256Hex(text(value,"raw_text")).equals(text(provenance,"raw_text_sha256"))) invalid();
            var evidence=source.required("evidence_ref"); var identity=target.required("identity");
            same(evidence,"tenant_id",identity,"tenant_id"); same(evidence,"project_id",identity,"project_id");
        }
        var lineage=unique(node.required("source_lineage"),"version_id"); var governance=unique(node.required("governance_pins"),"version_id");
        if (lineage.keySet().stream().anyMatch(governance::containsKey)) invalid();
        if (!entries.isEmpty()) newStrictSchema().target(target);
    }
    private StrictFieldComparisonSchema newStrictSchema() throws IOException {
        try { return new StrictFieldComparisonSchema(); } catch (Exception failure) { throw new IOException(failure); }
    }
    private void sourceReceipt(JsonNode node) {
        same(node,"source_version",node.required("source_exact"),"source_version"); same(node,"actor_id",node,"created_by");
        if (Set.of(TaskFieldTargetProtocol.PREPARATION_PURPOSE,TaskFieldTargetProtocol.INPUT_PURPOSE).contains(text(node,"purpose"))) invalid();
        for (String key : List.of("original_read","parsed_read")) {
            var read=node.required(key);var object=read.required("object_ref");
            same(read,"length",object,"size_bytes"); same(read,"full_sha256",object,"sha256"); same(read,"delivered_sha256",object,"sha256");
        }
    }
    private void intent(JsonNode node) throws IOException {
        var target = validate("TargetManifest",node.required("target_manifest")); var identity=target.at("/target/identity");
        for (String key : List.of("tenant_id","project_id","task_id","task_revision","plan_revision","assignment_id","assignment_sha256")) same(node,key,identity,key);
        String canonical=canonical(target);
        if (!DigestUtils.sha256Hex(canonical).equals(text(node,"target_sha256"))
                || !Integer.toString(canonical.getBytes(StandardCharsets.UTF_8).length).equals(text(node,"size_bytes"))) invalid();
    }
    private void input(JsonNode node) {
        var object=node.required("object_ref"); same(node,"artifact_sha256",object,"sha256"); same(node,"object_version",object,"object_version");
        if (!"application/json".equals(text(object,"media_type")) || new BigInteger(text(node,"maximum_read_bytes")).compareTo(new BigInteger(text(object,"size_bytes")))<0
                || !manifest.equals(text(node,"artifact_schema_sha256"))) invalid();
    }
    private static String inventoryKey(JsonNode node){return text(node,"field_definition_id")+":"+node.required("section_id").asText()+":"+text(node,"node_id");}
    private static void mappings(JsonNode entries) {
        unique(entries,"entry_key"); for (var entry:entries) context(entry);
    }
    private static void context(JsonNode node) {
        for(String kind:List.of("model","condition")) {
            String state=text(node,kind+"_context");boolean empty=node.required(kind+"_ids").isEmpty();
            if ("KNOWN_GLOBAL".equals(state) && !empty || "KNOWN_SCOPED".equals(state) && empty) invalid();
        }
    }
    private static boolean unresolved(JsonNode node) {
        return "UNRESOLVED".equals(text(node,"model_context")) || "UNRESOLVED".equals(text(node,"condition_context")) || "UNKNOWN".equals(text(node,"applicability"));
    }
    private static Map<String,JsonNode> unique(JsonNode array,String key) {
        var result=new HashMap<String,JsonNode>(); for(var item:array) if(result.put(text(item,key),item)!=null) invalid();return result;
    }
    private static Set<String> strings(JsonNode array) {var result=new HashSet<String>();for(var item:array)if(!result.add(item.textValue()))invalid();return result;}
    private static void same(JsonNode a,String ak,JsonNode b,String bk) {if(!a.required(ak).equals(b.required(bk)))invalid();}
    private static String text(JsonNode value,String key) {return value.required(key).textValue();}
    private JsonSchema definition(String type) {return schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(type));}
    private static void numericStrings(JsonNode node) {
        if(node.isObject()) {var fields=node.fields();while(fields.hasNext()){var f=fields.next();if(Set.of("revision","expected_revision","head_revision","context_revision","authority_revision","auth_epoch","governance_epoch","size_bytes","maximum_read_bytes","length").contains(f.getKey()) && f.getValue().isTextual() && new BigInteger(f.getValue().textValue()).compareTo(U64_MAX)>0)invalid();}}
        if(node.isContainerNode())for(var child:node)numericStrings(child);
    }
    private void encodedSize(JsonNode value) throws IOException {
        try {json.writeValue(new java.io.OutputStream(){long count;public void write(int value)throws IOException{add(1);}public void write(byte[] value,int off,int len)throws IOException{add(len);}private void add(int len)throws IOException{if(len>MAX_BYTES-count)throw new IOException("SCHEMA_INPUT_LIMIT");count+=len;}},value);}
        catch(IOException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    private static void bounded(JsonNode node,int depth,long[] count) {
        if(node==null || depth>48 || ++count[0]>MAX_BYTES)invalid();
        if(node.isTextual())count[0]+=node.textValue().length();
        if(node.isObject()){var names=node.fieldNames();while(names.hasNext())count[0]+=names.next().length();}
        if(count[0]>MAX_BYTES)invalid();if(node.isContainerNode())for(var child:node)bounded(child,depth+1,count);
    }
    private static void unicode(JsonNode node) throws IOException {
        try {
            if(node.isTextual())StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(node.textValue()));
            if(node.isObject()){var names=node.fieldNames();while(names.hasNext())StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(names.next()));}
            if(node.isContainerNode())for(var child:node)unicode(child);
        }catch(java.nio.charset.CharacterCodingException failure){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    }
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
}

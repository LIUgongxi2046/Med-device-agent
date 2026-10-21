package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Closed, explicitly selected data ABI. These functions never manufacture read authority. */
final class TaskProfessionalPromptV2 {
    static final String VERSION="2.0.0";
    static final String COMPILER="N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_PREDECESSOR_EVIDENCE_V2";
    static final String PROMPT_REF="urn:medical:task-professional-package:2.0.0#/$defs/PromptPackage";
    static final String PROFILE_REF="urn:medical:task-professional-package:2.0.0#/$defs/ProfilePackage";
    static final String ENCODING="TASK_NODE_INPUT_WITH_REGISTERED_PREDECESSORS_V2";
    static final String PROJECTION="TEXT_SYSTEM_USER_WITH_REGISTERED_PREDECESSORS_V2";
    static final Set<String> PREDECESSOR_FIELDS=Set.of("step_id","result_artifact_id","result_sha256","result_object_version","result_receipt_sha256","evidence_artifact_id","evidence_sha256","evidence_object_version","evidence_receipt_sha256","completion_receipt_sha256");
    private TaskProfessionalPromptV2() { }
    static void validatePrompt(JsonNode prompt) {
        ProfessionalRuntimeObservations.closed(prompt,Set.of("schema_version","node_kind","system_instructions","input_encoding"));
        if(!VERSION.equals(text(prompt,"schema_version"))||!"N02".equals(text(prompt,"node_kind"))||!ENCODING.equals(text(prompt,"input_encoding"))
                ||text(prompt,"system_instructions").isBlank()||text(prompt,"system_instructions").getBytes(StandardCharsets.UTF_8).length>262144)bad();
    }
    static void validateProfile(JsonNode profile) {
        ProfessionalRuntimeObservations.closed(profile,Set.of("schema_version","node_kind","profile_name","projection","system_prefix","files","adapter_artifact"));
        if(!VERSION.equals(text(profile,"schema_version"))||!PROJECTION.equals(text(profile,"projection")))bad();
        // Reuse unchanged V1 constraints only after explicit V2 identity is checked.
        var copy=profile.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)copy).put("schema_version","1.0.0").put("projection","TEXT_SYSTEM_USER_V1");
        TaskProfessionalPromptCompiler.validateProfile(copy);
    }
    static void predecessor(JsonNode pin) {
        ProfessionalRuntimeObservations.closed(pin,PREDECESSOR_FIELDS);
        for(String key:PREDECESSOR_FIELDS) {
            String value=text(pin,key);
            if(key.endsWith("_id"))WireValues.uuid(value);
            else if(key.endsWith("sha256"))WireValues.hash(value);
            else if(value.isBlank()||value.length()>256)bad();
        }
        if(text(pin,"result_artifact_id").equals(text(pin,"evidence_artifact_id")))bad();
    }
    static JsonNode compile(JsonNode prompt,JsonNode input,JsonNode evidence)throws Exception {return compile(prompt,input,evidence,null);}
    static JsonNode compile(JsonNode prompt,JsonNode input,JsonNode evidence,JsonNode verifiedPins)throws Exception {
        validatePrompt(prompt);
        if(!input.isObject()||!"N02".equals(text(input,"node_kind"))||!input.required("predecessors").isArray()
                ||!evidence.isArray()||evidence.size()!=input.required("predecessors").size()||evidence.size()>512)bad();
        if(verifiedPins!=null&&(!verifiedPins.isArray()||verifiedPins.size()!=evidence.size()))bad();
        var steps=new HashSet<String>();var artifacts=new HashSet<String>();
        for(int n=0;n<evidence.size();n++) {
            var item=evidence.get(n);ProfessionalRuntimeObservations.closed(item,Set.of("predecessor","evidence_bundle"));var pin=item.required("predecessor");predecessor(pin);
            if(!pin.equals(input.required("predecessors").get(n))||!steps.add(text(pin,"step_id"))||text(pin,"step_id").equals(text(input,"step_id"))
                    ||!artifacts.add(text(pin,"result_artifact_id"))||!artifacts.add(text(pin,"evidence_artifact_id")))bad();
            var bundle=item.required("evidence_bundle");
            if(verifiedPins==null)TypedSchemaBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,bundle).read();
            else {var declared=verifiedPins.get(n);if(!pin.equals(declared.required("predecessor")))bad();ArtifactEvidenceBindings.bind(SchemaCatalogue.fromClasspath(),ArtifactBindings.EVIDENCE_BUNDLE,bundle,text(declared,"schema_ref"),text(declared,"schema_sha256")).read();}
            if(!WireValues.sha256(ContentReadAuthorization.canonicalBytes(bundle)).equals(text(pin,"evidence_sha256")))bad();
        }
        var output=new JsonMapper().createObjectNode().put("schema_version",VERSION).put("node_kind","N02").put("instructions",text(prompt,"system_instructions"));
        output.set("task_node_input",input.deepCopy());output.set("predecessor_evidence",evidence.deepCopy());return output;
    }
    static String text(JsonNode n,String field){if(n==null||!n.required(field).isTextual())bad();return n.required(field).textValue();}
    static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Closed versioned source receipt envelope. This kind is deliberately separate from artifact content receipts. */
final class TaskSourceReadReceiptSchema {
    private final PermissionSchema json;
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final SchemaCatalogue catalogue;
    private final String manifest;private final String manifestV2;
    private static final Set<String> FIELDS=Set.of("schema_version","kind","receipt_id","action_id","request","identity","evidence_ref",
            "source_owner_project_id","processing_project_id","processing_attempt_id","original_object","parsed_object",
            "processing_command_sha256","processing_result_sha256","processor_manifest_sha256","source_authorization_sha256",
            "provenance_sha256","delivered_range","scope_sha256","created_at","classification","action_request","token_sha256","original_read","parsed_read");
    TaskSourceReadReceiptSchema(PermissionSchema json) throws Exception {
        this.json=json;catalogue=SchemaCatalogue.fromClasspath();
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/task-source-read-receipt.schema.json"))) {
            manifest=WireValues.sha256(input.readAllBytes());
        }
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/task-source-read-receipt-v2.schema.json"))) {manifestV2=WireValues.sha256(input.readAllBytes());}
    }
    String manifest(){return manifest;}
    String manifest(String version){if("1.0.0".equals(version))return manifest;if("2.0.0".equals(version))return manifestV2;bad();return null;}
    JsonNode seal(JsonNode body) throws Exception {
        var document=json.object();document.set("body",body.deepCopy());document.put("receipt_sha256",TaskSourceIdentity.sha256(body));
        verify(document);
        // Persisted JCS bytes must retain the exact validated locator and all other numeric identity.
        if(!TaskSourceIdentity.same(document,reader.readTree(json.canonical(document))))bad();
        return document;
    }
    JsonNode read(String raw) throws Exception {var value=reader.readTree(raw);verify(value);return value;}
    DatasetProtocol.EvidenceRef evidence(JsonNode value) throws Exception {return SourceEvidenceBindings.bind(catalogue,value).read();}
    void verify(JsonNode document) throws Exception {
        fields(document,Set.of("body","receipt_sha256"));var body=document.required("body");fields(body,FIELDS);
        if(!Set.of("1.0.0","2.0.0").contains(text(body,"schema_version")) || !TaskResearchSourceReader.KIND.equals(text(body,"kind"))) bad();
        if(!TaskSourceIdentity.sha256(body).equals(text(document,"receipt_sha256"))) bad();
        WireValues.uuid(text(body,"receipt_id"));WireValues.uuid(text(body,"action_id"));WireValues.uuid(text(body,"processing_project_id"));WireValues.uuid(text(body,"processing_attempt_id"));
        if(!body.required("source_owner_project_id").isNull())WireValues.uuid(text(body,"source_owner_project_id"));
        for(String hash:List.of("processing_command_sha256","processing_result_sha256","processor_manifest_sha256","source_authorization_sha256","provenance_sha256","scope_sha256","token_sha256"))WireValues.hash(text(body,hash));
        WireValues.utcTime(text(body,"created_at"));
        if(!Set.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(text(body,"classification"))) bad();
        fields(body.required("action_request"),Set.of("run_id","step_id","action_id","generation","input_sha256","arguments_sha256","kind","tool_version","scope","action","purpose"));
        var ref=evidence(body.required("evidence_ref"));if(!ref.schemaVersion().equals(text(body,"schema_version")))bad();
        fields(body.required("request"),Set.of("evidence_ref","cursor"));
        if(!body.required("request").required("cursor").isNull() || !body.required("request").required("evidence_ref").equals(body.required("evidence_ref"))) bad();
        if(!TaskSourceIdentity.sha256(ref).equals(text(body,"scope_sha256"))) bad();
        var identity=body.required("identity");fields(identity,Set.of("input_id","input_sha256","input_receipt_sha256","node_input","actor_id","worker_service","lease_generation","session_id"));
        WireValues.uuid(text(identity,"session_id"));WireValues.uuid(text(identity,"input_id"));WireValues.uuid(text(identity,"actor_id"));WireValues.hash(text(identity,"input_sha256"));WireValues.hash(text(identity,"input_receipt_sha256"));
        WireValues.revision(text(identity,"lease_generation"));if(text(identity,"worker_service").isBlank()||!identity.required("node_input").isObject())bad();
        for(String object:List.of("original_object","parsed_object"))catalogue.validate("interfaces",body.required(object),"ObjectRef");
        for(String kind:List.of("original","parsed")) {
            var read=body.required(kind+"_read");var object=body.required(kind+"_object");
            fields(read,Set.of("representation","start_byte","end_byte","content_sha256","complete"));
            if(!(kind.equals("original")?"ORIGINAL_BINARY":"PARSED_BUNDLE").equals(text(read,"representation"))||!"0".equals(text(read,"start_byte"))
                    ||!object.required("size_bytes").equals(read.required("end_byte"))||!object.required("sha256").equals(read.required("content_sha256"))
                    ||!read.required("complete").isBoolean()||!read.required("complete").booleanValue())bad();
        }
        var range=body.required("delivered_range");fields(range,Set.of("representation","start_byte","end_byte","content_sha256","complete"));
        if(!"PARSED_CHUNK_UTF8".equals(text(range,"representation"))||!"0".equals(text(range,"start_byte"))
                ||!range.required("complete").isBoolean()||!range.required("complete").booleanValue()
                ||!ref.quoteHash().equals(text(range,"content_sha256")))bad();
        var length=WireValues.revision(text(range,"end_byte"));if(length.signum()<=0||length.compareTo(java.math.BigInteger.valueOf(TaskResearchSourceReader.MAXIMUM_TEXT_BYTES))>0)bad();
    }
    private static String text(JsonNode node,String key){var value=node.required(key);if(!value.isTextual())bad();return value.textValue();}
    private static void fields(JsonNode node,Set<String> required){if(node==null||!node.isObject())bad();var keys=new HashSet<String>();node.fieldNames().forEachRemaining(keys::add);if(!keys.equals(required))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

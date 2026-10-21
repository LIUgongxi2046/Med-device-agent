package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.ErrorCode;

/** Explicit opt-in evidence contract. Never coerces IMAGE into a dataset-v1 locator. */
final class SourceEvidenceV2 {
    static final String CONTRACT = "2.0.0";
    static boolean requested(JsonNode arguments) { return CONTRACT.equals(arguments.path("evidence_contract").textValue()); }
    static String inputs(JsonNode arguments) { return requested(arguments)?"tool-inputs-v2":"tool-inputs"; }
    static String outputs(JsonNode arguments) { return requested(arguments)?"tool-success-v2":"tool-success"; }
    static String taskOutputs(JsonNode arguments) { return requested(arguments)?"task-tool-read-success-v2":"task-tool-read-success"; }
    static String taskResponse(JsonNode response) { return CONTRACT.equals(response.path("schema_version").asText())?"task-tool-read-success-v2":"task-tool-read-success"; }
    static void validateRequest(String tool,JsonNode request,SchemaCatalogue catalogue) throws Exception {
        if(!java.util.Set.of("AT-01","AT-02","AT-08").contains(tool) || !requested(request.required("arguments")))
            throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        ProfessionalRuntimeObservations.closed(request,java.util.Set.of("call_id","name","arguments","arguments_sha256"));
        String expected=switch(tool) { case "AT-01" -> "search_sources"; case "AT-02" -> "read_source"; default -> TaskRegisteredToolAuthority.name(tool); };
        if(!expected.equals(request.path("name").textValue()) || request.path("call_id").asText().isBlank()
                || request.path("call_id").asText().length()>256
                || !com.ruoyi.medical.contracts.WireValues.sha256(ContentReadAuthorization.canonicalBytes(request.required("arguments")))
                    .equals(request.path("arguments_sha256").textValue()))
            throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        String definition=switch(tool) { case "AT-01" -> "SearchArgs"; case "AT-02" -> "ReadArgs"; default -> "QuestionArgs"; };
        catalogue.validate("tool-inputs-v2",request.required("arguments"),definition);
        if(request.required("arguments").has("cursor") && !request.required("arguments").required("cursor").isNull())
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
    }
    static void validate(SchemaCatalogue catalogue, JsonNode value) throws java.io.IOException {
        catalogue.validate("source-evidence-v2",value,"EvidenceRef");
        SourceLocatorSchema.validate(catalogue,value.required("locator"));
        if(!value.required("locator_kind").equals(value.required("locator").required("kind")))
            throw new TenantAccessException(ErrorCode.LOCATOR_INVALID);
    }
}

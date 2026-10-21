package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import java.util.Objects;
import java.util.Set;

/** Host-selected N06 inner contracts. The immutable outer schema remains version one.
 * Missing declarations mean historical v1, never inference from content bytes. */
final class TaskCandidateAssemblyContracts {
    private TaskCandidateAssemblyContracts(){}
    static TaskWriterEvidenceContract fromRole(JsonNode request)throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();var declaration=request.required("output_schema_ref");
        var published=catalogue.requirePublished(declaration.required("schema_ref").asText(),declaration.required("schema_sha256").asText());
        if(!"AssemblyManifest".equals(published.definition())||!Set.of("artifacts","artifacts-v2").contains(published.name()))bad();
        return new TaskWriterEvidenceContract("artifacts-v2".equals(published.name()));
    }
    static void declare(ObjectNode input,TaskWriterEvidenceContract contract)throws Exception {
        input.put("body_schema_ref",contract.bodyRef()).put("body_schema_sha256",contract.bodySha())
            .put("assembly_schema_ref",contract.artifactRef("AssemblyManifest")).put("assembly_schema_sha256",contract.artifactSha());
    }
    static TaskWriterEvidenceContract fromInput(JsonNode input)throws Exception {
        if(!input.has("body_schema_ref")&&!input.has("body_schema_sha256")&&!input.has("assembly_schema_ref")&&!input.has("assembly_schema_sha256"))return new TaskWriterEvidenceContract(false);
        var catalogue=SchemaCatalogue.fromClasspath();var p=catalogue.requirePublished(input.required("assembly_schema_ref").asText(),input.required("assembly_schema_sha256").asText());
        if(!"AssemblyManifest".equals(p.definition())||!Set.of("artifacts","artifacts-v2").contains(p.name()))bad();
        var result=new TaskWriterEvidenceContract("artifacts-v2".equals(p.name()));
        if(!result.bodyRef().equals(input.required("body_schema_ref").asText())||!result.bodySha().equals(input.required("body_schema_sha256").asText()))bad();
        return result;
    }
    static String outerSha()throws Exception {try(var in=Objects.requireNonNull(TaskCandidateAssemblyContracts.class.getResourceAsStream("/task-candidate-assembly-output.schema.json"))){return WireValues.sha256(in.readAllBytes());}}
    static void validate(JsonNode input,String kind,JsonNode payload)throws Exception {
        var catalogue=SchemaCatalogue.fromClasspath();var contract=fromInput(input);var json=new TaskSourceVerifierSchema();
        try(var in=Objects.requireNonNull(TaskCandidateAssemblyContracts.class.getResourceAsStream("/task-candidate-assembly-output.schema.json"))){
            var schema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(json.read(in.readAllBytes()),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
            if(!schema.validate(payload).isEmpty())bad();
        }
        switch(kind){
            case "DOCUMENT_BODY" -> DocumentBodySemantics.verify(catalogue,payload,new DocumentBodySemantics.Limits(10000,10000,100000),contract.bodyRef(),contract.bodySha());
            case "ASSEMBLY_MANIFEST" -> ArtifactEvidenceBindings.bind(catalogue,ArtifactBindings.ASSEMBLY_MANIFEST,payload,contract.artifactRef("AssemblyManifest"),contract.artifactSha()).read();
            case "ASSEMBLY_COMPANION" -> {for(var c:payload.required("claim_records"))catalogue.validate(contract.artifacts(),c,"ClaimRecord");for(var a:payload.required("field_assignments"))catalogue.validate(contract.artifacts(),a,"FieldAssignment");for(var a:payload.required("attachment_bindings"))catalogue.validate(contract.artifacts(),a,"AttachmentBinding");}
            case "CROSS_FILE_CHECK_REPORT" -> {if(!"N06".equals(payload.required("node_kind").asText()))bad();}
            default -> bad();
        }
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

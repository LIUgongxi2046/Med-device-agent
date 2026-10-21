package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.util.Objects;

/** Pure deterministic execution only. Caller authenticates, loads canonical bytes and persists the actual manifest.
 * Neither source access/locator validity, factual support nor professional readiness is established here. */
public final class DocumentTechnicalCheckEngine {
    private final DocumentTechnicalCheckSchema schema;
    public DocumentTechnicalCheckEngine(DocumentTechnicalCheckSchema schema){this.schema=Objects.requireNonNull(schema);}

    public ObjectNode execute(JsonNode fixedBody,JsonNode manifest,DocumentBodySemantics.Limits limits)throws IOException{
        Objects.requireNonNull(limits);var input=schema.input(manifest);var declared=input.required("limits");
        if(declared.required("maximum_nodes").intValue()!=limits.maximumNodes()
            ||declared.required("maximum_table_cells").intValue()!=limits.maximumTableCells()
            ||declared.required("maximum_relationships").intValue()!=limits.maximumRelationships())mismatch();
        var body=Objects.requireNonNull(fixedBody).deepCopy();
        if(!schema.hash(body).equals(input.required("body_sha256").textValue()))mismatch();
        boolean valid=true;
        try{
            var declaration=schema.catalogue().requirePublished(input.required("body_schema_ref").asText(),input.required("body_schema_sha256").asText());
            var verified=DocumentBodySemantics.verify(schema.catalogue(),body,limits,declaration.schemaRef(),declaration.schemaSha256());
            var published=schema.catalogue().bind(declaration.name(),verified,"DocumentBody");
            if(!org.apache.commons.codec.digest.DigestUtils.sha256Hex(published.canonicalBytes()).equals(input.required("body_sha256").textValue()))mismatch();
        }catch(SchemaRejection failure){if(failure.code()!=ErrorCode.SCHEMA_INVALID)throw failure;valid=false;}
         catch(TenantAccessException failure){if(failure.code()!=ErrorCode.SCHEMA_INVALID)throw failure;valid=false;}
        String inputHash=schema.hash(input);
        var result=JsonNodeFactory.instance.objectNode().put("schema_version","1.0.0")
            .put("checker_id",DocumentTechnicalCheckSchema.CHECKER).put("check_kind",DocumentTechnicalCheckSchema.KIND)
            .put("input_sha256",inputHash).put("execution_status","COMPLETED").put("technical_result",valid?"PASS":"FAIL")
            .put("professional_coverage","UNKNOWN").put("review_ready",false);
        result.set("input",input);
        result.putObject("coverage").put("schema_version","1.0.0").put("check_kind",DocumentTechnicalCheckSchema.KIND)
            .put("selection_sha256",inputHash).put("delivered_sha256",inputHash).put("complete",true).putNull("next_cursor");
        var findings=result.putArray("findings");
        if(!valid)findings.addObject().put("code","DOCUMENT_STRUCTURE_INVALID").put("check_kind",DocumentTechnicalCheckSchema.KIND)
            .put("message","The fixed document failed structural or internal-reference validation.");
        return schema.result(result);
    }
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

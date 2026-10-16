package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceIndexManifestIT {
    private JsonNode actualBundle() throws Exception {
        var json=JsonMapper.builder().build();
        var receipt=json.readTree(Files.readAllBytes(Path.of(System.getProperty("medical.processing.storage"))));
        assertEquals("LOCAL_PARSER_CHECK",receipt.required("permission").textValue());
        assertFalse(receipt.required("customer_processing_verified").booleanValue());
        var command=json.readTree(Files.readAllBytes(Path.of(receipt.required("command_path").textValue())));
        var result=json.readTree(Files.readAllBytes(Path.of(receipt.required("result_path").textValue())));
        return new SourceProcessingResult().verify(command,result,
            Files.readAllBytes(Path.of(receipt.required("bundle_path").textValue()))).bundle();
    }

    private JsonNode chunks(JsonNode bundle,int limit) throws Exception {
        return new SourceIndexManifest().chunks(bundle,limit);
    }

    @Test
    void completeActualParsedDocumentProducesAnOrderedFulltextManifest() throws Exception {
        var bundle=actualBundle(); var manifest=chunks(bundle,1000);
        assertEquals(220,manifest.size()); assertEquals(manifest,chunks(bundle,1000));
        for(int ordinal=0;ordinal<manifest.size();ordinal++) {
            assertEquals(ordinal,manifest.get(ordinal).required("ordinal").intValue());
            assertEquals(bundle.required("chunks").get(ordinal).required("chunk_id"),manifest.get(ordinal).required("chunk_id"));
            assertEquals(bundle.required("nodes").get(ordinal).required("quote_hash"),manifest.get(ordinal).required("quote_hash"));
        }
    }

    @Test
    void completeActualDocumentCannotBeTruncatedToThePublishedCapacity() throws Exception {
        var bundle=actualBundle();
        var failure=assertThrows(TenantAccessException.class,() -> chunks(bundle,219));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTEXT_LIMIT,
            failure.code());
    }

    @Test
    void alteredActualChunkContentAndRepeatedIdentityAreRejected() throws Exception {
        var original=actualBundle(); var changed=original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.required("chunks").get(0))
            .put("raw_text",original.required("chunks").get(1).required("raw_text").textValue());
        var failure=assertThrows(TenantAccessException.class,() -> chunks(changed,1000));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,
            failure.code());
        var repeated=original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)repeated.required("chunks").get(1))
            .set("chunk_id",original.required("chunks").get(0).required("chunk_id"));
        var duplicate=assertThrows(TenantAccessException.class,() -> chunks(repeated,1000));
        assertEquals(com.ruoyi.medical.contracts.ErrorCode.CONTENT_MISMATCH,
            duplicate.code());
    }
}

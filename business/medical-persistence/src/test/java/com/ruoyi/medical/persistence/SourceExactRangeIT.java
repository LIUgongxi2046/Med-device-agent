package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceExactRangeIT {
    private JsonNode actualBundle() throws Exception {
        var json=JsonMapper.builder().build();
        var receipt=json.readTree(Files.readAllBytes(Path.of(System.getProperty("medical.processing.storage"))));
        assertEquals("LOCAL_PARSER_CHECK",receipt.required("permission").textValue());
        assertFalse(receipt.required("customer_processing_verified").booleanValue());
        var command=json.readTree(Files.readAllBytes(Path.of(receipt.required("command_path").textValue())));
        var result=json.readTree(Files.readAllBytes(Path.of(receipt.required("result_path").textValue())));
        return new SourceProcessingResult().verify(command,result,Files.readAllBytes(Path.of(receipt.required("bundle_path").textValue()))).bundle();
    }
    @Test
    void actualOriginalTextRangeAndCompleteLocatorRemainByteIdentical() throws Exception {
        var bundle=actualBundle(); var chunk=bundle.required("chunks").get(0); var json=new PermissionSchema();
        var schema=new SourceExactSchema(json); String raw=chunk.required("raw_text").textValue();
        String id=chunk.required("chunk_id").textValue(),quote=chunk.required("quote_hash").textValue();
        String locator=json.canonical(chunk.required("locator")),locatorHash=DigestUtils.sha256Hex(locator);
        assertTrue(raw.codePointCount(0,raw.length())<=512);
        schema.verifyRange(raw,id,quote,locatorHash,0,raw.codePointCount(0,raw.length()),id,raw,quote,locator);
        assertEquals(chunk.required("locator"),schema.locator(locator));
        assertEquals(quote,DigestUtils.sha256Hex(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @Test
    void changedActualTextRangeAndAnotherActualLocatorAreRejected() throws Exception {
        var bundle=actualBundle(); var first=bundle.required("chunks").get(0); var other=bundle.required("chunks").get(1);
        var json=new PermissionSchema(); var schema=new SourceExactSchema(json); String raw=first.required("raw_text").textValue();
        String id=first.required("chunk_id").textValue(),quote=first.required("quote_hash").textValue(),locator=json.canonical(first.required("locator"));
        String locatorHash=DigestUtils.sha256Hex(locator);
        assertNotEquals(raw,other.required("raw_text").textValue()); assertNotEquals(first.required("locator"),other.required("locator"));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,() -> schema.verifyRange(
            other.required("raw_text").textValue(),id,quote,locatorHash,0,raw.codePointCount(0,raw.length()),id,raw,quote,locator)).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,() -> schema.verifyRange(
            raw,id,quote,locatorHash,0,raw.codePointCount(0,raw.length()),id,raw,quote,json.canonical(other.required("locator")))).code());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,() -> schema.verifyRange(
            raw,id,quote,locatorHash,0,raw.codePointCount(0,raw.length())+1,id,raw,quote,locator)).code());
    }
}

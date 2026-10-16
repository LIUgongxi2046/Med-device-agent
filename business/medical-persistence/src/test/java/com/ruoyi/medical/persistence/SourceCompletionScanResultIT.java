package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceCompletionScanResultIT {
    @Test
    void actualScannerReceiptPreservesTheCompleteInputAndFourActualOutputs() throws Exception {
        var input=Input.read(); var result=verify(input.command(),input.receipt());
        JsonNode snapshot=result.receipt();
        assertEquals(input.receipt(),snapshot);
        assertEquals("DOCX",snapshot.required("format").textValue()); assertTrue(snapshot.required("format_verified").booleanValue());
        ((com.fasterxml.jackson.databind.node.ArrayNode)snapshot.required("artifacts")).removeAll();
        assertEquals(4,result.receipt().required("artifacts").size());
    }

    @Test
    void originalIdentityAnotherActualDigestAndUnknownFieldsAreRejected() throws Exception {
        var input=Input.read(); var command=input.command().deepCopy();
        ((ObjectNode)command).put("original_sha256",DigestUtils.sha256Hex(input.receipt().toString()));
        assertThrows(TenantAccessException.class,() -> verify(command,input.receipt()));
        var unknown=input.receipt().deepCopy(); ((ObjectNode)unknown).put("approval_status","APPROVED");
        assertThrows(TenantAccessException.class,() -> verify(input.command(),unknown));
        var changed=input.receipt().deepCopy(); ((ObjectNode)changed).put("lease_generation","2");
        assertThrows(TenantAccessException.class,() -> verify(input.command(),changed));
        var format=input.receipt().deepCopy(); ((ObjectNode)format).put("format","PDF");
        assertThrows(TenantAccessException.class,() -> verify(input.command(),format));
    }

    @Test
    void actualOutputsCannotBeDuplicatedOrReplacedByOtherInputBytes() throws Exception {
        var input=Input.read(); var receipt=input.receipt().deepCopy();
        ((ObjectNode)receipt.required("artifacts").get(1)).set("content_base64",receipt.required("artifacts").get(0).required("content_base64"));
        assertThrows(TenantAccessException.class,() -> verify(input.command(),receipt));
        var duplicate=input.receipt().deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.required("artifacts")).set(1,duplicate.required("artifacts").get(0));
        assertThrows(TenantAccessException.class,() -> verify(input.command(),duplicate));
    }

    private static SourceCompletionScanResult.Verified verify(JsonNode command,JsonNode receipt) throws Exception {
        return new SourceCompletionScanResult().verify(command,receipt);
    }

    private record Input(JsonNode command,JsonNode receipt) {
        static Input read() throws Exception {
            var json=JsonMapper.builder().build();
            Path path=Path.of(Objects.requireNonNull(System.getProperty("medical.completion.scan"),"实际扫描回执缺少"));
            var evidence=json.readTree(SourceProcessingResult.privateBytes(path,65536));
            assertEquals("LOCAL_PARSER_CHECK",evidence.required("permission").textValue());
            assertEquals("REGISTERED",evidence.required("input_data_kind").textValue());
            assertFalse(evidence.required("customer_upload_completed").booleanValue());
            assertFalse(evidence.required("professional_approval_created").booleanValue());
            Path root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
            for(var item:evidence.required("inputs")) {
                Path original=Path.of(item.required("path").textValue()); assertTrue(original.startsWith(root));
                assertEquals(original,original.toRealPath()); assertTrue(Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS));
                assertEquals(item.required("sha256").textValue(),DigestUtils.sha256Hex(Files.readAllBytes(original)));
            }
            for(var item:evidence.required("artifacts")) {
                Path artifact=Path.of(item.required("path").textValue());
                assertEquals(artifact,artifact.toRealPath()); assertTrue(Files.isRegularFile(artifact,LinkOption.NOFOLLOW_LINKS));
                assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(artifact,LinkOption.NOFOLLOW_LINKS));
                SourceProcessingResult.privateDirectory(artifact.getParent()); assertTrue(Files.size(artifact)<=262144);
                byte[] bytes=Files.readAllBytes(artifact);
                assertEquals(item.required("sha256").textValue(),DigestUtils.sha256Hex(bytes));
                assertEquals(item.required("size_bytes").textValue(),Integer.toString(bytes.length));
            }
            return new Input(json.readTree(SourceProcessingResult.privateBytes(Path.of(evidence.required("command_path").textValue()),65536)),
                json.readTree(SourceProcessingResult.privateBytes(Path.of(evidence.required("scan_receipt_path").textValue()),262144)));
        }
    }
}

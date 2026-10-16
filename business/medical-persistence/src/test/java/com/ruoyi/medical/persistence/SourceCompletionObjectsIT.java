package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.util.function.ThrowingSupplier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class SourceCompletionObjectsIT {
    @Test
    void actualFixedPartsReconstructTheCompleteRegisteredOriginal() throws Exception {
        var input=Input.read(); var service=new SourceCompletionObjects();
        var manifest=service.verify(input.receipt().required("parts"),input.size(),input.sha256(),input.prefix(),104857600);
        assertEquals(input.size(),manifest.total()); assertEquals(input.sha256(),manifest.originalSha256());
        assertEquals(input.receipt().required("parts").size(),manifest.parts().size());
        Path work=Files.createTempDirectory(input.root().resolve(".implementation-work/source-completion-object-checks"),"java-assembly-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        try(var reader=new S3ObjectReader(URI.create(input.receipt().required("endpoint").textValue()),
                input.receipt().required("bucket").textValue(),input.receipt().required("region").textValue(),
                S3ObjectReader.Network.LOCAL_LOOPBACK,input.credentials(),work,104857600,262144);
                var writer=new S3ObjectWriter(URI.create(input.receipt().required("endpoint").textValue()),
                    input.receipt().required("bucket").textValue(),input.receipt().required("region").textValue(),
                    S3ObjectReader.Network.LOCAL_LOOPBACK,input.credentials(),work,104857600,262144)) {
            byte[] actual=service.assemble(manifest,work,104857600,reader,writer,input.current());
            assertArrayEquals(input.original(),actual); assertEquals(input.sha256(),DigestUtils.sha256Hex(actual));
            try(var files=Files.list(work)) { assertEquals(0,files.count()); }
            assertArrayEquals(input.original(),service.assemble(manifest,work,104857600,reader,writer,input.current()));
        } finally { Files.delete(work); }
    }

    @Test
    void incorrectObjectScopeDuplicatePartsAndDifferentActualDigestAreRejected() throws Exception {
        var input=Input.read(); var service=new SourceCompletionObjects(); var parts=input.receipt().required("parts");
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
            () -> service.verify(parts,input.size(),input.sha256(),input.prefix()+"/",104857600)).code());
        var duplicate=parts.deepCopy(); ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate).add(parts.get(0));
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
            () -> service.verify(duplicate,input.size(),input.sha256(),input.prefix(),104857600)).code());
        var parser=input.json().readTree(SourceProcessingResult.privateBytes(Path.of(input.receipt().required("parser_receipt").textValue()),65536));
        String otherDigest=DigestUtils.sha256Hex(Files.readAllBytes(Path.of(parser.required("bundle_path").textValue())));
        assertNotEquals(input.sha256(),otherDigest);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
            () -> service.verify(parts,input.size(),otherDigest,input.prefix(),104857600)).code());
        assertEquals(ErrorCode.FILE_TOO_LARGE,assertThrows(TenantAccessException.class,
            () -> service.verify(parts,input.size(),input.sha256(),input.prefix(),input.size().longValueExact()-1)).code());
    }

    @Test
    void acceptedManifestPreservesTheOriginalIdentityAndCannotBeEdited() throws Exception {
        var input=Input.read(); var service=new SourceCompletionObjects(); var original=input.receipt().required("parts");
        var mutable=original.deepCopy();
        var manifest=service.verify(mutable,input.size(),input.sha256(),input.prefix(),104857600);
        String before=manifest.sha256(); ((com.fasterxml.jackson.databind.node.ArrayNode)mutable).removeAll();
        assertEquals(original.size(),manifest.parts().size()); assertEquals(before,manifest.sha256());
        assertThrows(UnsupportedOperationException.class,() -> manifest.parts().clear());
        assertEquals(original.get(0).required("object_ref").required("object_version").textValue(),manifest.parts().get(0).object().objectVersion());
        var unknown=original.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode)unknown.get(0)).put("file_name",Path.of(input.receipt().required("original_path").textValue()).getFileName().toString());
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
            () -> service.verify(unknown,input.size(),input.sha256(),input.prefix(),104857600)).code());
    }

    private record Input(Path root,JsonMapper json,JsonNode receipt,byte[] original,StaticCredentialsProvider credentials,
            S3ObjectReader.Admission current) {
        BigInteger size() { return new BigInteger(receipt.required("size_bytes").textValue()); }
        String sha256() { return receipt.required("original_sha256").textValue(); }
        String prefix() { return receipt.required("object_prefix").textValue(); }
        static Input read() throws Exception {
            var json=JsonMapper.builder().build();
            Path path=Path.of(Objects.requireNonNull(System.getProperty("medical.completion.storage"),"实际原件分段回执缺少"));
            byte[] receiptBytes=SourceProcessingResult.privateBytes(path,65536); var receipt=json.readTree(receiptBytes);
            assertEquals("LOCAL_PARSER_CHECK",receipt.required("permission").textValue());
            assertEquals("REGISTERED",receipt.required("input_data_kind").textValue());
            assertFalse(receipt.required("customer_upload_completed").booleanValue());
            assertFalse(receipt.required("professional_approval_created").booleanValue());
            Path root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
            Path storage=root.resolve(".implementation-work/runtime/object-storage");
            var deployment=json.readTree(SourceProcessingResult.privateBytes(storage.resolve("deployment.json"),65536));
            var secrets=json.readTree(SourceProcessingResult.privateBytes(storage.resolve("service-secret.json"),65536));
            assertEquals(receipt.required("service_id"),deployment.required("service_id"));
            assertEquals(receipt.required("deployment_id"),deployment.required("deployment_id"));
            assertEquals(deployment.required("service_id"),secrets.required("service_id"));
            assertEquals("local-parser-checks/"+receipt.required("check_id").textValue()+"/original-parts",receipt.required("object_prefix").textValue());
            byte[] original=Files.readAllBytes(Path.of(receipt.required("original_path").textValue()));
            assertEquals(receipt.required("original_sha256").textValue(),DigestUtils.sha256Hex(original));
            assertEquals(receipt.required("size_bytes").textValue(),Integer.toString(original.length));
            var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(),secrets.required("secret_key").textValue()));
            S3ObjectReader.Admission current=() -> {
                if(!java.util.Arrays.equals(receiptBytes,ThrowingSupplier.of(() -> SourceProcessingResult.privateBytes(path,65536)).get()))
                    throw new IllegalStateException("实际原件分段回执身份已经变化");
            };
            return new Input(root,json,receipt,original,credentials,current);
        }
    }
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import static org.junit.jupiter.api.Assertions.*;

class SourceRightsEvidenceBytesIT {
    private final JsonMapper json=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
    private final Path root=Path.of(Objects.requireNonNull(System.getProperty("medical.projectRoot"))).toAbsolutePath();
    private final Path receiptPath=Path.of(Objects.requireNonNull(System.getProperty("medical.processing.storage")));

    @Test
    void actualFixedObjectHasCompleteProofForTheCurrentEvidenceReader() throws Exception {
        var actual=configuration();
        try(var reader=actual.reader()) {
            var first=SourceRightsEvidenceBytes.read(actual.reference(),reader,actual.admission());
            var second=SourceRightsEvidenceBytes.read(actual.reference(),reader,actual.admission());
            assertEquals(actual.reference(),first.reference()); assertEquals(first.reference(),second.reference());
            assertEquals(actual.reference().sha256(),first.fullSha256()); assertEquals(first.fullSha256(),second.fullSha256());
            assertTrue(java.time.Instant.parse(second.verifiedAt()).compareTo(java.time.Instant.parse(first.verifiedAt()))>=0);
        } finally { Files.delete(actual.directory()); }
    }

    @Test
    void actualOriginalDigestCannotIdentifyTheStoredParsedObject() throws Exception {
        var actual=configuration(); var command=json.readTree(Files.readAllBytes(Path.of(actual.receipt().required("command_path").textValue())));
        var digest=command.required("original_sha256").textValue(); assertNotEquals(digest,actual.reference().sha256());
        var other=new ObjectRef(actual.reference().objectKey(),actual.reference().objectVersion(),digest,
            actual.reference().sizeBytes(),actual.reference().mediaType());
        try(var reader=actual.reader()) {
            assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,
                () -> SourceRightsEvidenceBytes.read(other,reader,actual.admission())).code());
        } finally { Files.delete(actual.directory()); }
    }

    private Actual configuration() throws Exception {
        byte[] bytes=SourceProcessingResult.privateBytes(receiptPath,65536); var receipt=json.readTree(bytes);
        assertEquals("LOCAL_PARSER_CHECK",receipt.required("permission").textValue());
        assertFalse(receipt.required("customer_processing_verified").booleanValue());
        var deployment=json.readTree(SourceProcessingResult.privateBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json"),65536));
        var secrets=json.readTree(SourceProcessingResult.privateBytes(root.resolve(".implementation-work/runtime/object-storage/service-secret.json"),65536));
        assertEquals(receipt.required("service_id"),deployment.required("service_id"));
        assertEquals(receipt.required("deployment_id"),deployment.required("deployment_id"));
        assertEquals(deployment.required("service_id"),secrets.required("service_id"));
        var reference=json.treeToValue(receipt.required("object_ref"),ObjectRef.class);
        assertTrue(reference.objectKey().startsWith("local-parser-checks/"+receipt.required("check_id").textValue()+"/"));
        Path work=Files.createTempDirectory(root.resolve(".implementation-work/source-processing-storage-checks"),"rights-java-read-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(),secrets.required("secret_key").textValue()));
        var reader=new S3ObjectReader(URI.create(receipt.required("endpoint").textValue()),receipt.required("bucket").textValue(),
            receipt.required("region").textValue(),S3ObjectReader.Network.LOCAL_LOOPBACK,credentials,work,268435456,262144);
        S3ObjectReader.Admission current=() -> {
            if(!Arrays.equals(bytes,org.springframework.util.function.ThrowingSupplier.of(
                    () -> SourceProcessingResult.privateBytes(receiptPath,65536)).get()))
                throw new IllegalStateException("实际文件检查回执已经变化");
        };
        return new Actual(reader,reference,current,work,receipt);
    }
    private record Actual(S3ObjectReader reader,ObjectRef reference,S3ObjectReader.Admission admission,Path directory,JsonNode receipt) { }
}

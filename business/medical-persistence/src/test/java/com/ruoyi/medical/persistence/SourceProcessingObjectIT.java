package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceProcessingObjectIT {
    @Test
    void actualVersionedPythonBundleIsRecoveredAndConsumedByJava() throws Exception {
        var json=JsonMapper.builder().propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE).build();
        Path receiptPath=Path.of(java.util.Objects.requireNonNull(System.getProperty("medical.processing.storage"),"实际对象验证回执缺少"));
        byte[] receiptBytes=SourceProcessingResult.privateBytes(receiptPath,65536);
        var receipt=json.readTree(receiptBytes);
        assertEquals("LOCAL_PARSER_CHECK",receipt.required("permission").textValue());
        assertFalse(receipt.required("customer_processing_verified").booleanValue());
        var root=Path.of(System.getProperty("medical.projectRoot")).toRealPath();
        var deployment=json.readTree(SourceProcessingResult.privateBytes(root.resolve(".implementation-work/runtime/object-storage/deployment.json"),65536));
        var secrets=json.readTree(SourceProcessingResult.privateBytes(root.resolve(".implementation-work/runtime/object-storage/service-secret.json"),65536));
        assertEquals(receipt.required("service_id"),deployment.required("service_id"));
        assertEquals(receipt.required("deployment_id"),deployment.required("deployment_id"));
        assertEquals(deployment.required("service_id"),secrets.required("service_id"));
        var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(secrets.required("access_key").textValue(),secrets.required("secret_key").textValue()));
        var object=json.treeToValue(receipt.required("object_ref"),ObjectRef.class);
        assertTrue(object.objectKey().startsWith("local-parser-checks/"+receipt.required("check_id").textValue()+"/"));
        var intent=new S3ObjectWriter.WriteIntent(receipt.required("write_id").textValue(),object.objectKey(),object.sha256(),object.sizeBytes(),object.mediaType());
        var command=json.readTree(Files.readAllBytes(Path.of(receipt.required("command_path").textValue())));
        var result=json.readTree(Files.readAllBytes(Path.of(receipt.required("result_path").textValue())));
        byte[] expected=Files.readAllBytes(Path.of(receipt.required("bundle_path").textValue()));
        Path work=Files.createTempDirectory(root.resolve(".implementation-work/source-processing-storage-checks"),"java-read-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        S3ObjectReader.Admission current=() -> {
            if(!java.util.Arrays.equals(receiptBytes,org.springframework.util.function.ThrowingSupplier.of(
                    () -> SourceProcessingResult.privateBytes(receiptPath,65536)).get()))
                throw new IllegalStateException("实际对象验证身份已经变化");
        };
        URI endpoint=URI.create(receipt.required("endpoint").textValue());
        String bucket=receipt.required("bucket").textValue(),region=receipt.required("region").textValue();
        try(var writer=new S3ObjectWriter(endpoint,bucket,region,S3ObjectReader.Network.LOCAL_LOOPBACK,credentials,work,268435456,262144);
                var reader=new S3ObjectReader(endpoint,bucket,region,S3ObjectReader.Network.LOCAL_LOOPBACK,credentials,work,268435456,262144)) {
            assertEquals(object,writer.recoverVerified(intent,current));
            assertEquals(object,writer.recoverVerified(intent,current));
            try(var verified=reader.readVerified(object,current)) {
                byte[] actual=new byte[expected.length];
                for(int start=0;start<actual.length;) {
                    int size=Math.min(S3ObjectReader.MAX_SEGMENT_BYTES,actual.length-start);
                    System.arraycopy(verified.chunk(start,size),0,actual,start,size); start+=size;
                }
                assertArrayEquals(expected,actual);
                var parsed=new SourceProcessingResult().verify(command,result,actual);
                assertEquals(220,parsed.bundle().required("nodes").size());
            }
        } finally { Files.delete(work); }
    }
}

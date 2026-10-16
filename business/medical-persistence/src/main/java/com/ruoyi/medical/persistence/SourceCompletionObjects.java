package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;

final class SourceCompletionObjects {
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final JsonSchema schema;

    SourceCompletionObjects() throws IOException {
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-completion-objects.schema.json"))) {
            bytes=input.readAllBytes();
        }
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }

    Manifest verify(JsonNode storedParts,BigInteger total,String originalSha256,String objectPrefix,long maximumBytes) throws IOException {
        Objects.requireNonNull(storedParts); WireValues.revision(Objects.requireNonNull(total).toString()); WireValues.hash(originalSha256);
        if(maximumBytes<=0 || maximumBytes>104857600 || total.signum()<=0) throw new TenantAccessException(ErrorCode.SCHEMA_INVALID);
        if(total.compareTo(BigInteger.valueOf(maximumBytes))>0) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        Objects.requireNonNull(objectPrefix);
        if(objectPrefix.isBlank() || objectPrefix.startsWith("/") || objectPrefix.endsWith("/")
                || !Path.of(objectPrefix).normalize().toString().equals(objectPrefix)) mismatch();
        if(!schema.validate(storedParts).isEmpty()) mismatch();
        BigInteger count=total.add(UploadTransferProtocol.PART_BYTES).subtract(BigInteger.ONE).divide(UploadTransferProtocol.PART_BYTES);
        if(!count.equals(BigInteger.valueOf(storedParts.size()))) mismatch();
        var parts=new ArrayList<Part>(); var writes=new HashSet<String>(); var keys=new HashSet<String>();
        BigInteger received=BigInteger.ZERO;
        for(var value:storedParts) {
            long number=value.required("part_no").longValue();
            String write=value.required("write_id").textValue(); WireValues.uuid(write);
            BigInteger revision=WireValues.revision(value.required("revision").textValue());
            BigInteger size=WireValues.revision(value.required("size_bytes").textValue());
            String sha256=value.required("sha256").textValue(); WireValues.hash(sha256);
            var object=json.treeToValue(value.required("object_ref"),ObjectRef.class);
            if(number!=parts.size()+1L || revision.signum()<=0 || !writes.add(write) || !keys.add(object.objectKey())
                    || !object.objectKey().equals(objectPrefix+"/"+number+"/"+write)
                    || !size.equals(total.subtract(received).min(UploadTransferProtocol.PART_BYTES))
                    || !size.toString().equals(object.sizeBytes()) || !sha256.equals(object.sha256())) mismatch();
            parts.add(new Part(number,write,revision,object)); received=received.add(size);
        }
        if(!received.equals(total) || parts.size()==1 && !originalSha256.equals(parts.get(0).object().sha256())) mismatch();
        String canonical=new JsonCanonicalizer(json.writeValueAsBytes(storedParts)).getEncodedString();
        return new Manifest(total,originalSha256,objectPrefix,parts,DigestUtils.sha256Hex(canonical));
    }

    byte[] assemble(Manifest manifest,Path privateDirectory,long maximumBytes,S3ObjectReader reader,S3ObjectWriter writer,
            S3ObjectReader.Admission current) throws Exception {
        Objects.requireNonNull(manifest); Objects.requireNonNull(reader); Objects.requireNonNull(writer); Objects.requireNonNull(current).requireCurrent();
        try(var assembly=new UploadObjectAssembly(privateDirectory,maximumBytes,manifest.total(),manifest.originalSha256(),current)) {
            for(var part:manifest.parts()) {
                current.requireCurrent(); var object=part.object();
                var intent=new S3ObjectWriter.WriteIntent(part.writeId(),object.objectKey(),object.sha256(),object.sizeBytes(),object.mediaType());
                if(!object.equals(writer.recoverVerified(intent,current))) mismatch();
                assembly.append(part.number(),object,reader);
            }
            Path content=assembly.finish(); current.requireCurrent(); byte[] bytes=Files.readAllBytes(content);
            if(!manifest.total().toString().equals(Integer.toString(bytes.length))
                    || !manifest.originalSha256().equals(WireValues.sha256(bytes))) mismatch();
            current.requireCurrent(); return bytes;
        }
    }

    record Part(long number,String writeId,BigInteger revision,ObjectRef object) { }

    static final class Manifest {
        private final BigInteger total;
        private final String originalSha256,objectPrefix,sha256;
        private final List<Part> parts;
        private Manifest(BigInteger total,String originalSha256,String objectPrefix,List<Part> parts,String sha256) {
            this.total=total; this.originalSha256=originalSha256; this.objectPrefix=objectPrefix; this.parts=List.copyOf(parts); this.sha256=sha256;
        }
        BigInteger total() { return total; }
        String originalSha256() { return originalSha256; }
        String objectPrefix() { return objectPrefix; }
        String sha256() { return sha256; }
        List<Part> parts() { return parts; }
    }

    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

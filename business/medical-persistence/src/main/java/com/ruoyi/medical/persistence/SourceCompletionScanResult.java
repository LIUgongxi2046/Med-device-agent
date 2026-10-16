package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;

final class SourceCompletionScanResult {
    private static final List<String> NAMES=List.of("scan.txt","signature-0.txt","signature-1.txt","signature-2.txt");
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema schema,commands;
    private final String manifest;

    SourceCompletionScanResult() throws IOException {
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-completion-scan.schema.json"))) {
            bytes=input.readAllBytes();
        }
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        commands=schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append("Command"));
        schema.initializeValidators(); commands.initializeValidators(); manifest=DigestUtils.sha256Hex(bytes);
    }

    Verified verify(JsonNode command,JsonNode receipt) throws IOException {
        if(command==null || receipt==null || !commands.validate(command).isEmpty() || !schema.validate(receipt).isEmpty()) mismatch();
        for(String name:List.of("schema_version","event_id","operation_id","upload_id","worker_id","lease_generation",
                "original_sha256","size_bytes","scanner_release_sha256"))
            if(!command.required(name).equals(receipt.required(name))) mismatch();
        String commandSha256=DigestUtils.sha256Hex(canonical(command));
        if(!commandSha256.equals(receipt.required("command_sha256").textValue())) mismatch();
        String extension=org.apache.commons.io.FilenameUtils.getExtension(command.required("source_path").textValue()).toLowerCase(java.util.Locale.ROOT);
        String format=switch(extension) {
            case "pdf" -> "PDF"; case "docx" -> "DOCX"; case "xlsx" -> "XLSX"; case "csv" -> "CSV";
            case "pptx" -> "PPTX"; case "md","markdown" -> "MARKDOWN"; case "txt" -> "TXT";
            default -> throw new TenantAccessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        };
        if(!format.equals(receipt.required("format").textValue())) mismatch();
        BigInteger size=WireValues.revision(receipt.required("size_bytes").textValue());
        WireValues.revision(receipt.required("lease_generation").textValue());
        if(size.signum()<=0 || size.compareTo(BigInteger.valueOf(104857600))>0) mismatch();
        long total=0;
        for(int index=0;index<NAMES.size();index++) {
            var artifact=receipt.required("artifacts").get(index);
            if(!NAMES.get(index).equals(artifact.required("name").textValue())) mismatch();
            byte[] content=Base64.getDecoder().decode(artifact.required("content_base64").textValue());
            BigInteger length=WireValues.revision(artifact.required("size_bytes").textValue());
            if(content.length==0 || !length.equals(BigInteger.valueOf(content.length))
                    || !Base64.getEncoder().encodeToString(content).equals(artifact.required("content_base64").textValue())
                    || !DigestUtils.sha256Hex(content).equals(artifact.required("sha256").textValue())) mismatch();
            total+=content.length;
        }
        if(total>262144 || canonical(receipt).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>524288) mismatch();
        return new Verified(command,receipt,manifest,commandSha256,DigestUtils.sha256Hex(canonical(receipt)));
    }

    JsonNode read(String value) throws IOException { return json.readTree(Objects.requireNonNull(value)); }
    String canonical(JsonNode value) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(value)).getEncodedString(); }
    String manifest() { return manifest; }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }

    static final class Verified {
        private final JsonNode command,receipt;
        private final String manifest,commandSha256,receiptSha256;
        private Verified(JsonNode command,JsonNode receipt,String manifest,String commandSha256,String receiptSha256) {
            this.command=command.deepCopy(); this.receipt=receipt.deepCopy(); this.manifest=manifest;
            this.commandSha256=commandSha256; this.receiptSha256=receiptSha256;
        }
        public JsonNode command() { return command.deepCopy(); }
        public JsonNode receipt() { return receipt.deepCopy(); }
        String manifest() { return manifest; }
        String commandSha256() { return commandSha256; }
        String receiptSha256() { return receiptSha256; }
    }
}

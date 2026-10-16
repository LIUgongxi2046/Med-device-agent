package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.DatasetBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;
import org.erdtman.jcs.JsonCanonicalizer;

final class SourceProcessingResult {
    private final JsonMapper json=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema validator;
    private final SchemaCatalogue catalogue;
    private final String manifest;

    SourceProcessingResult() throws Exception {
        byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-processing.schema.json"))) { bytes=input.readAllBytes(); }
        validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(json.readTree(bytes),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for(String name:List.of("Command","Bundle","Receipt","ToolScope")) definition(name).initializeValidators();
        catalogue=SchemaCatalogue.fromClasspath(); manifest=DigestUtils.sha256Hex(bytes);
    }

    JsonNode read(String value) throws IOException { return json.readTree(value); }
    String canonical(JsonNode node) throws IOException { return new JsonCanonicalizer(json.writeValueAsBytes(node)).getEncodedString(); }
    String manifest() { return manifest; }

    void requireBundle(JsonNode bundle) { validate("Bundle",bundle); }

    void requireCapability(SourceGovernanceProtocol.ProcessingConfigRow configuration,
            SourceProcessingPolicy.Binding binding,SourceProcessingPolicy policy) throws Exception {
        requirePublishedDefinition(configuration);
        if(!toolScope(binding,policy).equals(read(configuration.toolScope()))
                || !binding.artifactDigest().equals(configuration.artifactDigest())
                || !binding.artifactDigest().equals(artifactDigest(binding,policy))) mismatch();
    }

    void requirePublishedDefinition(SourceGovernanceProtocol.ProcessingConfigRow configuration) throws IOException {
        if(!publication("Command").equals(read(configuration.inputSchemaRef()))
                || !publication("Bundle").equals(read(configuration.outputSchemaRef()))) mismatch();
        WireValues.hash(configuration.artifactDigest()); validate("ToolScope",read(configuration.toolScope()));
    }

    JsonNode publication(String name) {
        var reference=json.createObjectNode();
        reference.put("schema_ref","urn:medical:source-processing:1.0.0#/$defs/"+name);
        reference.put("schema_sha256",manifest); return reference;
    }

    String indexConfiguration(SourceGovernanceProtocol.ProcessingConfigRow configuration) throws IOException {
        requirePublishedDefinition(configuration);
        return read(configuration.toolScope()).required("index_config_revision").isNull()?null:
            read(configuration.toolScope()).required("index_config_revision").textValue();
    }

    JsonNode toolScope(SourceProcessingPolicy.Binding binding,SourceProcessingPolicy policy) {
        var scope=json.createObjectNode(); scope.put("schema_version","1.0.0"); scope.put("tool_id","source.parse");
        scope.put("purpose","SOURCE_PROCESSING"); scope.put("input_data_kind","REGISTERED");
        scope.put("network_enabled",false); scope.put("external_process",false);
        for(String format:List.of("DOCX","PDF","XLSX","CSV","TXT","MARKDOWN","PPTX")) scope.withArray("formats").add(format);
        for(String action:List.of("READ_ORIGINAL","WRITE_PARSED")) scope.withArray("storage_actions").add(action);
        scope.put("maximum_source_bytes",policy.maximumSourceBytes()); scope.put("maximum_bundle_bytes",policy.maximumBundleBytes());
        scope.put("timeout_ms",policy.timeout().toMillis()); scope.put("environment",policy.environment()); scope.put("region",policy.region());
        if(binding.indexConfigRevision()==null) scope.putNull("index_config_revision"); else scope.put("index_config_revision",binding.indexConfigRevision());
        return scope;
    }

    String artifactDigest(SourceProcessingPolicy.Binding binding,SourceProcessingPolicy policy) throws Exception {
        var artifact=json.createObjectNode(); artifact.put("schema_version","1.0.0"); artifact.put("processor","SOURCE-PROCESSING-1");
        artifact.put("python_sha256",policy.pythonSha256()); artifact.put("schema_manifest_sha256",manifest);
        artifact.put("scanner_release_sha256",binding.scannerReleaseSha256());
        for(var file:policy.sourceFiles().stream().sorted(java.util.Comparator.comparing(SourceProcessingPolicy.CodeFile::path)).toList()) {
            var entry=artifact.withArray("source_files").addObject(); entry.put("path",file.path()); entry.put("sha256",file.sha256());
        }
        return DigestUtils.sha256Hex(canonical(artifact));
    }

    Verified local(JsonNode command, Path resultPath, int maximumBytes) throws Exception {
        validate("Command",command);
        Path work=privateDirectory(Path.of(command.required("output_directory").textValue()));
        var receipt=read(new String(privateBytes(resultPath,65536),StandardCharsets.UTF_8));
        Path bundle=Path.of(receipt.required("bundle_path").textValue());
        if(!bundle.isAbsolute() || !work.equals(bundle.getParent()) || !"parsed.json".equals(bundle.getFileName().toString())) mismatch();
        var verified=verify(command,receipt,privateBytes(bundle,maximumBytes));
        for(var artifact:verified.bundle().required("scan").required("artifacts")) {
            byte[] output=privateBytes(work.resolve(artifact.required("name").textValue()),1048576);
            if(!artifact.required("sha256").textValue().equals(DigestUtils.sha256Hex(output))
                    || !artifact.required("size_bytes").textValue().equals(Integer.toString(output.length))) mismatch();
        }
        return verified;
    }

    Verified verify(JsonNode command, JsonNode receipt, byte[] bytes) throws Exception {
        validate("Command",command);
        var bundle=json.readTree(bytes); validate("Receipt",receipt); validate("Bundle",bundle);
        if(!java.util.Arrays.equals(bytes,canonical(bundle).getBytes(StandardCharsets.UTF_8))) mismatch();
        for(String field:List.of("schema_version","attempt_id","run_id","source_version","parse_version","config_revision",
                "config_sha256","worker_id","lease_generation","original_sha256"))
            if(!command.required(field).equals(receipt.required(field)) || !command.required(field).equals(bundle.required(field))) mismatch();
        String commandHash=DigestUtils.sha256Hex(canonical(command));
        if(!commandHash.equals(receipt.required("command_sha256").textValue())
                || !commandHash.equals(bundle.required("command_sha256").textValue())
                || !DigestUtils.sha256Hex(bytes).equals(receipt.required("bundle_sha256").textValue())
                || !Integer.toString(bytes.length).equals(receipt.required("bundle_size_bytes").textValue())
                || !command.required("format").equals(bundle.required("format"))
                || !receipt.required("status").equals(bundle.required("status"))) mismatch();
        WireValues.revision(receipt.required("bundle_size_bytes").textValue());
        WireValues.revision(receipt.required("lease_generation").textValue());
        var nodes=bundle.required("nodes"); var chunks=bundle.required("chunks"); var issues=bundle.required("issues");
        if(nodes.size()!=receipt.required("node_count").intValue() || chunks.size()!=nodes.size()
                || chunks.size()!=receipt.required("chunk_count").intValue() || issues.size()!=receipt.required("issue_count").intValue()
                || !(issues.isEmpty()?"COMPLETE":"PARTIAL_FAILED").equals(bundle.required("status").textValue())) mismatch();
        var nodeIds=new HashSet<String>(); var chunkIds=new HashSet<String>();
        for(int ordinal=0;ordinal<nodes.size();ordinal++) {
            var node=nodes.get(ordinal); var chunk=chunks.get(ordinal);
            if(!nodeIds.add(node.required("node_id").textValue()) || !chunkIds.add(chunk.required("chunk_id").textValue())
                    || node.required("ordinal").intValue()!=ordinal || chunk.required("ordinal").intValue()!=ordinal
                    || !node.required("node_id").equals(chunk.required("node_id"))
                    || !chunk.required("raw_text").equals(chunk.required("search_text"))) mismatch();
            for(String field:List.of("raw_text","quote_hash","locator")) if(!node.required(field).equals(chunk.required(field))) mismatch();
            if(!node.required("quote_hash").textValue().equals(DigestUtils.sha256Hex(node.required("raw_text").textValue().getBytes(StandardCharsets.UTF_8)))) mismatch();
            TypedSchemaBindings.bind(catalogue,DatasetBindings.LOCATOR,node.required("locator"));
        }
        for(var issue:issues) if(!issue.required("locator").isNull()) TypedSchemaBindings.bind(catalogue,DatasetBindings.LOCATOR,issue.required("locator"));
        var scan=bundle.required("scan"); var artifactNames=new HashSet<String>();
        if(!command.required("original_sha256").equals(scan.required("original_sha256"))
                || !command.required("size_bytes").equals(scan.required("size_bytes"))
                || !command.required("scanner_release_sha256").equals(scan.required("release_sha256"))) mismatch();
        for(var artifact:scan.required("artifacts")) {
            if(!artifactNames.add(artifact.required("name").textValue())) mismatch();
            String encoded=artifact.required("content_base64").textValue(); byte[] content=java.util.Base64.getDecoder().decode(encoded);
            if(!encoded.equals(java.util.Base64.getEncoder().encodeToString(content))
                    || !DigestUtils.sha256Hex(content).equals(artifact.required("sha256").textValue())
                    || !Integer.toString(content.length).equals(artifact.required("size_bytes").textValue())) mismatch();
        }
        var processor=bundle.required("processor_manifest");
        catalogue.requirePublished(catalogue.schemaRef("dataset","locator"),processor.required("locator_schema_sha256").textValue());
        if(!command.required("config_revision").equals(processor.required("config_revision"))
                || !command.required("config_sha256").equals(processor.required("config_sha256"))
                || !command.required("scanner_release_sha256").equals(processor.required("scanner_release_sha256"))
                || !processor.required("network_enabled").isBoolean() || processor.required("network_enabled").booleanValue()
                || !"REGISTERED".equals(processor.required("data_kind").textValue())
                || !"SOURCE-NODE-CHUNKS-1".equals(processor.required("chunking_revision").textValue())
                || !processor.required("parameters_sha256").textValue().equals(DigestUtils.sha256Hex(
                    canonical(read(processor.required("parameters_json").textValue()))))) mismatch();
        return new Verified(receipt.deepCopy(),bundle.deepCopy(),bytes);
    }

    static Path privateDirectory(Path path) throws IOException {
        if(!path.isAbsolute() || !path.equals(path.toRealPath())
                || !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))) mismatch();
        return path;
    }
    static byte[] privateBytes(Path path,int maximum) throws IOException {
        if(!path.isAbsolute() || !path.equals(path.toRealPath())
                || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path,LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rw-------"))
                || Files.size(path)<=0 || Files.size(path)>maximum) mismatch();
        return Files.readAllBytes(path);
    }
    private void validate(String name,JsonNode value) { if(!definition(name).validate(value).isEmpty()) mismatch(); }
    private JsonSchema definition(String name) {
        return validator.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
    }
    record Verified(JsonNode receipt,JsonNode bundle,byte[] bytes) { }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

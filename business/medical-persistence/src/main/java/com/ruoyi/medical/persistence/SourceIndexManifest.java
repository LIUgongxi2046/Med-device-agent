package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
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
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;

final class SourceIndexManifest {
    private static final NameBasedGenerator POINTS=Generators.nameBasedGenerator(
        Generators.nameBasedGenerator(NameBasedGenerator.NAMESPACE_URL).generate("urn:medical:index-point:1.0.0"));
    private final PermissionSchema json=new PermissionSchema();
    private final SourceProcessingResult parsed;
    private final SchemaCatalogue catalogue;
    private final JsonSchema validator;
    private final String sha256;

    SourceIndexManifest() throws Exception {
        parsed=new SourceProcessingResult(); catalogue=SchemaCatalogue.fromClasspath(); byte[] bytes;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-index.schema.json"))) { bytes=input.readAllBytes(); }
        sha256=DigestUtils.sha256Hex(bytes);
        validator=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(parsed.read(new String(bytes,StandardCharsets.UTF_8)),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        for(String name:List.of("Command","Chunk","Item","Manifest","ToolScope","Receipt")) definition(name).initializeValidators();
    }

    JsonNode read(String value) throws Exception { return parsed.read(value); }
    String canonical(JsonNode value) throws Exception { return json.canonical(value); }
    String sha256() { return sha256; }
    String hash(JsonNode value) throws Exception { return DigestUtils.sha256Hex(canonical(value)); }
    void require(String name,JsonNode value) { if(!definition(name).validate(value).isEmpty()) mismatch(); }
    private JsonSchema definition(String name) {
        return validator.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name));
    }

    JsonNode chunks(JsonNode bundle,int maximum) throws Exception {
        parsed.requireBundle(bundle);
        var nodes=bundle.required("nodes"); var chunks=bundle.required("chunks");
        if(maximum<1) throw new IllegalArgumentException("全文索引容量无效");
        if(chunks.size()>maximum) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(chunks.isEmpty() || !"COMPLETE".equals(bundle.required("status").textValue())
                || !bundle.required("issues").isEmpty() || nodes.size()!=chunks.size()) mismatch();
        var result=json.object().arrayNode(); var chunkIds=new HashSet<String>(); var nodeIds=new HashSet<String>();
        for(int ordinal=0;ordinal<chunks.size();ordinal++) {
            var chunk=chunks.get(ordinal); var node=nodes.get(ordinal);
            if(chunk.required("ordinal").intValue()!=ordinal || node.required("ordinal").intValue()!=ordinal
                    || !chunkIds.add(chunk.required("chunk_id").textValue()) || !nodeIds.add(node.required("node_id").textValue())
                    || !chunk.required("node_id").equals(node.required("node_id"))) mismatch();
            for(String field:List.of("raw_text","quote_hash","locator")) if(!chunk.required(field).equals(node.required(field))) mismatch();
            if(!chunk.required("raw_text").equals(chunk.required("search_text"))
                    || !chunk.required("quote_hash").textValue().equals(DigestUtils.sha256Hex(chunk.required("raw_text").textValue()))) mismatch();
            TypedSchemaBindings.bind(catalogue,DatasetBindings.LOCATOR,chunk.required("locator"));
            var item=result.addObject(); item.put("chunk_id",chunk.required("chunk_id").textValue());
            item.put("node_id",chunk.required("node_id").textValue()); item.put("ordinal",ordinal);
            item.put("quote_hash",chunk.required("quote_hash").textValue());
            item.put("search_sha256",DigestUtils.sha256Hex(chunk.required("search_text").textValue()));
            item.put("locator_sha256",hash(chunk.required("locator"))); item.put("node_hash",hash(node)); require("Chunk",item);
        }
        return result;
    }

    JsonNode projected(List<Map<String,Object>> rows,int maximum) throws Exception {
        if(rows.size()>maximum) throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        if(rows.isEmpty()) throw new TenantAccessException(ErrorCode.SOURCE_NOT_READY);
        var result=json.object().arrayNode(); var ids=new HashSet<String>(); var nodeIds=new HashSet<String>();
        for(int ordinal=0;ordinal<rows.size();ordinal++) {
            var row=rows.get(ordinal); var locator=read((String)row.get("locator"));
            TypedSchemaBindings.bind(catalogue,DatasetBindings.LOCATOR,locator);
            String raw=(String)Objects.requireNonNull(row.get("rawText")),search=(String)Objects.requireNonNull(row.get("searchText"));
            if(!Integer.toString(ordinal).equals(row.get("ordinal").toString())
                    || !Integer.toString(ordinal).equals(row.get("chunkOrdinal").toString())
                    || !ids.add((String)row.get("chunkId")) || !nodeIds.add((String)row.get("nodeId"))
                    || !raw.equals(search) || !DigestUtils.sha256Hex(raw).equals(row.get("quoteHash"))
                    || !locator.equals(read((String)row.get("chunkLocator")))) mismatch();
            var range=read((String)row.get("nodeRange")); var expected=json.object();
            expected.put("char_start",0); expected.put("char_end",raw.codePointCount(0,raw.length()));
            if(!range.equals(expected)) mismatch();
            var item=result.addObject(); item.put("chunk_id",(String)row.get("chunkId")); item.put("node_id",(String)row.get("nodeId"));
            item.put("ordinal",ordinal); item.put("quote_hash",(String)row.get("quoteHash"));
            item.put("search_sha256",DigestUtils.sha256Hex(search)); item.put("locator_sha256",hash(locator));
            item.put("node_hash",(String)row.get("nodeHash")); require("Chunk",item);
        }
        return result;
    }

    JsonNode manifest(SourceProcessingRow row,String event,JsonNode command,String index,JsonNode chunks,SourceIndexPolicy policy) throws Exception {
        var result=json.object(); result.put("schema_version","1.0.0"); result.put("event_id",event);
        result.put("consumer_id",SourceIndexProtocol.CONSUMER); result.put("tenant_id",row.tenantId()); result.put("project_id",row.projectId());
        result.put("source_id",row.sourceId()); result.put("source_version",row.sourceVersion()); result.put("parse_version",row.parseVersion());
        result.put("index_version",index); result.put("rights_revision",row.rightsRevision());
        result.put("config_revision",command.required("config_revision").textValue());
        result.put("config_sha256",policy.binding(command.required("config_revision").textValue()).configSha256());
        result.put("input_sha256",hash(command)); result.put("content_sha256",command.required("content_sha256").textValue());
        result.put("mode","FULLTEXT"); result.put("chunking_revision","SOURCE-NODE-CHUNKS-1"); result.put("ngram_token_size",policy.ngramTokenSize());
        var items=result.putArray("items");
        for(var chunk:chunks) {
            var item=items.addObject(); item.set("chunk",chunk);
            var identity=json.object().arrayNode();
            for(String part:List.of(row.tenantId(),row.sourceVersion(),row.parseVersion(),chunk.required("chunk_id").textValue(),index)) {
                WireValues.uuid(part); identity.add(part);
            }
            item.put("point_id",POINTS.generate(canonical(identity)).toString());
        }
        require("Manifest",result); if(canonical(result).getBytes(StandardCharsets.UTF_8).length>policy.maximumBundleBytes())
            throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
        return result;
    }

    JsonNode verifyParsed(SourceProcessingRow row,byte[] bytes) throws Exception {
        var command=parsed.read(row.commandRef()); var receipt=(ObjectNode)parsed.read(row.resultRef());
        var object=receipt.required("object_ref"); receipt.remove(List.of("object_ref","confirmed_by_worker","confirmed_lease_generation"));
        var verified=parsed.verify(command,receipt,bytes);
        if(!row.sourceVersion().equals(verified.bundle().required("source_version").textValue())
                || !row.parseVersion().equals(verified.bundle().required("parse_version").textValue())
                || !row.configRevision().equals(verified.bundle().required("config_revision").textValue())
                || !row.attemptId().equals(verified.bundle().required("attempt_id").textValue())
                || !row.runId().equals(verified.bundle().required("run_id").textValue())
                || !row.originalSha256().equals(verified.bundle().required("original_sha256").textValue())
                || !object.required("sha256").textValue().equals(DigestUtils.sha256Hex(bytes))
                || !object.required("size_bytes").textValue().equals(Integer.toString(bytes.length))) mismatch();
        return verified.bundle();
    }

    void requireCapability(SourceGovernanceProtocol.ProcessingConfigRow config,SourceIndexPolicy policy) throws Exception {
        var binding=policy.binding(config.versionId());
        if(!"CAPABILITY_VERSION".equals(config.entityKind()) || !"CAPABILITY".equals(config.configurationKind())
                || !"TOOL".equals(config.capabilityKind()) || !"PUBLISHED".equals(config.status())
                || !binding.configSha256().equals(config.contentSha256()) || !binding.configSha256().equals(config.registeredSha256())
                || !publication("Command").equals(read(config.inputSchemaRef())) || !publication("Manifest").equals(read(config.outputSchemaRef()))
                || !scope(policy).equals(read(config.toolScope())) || !binding.artifactDigest().equals(config.artifactDigest())
                || !binding.artifactDigest().equals(artifactDigest())) throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
    }

    private JsonNode publication(String name) {
        var result=json.object(); result.put("schema_ref","urn:medical:source-index:1.0.0#/$defs/"+name); result.put("schema_sha256",sha256); return result;
    }
    private JsonNode scope(SourceIndexPolicy policy) {
        var result=json.object(); result.put("schema_version","1.0.0"); result.put("tool_id","source.index.fulltext");
        result.put("purpose","SOURCE_PROCESSING"); result.put("mode","FULLTEXT"); result.put("network_enabled",false);
        result.put("external_process",false); result.put("engine","MYSQL_FULLTEXT"); result.put("parser","ngram");
        result.put("ngram_token_size",policy.ngramTokenSize()); result.put("chunking_revision","SOURCE-NODE-CHUNKS-1");
        result.put("maximum_chunks",policy.maximumChunks()); result.put("maximum_bundle_bytes",policy.maximumBundleBytes());
        result.put("timeout_ms",policy.timeout().toMillis()); result.put("environment",policy.environment()); result.put("region",policy.region());
        require("ToolScope",result); return result;
    }
    String artifactDigest() throws Exception {
        var result=json.object(); result.put("schema_version","1.0.0"); result.put("processor","SOURCE-INDEX-FULLTEXT-1");
        result.put("schema_sha256",sha256); var classes=new java.util.TreeSet<String>();
        for(var type:List.of(SourceIndexManifest.class,SourceIndexWorker.class,SourceIndexAccess.class,SourceIndexConfiguration.class,
                SourceIndexPolicy.class,SourceIndexProtocol.class,com.ruoyi.medical.persistence.mapper.SourceIndexMapper.class,
                Generators.class,NameBasedGenerator.class,com.fasterxml.uuid.impl.UUIDUtil.class,
                com.fasterxml.uuid.StringArgGenerator.class,com.fasterxml.uuid.UUIDType.class)) collect(type,classes);
        classes.add(SourceIndexConfiguration.class.getName()+"$1");
        classes.add(com.fasterxml.uuid.impl.UUIDUtil.class.getName()+"$1");
        for(String name:classes) {
            String path="/"+name.replace('.','/')+".class";
            try(var input=Objects.requireNonNull(getClass().getResourceAsStream(path))) {
                var item=result.withArray("classes").addObject(); item.put("name",name); item.put("sha256",DigestUtils.sha256Hex(input));
            }
        }
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/medical-mappers/SourceIndexMapper.xml"))) {
            result.put("mapper_sha256",DigestUtils.sha256Hex(input));
        }
        return hash(result);
    }
    private static void collect(Class<?> type,java.util.Set<String> result) {
        result.add(type.getName()); for(var nested:type.getDeclaredClasses()) collect(nested,result);
    }
    JsonNode receipt(SourceIndexProtocol.Build build,boolean ready) throws Exception {
        var manifest=read(build.manifest()); var result=json.object(); result.put("schema_version","1.0.0");
        result.put("event_id",build.eventId()); result.put("consumer_id",SourceIndexProtocol.CONSUMER);
        result.put("source_version",build.sourceVersion()); result.put("parse_version",build.parseVersion()); result.put("index_version",build.indexVersion());
        result.put("input_sha256",build.inputSha256()); result.put("manifest_sha256",build.manifestSha256());
        int count=manifest.required("items").size(); result.put("expected_count",count); result.put("verified_count",ready?count:0);
        result.put("status",ready?"READY":"FAILED"); if(ready) result.putNull("failure_code"); else result.put("failure_code","INDEX_LIMIT");
        require("Receipt",result); return result;
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

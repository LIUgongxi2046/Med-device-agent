package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.Points.UpdateStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.digest.DigestUtils;
import org.redisson.api.RedissonClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import static io.qdrant.client.ConditionFactory.matchKeyword;

final class SourceDeletionStores {
    private static final Duration TIMEOUT=Duration.ofSeconds(30);
    private final SourceDeletionPolicy policy;
    private final SourceDeletionSchemaSupport schema;
    private final RedissonClient redis;

    SourceDeletionStores(SourceDeletionPolicy policy,PermissionSchema json,SourceGovernanceSchema governance,RedissonClient redis) throws Exception {
        this.policy=policy; this.schema=new SourceDeletionSchemaSupport(json,governance); this.redis=redis;
    }

    JsonNode erase(String tenant,SourceDeletionTarget target,Guard guard,SourceDeletionAccess access,
            com.ruoyi.medical.persistence.mapper.SourceDeletionMapper mapper,Map<String,Object> values) throws Exception {
        var evidence=schema.json.object(); evidence.put("kind",target.kind());
        var stores=evidence.putArray("stores");
        switch(target.kind()) {
            case "ORIGINAL" -> {
                var parts=guard.call(() -> mapper.originalParts(values));
                var copies=guard.call(() -> mapper.originalCopies(values));
                if(parts.size()+copies.size()+1>policy.maximumEntries()) capacity();
                for(var store:policy.originals()) {
                    requireKey(store,target.objectKey());
                    guard.call(() -> { access.requireExclusiveObject(tenant,target,target.objectKey()); return null; });
                    stores.add(eraseObjects(store,target.objectKey(),true,target.contentSha256(),guard));
                    for(var copy:copies) {
                        String key=Objects.toString(Objects.requireNonNull(copy.get("object_key")));
                        String hash=Objects.toString(Objects.requireNonNull(copy.get("sha256"))); WireValues.hash(hash);
                        if(copy.get("object_version")==null) mismatch();
                        requireKey(store,key);
                        guard.call(() -> { access.requireExclusiveObject(tenant,target,key); return null; });
                        stores.add(eraseObjects(store,key,true,hash,guard));
                    }
                    for(var part:parts) {
                        if(!"STORED".equals(part.get("state")) || part.get("object_version")==null) mismatch();
                        String key=Objects.toString(part.get("object_key")),hash=Objects.toString(part.get("sha256"));
                        WireValues.hash(hash);
                        guard.call(() -> { access.requireExclusiveObject(tenant,target,key); return null; });
                        stores.add(eraseObjects(store,key,true,hash,guard));
                    }
                }
            }
            case "PARSED" -> {
                requireKey(policy.parsed(),target.objectKey());
                guard.call(() -> { access.requireExclusiveObject(tenant,target,target.objectKey()); return null; });
                stores.add(eraseObjects(policy.parsed(),target.objectKey(),true,target.contentSha256(),guard));
                guard.call(() -> { mapper.eraseParsedChunks(values); mapper.eraseParsedNodes(values); return null; });
                if(guard.call(() -> mapper.remainingParsed(values)).signum()!=0) mismatch();
                stores.addObject().put("store_id","MYSQL_PARSED").put("remaining","0");
            }
            case "FULLTEXT" -> {
                guard.call(() -> { mapper.withdrawIndex(values); mapper.eraseFulltext(values); return null; });
                if(guard.call(() -> mapper.remainingFulltext(values)).signum()!=0) mismatch();
                stores.addObject().put("store_id","MYSQL_FULLTEXT").put("remaining","0");
            }
            case "VECTOR" -> {
                var filter=Filter.newBuilder().addMust(matchKeyword("tenant_id",tenant))
                    .addMust(matchKeyword("source_version",target.sourceVersion()))
                    .addMust(matchKeyword("parse_version",target.parseVersion()))
                    .addMust(matchKeyword("index_version",target.indexVersion())).build();
                for(var store:policy.vectors()) {
                    String apiKey=Files.readString(store.apiKeyFile(),StandardCharsets.UTF_8).stripTrailing();
                    if(apiKey.isBlank() || apiKey.contains("\n") || apiKey.contains("\r")) throw new IllegalArgumentException("向量秘密格式无效");
                    try(var client=new QdrantClient(QdrantGrpcClient.newBuilder(store.host(),store.port(),store.tls())
                            .withApiKey(apiKey).build())) {
                        var result=guard.call(() -> client.deleteAsync(store.collection(),filter,TIMEOUT).get(30,TimeUnit.SECONDS));
                        if(result.getStatus()!=UpdateStatus.Completed) mismatch();
                        long remaining=guard.call(() -> client.countAsync(store.collection(),filter,true,TIMEOUT).get(30,TimeUnit.SECONDS));
                        if(remaining!=0) mismatch();
                        stores.addObject().put("store_id",store.collection()).put("remaining","0")
                            .put("tenant_id",tenant).put("source_version",target.sourceVersion())
                            .put("parse_version",target.parseVersion()).put("index_version",target.indexVersion())
                            .put("provider_operation_id",Long.toString(result.getOperationId()))
                            .put("provider_status","Completed").put("exact_count_verified",true);
                    }
                }
                guard.call(() -> { mapper.withdrawIndex(values); return null; });
            }
            case "CACHE" -> {
                String pattern=policy.cacheNamespace()+":"+tenant+":"+target.sourceVersion()+":*";
                var keys=guard.call(() -> cacheKeys(pattern));
                var cacheEvidence=stores.addObject().put("store_id","REDIS").put("scope_pattern",pattern);
                var receipts=cacheEvidence.putArray("copy_receipts");
                for(String key:keys) {
                    long removed=guard.call(() -> redis.getKeys().delete(key));
                    if(removed<0||removed>1)mismatch();
                    receipts.addObject().put("cache_key",key).put("removed_count",Long.toString(removed))
                        .put("observation",removed==1?"DELETE_ACKNOWLEDGED":"ALREADY_ABSENT");
                }
                if(!guard.call(() -> cacheKeys(pattern)).isEmpty()) mismatch();
                cacheEvidence.put("remaining","0");
            }
            case "DIAGNOSTIC" -> {
                String prefix=policy.diagnostics().prefix()+tenant+"/"+target.sourceVersion()+"/";
                stores.add(eraseObjects(policy.diagnostics(),prefix,false,null,guard));
                guard.call(() -> { mapper.eraseProcessingDiagnostics(values); mapper.eraseParseDiagnostics(values);
                    mapper.eraseReadPositions(values); return null; });
                if(guard.call(() -> mapper.remainingDiagnostics(values)).signum()!=0) mismatch();
                stores.addObject().put("store_id","MYSQL_DIAGNOSTIC").put("remaining","0");
            }
            default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
        return evidence;
    }

    /** Read-only independent approval probe. It never erases or treats a declaration as observation. */
    JsonNode observeAuthority(String tenant,List<SourceGovernanceProtocol.DeletionItemRow> targets,
            Guard guard,com.ruoyi.medical.persistence.mapper.SourceDeletionMapper mapper,Map<String,Object> values) throws Exception {
        var report=schema.json.object().put("schema_version","2.0.0").put("complete",true);
        var observations=report.putArray("observations");
        for(var row:targets) {
            var target=SourceDeletionTarget.read(row,schema.governance);
            var v=new java.util.HashMap<>(values);v.put("sourceVersion",target.sourceVersion());
            v.put("parseVersion",target.parseVersion());v.put("indexVersion",target.indexVersion());v.put("objectKey",target.objectKey());
            switch(target.kind()) {
                case "ORIGINAL" -> {
                    var keys=new java.util.TreeSet<String>();keys.add(target.objectKey());
                    for(var copy:guard.call(()->mapper.originalCopies(v)))keys.add(Objects.toString(copy.get("object_key")));
                    for(var part:guard.call(()->mapper.originalParts(v)))keys.add(Objects.toString(part.get("object_key")));
                    if(keys.size()>policy.maximumEntries())capacity();
                    for(var store:policy.originals())for(String key:keys)observations.add(observeVersions(store,key,true,guard));
                }
                case "PARSED" -> {
                    observations.add(observeVersions(policy.parsed(),target.objectKey(),true,guard));
                    observations.addObject().put("store_id","MYSQL_PARSED").put("copy_count",guard.call(()->mapper.remainingParsed(v)).toString());
                }
                case "FULLTEXT" -> observations.addObject().put("store_id","MYSQL_FULLTEXT")
                    .put("copy_count",guard.call(()->mapper.remainingFulltext(v)).toString());
                case "VECTOR" -> {
                    var filter=Filter.newBuilder().addMust(matchKeyword("tenant_id",tenant))
                        .addMust(matchKeyword("source_version",target.sourceVersion()))
                        .addMust(matchKeyword("parse_version",target.parseVersion()))
                        .addMust(matchKeyword("index_version",target.indexVersion())).build();
                    for(var store:policy.vectors())try(var client=new QdrantClient(QdrantGrpcClient.newBuilder(store.host(),store.port(),store.tls())
                            .withApiKey(Files.readString(store.apiKeyFile(),StandardCharsets.UTF_8).stripTrailing()).build())) {
                        long count=guard.call(()->client.countAsync(store.collection(),filter,true,TIMEOUT).get(30,TimeUnit.SECONDS));
                        observations.addObject().put("store_id",store.collection()).put("copy_count",Long.toString(count))
                            .put("source_version",target.sourceVersion()).put("index_version",target.indexVersion());
                    }
                }
                case "CACHE" -> {
                    var keys=guard.call(()->cacheKeys(policy.cacheNamespace()+":"+tenant+":"+target.sourceVersion()+":*"));
                    observations.addObject().put("store_id","REDIS").set("keys",schema.json.tree(keys));
                }
                case "DIAGNOSTIC" -> {
                    observations.add(observeVersions(policy.diagnostics(),policy.diagnostics().prefix()+tenant+"/"+target.sourceVersion()+"/",false,guard));
                    observations.addObject().put("store_id","MYSQL_DIAGNOSTIC").put("copy_count",guard.call(()->mapper.remainingDiagnostics(v)).toString());
                }
                default -> mismatch();
            }
        }
        String source=targets.get(0).sourceVersion();
        // Registry keys alone are not a complete-copy inventory. Independently enumerate the
        // entire declared object-store scope to detect unregistered copies as well.
        var objectScopes=new ArrayList<SourceDeletionPolicy.ObjectStore>(policy.originals());
        objectScopes.add(policy.parsed());objectScopes.add(policy.diagnostics());
        for(var store:objectScopes)try(var client=client(store)) {
            var observed=inventory(client,store,tenant,source,guard.now(),guard);
            observations.add(observed);if(!observed.path("complete").asBoolean(false))report.put("complete",false);
        }
        for(var backup:policy.backups())try(var client=client(backup.store())) {
            var observed=inventory(client,backup.store(),tenant,source,guard.now(),guard);
            observations.add(observed);if(!observed.path("complete").asBoolean(false))report.put("complete",false);
        }
        report.put("observed_at",guard.now().toString());return report;
    }

    private JsonNode observeVersions(SourceDeletionPolicy.ObjectStore store,String key,boolean exact,Guard guard)throws Exception {
        requireKey(store,key);var observation=schema.json.object().put("store_id",store.id()).put("scope",key);
        var copies=observation.putArray("copies");
        try(var client=client(store)) {
            if(!"Enabled".equals(guard.call(()->client.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(store.bucket()).build())).statusAsString()))
                throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            for(var copy:versions(client,store,key,exact,guard))copies.addObject().put("object_key",copy.key())
                .put("object_version",copy.version()).put("delete_marker",copy.marker());
        }
        observation.put("listing_exhausted",true);return observation;
    }

    JsonNode confirmSourceScopesAbsent(String tenant,String source,Guard guard)throws Exception {
        var result=schema.json.object().put("tenant_id",tenant).put("source_version",source);
        var objects=result.putArray("objects");
        var scopes=new ArrayList<SourceDeletionPolicy.ObjectStore>(policy.originals());scopes.add(policy.parsed());scopes.add(policy.diagnostics());
        for(var store:scopes)try(var client=client(store)) {
            var inventory=inventory(client,store,tenant,source,guard.now(),guard);
            SourceCopyInventory.requireAbsent(inventory);objects.add(inventory);
        }
        var vectors=result.putArray("vectors");
        var filter=Filter.newBuilder().addMust(matchKeyword("tenant_id",tenant)).addMust(matchKeyword("source_version",source)).build();
        for(var store:policy.vectors())try(var client=new QdrantClient(QdrantGrpcClient.newBuilder(store.host(),store.port(),store.tls())
                .withApiKey(Files.readString(store.apiKeyFile(),StandardCharsets.UTF_8).stripTrailing()).build())) {
            if(guard.call(()->client.countAsync(store.collection(),filter,true,TIMEOUT).get(30,TimeUnit.SECONDS))!=0)
                throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
            vectors.addObject().put("host",store.host()).put("port",store.port()).put("collection",store.collection()).put("remaining","0");
        }
        String pattern=policy.cacheNamespace()+":"+tenant+":"+source+":*";
        if(!guard.call(()->cacheKeys(pattern)).isEmpty())throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
        result.putObject("cache").put("scope_pattern",pattern).put("remaining","0");
        result.put("observed_at",guard.now().toString());return result;
    }

    JsonNode confirmBackups(String tenant,SourceDeletionTarget target,Instant primaryConfirmed,Instant now,Guard guard) throws Exception {
        var evidence=schema.json.object().put("schema_version","2.0.0").put("tenant_id",tenant)
            .put("source_version",target.sourceVersion()).put("config_revision",policy.revision()); var stores=evidence.putArray("stores");
        for(var backup:policy.backups()) {
            var store=backup.store();
            try(var client=client(store)) {
                if(!"Enabled".equals(guard.call(() -> client.getBucketVersioning(
                        GetBucketVersioningRequest.builder().bucket(store.bucket()).build())).statusAsString()))
                    throw new TenantAccessException(ErrorCode.ACTION_STALE);
                String key=backup.catalogueKey()+tenant+"/"+target.sourceVersion()+".json";
                byte[] bytes=guard.call(() -> readBytes(client,store.bucket(),key,null,1048576));
                var catalogue=schema.governance.read(new String(bytes,StandardCharsets.UTF_8)); schema.backup(catalogue);
                WireValues.uuid(catalogue.required("inventory_revision").textValue());
                if(!tenant.equals(catalogue.required("tenant_id").textValue())
                        || !target.sourceVersion().equals(catalogue.required("source_version").textValue())
                        || !store.id().equals(catalogue.required("store_id").textValue())) mismatch();
                var signed=(ObjectNode)catalogue.deepCopy(); String signature=signed.remove("signature").textValue();
                byte[] signingKey=Files.readAllBytes(backup.signingKeyFile());
                if(signingKey.length!=32) throw new IllegalArgumentException("备份证明秘密必须具有256位长度");
                var mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey,"HmacSHA256"));
                if(!MessageDigest.isEqual(HexFormat.of().parseHex(signature),mac.doFinal(schema.json.canonical(signed).getBytes(StandardCharsets.UTF_8)))) mismatch();
                Instant through=WireValues.utcTime(catalogue.required("captured_through").textValue());
                if(through.isBefore(primaryConfirmed) || through.isAfter(now)) throw new TenantAccessException(ErrorCode.ACTION_STALE);
                var copies=catalogue.required("copies"); if(copies.size()>policy.maximumEntries()) capacity();
                var identities=new HashSet<String>();
                var observations=schema.json.object().putArray("copies");
                for(var copy:copies) {
                    String object=copy.required("object_key").textValue(),version=copy.required("object_version").textValue();
                    requireKey(store,object);
                    if(!identities.add(object+"\n"+version)) mismatch();
                    if(WireValues.utcTime(copy.required("expires_at").textValue()).isAfter(now))
                        throw new TenantAccessException(ErrorCode.ACTION_STALE);
                    var present=versions(client,store,object,true,guard);
                    var observation=observations.addObject().put("object_key",object).put("object_version",version)
                        .put("sha256",copy.required("sha256").textValue()).put("observation","ALREADY_ABSENT")
                        .put("expires_at",copy.required("expires_at").textValue());
                    if(present.stream().anyMatch(value -> value.version().equals(version) && !value.marker())) {
                        var head=guard.call(() -> client.headObject(HeadObjectRequest.builder().bucket(store.bucket())
                            .key(object).versionId(version).build()));
                        if(!version.equals(head.versionId()))mismatch();
                        // Never delete a shared archive or an object whose ownership is merely inferred
                        // from its path. Backup owner metadata and signed catalogue must agree.
                        if(!tenant.equals(head.metadata().get("tenant-id"))
                                ||!target.sourceVersion().equals(head.metadata().get("source-version")))
                            throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                        if("ON".equals(head.objectLockLegalHoldStatusAsString())
                                ||head.objectLockRetainUntilDate()!=null && head.objectLockRetainUntilDate().isAfter(guard.now()))
                            throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
                        // Provider metadata can extend, but can never shorten, signed retention.
                        String providerExpiry=head.metadata().get("retention-expires-at");
                        if(providerExpiry==null)throw new TenantAccessException(ErrorCode.RETENTION_UNCONFIGURED);
                        if(WireValues.utcTime(providerExpiry).isAfter(guard.now()))
                            throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
                        String actual=guard.call(() -> readSha256(client,store.bucket(),object,version,policy.maximumObjectBytes()));
                        if(!copy.required("sha256").textValue().equals(actual))mismatch();
                        var response=guard.call(() -> client.deleteObject(DeleteObjectRequest.builder().bucket(store.bucket())
                            .key(object).versionId(version).build()));
                        if(!response.sdkHttpResponse().isSuccessful()||response.responseMetadata().requestId()==null
                                ||response.responseMetadata().requestId().isBlank())mismatch();
                        observation.put("observation","DELETE_ACKNOWLEDGED_AND_ABSENT")
                            .put("provider_request_id",response.responseMetadata().requestId());
                    }
                    if(versions(client,store,object,true,guard).stream().anyMatch(value -> value.version().equals(version)&&!value.marker()))
                        throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
                    observation.put("absence_confirmed",true).put("observed_at",guard.now().toString());
                }
                // The signed historical catalogue alone cannot rule out omitted live versions.
                // Enumerate the entire configured backup data scope, never just listed catalogue keys.
                var inventory=inventory(client,store,tenant,target.sourceVersion(),now,guard);
                SourceCopyInventory.requireAbsent(inventory);
                var proof=stores.addObject();proof.set("copy_observations",observations);proof.set("live_inventory",inventory);
                proof.put("store_id",store.id()).put("inventory_revision",catalogue.required("inventory_revision").textValue())
                    .put("catalogue_sha256",DigestUtils.sha256Hex(bytes)).put("remaining","0");
            }
        }
        return evidence;
    }

    void verifyAbsent(String tenant,SourceDeletionTarget target,Guard guard,
            com.ruoyi.medical.persistence.mapper.SourceDeletionMapper mapper,Map<String,Object> values) throws Exception {
        switch(target.kind()) {
            case "ORIGINAL" -> {
                var keys=new ArrayList<String>(); keys.add(target.objectKey());
                for(var copy:guard.call(() -> mapper.originalCopies(values))) keys.add(Objects.toString(Objects.requireNonNull(copy.get("object_key"))));
                for(var part:guard.call(() -> mapper.originalParts(values))) keys.add(Objects.toString(part.get("object_key")));
                if(keys.size()>policy.maximumEntries()) capacity();
                for(var store:policy.originals()) for(String key:keys) requireAbsent(store,key,true,guard);
            }
            case "PARSED" -> {
                requireAbsent(policy.parsed(),target.objectKey(),true,guard);
                if(guard.call(() -> mapper.remainingParsed(values)).signum()!=0
                        || guard.call(() -> mapper.remainingFulltext(values)).signum()!=0) mismatch();
            }
            case "FULLTEXT" -> { if(guard.call(() -> mapper.remainingFulltext(values)).signum()!=0) mismatch(); }
            case "VECTOR" -> {
                var filter=Filter.newBuilder().addMust(matchKeyword("tenant_id",tenant))
                    .addMust(matchKeyword("source_version",target.sourceVersion()))
                    .addMust(matchKeyword("parse_version",target.parseVersion()))
                    .addMust(matchKeyword("index_version",target.indexVersion())).build();
                for(var store:policy.vectors()) try(var client=new QdrantClient(QdrantGrpcClient.newBuilder(store.host(),store.port(),store.tls())
                        .withApiKey(Files.readString(store.apiKeyFile(),StandardCharsets.UTF_8).stripTrailing()).build())) {
                    if(guard.call(() -> client.countAsync(store.collection(),filter,true,TIMEOUT).get(30,TimeUnit.SECONDS))!=0) mismatch();
                }
            }
            case "CACHE" -> {
                if(!guard.call(() -> cacheKeys(policy.cacheNamespace()+":"+tenant+":"+target.sourceVersion()+":*")).isEmpty()) mismatch();
            }
            case "DIAGNOSTIC" -> {
                requireAbsent(policy.diagnostics(),policy.diagnostics().prefix()+tenant+"/"+target.sourceVersion()+"/",false,guard);
                if(guard.call(() -> mapper.remainingDiagnostics(values)).signum()!=0) mismatch();
            }
            default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        }
    }

    private void requireAbsent(SourceDeletionPolicy.ObjectStore store,String key,boolean exact,Guard guard) throws Exception {
        requireKey(store,key);
        try(var client=client(store)) {
            if(!versions(client,store,key,exact,guard).isEmpty()) mismatch();
        }
    }

    private List<String> cacheKeys(String pattern) {
        var keys=new HashSet<String>();
        for(String key:redis.getKeys().getKeysByPattern(pattern,Math.min(1000,policy.maximumEntries()))) {
            keys.add(key); if(keys.size()>policy.maximumEntries()) capacity();
        }
        return List.copyOf(keys);
    }

    private JsonNode eraseObjects(SourceDeletionPolicy.ObjectStore store,String key,boolean exact,String expected,Guard guard) throws Exception {
        requireKey(store,key);
        try(var client=client(store)) {
            if(!"Enabled".equals(guard.call(() -> client.getBucketVersioning(
                    GetBucketVersioningRequest.builder().bucket(store.bucket()).build())).statusAsString()))
                throw new TenantAccessException(ErrorCode.ACTION_STALE);
            var versions=versions(client,store,key,exact,guard);
            // 删除前核验全部内容版本；删除标记与每个精确版本分别处理。
            if(expected!=null) for(var version:versions) if(!version.marker()) {
                String digest=guard.call(() -> readSha256(client,store.bucket(),version.key(),version.version(),policy.maximumObjectBytes()));
                if(!expected.equals(digest)) mismatch();
            }
            // Inspect every version's lock state before any delete. Never bypass governance retention.
            for(var version:versions) if(!version.marker()) {
                var head=guard.call(() -> client.headObject(HeadObjectRequest.builder().bucket(store.bucket())
                    .key(version.key()).versionId(version.version()).build()));
                if(!version.version().equals(head.versionId())) mismatch();
                if("ON".equals(head.objectLockLegalHoldStatusAsString()) || head.objectLockRetainUntilDate()!=null
                        && head.objectLockRetainUntilDate().isAfter(guard.now()))
                    throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
            }
            var evidence=schema.json.object(); var receipts=evidence.putArray("copy_receipts");
            for(var version:versions) {
                var response=guard.call(() -> client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(store.bucket()).key(version.key()).versionId(version.version()).build()));
                if(!response.sdkHttpResponse().isSuccessful() || response.responseMetadata().requestId()==null
                        || response.responseMetadata().requestId().isBlank()) mismatch();
                receipts.addObject().put("object_key",version.key()).put("object_version",version.version())
                    .put("delete_marker",version.marker()).put("provider_request_id",response.responseMetadata().requestId())
                    .put("delete_acknowledged",true);
            }
            if(!versions(client,store,key,exact,guard).isEmpty()) mismatch();
            for(var receipt:receipts)((ObjectNode)receipt).put("absence_confirmed",true);
            evidence.put("observation",versions.isEmpty()?"ALREADY_ABSENT":"DELETE_ACKNOWLEDGED_AND_ABSENT");
            evidence.put("store_id",store.id()); evidence.put("deleted_versions",Integer.toString(versions.size()));
            evidence.put("object_key",key); if(expected!=null) evidence.put("content_sha256",expected);
            evidence.put("remaining","0"); return evidence;
        }
    }

    private List<Version> versions(S3Client client,SourceDeletionPolicy.ObjectStore store,String prefix,boolean exact,Guard guard) throws Exception {
        var values=new ArrayList<Version>(); String keyMarker=null,versionMarker=null;
        for(int page=0;page<=policy.maximumEntries();page++) {
            var request=ListObjectVersionsRequest.builder().bucket(store.bucket()).prefix(prefix).maxKeys(1000)
                .keyMarker(keyMarker).versionIdMarker(versionMarker).build();
            var response=guard.call(() -> client.listObjectVersions(request));
            for(var object:response.versions()) if(!exact || object.key().equals(prefix)) values.add(new Version(object.key(),object.versionId(),false));
            for(var marker:response.deleteMarkers()) if(!exact || marker.key().equals(prefix)) values.add(new Version(marker.key(),marker.versionId(),true));
            if(values.size()>policy.maximumEntries()) capacity();
            if(!Boolean.TRUE.equals(response.isTruncated())) return List.copyOf(values);
            if(response.nextKeyMarker()==null || Objects.equals(keyMarker,response.nextKeyMarker())
                    && Objects.equals(versionMarker,response.nextVersionIdMarker())) mismatch();
            keyMarker=response.nextKeyMarker(); versionMarker=response.nextVersionIdMarker();
        }
        throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
    }

    private JsonNode inventory(S3Client client,SourceDeletionPolicy.ObjectStore store,String tenant,String source,
            Instant now,Guard guard) throws Exception {
        if(!"Enabled".equals(guard.call(()->client.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(store.bucket()).build())).statusAsString()))
            throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var copies=new ArrayList<SourceCopyInventory.Copy>();
        for(var version:versions(client,store,store.prefix(),false,guard)) {
            if(version.marker()) { copies.add(new SourceCopyInventory.Copy(version.key(),version.version(),true,null,null,null,null,null));continue; }
            var head=guard.call(() -> client.headObject(HeadObjectRequest.builder().bucket(store.bucket())
                .key(version.key()).versionId(version.version()).build()));
            if(!version.version().equals(head.versionId()))mismatch();
            var metadata=head.metadata(); Instant expiry=null; Boolean hold=null;
            String expiryText=metadata.get("retention-expires-at");
            if(expiryText!=null)try{expiry=WireValues.utcTime(expiryText);}catch(RuntimeException invalid){/* Unknown, not expired. */}
            if(head.objectLockRetainUntilDate()!=null && (expiry==null||head.objectLockRetainUntilDate().isAfter(expiry)))
                expiry=head.objectLockRetainUntilDate();
            String lock=head.objectLockLegalHoldStatusAsString();
            if("ON".equals(lock))hold=true;else if("OFF".equals(lock))hold=false;
            copies.add(new SourceCopyInventory.Copy(version.key(),version.version(),false,metadata.get("tenant-id"),
                metadata.get("source-version"),metadata.get("sha256"),expiry,hold));
        }
        return SourceCopyInventory.produce(schema.json,store.id(),store.prefix(),tenant,source,now,copies,true);
    }

    private S3Client client(SourceDeletionPolicy.ObjectStore store) throws Exception {
        var credentials=schema.governance.read(Files.readString(store.credentialsFile(),StandardCharsets.UTF_8));
        return S3Client.builder().endpointOverride(store.endpoint()).region(Region.of(store.region())).forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                SourceDeletionTarget.text(credentials,"access_key"),SourceDeletionTarget.text(credentials,"secret_key"))))
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED).responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(ApacheHttpClient.builder().connectionTimeout(Duration.ofSeconds(5)).socketTimeout(TIMEOUT)
                .connectionAcquisitionTimeout(Duration.ofSeconds(5)).proxyConfiguration(ProxyConfiguration.builder()
                    .useEnvironmentVariableValues(false).useSystemPropertyValues(false).build()))
            .overrideConfiguration(options -> options.apiCallTimeout(TIMEOUT).apiCallAttemptTimeout(TIMEOUT)
                .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())).build();
    }

    private static byte[] readBytes(S3Client client,String bucket,String key,String version,long maximum) throws Exception {
        var request=GetObjectRequest.builder().bucket(bucket).key(key).versionId(version).build();
        try(var stream=client.getObject(request)) {
            if(version!=null && !version.equals(stream.response().versionId())) mismatch();
            if(stream.response().contentLength()==null || stream.response().contentLength()>maximum) capacity();
            var bounded=org.apache.commons.io.input.BoundedInputStream.builder().setInputStream(stream).setMaxCount(maximum+1).get();
            byte[] bytes=bounded.readAllBytes();
            if(bytes.length>maximum || bytes.length!=stream.response().contentLength()) mismatch();
            return bytes;
        }
    }
    private static String readSha256(S3Client client,String bucket,String key,String version,long maximum) throws Exception {
        try(var stream=client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).versionId(version).build())) {
            if(!version.equals(stream.response().versionId()) || stream.response().contentLength()==null) mismatch();
            if(stream.response().contentLength()>maximum) capacity();
            var bounded=org.apache.commons.io.input.BoundedInputStream.builder().setInputStream(stream).setMaxCount(maximum+1).get();
            String digest=DigestUtils.sha256Hex(bounded);
            if(bounded.getCount()>maximum || bounded.getCount()!=stream.response().contentLength()) mismatch();
            return digest;
        }
    }
    private static void requireKey(SourceDeletionPolicy.ObjectStore store,String key) {
        if(key==null || !key.startsWith(store.prefix())) throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void capacity() { throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE); }
    private record Version(String key,String version,boolean marker) { }
    @FunctionalInterface interface Guard {
        <T> T call(Action<T> action) throws Exception;
        default Instant now() throws Exception { throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE); }
    }
    @FunctionalInterface interface Action<T> { T run() throws Exception; }

    private static final class SourceDeletionSchemaSupport {
        private final PermissionSchema json;
        private final SourceGovernanceSchema governance;
        private final JsonSchema backup;
        private SourceDeletionSchemaSupport(PermissionSchema json,SourceGovernanceSchema governance) throws Exception {
            this.json=json; this.governance=governance;
            try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("source-deletion.schema.json"))) {
                backup=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(
                    governance.read(new String(input.readAllBytes(),StandardCharsets.UTF_8)),
                    SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
                backup.initializeValidators();
            }
        }
        private void backup(JsonNode value) { if(!backup.validate(value).isEmpty()) mismatch(); }
    }
}

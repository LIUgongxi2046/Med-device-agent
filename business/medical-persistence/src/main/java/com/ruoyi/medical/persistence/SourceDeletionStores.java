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
                        stores.addObject().put("store_id",store.collection()).put("remaining","0");
                    }
                }
                guard.call(() -> { mapper.withdrawIndex(values); return null; });
            }
            case "CACHE" -> {
                String pattern=policy.cacheNamespace()+":"+tenant+":"+target.sourceVersion()+":*";
                var keys=guard.call(() -> cacheKeys(pattern));
                for(String key:keys) guard.call(() -> redis.getKeys().delete(key));
                if(!guard.call(() -> cacheKeys(pattern)).isEmpty()) mismatch();
                stores.addObject().put("store_id","REDIS").put("remaining","0");
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

    JsonNode confirmBackups(String tenant,SourceDeletionTarget target,Instant primaryConfirmed,Instant now,Guard guard) throws Exception {
        var evidence=schema.json.object(); var stores=evidence.putArray("stores");
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
                for(var copy:copies) {
                    String object=copy.required("object_key").textValue(),version=copy.required("object_version").textValue();
                    requireKey(store,object);
                    if(!identities.add(object+"\n"+version)) mismatch();
                    if(WireValues.utcTime(copy.required("expires_at").textValue()).isAfter(now))
                        throw new TenantAccessException(ErrorCode.ACTION_STALE);
                    var present=versions(client,store,object,true,guard);
                    if(present.stream().anyMatch(value -> value.version().equals(version) && !value.marker()))
                        throw new TenantAccessException(ErrorCode.REFERENCE_HELD);
                }
                stores.addObject().put("store_id",store.id()).put("inventory_revision",catalogue.required("inventory_revision").textValue())
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
            for(var version:versions) guard.call(() -> client.deleteObject(DeleteObjectRequest.builder()
                .bucket(store.bucket()).key(version.key()).versionId(version.version()).build()));
            if(!versions(client,store,key,exact,guard).isEmpty()) mismatch();
            var evidence=schema.json.object(); evidence.put("store_id",store.id()); evidence.put("deleted_versions",Integer.toString(versions.size()));
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
    @FunctionalInterface interface Guard { <T> T call(Action<T> action) throws Exception; }
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

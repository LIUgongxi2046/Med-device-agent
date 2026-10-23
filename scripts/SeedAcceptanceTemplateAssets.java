package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;

class SeedAcceptanceTemplateAssets {
    static final JsonMapper JSON = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    static PermissionSchema SCHEMA;
    static Path root, ledgerPath;
    static ObjectNode ledger;
    static JdbcTemplate db;
    static String tenant, actor;
    static String id() { return UUID.randomUUID().toString(); }
    static void save() throws Exception {
        Files.writeString(ledgerPath, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(ledger));
        Files.setPosixFilePermissions(ledgerPath, PosixFilePermissions.fromString("rw-------"));
    }
    static void current() {
        Integer count = db.queryForObject("""
            SELECT COUNT(*) FROM platform_local_account a JOIN tenant_member m ON m.user_id=a.user_id
            WHERE a.user_id=? AND a.platform_role='PLATFORM_ADMIN' AND a.status='ACTIVE'
              AND m.tenant_id=? AND m.status='ACTIVE' AND m.valid_from<=UTC_TIMESTAMP(6)
              AND (m.valid_to IS NULL OR m.valid_to>UTC_TIMESTAMP(6))
            """, Integer.class, actor, tenant);
        if (!Integer.valueOf(1).equals(count)) throw new IllegalStateException("PUBLIC_ASSET_OPERATOR_REQUIRED");
    }
    public static void main(String[] arguments) throws Exception {
        SCHEMA = new PermissionSchema();
        root = Path.of(arguments[0]).toRealPath();
        var runtime = root.resolve(".implementation-work/runtime");
        var seed = JSON.readTree(Files.readAllBytes(runtime.resolve("acceptance-workspace-seed.json")));
        tenant = seed.required("tenant_id").asText();
        var owner = JSON.readTree(Files.readAllBytes(runtime.resolve("owner-credentials.json")));
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:13306/medical_local?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC",
            "medical_runtime", Objects.requireNonNull(System.getenv("MEDICAL_LOCAL_DATABASE_PASSWORD")));
        db = new JdbcTemplate(source); db.setQueryTimeout(10);
        actor = db.queryForObject("SELECT user_id FROM platform_local_account WHERE username=? AND status='ACTIVE' AND platform_role='PLATFORM_ADMIN'", String.class, owner.required("username").asText());
        current();
        var provenance = JSON.readTree(Files.readAllBytes(root.resolve(".implementation-work/public-template-assets/provenance.json")));
        if (!"SIMULATION".equals(provenance.required("data_kind").asText())) throw new IllegalStateException("PUBLIC_ASSET_CLASSIFICATION_REQUIRED");
        ledgerPath = runtime.resolve("acceptance-template-assets.json");
        ledger = Files.exists(ledgerPath) ? (ObjectNode) JSON.readTree(Files.readAllBytes(ledgerPath)) : JSON.createObjectNode().put("data_kind", "SIMULATION").put("tenant_id", tenant);
        if (!tenant.equals(ledger.required("tenant_id").asText())) throw new IllegalStateException("PUBLIC_ASSET_SCOPE_CHANGED");
        if (!ledger.has("assets")) {
            var assets = ledger.putArray("assets");
            for (var asset : provenance.required("assets")) {
                var value = (ObjectNode) asset.deepCopy();
                value.put("artifact_id", id()).put("access_set_id", id()).put("write_id", id()).put("state", "PREPARED");
                assets.add(value);
            }
            ledger.set("provenance", provenance);
            save();
        }
        var selectedAssets = (com.fasterxml.jackson.databind.node.ArrayNode) ledger.required("assets");
        for (var input : provenance.required("assets")) {
            for (int index = 0; index < selectedAssets.size(); index++) {
                var previous = selectedAssets.get(index);
                if (!input.required("kind").equals(previous.required("kind")) || input.required("sha256").equals(previous.required("sha256"))) continue;
                if ("REGISTERED".equals(previous.required("state").asText())) throw new IllegalStateException("PUBLIC_ASSET_IMMUTABLE_VERSION_REQUIRED");
                if (!ledger.has("prior_write_records")) ledger.putArray("prior_write_records");
                ((com.fasterxml.jackson.databind.node.ArrayNode) ledger.required("prior_write_records")).add(previous.deepCopy());
                var value = (ObjectNode) input.deepCopy();
                value.put("artifact_id", id()).put("access_set_id", id()).put("write_id", id()).put("state", "PREPARED");
                selectedAssets.set(index, value);
            }
        }
        ledger.set("provenance", provenance); save();
        var deployment = JSON.readTree(Files.readAllBytes(runtime.resolve("object-storage/deployment.json")));
        var credentials = JSON.readTree(Files.readAllBytes(runtime.resolve("object-storage/service-secret.json")));
        if (!deployment.required("service_id").equals(credentials.required("service_id")) || !"LOCAL_PRIVATE_OBJECT_STORAGE".equals(deployment.required("scope").asText())) throw new IllegalStateException("PUBLIC_ASSET_STORAGE_REQUIRED");
        var spool = root.resolve(".implementation-work/public-template-assets");
        Files.createDirectories(spool); Files.setPosixFilePermissions(spool, PosixFilePermissions.fromString("rwx------"));
        var schemaCatalogue = SchemaCatalogue.fromClasspath();
        String interfacesHash = DigestUtils.sha256Hex(Files.readAllBytes(root.resolve("backend-design/interfaces.schema.yaml")));
        String interfacesRef = "https://schemas.medical-agent.invalid/1.0.0/backend-design/interfaces.schema.yaml#/$defs/";
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        var provider = StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.required("access_key").asText(), credentials.required("secret_key").asText()));
        try (var inspection = S3Client.builder().endpointOverride(URI.create(deployment.required("endpoint").asText())).region(Region.US_EAST_1)
                .credentialsProvider(provider).forcePathStyle(true).httpClientBuilder(ApacheHttpClient.builder().proxyConfiguration(
                    ProxyConfiguration.builder().useEnvironmentVariableValues(false).useSystemPropertyValues(false).build()))
                .overrideConfiguration(value -> value.apiCallTimeout(java.time.Duration.ofSeconds(30)).apiCallAttemptTimeout(java.time.Duration.ofSeconds(30))
                    .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(1).build())).build();
                var writer = new S3ObjectWriter(URI.create(deployment.required("endpoint").asText()), deployment.required("bucket").asText(), "us-east-1",
                S3ObjectReader.Network.LOCAL_LOOPBACK, StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.required("access_key").asText(), credentials.required("secret_key").asText())),
                spool, 64L * 1024 * 1024, 1024 * 1024)) {
            for (var entry : ledger.required("assets")) {
                var asset = (ObjectNode) entry;
                Path file = root.resolve(asset.required("path").asText()).toRealPath();
                if (!file.startsWith(root.resolve(".implementation-work/public-template-assets"))) throw new IllegalStateException("PUBLIC_ASSET_PATH_INVALID");
                String hash = asset.required("sha256").asText(), size = asset.required("size_bytes").asText();
                try (var input = Files.newInputStream(file)) {
                    if (!hash.equals(DigestUtils.sha256Hex(input)) || !size.equals(Long.toString(Files.size(file)))) throw new IllegalStateException("PUBLIC_ASSET_INPUT_CHANGED");
                }
                String artifact = asset.required("artifact_id").asText(), access = asset.required("access_set_id").asText(), kind = asset.required("kind").asText();
                String schemaRef = interfacesRef + (List.of("FONT", "FORMAT").contains(kind) ? "ObjectRef" : "TextContent");
                if (!List.of("FONT", "FORMAT").contains(kind)) schemaCatalogue.bind("interfaces", JSON.readTree(Files.readAllBytes(file)), "TextContent");
                var intent = new S3ObjectWriter.WriteIntent(asset.required("write_id").asText(), "acceptance/public-template/" + tenant + "/" + artifact, hash, size, asset.required("media_type").asText());
                if (!asset.has("object_ref")) {
                    boolean recovery = "WRITING".equals(asset.required("state").asText());
                    if (recovery) {
                        current();
                        var objects = inspection.listObjectVersions(ListObjectVersionsRequest.builder().bucket(deployment.required("bucket").asText()).prefix(intent.objectKey()).build());
                        current();
                        if (!Boolean.FALSE.equals(objects.isTruncated()) || objects.versions().stream().anyMatch(value -> !intent.objectKey().equals(value.key())) || !objects.deleteMarkers().isEmpty()) throw new IllegalStateException("PUBLIC_ASSET_RECOVERY_INCOMPLETE");
                        recovery = !objects.versions().isEmpty();
                        if (!recovery) { asset.put("absence_verified_at", java.time.Instant.now().toString()); save(); }
                    }
                    asset.put("state", "WRITING"); save();
                    var reference = recovery ? writer.recoverVerified(intent, SeedAcceptanceTemplateAssets::current) : writer.writeVerified(intent, file, SeedAcceptanceTemplateAssets::current);
                    schemaCatalogue.bind("interfaces", SCHEMA.tree(reference), "ObjectRef");
                    asset.set("object_ref", SCHEMA.tree(reference)); asset.put("state", "STORED"); save();
                }
                var reference = JSON.treeToValue(asset.required("object_ref"), InterfaceProtocol.ObjectRef.class);
                String storedObject = StoredObjectReferences.encode(reference);
                // ObjectRef核查二进制对象身份，字体和DOCX格式使用各自原生文件解析器核验。
                var manifest = new ArtifactRegistryProtocol.DependencyManifest("1.0.0", tenant, null, access, List.of());
                String manifestJson = SCHEMA.canonicalObject(manifest), manifestHash = DigestUtils.sha256Hex(manifestJson);
                transaction.executeWithoutResult(status -> {
                    current();
                    if (db.queryForObject("SELECT COUNT(*) FROM artifact WHERE tenant_id=? AND artifact_id=?", Integer.class, tenant, artifact) == 0) {
                        db.update("INSERT INTO version_registry(tenant_id,version_id,entity_kind,content_sha256) VALUES(?,?,'DEPENDENCY_ACCESS_SET',?),(?,?,'ARTIFACT',?)", tenant, access, manifestHash, tenant, artifact, hash);
                        db.update("INSERT INTO dependency_access_set(tenant_id,access_set_id,manifest_sha256,content_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),'SIMULATION')", tenant, access, manifestHash, manifestHash, actor);
                        db.update("INSERT INTO dependency_access_manifest(tenant_id,access_set_id,manifest_json,manifest_sha256,content_sha256,member_count,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,0,'1.0.0',?,?,UTC_TIMESTAMP(6),'SIMULATION')", tenant, access, manifestJson, manifestHash, manifestHash, manifestSchema(), actor);
                        db.update("INSERT INTO artifact(tenant_id,artifact_id,artifact_kind,object_ref,object_key,object_version,sha256,size_bytes,media_type,dependency_access_set_id,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,'1.0.0',?,?,UTC_TIMESTAMP(6),'SIMULATION')", tenant, artifact, "ACCEPTANCE_" + kind, storedObject, reference.objectKey(), reference.objectVersion(), hash, Long.parseLong(size), reference.mediaType(), access, hash, interfacesHash, actor);
                        db.update("INSERT INTO artifact_content_binding(tenant_id,artifact_id,resource_kind,resource_id,schema_ref,schema_document_sha256,dependency_access_set_id,dependency_manifest_sha256,created_by,created_at,classification) VALUES(?,?,'ARTIFACT',?,?,?,?,?,?,UTC_TIMESTAMP(6),'SIMULATION')", tenant, artifact, artifact, schemaRef, interfacesHash, access, manifestHash, actor);
                        db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=?", tenant);
                    }
                    String actual = db.queryForObject("SELECT sha256 FROM artifact WHERE tenant_id=? AND artifact_id=? AND object_version=? AND classification='SIMULATION'", String.class, tenant, artifact, reference.objectVersion());
                    if (!hash.equals(actual)) throw new IllegalStateException("PUBLIC_ASSET_STORED_IDENTITY_CHANGED");
                });
                asset.put("schema_ref", schemaRef).put("state", "REGISTERED"); save();
            }
        }
        ledger.put("state", "REGISTERED").put("verified_at", java.time.Instant.now().toString()); save();
        System.out.println("ACCEPTANCE_PUBLIC_TEMPLATE_ASSETS_REGISTERED");
    }
    static String manifestSchema() {
        return org.springframework.util.function.ThrowingSupplier.of(() -> new DependencyManifestSchema().manifestSha256()).get();
    }
}

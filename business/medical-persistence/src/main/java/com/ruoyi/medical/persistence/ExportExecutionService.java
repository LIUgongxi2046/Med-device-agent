package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.ExportMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
@EnableConfigurationProperties(ExportExecutionPolicy.class)
public final class ExportExecutionService {
    private final EnterpriseAccess access;
    private final ExportMapper mapper;
    private final PermissionMapper permissions;
    private final ResourceReadAuthority resources;
    private final ArtifactRegistryMapper artifacts;
    private final SourceUploadTransport transport;
    private final ExportSchema schema;
    private final ExportExecutionPolicy policy;
    private final Path workspace;

    public ExportExecutionService(EnterpriseAccess access, ExportMapper mapper, PermissionMapper permissions,
            ResourceReadAuthority resources, ArtifactRegistryMapper artifacts, SourceUploadTransport transport,
            ExportSchema schema, ExportExecutionPolicy policy, SourceUploadTransportPolicy storage) throws Exception {
        this.access = access; this.mapper = mapper; this.permissions = permissions; this.resources = resources;
        this.artifacts = artifacts; this.transport = transport; this.schema = schema; this.policy = policy;
        Path root = storage.storageDirectory().toRealPath(LinkOption.NOFOLLOW_LINKS);
        workspace = root.resolve("export-execution");
        Files.createDirectories(workspace, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (!Files.getPosixFilePermissions(workspace, LinkOption.NOFOLLOW_LINKS)
                .equals(PosixFilePermissions.fromString("rwx------")))
            throw new IllegalArgumentException("导出执行目录权限无效");
    }

    public String execute(ApplicationSessionIdentity.Verified subject, String context, String exportItemId,
            ExportProtocol.ProcessInput input) throws Exception {
        var plan = claim(subject, context, exportItemId, input);
        boolean terminal = false; Path work = null;
        try {
            var body = readArtifact(subject, context, plan, plan.document().bodyArtifactId(), "EXPORT_BODY");
            var profile = schema.read(plan.document().formatProfile());
            String templateId = profile.required("format_revision").required("version_id").textValue();
            var template = readArtifact(subject, context, plan, templateId, "EXPORT_TEMPLATE");
            var fonts = new ArrayList<LoadedArtifact>();
            for (var font : profile.required("font_artifacts"))
                fonts.add(readArtifact(subject, context, plan, font.required("version_id").textValue(), "EXPORT_FONT"));
            var images = new ArrayList<LoadedArtifact>();
            var imageIds = new LinkedHashSet<String>();
            for (var node : schema.read(new String(body.bytes(), StandardCharsets.UTF_8)).required("nodes")) {
                var content = node.required("content");
                if ("IMAGE".equals(content.required("kind").textValue())) imageIds.add(content.required("artifact_id").textValue());
            }
            for (var imageId : imageIds) images.add(readArtifact(subject, context, plan, imageId, "EXPORT_IMAGE"));
            work = Files.createDirectory(workspace.resolve(exportItemId),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            var command = prepareCommand(plan, body, template, fonts, images, work);
            int result = run(command.request(), command.result(), work);
            if (result != 0) {
                fail(subject, context, plan, command.itemRevision());
                terminal = true;
                return plan.item().exportId();
            }
            var descriptor = schema.read(Files.readString(command.result(), StandardCharsets.UTF_8));
            if (!"1.0.0".equals(descriptor.required("schema_version").textValue())) mismatch();
            Path output = requireChild(work, Path.of(descriptor.required("output_path").textValue()));
            Path validationPath = requireChild(work, Path.of(descriptor.required("validation_path").textValue()));
            byte[] outputBytes = Files.readAllBytes(output), validationBytes = Files.readAllBytes(validationPath);
            if (outputBytes.length == 0 || outputBytes.length > policy.maximumBytes() || validationBytes.length == 0
                    || validationBytes.length > policy.maximumBytes()) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
            var validation = descriptor.required("validation"); schema.verify("Validation", validation);
            if (!schema.canonical(validation).equals(new String(validationBytes, StandardCharsets.UTF_8))
                    || !plan.item().format().equals(validation.required("format").textValue())
                    || !DigestUtils.sha256Hex(outputBytes).equals(validation.required("sha256").textValue())
                    || !Integer.toString(outputBytes.length).equals(validation.required("size_bytes").textValue())
                    || !policy.converterRevision().equals(validation.required("converter_revision").textValue())
                    || !plan.document().formatProfileRevision().equals(validation.required("font_manifest_revision").textValue())) mismatch();
            BigInteger verifyingRevision = verifying(subject, context, plan, command.itemRevision());
            String outputId = id(), validationId = id();
            var outputRef = write(subject, context, plan, verifyingRevision, outputId, outputBytes, mediaType(plan.item().format()), extension(plan.item().format()));
            var validationRef = write(subject, context, plan, verifyingRevision, validationId, validationBytes, "application/json", "json");
            complete(subject, context, plan, verifyingRevision, outputId, outputRef, validationId, validationRef,
                schema.canonical(validation), templateId, fonts, images);
            terminal = true;
            return plan.item().exportId();
        } finally {
            if (!terminal) failActive(subject, context, plan);
            if (work != null) remove(work);
        }
    }

    private Plan claim(ApplicationSessionIdentity.Verified subject, String context, String exportItemId,
            ExportProtocol.ProcessInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(exportItemId);
            var values = values(admission); values.put("exportItemId", exportItemId);
            var item = single(mapper.itemForUpdate(values)); values.put("exportId", item.exportId());
            var manifest = single(mapper.manifest(values)); var document = requireExport(admission, manifest, "artifact.export");
            if (!input.expected_revision().equals(item.revision().toString()) || !"QUEUED".equals(item.status())
                    || !policy.converterRevision().equals(item.converterRevision())) stale();
            var profile = schema.read(document.formatProfile());
            var bodyArtifact = requireArtifact(admission, document.bodyArtifactId(), item.projectId(), "EXPORT_BODY");
            if (!item.projectId().equals(bodyArtifact.projectId())) mismatch();
            requireArtifact(admission, profile.required("format_revision").required("version_id").textValue(), item.projectId(), "EXPORT_TEMPLATE");
            for (var font : profile.required("font_artifacts"))
                requireArtifact(admission, font.required("version_id").textValue(), item.projectId(), "EXPORT_FONT");
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("itemRevision", item.revision()); values.put("now", now); one(mapper.claimItem(values));
            values.put("manifestRevision", manifest.revision()); values.put("status", "RUNNING"); one(mapper.updateManifestStatus(values));
            return new Plan(item, manifest, document, item.revision().add(BigInteger.ONE));
        }).get());
    }

    private LoadedArtifact readArtifact(ApplicationSessionIdentity.Verified subject, String context, Plan plan,
            String artifactId, String purpose) throws Exception {
        var row = access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireRunning(admission, plan, plan.itemRevision(), "RUNNING");
            var artifact = requireArtifact(admission, artifactId, plan.item().projectId(), purpose);
            if (!plan.item().tenantId().equals(artifact.tenantId()) || !Objects.equals(plan.item().projectId(), artifact.projectId())
                    || artifact.sizeBytes().signum() <= 0 || artifact.sizeBytes().compareTo(BigInteger.valueOf(policy.maximumBytes())) > 0) mismatch();
            return artifact;
        }).get());
        int size = row.sizeBytes().intValueExact();
        byte[] bytes = transport.readDocument(subject, row.tenantId(), policy.maximumBytes(),
            () -> requireCurrent(subject, context, plan, plan.itemRevision(), "RUNNING"), (reader, admission) -> {
                try (var verified = reader.readVerified(row.reference(), admission)) {
                    byte[] content = verified.chunk(0, size); admission.requireCurrent(); return content;
                }
            });
        if (!row.sha256().equals(DigestUtils.sha256Hex(bytes))) mismatch();
        return new LoadedArtifact(row, bytes);
    }

    private Command prepareCommand(Plan plan, LoadedArtifact body, LoadedArtifact template, List<LoadedArtifact> fonts,
            List<LoadedArtifact> images, Path work) throws Exception {
        Path bodyPath = file(work, "body.json", body.bytes()); Path templatePath = file(work, "template.docx", template.bytes());
        var fontValues = schema.object().arrayNode(); int number = 0;
        for (var font : fonts) {
            Path path = file(work, "font-" + number + extension(font.row().mediaType()), font.bytes());
            var value = fontValues.addObject(); value.put("path", path.toString()); value.put("sha256", font.row().sha256()); number++;
        }
        var assetValues = schema.object().arrayNode();
        for (var image : images) {
            Path path = file(work, "image-" + image.row().artifactId() + extension(image.row().mediaType()), image.bytes());
            var value = assetValues.addObject(); value.put("artifact_id", image.row().artifactId()); value.put("path", path.toString());
            value.put("sha256", image.row().sha256()); value.put("media_type", image.row().mediaType());
        }
        var request = schema.object(); request.put("schema_version", "1.0.0"); request.put("tenant_id", plan.item().tenantId());
        request.put("project_id", plan.item().projectId()); request.put("document_revision", plan.item().documentRevision());
        request.put("attempt_id", plan.item().exportItemId()); request.put("template_version", plan.manifest().templateVersion());
        request.put("body_sha256", body.row().sha256()); request.put("template_sha256", template.row().sha256());
        request.put("purpose", plan.manifest().purpose()); request.put("input_data_kind", policy.inputDataKind());
        request.put("font_manifest_revision", plan.document().formatProfileRevision()); request.set("fonts", fontValues);
        var converter = request.putObject("converter"); converter.put("revision", policy.converterRevision());
        converter.put("path", policy.converterExecutable().toString()); converter.put("version", policy.converterVersion());
        converter.put("timeout_seconds", Math.toIntExact(policy.timeout().toSeconds())); converter.put("sha256", policy.converterSha256());
        request.set("assets", assetValues); request.put("body_path", bodyPath.toString()); request.put("template_path", templatePath.toString());
        request.put("catalogue_root", policy.projectRoot().toString()); request.put("output_directory", work.toString());
        request.put("format", plan.item().format());
        Path requestPath = file(work, "request.json", schema.canonical(request).getBytes(StandardCharsets.UTF_8));
        return new Command(requestPath, work.resolve("result.json"), plan.itemRevision());
    }

    private int run(Path request, Path result, Path work) throws Exception {
        try (var input = Files.newInputStream(policy.converterExecutable())) {
            if (!policy.converterSha256().equals(DigestUtils.sha256Hex(input))) mismatch();
        }
        var process = new ProcessBuilder(policy.pythonExecutable().toString(), "-m", "medical_document.export_command",
            request.toString(), result.toString()).directory(policy.projectRoot().toFile())
            .redirectOutput(work.resolve("stdout.log").toFile()).redirectError(work.resolve("stderr.log").toFile());
        process.environment().put("PYTHONPATH", policy.projectRoot().resolve("document/src") + java.io.File.pathSeparator
            + policy.projectRoot().resolve("contracts/python/src"));
        var running = process.start();
        if (!running.waitFor(policy.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            running.destroyForcibly(); running.waitFor(); return 124;
        }
        return running.exitValue();
    }

    private BigInteger verifying(ApplicationSessionIdentity.Verified subject, String context, Plan plan, BigInteger revision) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireRunning(admission, plan, revision, "RUNNING");
            var values = values(admission); values.put("exportItemId", plan.item().exportItemId()); values.put("itemRevision", revision);
            values.put("now", Objects.requireNonNull(permissions.databaseTime())); one(mapper.markItemVerifying(values));
            return revision.add(BigInteger.ONE);
        }).get());
    }

    private InterfaceProtocol.ObjectRef write(ApplicationSessionIdentity.Verified subject, String context, Plan plan,
            BigInteger revision, String artifactId, byte[] bytes, String mediaType, String extension) throws Exception {
        var intent = new S3ObjectWriter.WriteIntent(id(), "exports/" + plan.item().tenantId() + "/" + plan.item().exportId()
            + "/" + plan.item().exportItemId() + "/" + artifactId + "." + extension,
            DigestUtils.sha256Hex(bytes), Integer.toString(bytes.length), mediaType);
        return transport.writeDocumentProjection(subject, plan.item().tenantId(), intent, bytes, false, policy.maximumBytes(),
            () -> requireCurrent(subject, context, plan, revision, "VERIFYING"));
    }

    private void complete(ApplicationSessionIdentity.Verified subject, String context, Plan plan, BigInteger revision,
            String artifactId, InterfaceProtocol.ObjectRef reference, String validationId, InterfaceProtocol.ObjectRef validationReference,
            String validation, String templateId, List<LoadedArtifact> fonts, List<LoadedArtifact> images) {
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireRunning(admission, plan, revision, "VERIFYING");
            var values = values(admission); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("projectId", plan.item().projectId()); values.put("documentId", plan.item().documentId());
            values.put("documentRevision", plan.item().documentRevision()); values.put("dependencyAccessSetId", plan.manifest().dependencyAccessSetId());
            values.put("dependencyManifestSha256", plan.manifest().dependencyManifestSha256()); values.put("schemaManifest", schema.manifestSha256());
            values.put("classification", plan.item().classification()); values.put("now", now);
            saveArtifact(values, artifactId, "DOCUMENT_EXPORT", schemaRef("File"), reference);
            saveArtifact(values, validationId, "EXPORT_VALIDATION", schemaRef("Validation"), validationReference);
            var upstream = new LinkedHashSet<String>(); upstream.add(plan.item().exportId()); upstream.add(plan.document().bodyArtifactId()); upstream.add(templateId);
            fonts.forEach(value -> upstream.add(value.row().artifactId())); images.forEach(value -> upstream.add(value.row().artifactId()));
            values.put("artifactId", artifactId); values.put("relationKind", "EXPORT_INPUT");
            for (var upstreamId : upstream) { values.put("upstreamId", upstreamId); one(mapper.saveLineage(values)); }
            values.put("artifactId", validationId); values.put("upstreamId", artifactId); values.put("relationKind", "EXPORT_VALIDATION_INPUT");
            one(mapper.saveLineage(values));
            values.put("exportItemId", plan.item().exportItemId()); values.put("itemRevision", revision);
            values.put("objectRef", StoredObjectReferences.encode(reference)); values.put("artifactId", artifactId);
            values.put("validationRef", validation); values.put("validationArtifactId", validationId); one(mapper.completeItem(values));
            updateManifest(values, plan.item().exportId()); return null;
        }).get());
    }

    private void fail(ApplicationSessionIdentity.Verified subject, String context, Plan plan, BigInteger revision) {
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireRunning(admission, plan, revision, "RUNNING");
            var values = values(admission); values.put("exportItemId", plan.item().exportItemId()); values.put("itemRevision", revision);
            values.put("validationRef", null); values.put("now", Objects.requireNonNull(permissions.databaseTime())); one(mapper.failItem(values));
            updateManifest(values, plan.item().exportId()); return null;
        }).get());
    }

    private void failActive(ApplicationSessionIdentity.Verified subject, String context, Plan plan) {
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var values = values(admission); values.put("exportItemId", plan.item().exportItemId());
            var rows = mapper.itemForUpdate(values);
            if (rows.size() != 1) mismatch(); var item = rows.get(0);
            if (!List.of("RUNNING", "VERIFYING").contains(item.status())) return null;
            values.put("exportId", item.exportId()); requireExport(admission, single(mapper.manifest(values)), "artifact.export");
            values.put("itemRevision", item.revision()); values.put("validationRef", null);
            values.put("now", Objects.requireNonNull(permissions.databaseTime())); one(mapper.failItem(values));
            updateManifest(values, item.exportId()); return null;
        }).get());
    }

    private void saveArtifact(Map<String,Object> values, String artifactId, String kind, String schemaRef,
            InterfaceProtocol.ObjectRef reference) throws java.io.IOException {
        values.put("artifactId", artifactId); values.put("artifactKind", kind); values.put("schemaRef", schemaRef);
        values.put("objectRef", StoredObjectReferences.encode(reference)); values.put("objectKey", reference.objectKey());
        values.put("objectVersion", reference.objectVersion()); values.put("sha256", reference.sha256());
        values.put("sizeBytes", reference.sizeBytes()); values.put("mediaType", reference.mediaType());
        one(mapper.saveArtifactVersion(values)); one(mapper.saveArtifact(values)); one(mapper.saveBinding(values));
    }

    private void updateManifest(Map<String,Object> values, String exportId) {
        values.put("exportId", exportId); var manifest = single(mapper.manifest(values)); var latest = new LinkedHashMap<String,ExportProtocol.ItemRow>();
        for (var item : mapper.items(values)) {
            var existing = latest.get(item.format());
            if (existing == null || item.attemptNo().compareTo(existing.attemptNo()) > 0) latest.put(item.format(), item);
        }
        String status;
        if (latest.values().stream().allMatch(item -> "SUCCEEDED".equals(item.status()))) status = "SUCCEEDED";
        else if (latest.values().stream().anyMatch(item -> List.of("QUEUED","RUNNING","VERIFYING","CANCEL_REQUESTED").contains(item.status()))) status = "RUNNING";
        else if (latest.values().stream().anyMatch(item -> "SUCCEEDED".equals(item.status()))) status = "PARTIAL_FAILED";
        else if (latest.values().stream().allMatch(item -> "CANCELLED".equals(item.status()))) status = "CANCELLED";
        else status = "FAILED";
        values.put("status", status); values.put("manifestRevision", manifest.revision()); one(mapper.updateManifestStatus(values));
    }

    private ExportProtocol.DocumentRow requireExport(EnterpriseAccess.Admission admission, ExportProtocol.ManifestRow manifest,
            String action) throws Exception {
        var values = values(admission); values.put("documentId", manifest.documentId()); values.put("documentRevision", manifest.documentRevision());
        var document = single(mapper.document(values));
        var scope = new PermissionProtocol.Scope(document.tenantId(), document.projectId(), "DOCUMENT", document.documentId());
        resources.require(admission, scope, "document.read", manifest.purpose()); resources.require(admission, scope, action, manifest.purpose());
        return document;
    }

    private void requireRunning(EnterpriseAccess.Admission admission, Plan plan, BigInteger revision, String status) throws Exception {
        var values = values(admission); values.put("exportItemId", plan.item().exportItemId());
        var item = single(mapper.itemForUpdate(values)); values.put("exportId", item.exportId()); var manifest = single(mapper.manifest(values));
        requireExport(admission, manifest, "artifact.export");
        if (!plan.item().exportId().equals(item.exportId()) || !revision.equals(item.revision()) || !status.equals(item.status())) stale();
    }

    private ArtifactRegistryProtocol.ArtifactRow requireArtifact(EnterpriseAccess.Admission admission, String artifactId,
            String projectId, String purpose) throws Exception {
        var artifact = single(artifacts.artifact(artifactId));
        if (!admission.context().tenant_id().equals(artifact.tenantId())
                || artifact.projectId() != null && !projectId.equals(artifact.projectId())) mismatch();
        resources.require(admission, new PermissionProtocol.Scope(admission.context().tenant_id(), artifact.projectId(), "ARTIFACT", artifactId),
            "artifact.read", purpose);
        return artifact;
    }

    private void requireCurrent(ApplicationSessionIdentity.Verified subject, String context, Plan plan,
            BigInteger revision, String status) {
        access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireRunning(admission, plan, revision, status); return null;
        }).get());
    }

    private static Path file(Path directory, String name, byte[] bytes) throws Exception {
        Path path = Files.createFile(directory.resolve(name),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(path, bytes); return path;
    }
    private static Path requireChild(Path directory, Path path) throws Exception {
        Path actual = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!actual.startsWith(directory.toRealPath(LinkOption.NOFOLLOW_LINKS)) || !Files.isRegularFile(actual, LinkOption.NOFOLLOW_LINKS)) mismatch();
        return actual;
    }
    private static void remove(Path directory) throws Exception {
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws java.io.IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path value, java.io.IOException failure) throws java.io.IOException {
                if (failure != null) throw failure; Files.delete(value); return FileVisitResult.CONTINUE;
            }
        });
    }
    private static String mediaType(String format) {
        return switch (format) {
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "PDF" -> "application/pdf";
            case "MARKDOWN" -> "text/markdown";
            default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        };
    }
    private static String extension(String value) {
        return switch (value) {
            case "DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "PDF", "application/pdf" -> "pdf";
            case "MARKDOWN", "text/markdown" -> "md";
            case "font/ttf", "application/x-font-ttf" -> ".ttf";
            case "font/otf", "application/vnd.ms-opentype" -> ".otf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> ".bin";
        };
    }
    private static String schemaRef(String kind) { return "urn:medical:export-api:1.0.0#/$defs/" + kind; }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); return values;
    }
    private static void current(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static <T> T single(List<T> rows) { if (rows.size() != 1) mismatch(); return rows.get(0); }
    private static void one(int count) { if (count != 1) mismatch(); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void stale() { throw new TenantAccessException(ErrorCode.ACTION_STALE); }
    private record Plan(ExportProtocol.ItemRow item, ExportProtocol.ManifestRow manifest,
            ExportProtocol.DocumentRow document, BigInteger itemRevision) { }
    private record LoadedArtifact(ArtifactRegistryProtocol.ArtifactRow row, byte[] bytes) {
        private LoadedArtifact { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private record Command(Path request, Path result, BigInteger itemRevision) { }
}

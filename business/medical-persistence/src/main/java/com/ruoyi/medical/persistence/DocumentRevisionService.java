package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.ArtifactBindings;
import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceBindings;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.DocumentEvidenceBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.DocumentReadMapper;
import com.ruoyi.medical.persistence.mapper.DocumentRevisionMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.UploadTransferMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

@Service
public final class DocumentRevisionService {
    private static final String PURPOSE = "DOCUMENT_BODY";
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    private final EnterpriseAccess access;
    private final DocumentRevisionMapper writes;
    private final DocumentReadMapper reads;
    private final UploadTransferMapper uploads;
    private final UploadTransferStore uploadStore;
    private final UploadAuthorizationAuthority authorization;
    private final UploadAuthorizationSchema authorizations;
    private final DocumentBodyDomainAuthority domains;
    private final ArtifactRegistryMapper artifacts;
    private final ArtifactSourceAuthority sourceAuthority;
    private final ArtifactContentService content;
    private final DocumentReadService documents;
    private final ResourceReadAuthority resources;
    private final PermissionMapper permissions;
    private final SourceUploadTransport transport;
    private final DocumentUploadPolicy policy;
    private final PermissionSchema json;
    private final SchemaCatalogue schemas;
    private final String interfacesHash;
    private final AnnotationAnchorLifecycle annotationAnchors;
    private final DocumentReviewJobService professionalReviews;

    public DocumentRevisionService(EnterpriseAccess access, DocumentRevisionMapper writes, DocumentReadMapper reads,
            UploadTransferMapper uploads, UploadAuthorizationAuthority authorization, UploadAuthorizationSchema authorizations,
            DocumentBodyDomainAuthority domains, ArtifactRegistryMapper artifacts, ArtifactSourceAuthority sourceAuthority,
            ArtifactContentService content, DocumentReadService documents, ResourceReadAuthority resources, PermissionMapper permissions,
            SourceUploadTransport transport, DocumentUploadPolicy policy, PermissionSchema json, AnnotationAnchorLifecycle annotationAnchors, DocumentReviewJobService professionalReviews) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.writes = writes; this.reads = reads; this.uploads = uploads; this.authorization = authorization;
        this.authorizations = authorizations; this.domains = domains; this.artifacts = artifacts; this.sourceAuthority = sourceAuthority;
        this.content = content; this.documents = documents; this.resources = resources; this.permissions = permissions;
        this.transport = transport; this.policy = policy; this.json = json; this.annotationAnchors = annotationAnchors; this.professionalReviews = professionalReviews; uploadStore = new UploadTransferStore(uploads, authorizations);
        schemas = SchemaCatalogue.fromClasspath();
        interfacesHash = schemas.bind("interfaces", com.fasterxml.jackson.databind.node.NullNode.instance, "NullableId").schemaSha256();
    }

    JsonNode save(ApplicationSessionIdentity.Verified subject, String context, String key, String documentId, byte[] bytes) throws Exception {
        json.headers(context, key); WireValues.uuid(documentId);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        var value = JSON.readTree(text); if (value == null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var input = TypedSchemaBindings.bind(schemas, InterfaceBindings.REVISION_INPUT, value).read();
        if (input.reason().isBlank()) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        String requestHash = WireValues.sha256(ContentReadAuthorization.canonicalBytes(Map.of("method", "POST",
            "path", "/api/documents/" + documentId + "/revisions", "schema_version", "1.0.0", "schema_sha256", interfacesHash, "body", input)));
        var original = transaction(subject, admission -> prepare(admission, context, key, documentId, input, requestHash));
        if (original.replayed() != null) {
            var actual = documents.snapshotFixed(subject, context, documentId, original.replayed().required("data").required("document_revision").textValue());
            var expected = original.replayed().required("data");
            for (String field : List.of("document_id", "project_id", "document_revision", "plan_revision", "template_version", "body", "fields", "evidence_links", "body_schema_revision"))
                if (!expected.required(field).equals(actual.required("data").required(field))) mismatch();
            return original.replayed();
        }
        var body = transport.readDocument(subject, original.current().document().tenantId(), policy.maximumBytes().intValueExact(),
            () -> requireCurrent(subject, context, key, documentId, input, requestHash, original), (reader, lease) -> {
                var payload = content.readVerifiedComplete(subject, original.intent(), reader, lease, policy.maximumBytes().intValueExact(), id()).jsonPayload();
                DocumentBodySemantics.published(schemas, original.current().publishedSchema(), payload.canonicalBytes(), policy.bodyLimits());
                if (!input.bodySha256().equals(WireValues.sha256(payload.canonicalBytes()))) mismatch();
                return DocumentEvidenceBindings.bind(schemas, InterfaceBindings.DOCUMENT_BODY, JSON.readTree(payload.canonicalJson()),original.current().publishedSchema().schemaRef(),original.current().publishedSchema().schemaSha256()).read();
            });
        var command = transaction(subject, admission -> start(admission, context, key, documentId, input, requestHash, original, body));
        for (int index = 0; index < command.projections().size(); index++) {
            final int position = index;
            var next = transaction(subject, admission -> {
                current(admission, context, key, documentId, input, requestHash, original);
                var operation = one(writes.operation(values(admission, key, documentId, input, requestHash)));
                var stored = command(operation); var projection = stored.projections().get(position);
                if (!projection.writing()) updateCommand(admission, key, documentId, input, requestHash, operation,
                    replace(stored, position, new DocumentRevisionProtocol.PreparedObject(projection.artifactId(), projection.kind(), projection.schemaRef(),
                        projection.intent(), true, null)));
                return projection;
            });
            byte[] projectionBytes = projection(body, command.documentRevision(), input.bodySha256(), next.kind(),original.current().publishedSchema());
            var reference = transport.writeDocumentProjection(subject, original.current().document().tenantId(), next.intent(), projectionBytes,
                next.writing(), policy.maximumBytes().intValueExact(), () -> requireCurrent(subject, context, key, documentId, input, requestHash, original));
            transaction(subject, admission -> {
                current(admission, context, key, documentId, input, requestHash, original);
                var operation = one(writes.operation(values(admission, key, documentId, input, requestHash))); var stored = command(operation);
                var fixed = stored.projections().get(position);
                if (!fixed.artifactId().equals(next.artifactId()) || !fixed.intent().equals(next.intent()) || !fixed.writing()) mismatch();
                if (fixed.reference() != null && !fixed.reference().equals(reference)) mismatch();
                updateCommand(admission, key, documentId, input, requestHash, operation,
                    replace(stored, position, new DocumentRevisionProtocol.PreparedObject(fixed.artifactId(), fixed.kind(), fixed.schemaRef(), fixed.intent(), true, reference)));
                return null;
            });
        }
        return transaction(subject, admission -> finish(admission, context, key, documentId, input, requestHash, original, body));
    }

    private Work prepare(EnterpriseAccess.Admission admission, String context, String key, String documentId,
            InterfaceProtocol.RevisionInput input, String hash) throws Exception {
        requireContext(admission, context);
        var owner = one(permissions.resourceOwner("DOCUMENT", documentId));
        if (owner.projectId() == null) denied();
        var scope = new PermissionProtocol.Scope(admission.context().tenant_id(), owner.projectId(), "DOCUMENT", documentId);
        resources.require(admission, scope, "document.write", PURPOSE);
        var values = values(admission, key, documentId, input, hash); values.put("projectId", owner.projectId());
        var operations = writes.operation(values);
        if (operations.size() > 1) mismatch();
        if (!operations.isEmpty()) {
            var operation = operations.get(0); requireOperation(admission, operation, key, documentId, hash);
            if ("COMPLETED".equals(operation.state())) {
                var saved = JSON.readTree(operation.responseRef()); schemas.validate("interfaces", saved, "Envelope");
                var snapshot = TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_SNAPSHOT, saved.required("data")).read();
                if (!documentId.equals(snapshot.documentId()) || !owner.projectId().equals(snapshot.projectId()) || operation.httpStatus() != 201) mismatch();
                return new Work(null, null, null, saved);
            }
        }
        policy.requireConfigured();
        String uploadId = one(writes.completedUpload(values));
        var binding = uploadStore.binding(admission, uploadId); var upload = uploadStore.session(admission, uploadId);
        var grant = one(uploads.grant(Map.of("userId", admission.subject().user().user_id(), "uploadId", uploadId, "lock", true)));
        var manifest = authorizations.requireBinding(binding, grant);
        var current = authorization.requireDocument(admission, documentId, input.expectedRevision(), manifest);
        if (!input.baseRevision().equals(current.document().currentDocumentRevision())) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if (!"COMPLETED".equals(upload.state()) || !"COMPLETED".equals(grant.status()) || !input.bodyArtifactId().equals(grant.artifactId())
                || !input.bodySha256().equals(grant.sha256()) || !input.bodySha256().equals(upload.contentSha256())
                || !input.expectedRevision().equals(binding.expectedRevision().toString()) || !documentId.equals(binding.documentId())) mismatch();
        values.put("documentRevision", input.baseRevision());
        var base = one(reads.header(values));
        if (!input.bodySchemaRevision().equals(base.bodySchemaRevision())) throw new TenantAccessException(ErrorCode.SCHEMA_UNSUPPORTED);
        var schemaVersion = one(artifacts.version(input.bodySchemaRevision()));
        WireValues.hash(schemaVersion.contentSha256());
        var required = new ArtifactRegistryProtocol.ReadRequirement(scope, "document.read", PURPOSE, manifest.schemaRef(),
            manifest.schemaDocumentSha256(), manifest.artifactSchemaManifest(), policy.limits());
        var intent = new ContentReadProtocol.ReadIntent(input.bodyArtifactId(), context, required, manifest.sources(), manifest.context(), manifest.upstreamArtifacts());
        var registered = sourceAuthority.require(admission, input.bodyArtifactId(), required, manifest.sources(), manifest.context(), manifest.upstreamArtifacts());
        var artifact = registered.registration().artifact();
        if (!"DOCUMENT_BODY".equals(artifact.artifactKind()) || !input.bodySha256().equals(artifact.sha256())
                || !upload.objectKey().equals(artifact.objectKey()) || !upload.objectVersion().equals(artifact.objectVersion())
                || !binding.dependencyAccessSetId().equals(artifact.accessSetId())) mismatch();
        return new Work(current, uploadId, intent, null);
    }

    private UploadAuthorizationAuthority.VerifiedDocument current(EnterpriseAccess.Admission admission, String context, String key,
            String documentId, InterfaceProtocol.RevisionInput input, String hash, Work original) throws Exception {
        var found = prepare(admission, context, key, documentId, input, hash);
        if (found.replayed() != null) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);
        if (!original.uploadId().equals(found.uploadId()) || !original.intent().equals(found.intent())
                || !original.current().authorizationSha256().equals(found.current().authorizationSha256()))
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        return found.current();
    }
    private void requireCurrent(ApplicationSessionIdentity.Verified subject, String context, String key, String id,
            InterfaceProtocol.RevisionInput input, String hash, Work original) {
        transaction(subject, admission -> { current(admission, context, key, id, input, hash, original); return null; });
    }

    private DocumentRevisionProtocol.Command start(EnterpriseAccess.Admission admission, String context, String key, String documentId,
            InterfaceProtocol.RevisionInput input, String hash, Work original, InterfaceProtocol.DocumentBody body) throws Exception {
        var verified = current(admission, context, key, documentId, input, hash, original);
        domains.require(admission, verified, schemas, json.tree(body));
        var values = values(admission, key, documentId, input, hash); var operations = writes.operation(values);
        if (!operations.isEmpty()) {
            var saved = command(one(operations));
            if (!original.uploadId().equals(saved.uploadId()) || !verified.authorizationSha256().equals(saved.authorizationSha256()))
                throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            for (var item : saved.projections()) {
                byte[] bytes = projection(body, saved.documentRevision(), input.bodySha256(), item.kind(),verified.publishedSchema());
                if (!WireValues.sha256(bytes).equals(item.intent().sha256()) || !Integer.toString(bytes.length).equals(item.intent().sizeBytes())) mismatch();
            }
            return saved;
        }
        String revision = id(); var projections = new ArrayList<DocumentRevisionProtocol.PreparedObject>();
        for (String kind : List.of("DOCUMENT_FIELDS", "DOCUMENT_EVIDENCE")) {
            String artifactId = id(); byte[] bytes = projection(body, revision, input.bodySha256(), kind,verified.publishedSchema());
            if (bytes.length > policy.maximumBytes().intValueExact()) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
            var intent = new S3ObjectWriter.WriteIntent(id(), "documents/" + admission.context().tenant_id() + "/" + documentId + "/" + revision + "/" + artifactId,
                WireValues.sha256(bytes), Integer.toString(bytes.length), "application/json");
            projections.add(new DocumentRevisionProtocol.PreparedObject(artifactId, kind, schemas.schemaRef(verified.publishedSchema().name(),
                kind.equals("DOCUMENT_FIELDS") ? "DocumentFieldProjection" : "DocumentEvidenceProjection"), intent, false, null));
        }
        var command = new DocumentRevisionProtocol.Command(revision, id(), original.uploadId(), verified.authorizationSha256(), projections);
        values.put("operationId", id()); values.put("commandJson", json.canonicalObject(command));
        values.put("now", permissions.databaseTime()); values.put("classification", verified.document().classification()); values.put("manifest", json.manifestSha256());
        changed(writes.startOperation(values));
        if (!command.equals(command(one(writes.operation(values))))) mismatch(); return command;
    }

    private byte[] projection(InterfaceProtocol.DocumentBody body, String revision, String hash, String kind, SchemaCatalogue.PublishedSchema publication) throws IOException {
        DocumentEvidenceBindings.bind(schemas,InterfaceBindings.DOCUMENT_BODY,json.tree(body),publication.schemaRef(),publication.schemaSha256());
        var value = kind.equals("DOCUMENT_FIELDS")
            ? json.tree(new InterfaceProtocol.DocumentFieldProjection(body.schemaVersion(), revision, hash, body.fields()))
            : json.tree(new InterfaceProtocol.DocumentEvidenceProjection(body.schemaVersion(), revision, hash, body.evidenceLinks(), body.factUsages()));
        return schemas.bind(publication.name(), value, kind.equals("DOCUMENT_FIELDS") ? "DocumentFieldProjection" : "DocumentEvidenceProjection").canonicalBytes();
    }

    private JsonNode finish(EnterpriseAccess.Admission admission, String context, String key, String documentId,
            InterfaceProtocol.RevisionInput input, String hash, Work original, InterfaceProtocol.DocumentBody body) throws Exception {
        var verified = current(admission, context, key, documentId, input, hash, original); domains.require(admission, verified, schemas, json.tree(body));
        var values = values(admission, key, documentId, input, hash); var operation = one(writes.operation(values)); var command = command(operation);
        if (!verified.authorizationSha256().equals(command.authorizationSha256()) || command.projections().stream().anyMatch(item -> item.reference() == null)) mismatch();
        var artifact = one(artifacts.artifact(input.bodyArtifactId())); var bodyReference = artifact.reference();
        values.put("projectId", verified.document().projectId()); values.put("classification", verified.document().classification());
        values.put("now", permissions.databaseTime()); values.put("basis", verified.basis()); values.put("body", body);
        values.put("bodyReference", bodyReference); values.put("documentRevision", command.documentRevision());
        values.put("accessSetId", verified.manifest().dependencyAccessSetId()); values.put("dependencyHash", verified.manifest().dependencyManifestSha256());
        values.put("artifactManifest", verified.manifest().artifactSchemaManifest()); values.put("interfacesHash", verified.publishedSchema().schemaSha256()); values.put("manifest", json.manifestSha256());
        saveVersion(values, command.documentRevision(), "DOCUMENT_REVISION", input.bodySha256()); changed(writes.saveRevision(values));
        changed(writes.adoptBody(values)); changed(writes.saveMetadata(values));
        for (var item : command.projections()) {
            values.put("projection", item); values.put("storedObject", StoredObjectReferences.encode(item.reference()));
            saveVersion(values, item.artifactId(), "ARTIFACT", item.reference().sha256()); changed(writes.saveArtifact(values)); changed(writes.saveBinding(values));
            for (var member : verified.dependencies().manifest().members()) lineage(values, item.artifactId(), member.upstreamVersionId(), "DOCUMENT_PROJECTION_INPUT");
            var registered = one(artifacts.artifact(item.artifactId()));
            if (!item.reference().equals(StoredObjectReferences.decode(registered.storedObject(), registered.reference()))
                    || !item.kind().equals(registered.artifactKind()) || !item.schemaRef().equals(one(artifacts.binding(item.artifactId())).schemaRef())) mismatch();
        }
        for (var member : verified.dependencies().manifest().members()) lineage(values, command.documentRevision(), member.upstreamVersionId(), "DOCUMENT_INPUT");
        lineage(values, command.documentRevision(), input.bodyArtifactId(), "DOCUMENT_BODY");
        var graph = graph(body); var nodeIndex = new HashMap<String,InterfaceProtocol.Node>(); body.nodes().forEach(node -> nodeIndex.put(node.nodeId(), node));
        var expectedNodes = new ArrayList<DocumentRevisionProtocol.NodeRow>(); var order = new TopologicalOrderIterator<>(graph);
        while (order.hasNext()) {
            var node = nodeIndex.get(order.next()); var row = new DocumentRevisionProtocol.NodeRow(node.nodeId(), node.parentId(), node.ordinal(),
                json.tree(node.content()).required("kind").textValue(), WireValues.sha256(ContentReadAuthorization.canonicalBytes(node)));
            values.put("node", row); changed(writes.saveNode(values)); expectedNodes.add(row);
        }
        expectedNodes.sort(Comparator.comparing(DocumentRevisionProtocol.NodeRow::nodeId));
        if (!expectedNodes.equals(writes.nodes(values))) mismatch();
        for (var field : body.fields()) {
            values.put("field", field); values.put("fieldJson", json.canonicalObject(field)); values.put("fieldEvidence", json.canonicalObject(field.evidenceLinkIds()));
            changed(writes.saveField(values));
        }
        var actualFields = writes.fields(values); var expectedFields = body.fields().stream().sorted(Comparator.comparing(InterfaceProtocol.FieldValue::fieldId)).toList();
        if (actualFields.size() != expectedFields.size()) mismatch();
        for (int position = 0; position < actualFields.size(); position++) {
            var actual = actualFields.get(position); var expected = expectedFields.get(position);
            if (!actual.fieldId().equals(expected.fieldId()) || !actual.nodeId().equals(expected.nodeId()) || !actual.definitionId().equals(expected.fieldDefinitionId())
                    || !json.tree(expected).equals(JSON.readTree(actual.valueJson()))) mismatch();
        }
        saveEvidence(values, body, nodeIndex); saveUsages(values, body); saveSections(values, body, graph);
        changed(writes.saveWatermarks(values));
        var previous = new HashMap<>(values); previous.put("documentRevision", input.baseRevision());
        writes.invalidateChecks(values); writes.invalidateEvidence(values);
        for (String review : reads.reviews(previous)) {
            values.put("reviewId", review); values.put("invalidationId", id()); changed(writes.invalidateReview(values));
        }
        int invalidatedAnnotations = invalidateAnnotationsAndAdvanceHead(admission, values);
        var saved = one(reads.header(values));
        if (!command.documentRevision().equals(saved.currentDocumentRevision()) || !WireValues.revision(input.expectedRevision()).add(BigInteger.ONE).equals(saved.revision())
                || !input.bodySha256().equals(saved.bodySha256()) || !input.bodyArtifactId().equals(saved.bodyArtifactId())
                || !body.templateVersion().equals(saved.templateVersion()) || !verified.basis().plan().planRevision().equals(saved.planRevision())) mismatch();
        var marks = one(reads.watermarks(values)); if (!marks.issueWatermark().equals(BigInteger.valueOf(invalidatedAnnotations)) || marks.evidenceWatermark().signum() != 0) mismatch();
        if (!reads.streams(values).isEmpty()) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var projections = new HashMap<String,DocumentRevisionProtocol.PreparedObject>(); command.projections().forEach(item -> projections.put(item.kind(), item));
        var fields = projections.get("DOCUMENT_FIELDS"); var evidence = projections.get("DOCUMENT_EVIDENCE");
        var snapshot = new InterfaceProtocol.DocumentSnapshot(documentId, saved.projectId(), command.documentRevision(), saved.revision().toString(), saved.planRevision(),
            saved.templateVersion(), reference(input.bodyArtifactId(), bodyReference, verified.manifest().schemaRef()), reference(fields.artifactId(), fields.reference(), fields.schemaRef()),
            reference(evidence.artifactId(), evidence.reference(), evidence.schemaRef()), input.bodySchemaRevision(), List.of(), List.of(), List.of(), List.of(), marks.issueWatermark().toString(), "0",
            admission.watermarks().authEpoch().toString(), admission.watermarks().governanceEpoch().toString(), List.of("document.edit"), List.of());
        var response = json.tree(new InterfaceProtocol.Envelope("1.0.0", command.requestId(), saved.revision().toString(),
            JSON.readTree(TypedSchemaBindings.bind(schemas, InterfaceBindings.DOCUMENT_SNAPSHOT, json.tree(snapshot)).payload().canonicalJson())));
        schemas.validate("interfaces", response, "Envelope");
        values.put("operationId", operation.operationId()); values.put("operationRevision", operation.revision()); values.put("response", json.canonical(response));
        changed(writes.completeOperation(values));
        var event = json.object(); event.put("schema_version", "1.0.0"); event.put("document_id", documentId); event.put("document_revision", command.documentRevision());
        event.put("base_revision", input.baseRevision()); event.put("body_sha256", input.bodySha256()); event.put("revision", saved.revision().toString());
        event.put("operation_id", operation.operationId()); event.put("reason", input.reason());
        changed(permissions.saveAudit(admission.subject().user().user_id(), saved.projectId(), id(), "document.revision.saved", json.canonical(event),
            command.requestId(), json.manifestSha256(), saved.classification()));
        changed(permissions.saveEvent(admission.subject().user().user_id(), id(), documentId, saved.revision().toString(), "document.revision.saved", json.canonical(event)));
        professionalReviews.enqueueSaved(admission, documentId, command.documentRevision(), operation.operationId());
        var completed = one(writes.operation(values)); requireOperation(admission, completed, key, documentId, hash);
        if (!"COMPLETED".equals(completed.state()) || !response.equals(JSON.readTree(completed.responseRef()))) mismatch(); return response;
    }

    /** DC03 calls this only after immutable physical objects and revision metadata are registered.
     * Reuses DC02 normalized content persistence and its annotation/head CAS tail, never upload grants. */
    DocumentReadProtocol.Header persistAdoptionContent(EnterpriseAccess.Admission admission,
            DocumentCandidateAdoptionService.Confirmed proof, DocumentCandidateAdoptionSaveService.Registration registration) throws Exception {
        registration.requireFor(admission, proof);
        String revision=registration.revision(), bodyArtifactId=registration.bodyArtifactId(),
            accessSetId=registration.accessSetId(), planHash=registration.planHash();
        var body = DocumentEvidenceBindings.bind(schemas, InterfaceBindings.DOCUMENT_BODY, proof.body(),proof.bodySchemaRef(),proof.bodySchemaSha256()).read();
        var values = new HashMap<String,Object>();
        values.put("userId", admission.actorId()); values.put("projectId", proof.projectId());
        values.put("documentId", proof.documentId()); values.put("documentRevision", revision);
        values.put("input", Map.of("baseRevision", proof.baseDocumentRevision(), "expectedRevision", proof.expectedDocumentRevision()));
        values.put("basis", Map.of("plan", Map.of("planRevision", proof.planRevision())));
        values.put("body", body); values.put("now", permissions.databaseTime());
        values.put("classification", proof.classification()); values.put("manifest", json.manifestSha256());
        var graph = graph(body); var nodeIndex = new HashMap<String,InterfaceProtocol.Node>(); body.nodes().forEach(node -> nodeIndex.put(node.nodeId(), node));
        var expectedNodes = new ArrayList<DocumentRevisionProtocol.NodeRow>(); var order = new TopologicalOrderIterator<>(graph);
        while (order.hasNext()) {
            var node = nodeIndex.get(order.next()); var row = new DocumentRevisionProtocol.NodeRow(node.nodeId(), node.parentId(), node.ordinal(),
                json.tree(node.content()).required("kind").textValue(), WireValues.sha256(ContentReadAuthorization.canonicalBytes(node)));
            values.put("node", row); changed(writes.saveNode(values)); expectedNodes.add(row);
        }
        expectedNodes.sort(Comparator.comparing(DocumentRevisionProtocol.NodeRow::nodeId));
        if (!expectedNodes.equals(writes.nodes(values))) mismatch();
        for (var field : body.fields()) {
            values.put("field", field); values.put("fieldJson", json.canonicalObject(field)); values.put("fieldEvidence", json.canonicalObject(field.evidenceLinkIds()));
            changed(writes.saveField(values));
        }
        var actualFields = writes.fields(values); var expectedFields = body.fields().stream().sorted(Comparator.comparing(InterfaceProtocol.FieldValue::fieldId)).toList();
        if (actualFields.size() != expectedFields.size()) mismatch();
        for (int position = 0; position < actualFields.size(); position++) {
            var actual = actualFields.get(position); var expected = expectedFields.get(position);
            if (!actual.fieldId().equals(expected.fieldId()) || !actual.nodeId().equals(expected.nodeId()) || !actual.definitionId().equals(expected.fieldDefinitionId())
                    || !json.tree(expected).equals(JSON.readTree(actual.valueJson()))) mismatch();
        }
        saveEvidence(values, body, nodeIndex); saveUsages(values, body); saveSections(values, body, graph, planHash, proof.profileRevision());
        changed(writes.saveWatermarks(values));
        var previous = new HashMap<>(values); previous.put("documentRevision", proof.baseDocumentRevision());
        writes.invalidateChecks(values); writes.invalidateEvidence(values);
        for (String review : reads.reviews(previous)) {
            values.put("reviewId", review); values.put("invalidationId", id()); changed(writes.invalidateReview(values));
        }
        int invalidatedAnnotations = invalidateAnnotationsAndAdvanceHead(admission, values);
        var saved = one(reads.header(values));
        if (!revision.equals(saved.currentDocumentRevision()) || !revision.equals(saved.documentRevision())
                || !WireValues.revision(proof.expectedDocumentRevision()).add(BigInteger.ONE).equals(saved.revision())
                || !proof.bodySha256().equals(saved.bodySha256()) || !bodyArtifactId.equals(saved.bodyArtifactId())
                || !accessSetId.equals(saved.accessSetId()) || !proof.planRevision().equals(saved.planRevision())
                || !proof.templateVersion().equals(saved.templateVersion())) mismatch();
        var marks = one(reads.watermarks(values));
        if (!marks.issueWatermark().equals(BigInteger.valueOf(invalidatedAnnotations)) || marks.evidenceWatermark().signum()!=0) mismatch();
        if (!reads.streams(values).isEmpty()) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return saved;
    }

    /** Atomic tail of body-save: caller already wrote and verified the new fixed body inside this transaction. */
    int invalidateAnnotationsAndAdvanceHead(EnterpriseAccess.Admission admission, Map<String,Object> values) throws Exception {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new TenantAccessException(ErrorCode.SCOPE_STALE);
        int count = annotationAnchors.invalidate(admission, values);
        if (count > 0) { values.put("annotationIssues", count); changed(writes.advanceAnnotationIssues(values)); }
        changed(writes.advanceHead(values));
        return count;
    }

    private void saveEvidence(Map<String,Object> values, InterfaceProtocol.DocumentBody body, Map<String,InterfaceProtocol.Node> nodes) throws Exception {
        var expected = new ArrayList<DocumentRevisionProtocol.EvidenceRow>();
        for (var link : body.evidenceLinks()) for (String nodeId : link.nodeIds()) {
            String claimId = id(), evidenceId = id(); values.put("claimId", claimId); values.put("evidenceId", evidenceId); values.put("nodeId", nodeId);
            values.put("statementHash", WireValues.sha256(ContentReadAuthorization.canonicalBytes(nodes.get(nodeId).content())));
            values.put("link", link); String serialized = json.canonicalObject(link); values.put("linkJson", serialized);
            values.put("evidenceRelation", switch (link.relation()) { case SUPPORT -> "SUPPORTS"; case CONFLICT -> "CONTRADICTS"; case BACKGROUND -> "BACKGROUND"; });
            changed(writes.saveClaim(values)); changed(writes.saveEvidence(values)); changed(writes.saveEvidenceProjection(values));
            expected.add(new DocumentRevisionProtocol.EvidenceRow(link.evidenceLinkId(), nodeId, evidenceId, claimId, serialized));
        }
        expected.sort(Comparator.comparing(DocumentRevisionProtocol.EvidenceRow::linkId).thenComparing(DocumentRevisionProtocol.EvidenceRow::nodeId));
        var actual = writes.evidence(values); if (actual.size() != expected.size()) mismatch();
        for (int index = 0; index < actual.size(); index++) {
            var row = actual.get(index); var wanted = expected.get(index);
            if (!row.linkId().equals(wanted.linkId()) || !row.nodeId().equals(wanted.nodeId()) || !row.evidenceId().equals(wanted.evidenceId())
                    || !row.claimId().equals(wanted.claimId()) || !JSON.readTree(row.valueJson()).equals(JSON.readTree(wanted.valueJson()))) mismatch();
        }
    }
    private void saveUsages(Map<String,Object> values, InterfaceProtocol.DocumentBody body) {
        var fields = new HashMap<String,InterfaceProtocol.FieldValue>(); body.fields().forEach(field -> fields.put(field.fieldId(), field));
        var expected = new ArrayList<DocumentRevisionProtocol.UsageRow>();
        for (var fact : body.factUsages()) {
            for (String nodeId : fact.nodeIds()) expected.add(new DocumentRevisionProtocol.UsageRow(fact.factVersionId(), nodeId, null));
            for (String fieldId : fact.fieldIds()) expected.add(new DocumentRevisionProtocol.UsageRow(fact.factVersionId(), fields.get(fieldId).nodeId(), fieldId));
        }
        for (var usage : expected) { values.put("usage", usage); values.put("usageId", id()); changed(writes.saveUsage(values)); }
        expected.sort(Comparator.comparing(DocumentRevisionProtocol.UsageRow::factId).thenComparing(DocumentRevisionProtocol.UsageRow::nodeId)
            .thenComparing(DocumentRevisionProtocol.UsageRow::fieldId, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (!expected.equals(writes.usages(values))) mismatch();
    }
    private void saveSections(Map<String,Object> values, InterfaceProtocol.DocumentBody body, DefaultDirectedGraph<String,DefaultEdge> graph) throws Exception {
        var basis = (DocumentUploadBasisProtocol.Basis) values.get("basis");
        saveSections(values, body, graph, basis.plan().contentSha256(), basis.taskRevision().profileRevision());
    }
    private void saveSections(Map<String,Object> values, InterfaceProtocol.DocumentBody body,
            DefaultDirectedGraph<String,DefaultEdge> graph, String planHash, String profileRevision) throws Exception {
        var bound = TypedSchemaBindings.bind(schemas, ArtifactBindings.PLAN_CANDIDATE, JSON.readTree(one(writes.planContent(values))));
        if (!planHash.equals(WireValues.sha256(bound.payload().canonicalBytes()))) mismatch();
        var candidate = bound.read();
        if (!body.templateVersion().equals(candidate.templateVersion()) || !profileRevision.equals(candidate.profileRevision())) mismatch();
        var sections = writes.planSections(values); var groups = new LinkedHashMap<String,List<String>>();
        sections.forEach(section -> groups.computeIfAbsent(section.sectionId(), ignored -> new ArrayList<>()).add(section.nodeId()));
        if (groups.size() != candidate.sections().size()) mismatch();
        var nodeIds = new HashSet<>(body.nodes().stream().map(InterfaceProtocol.Node::nodeId).toList());
        long ordinal = 0;
        for (var section : candidate.sections()) {
            if (!section.requirementNodeIds().equals(groups.get(section.sectionId())) || !nodeIds.containsAll(section.requirementNodeIds())) mismatch();
            var descendants = new HashSet<String>();
            for (String nodeId : section.requirementNodeIds()) {
                var traversal = new DepthFirstIterator<>(graph, nodeId); traversal.setCrossComponentTraversal(false);
                while (traversal.hasNext()) descendants.add(traversal.next());
            }
            var sectionBody = json.object(); sectionBody.set("nodes", json.tree(body.nodes().stream().filter(item -> descendants.contains(item.nodeId())).toList()));
            sectionBody.set("fields", json.tree(body.fields().stream().filter(item -> descendants.contains(item.nodeId())).toList()));
            sectionBody.set("fact_usages", json.tree(body.factUsages().stream().filter(item -> item.nodeIds().stream().anyMatch(descendants::contains)
                || body.fields().stream().anyMatch(field -> descendants.contains(field.nodeId()) && item.fieldIds().contains(field.fieldId()))).toList()));
            var sectionEvidence = json.tree(body.evidenceLinks().stream().filter(item -> item.nodeIds().stream().anyMatch(descendants::contains)).toList());
            sectionBody.set("evidence_links", sectionEvidence);
            String revision = id(), hash = WireValues.sha256(ContentReadAuthorization.canonicalBytes(sectionBody));
            values.put("section", section); values.put("sectionRevision", revision); values.put("sectionOrdinal", ordinal++);
            values.put("sectionBody", json.canonical(sectionBody)); values.put("sectionEvidence", json.canonical(sectionEvidence)); values.put("sectionHash", hash);
            saveVersion(values, revision, "SECTION_REVISION", hash); changed(writes.saveSection(values)); changed(writes.saveDocumentSection(values));
            lineage(values, revision, values.get("documentRevision").toString(), "SECTION_DOCUMENT");
        }
    }
    private static DefaultDirectedGraph<String,DefaultEdge> graph(InterfaceProtocol.DocumentBody body) {
        var graph = new DefaultDirectedGraph<String,DefaultEdge>(DefaultEdge.class); body.nodes().forEach(node -> graph.addVertex(node.nodeId()));
        body.nodes().forEach(node -> { if (node.parentId() != null) graph.addEdge(node.parentId(), node.nodeId()); }); return graph;
    }
    private void saveVersion(Map<String,Object> values, String id, String kind, String hash) {
        values.put("versionId", id); values.put("versionKind", kind); values.put("versionHash", hash); changed(writes.saveVersion(values));
    }
    private void lineage(Map<String,Object> values, String consumer, String upstream, String kind) {
        values.put("consumerId", consumer); values.put("upstreamId", upstream); values.put("relationKind", kind); changed(writes.saveLineage(values));
    }
    private static CommonProtocol.ArtifactRef reference(String id, InterfaceProtocol.ObjectRef object, String schemaRef) throws Exception {
        String range = ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(object), 0, WireValues.revision(object.sizeBytes()).longValueExact());
        return new CommonProtocol.ArtifactRef(id, object.sha256(), schemaRef, new CommonProtocol.Coverage(range, range, true, null));
    }
    private DocumentRevisionProtocol.Command command(UploadOperationRow operation) throws IOException {
        if (!"PROCESSING".equals(operation.state()) || operation.responseRef() == null || operation.httpStatus() != null) mismatch();
        return JSON.treeToValue(JSON.readTree(operation.responseRef()), DocumentRevisionProtocol.Command.class);
    }
    private void updateCommand(EnterpriseAccess.Admission admission, String key, String documentId, InterfaceProtocol.RevisionInput input,
            String hash, UploadOperationRow operation, DocumentRevisionProtocol.Command command) throws IOException {
        var values = values(admission, key, documentId, input, hash); values.put("operationId", operation.operationId());
        values.put("operationRevision", operation.revision()); values.put("now", permissions.databaseTime()); values.put("commandJson", json.canonicalObject(command));
        changed(writes.updateCommand(values)); if (!command.equals(command(one(writes.operation(values))))) mismatch();
    }
    private static DocumentRevisionProtocol.Command replace(DocumentRevisionProtocol.Command command, int index, DocumentRevisionProtocol.PreparedObject next) {
        var values = new ArrayList<>(command.projections()); values.set(index, next);
        return new DocumentRevisionProtocol.Command(command.documentRevision(), command.requestId(), command.uploadId(), command.authorizationSha256(), values);
    }
    private void requireOperation(EnterpriseAccess.Admission admission, UploadOperationRow operation, String key, String documentId, String hash) {
        if (!hash.equals(operation.requestSha256())) throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!admission.context().tenant_id().equals(operation.tenantId()) || !admission.subject().user().user_id().equals(operation.subjectId())
                || !operation.subjectId().equals(operation.createdBy()) || !documentId.equals(operation.targetId()) || !key.equals(operation.idempotencyKey())
                || !"DC-02".equals(operation.operationName()) || !"1.0.0".equals(operation.schemaVersion())
                || !json.manifestSha256().equals(operation.schemaManifestSha256()) || operation.errorCode() != null
                || operation.createdAt() == null || operation.updatedAt() == null || operation.updatedAt().isBefore(operation.createdAt())
                || operation.revision().signum() <= 0 || !List.of("PROCESSING", "COMPLETED").contains(operation.state())) mismatch();
    }
    private static HashMap<String,Object> values(EnterpriseAccess.Admission admission, String key, String documentId, InterfaceProtocol.RevisionInput input, String hash) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id()); values.put("key", key);
        values.put("documentId", documentId); values.put("input", input); values.put("requestHash", hash);
        TenantScope.requireSubjectParameter("com.ruoyi.medical.persistence.mapper.DocumentRevisionMapper.operation", values); return values;
    }
    private <T> T transaction(ApplicationSessionIdentity.Verified subject, Checked<T> operation) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> operation.apply(admission)).get());
    }
    @FunctionalInterface private interface Checked<T> { T apply(EnterpriseAccess.Admission admission) throws Exception; }
    private record Work(UploadAuthorizationAuthority.VerifiedDocument current, String uploadId, ContentReadProtocol.ReadIntent intent, JsonNode replayed) { }
    private static void requireContext(EnterpriseAccess.Admission admission, String context) {
        if (!context.equals(admission.context().context_revision())) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
    }
    private static <T> T one(List<T> rows) { if (rows.size() != 1) denied(); return Objects.requireNonNull(rows.get(0)); }
    private static void changed(int count) { if (count != 1) throw new TenantAccessException(ErrorCode.REVISION_CONFLICT); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

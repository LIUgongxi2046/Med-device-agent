package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.DatasetBindings;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.TypedSchemaBindings;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.ArtifactRegistryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

@Component
public final class SourceGovernanceService {
    private static final String PURPOSE = "SOURCE_REVIEW";
    private static final int MAXIMUM_CONTENT_PAGE_BYTES = 1024 * 1024;
    private final EnterpriseAccess access;
    private final SourceGovernanceMapper mapper;
    private final com.ruoyi.medical.persistence.mapper.SourceDeletionMapper deletionMapper;
    private final ResourceReadAuthority authority;
    private final PermissionAuthority permissionAuthority;
    private final SourceReadAuthority sourceAuthority;
    private final ArtifactRegistryMapper artifacts;
    private final DependencyLineage lineage;
    private final SourceRightsSchema rightsSchema;
    private final CollectionPolicy lineagePolicy;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final SourceGovernanceSchema schema;
    private final IdentityDirectoryPolicy identities;
    private final RetrievalPolicy policy;
    private final ProjectService projects;
    private final SourceUploadTransport transport;
    private final SchemaCatalogue catalogue;
    private final JdbcTemplate jdbc;
    private final SourceProcessingResult processingDefinition;

    public SourceGovernanceService(EnterpriseAccess access, SourceGovernanceMapper mapper,
            com.ruoyi.medical.persistence.mapper.SourceDeletionMapper deletionMapper,
            ResourceReadAuthority authority, PermissionAuthority permissionAuthority,
            SourceReadAuthority sourceAuthority, ArtifactRegistryMapper artifacts, DependencyLineage lineage,
            SourceRightsSchema rightsSchema, CollectionPolicy lineagePolicy, PermissionMapper permissions,
            PermissionSchema json, SourceGovernanceSchema schema, IdentityDirectoryPolicy identities,
            RetrievalPolicy policy, ProjectService projects, SourceUploadTransport transport,
            @Qualifier("masterDataSource") DataSource master) throws IOException, NoSuchAlgorithmException {
        this.access = access; this.mapper = mapper; this.deletionMapper = deletionMapper;
        this.authority = authority; this.permissionAuthority = permissionAuthority;
        this.sourceAuthority = sourceAuthority; this.artifacts = artifacts; this.lineage = lineage;
        this.rightsSchema = rightsSchema; this.lineagePolicy = lineagePolicy;
        this.permissions = permissions; this.json = json; this.schema = schema; this.identities = identities;
        this.policy = policy; this.projects = projects; this.transport = transport;
        catalogue = SchemaCatalogue.fromClasspath(); jdbc = new JdbcTemplate(master);
        processingDefinition = ThrowingSupplier.of(SourceProcessingResult::new).get();
    }

    public JsonNode read(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(sourceVersion);
            var values = values(admission, sourceVersion); var rows = mapper.reviews(values);
            if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var head = rows.get(0); requireRead(admission, head);
            var reviews = json.object().arrayNode();
            for (var row : rows) { verifyHead(head, row); reviews.add(review(row)); }
            var view = json.object(); view.put("schema_version", "1.0.0"); view.put("source_id", head.sourceId());
            view.put("source_version", head.sourceVersion()); nullable(view, "owner_project_id", head.ownerProjectId());
            view.put("ownership", head.ownership()); view.put("title", head.title()); view.put("classification", head.classification());
            view.put("original_sha256", head.originalSha256()); view.put("media_type", head.mediaType());
            view.put("size_bytes", head.sizeBytes().toString()); view.put("rights_revision", head.rightsRevision());
            view.put("rights_purpose", head.rightsPurpose()); view.put("rights_status", head.rightsStatus());
            var lifecycle = single(mapper.lifecycleState(values));
            if (!head.sourceId().equals(lifecycle.sourceId()) || !head.sourceVersion().equals(lifecycle.sourceVersion())) mismatch();
            view.put("lifecycle_state", lifecycle.status()); view.put("lifecycle_revision", lifecycle.revision().toString());
            nullable(view, "last_lifecycle_decision", lifecycle.lastDecisionId());
            var deletionJobs = mapper.currentDeletionJob(values);
            if (List.of("PURGE_PENDING", "PURGED").contains(lifecycle.status())) {
                var deletionJob = single(deletionJobs);
                if (!sourceVersion.equals(deletionJob.targetVersion())) mismatch();
                view.put("deletion_id", deletionJob.deletionId()); view.put("deletion_status", deletionJob.status());
            } else {
                if (!deletionJobs.isEmpty()) mismatch();
                view.putNull("deletion_id"); view.putNull("deletion_status");
            }
            values.put("sourceId", head.sourceId()); var versions = json.object().arrayNode();
            boolean selectedVersion = false;
            for (var version : mapper.versions(values)) {
                if (!head.tenantId().equals(version.tenantId()) || !head.sourceId().equals(version.sourceId())) mismatch();
                var item = json.object(); item.put("source_version", version.sourceVersion());
                item.put("version_no", version.versionNo().toString()); nullable(item, "publisher_version", version.publisherVersion());
                item.put("original_sha256", version.originalSha256()); item.put("media_type", version.mediaType());
                item.put("size_bytes", version.sizeBytes().toString()); item.put("rights_revision", version.rightsRevision());
                item.put("lifecycle", version.lifecycle()); item.put("created_at", time(version.createdAt()));
                boolean selected = sourceVersion.equals(version.sourceVersion()); item.put("selected", selected);
                if (selected) selectedVersion = true; schema.verify("SourceVersionSummary", item); versions.add(item);
            }
            if (!selectedVersion) mismatch(); view.set("versions", versions);
            view.set("reviews", reviews); var bindings = json.object().arrayNode();
            for (var binding : mapper.bindings(values)) {
                var grant = authority.find(admission,
                        new PermissionProtocol.Scope(head.tenantId(), binding.projectId(), "PROJECT", binding.projectId()),
                        "project.read", "PROJECT_READING");
                if (grant.isEmpty()) continue;
                var item = json.object(); item.put("project_id", binding.projectId()); item.put("binding_revision", binding.bindingRevision());
                item.put("applicability", binding.applicability()); item.set("decision_manifest", schema.read(binding.decisionManifest()));
                schema.verify("BindingView", item); bindings.add(item);
            }
            view.set("bindings", bindings); view.set("allowed_actions", json.tree(List.of()));
            return schema.envelope("View", view, UUID.randomUUID().toString(), null);
        }).get());
    }

    ProcessingStatus uploadStatus(EnterpriseAccess.Admission admission,String sourceVersion,String parseVersion,int maximum) throws IOException {
        WireValues.uuid(sourceVersion); if(parseVersion!=null) WireValues.uuid(parseVersion);
        if(maximum<1 || maximum>1000) throw new IllegalArgumentException("上传状态读取容量无效");
        var values=values(admission,sourceVersion); values.put("parseVersion",parseVersion); values.put("fetchLimit",maximum+1);
        var rows=mapper.uploadReviews(values);
        if(rows.size()>maximum) denied(ErrorCode.CONTEXT_LIMIT);
        if(rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
        var head=rows.get(0); requireRead(admission,head); var reviews=json.object().arrayNode();
        var unique=new java.util.HashSet<String>();
        var lifecycle=single(mapper.lifecycleState(values));
        if(!head.sourceId().equals(lifecycle.sourceId()) || !head.sourceVersion().equals(lifecycle.sourceVersion())) mismatch();
        for(var row:rows) {
            verifyHead(head,row);
            if(!unique.add(row.parseVersion()+":"+row.indexVersion()) || !lifecycle.status().equals(row.lifecycle())) mismatch();
            reviews.add(review(row));
        }
        return new ProcessingStatus(head,reviews,lifecycle.status());
    }
    record ProcessingStatus(SourceGovernanceProtocol.ReviewRow head,JsonNode reviews,String lifecycle) { }

    public JsonNode list(ApplicationSessionIdentity.Verified subject, String context,
            SourceGovernanceProtocol.CatalogueQuery query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
            values.put("projectId", query.projectId());
            values.put("ownership", query.ownership()); values.put("approvalStatus", query.approvalStatus());
            values.put("lifecycle", query.lifecycle()); values.put("bookmarked", query.bookmarked());
            values.put("category", query.category()); values.put("tag", query.tag());
            values.put("folderId", query.folderId());
            values.put("collectionVersion", query.collectionVersion());
            var visible = new java.util.ArrayList<SourceGovernanceProtocol.CatalogueRow>();
            for (var row : mapper.catalogue(values)) {
                var grant = authority.find(admission, scope(row), "source.read", "SOURCE_READING");
                if (grant.isPresent()) visible.add(row);
            }
            int start = 0;
            if (query.after() != null) {
                while (start < visible.size() && !visible.get(start).sourceVersion().equals(query.after())) start++;
                if (start == visible.size()) denied(ErrorCode.CURSOR_INVALID);
                start++;
            }
            int end = Math.min(start + query.limit(), visible.size());
            var items = json.object().arrayNode();
            for (var row : visible.subList(start, end)) items.add(catalogueItem(row));
            var page = json.object(); page.put("schema_version", "1.0.0"); page.set("items", items);
            page.put("total", Integer.toString(visible.size())); page.put("limit", query.limit());
            if (end < visible.size()) page.put("next_after", visible.get(end - 1).sourceVersion()); else page.putNull("next_after");
            return schema.envelope("Listing", page, UUID.randomUUID().toString(), null);
        }).get());
    }

    public JsonNode processingQueue(ApplicationSessionIdentity.Verified subject, String context,
            SourceGovernanceProtocol.ProcessingQueueQuery query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = new HashMap<String,Object>();
            values.put("userId", admission.subject().user().user_id()); values.put("projectId", query.projectId());
            values.put("status", query.status());
            var visible = new ArrayList<SourceGovernanceProtocol.ProcessingQueueRow>();
            for (var row : mapper.processingQueue(values)) {
                var scope = new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
                if (authority.find(admission, scope, "source.read", "SOURCE_READING").isPresent()) visible.add(row);
            }
            int start = 0;
            if (query.after() != null) {
                while (start < visible.size() && !visible.get(start).attemptId().equals(query.after())) start++;
                if (start == visible.size()) denied(ErrorCode.CURSOR_INVALID);
                start++;
            }
            int end = Math.min(start + query.limit(), visible.size());
            var items = json.object().arrayNode();
            for (var row : visible.subList(start, end)) {
                var item = json.object(); item.put("attempt_id", row.attemptId()); item.put("project_id", row.projectId());
                item.put("source_id", row.sourceId()); item.put("source_version", row.sourceVersion());
                item.put("title", row.title()); nullable(item, "parse_version", row.parseVersion());
                item.put("job_type", row.jobType()); item.put("config_revision", row.configRevision());
                item.put("status", row.status()); nullable(item, "parse_status", row.parseStatus());
                item.set("failure_manifest", schema.read(row.failureManifest()));
                item.set("request_manifest", schema.read(row.requestManifest())); item.put("run_id", row.runId());
                item.put("created_by", row.createdBy()); item.put("created_at", time(row.createdAt()));
                item.put("revision", row.revision().toString()); item.put("updated_at", time(row.updatedAt()));
                schema.verify("ProcessingQueueItem", item); items.add(item);
            }
            var page = json.object(); page.put("schema_version", "1.0.0"); page.set("items", items);
            page.put("total", Integer.toString(visible.size())); page.put("limit", query.limit());
            if (end < visible.size()) page.put("next_after", visible.get(end - 1).attemptId()); else page.putNull("next_after");
            return schema.envelope("ProcessingQueuePage", page, UUID.randomUUID().toString(), null);
        }).get());
    }

    public JsonNode artifacts(ApplicationSessionIdentity.Verified subject, String context,
            SourceGovernanceProtocol.ArtifactCatalogueQuery query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
            values.put("projectId", query.projectId()); values.put("artifactKind", query.artifactKind());
            var visible = new ArrayList<SourceGovernanceProtocol.ArtifactCatalogueRow>();
            for (var row : mapper.artifactCatalogue(values)) {
                var scope = new PermissionProtocol.Scope(row.tenantId(), row.projectId(), "ARTIFACT", row.artifactId());
                if (authority.find(admission, scope, "artifact.read", "ARTIFACT_READING").isPresent()) visible.add(row);
            }
            int start = 0;
            if (query.after() != null) {
                while (start < visible.size() && !visible.get(start).artifactId().equals(query.after())) start++;
                if (start == visible.size()) denied(ErrorCode.CURSOR_INVALID);
                start++;
            }
            int end = Math.min(start + query.limit(), visible.size()); var items = json.object().arrayNode();
            for (var row : visible.subList(start, end)) {
                var item = json.object(); item.put("artifact_id", row.artifactId());
                nullable(item, "project_id", row.projectId()); nullable(item, "run_id", row.runId());
                nullable(item, "document_revision", row.documentRevision()); item.put("artifact_kind", row.artifactKind());
                item.put("sha256", row.sha256()); item.put("size_bytes", row.sizeBytes().toString());
                item.put("media_type", row.mediaType()); item.put("created_at", time(row.createdAt()));
                schema.verify("ArtifactCatalogueItem", item); items.add(item);
            }
            var page = json.object(); page.put("schema_version", "1.0.0"); page.set("items", items);
            page.put("total", Integer.toString(visible.size())); page.put("limit", query.limit());
            if (end < visible.size()) page.put("next_after", visible.get(end - 1).artifactId()); else page.putNull("next_after");
            return schema.envelope("ArtifactCataloguePage", page, UUID.randomUUID().toString(), null);
        }).get());
    }

    public JsonNode submitArtifact(ApplicationSessionIdentity.Verified subject, String context, String key,
            String artifactId, SourceGovernanceProtocol.ArtifactSubmissionInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); String tenant = admission.context().tenant_id();
            String actor = admission.subject().user().user_id(); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            var values = values(admission, null); values.put("artifactId", artifactId); values.put("key", key);
            values.put("requestHash", schema.artifactSubmissionHash(artifactId, input, context, subject.sessionId()));
            values.put("schemaManifest", schema.manifestSha256()); values.put("classification", identities.classification());
            values.put("now", now);

            var artifact = single(artifacts.artifact(artifactId));
            if (!tenant.equals(artifact.tenantId()) || !artifactId.equals(artifact.artifactId())
                    || !artifact.sha256().equals(artifact.contentSha256())) mismatch();
            values.put("classification", artifact.classification());
            var artifactScope = new PermissionProtocol.Scope(tenant, artifact.projectId(), "ARTIFACT", artifactId);
            authority.require(admission, artifactScope, "artifact.read", "SOURCE_SUBMISSION");
            authority.require(admission, artifactScope, "artifact.submit", "SOURCE_SUBMISSION");
            authority.require(admission, artifactScope, "source.share", "SOURCE_SUBMISSION");

            String targetProject = input.target_project_id();
            values.put("targetProjectId", targetProject);
            if (targetProject != null) {
                var states = permissions.projectStatus(targetProject);
                if (states.size() != 1 || !"ACTIVE".equals(states.get(0))) denied(ErrorCode.ACCESS_DENIED);
            }
            String targetKind = targetProject == null ? "ENTERPRISE" : "PROJECT";
            String targetResource = targetProject == null ? tenant : targetProject;
            permissionAuthority.requireResource(new PermissionProtocol.Scope(tenant, targetProject, targetKind, targetResource),
                    actor, admission.subject().binding().view().mode(), "source.write", "SOURCE_SUBMISSION",
                    permissions.resourceGrants(actor, "source.write"), now);
            activeMember(input.maintainer_id(), now); activeMember(input.reviewer_id(), now);
            for (String project : input.applicable_project_ids()) {
                var states = permissions.projectStatus(project);
                if (states.size() != 1 || !"ACTIVE".equals(states.get(0))) denied(ErrorCode.ACCESS_DENIED);
                authority.require(admission, new PermissionProtocol.Scope(tenant, project, "PROJECT", project),
                        "project.read", "SOURCE_SUBMISSION");
            }

            var inspected = lineage.inspect(artifactId, lineagePolicy.limits());
            var sourceVersions = new TreeSet<String>();
            for (var version : inspected.versions().values())
                if ("SOURCE_VERSION".equals(version.entityKind())) sourceVersions.add(version.id());
            if (sourceVersions.isEmpty()) denied(ErrorCode.LINEAGE_REVIEW_REQUIRED);
            values.put("sourceVersions", sourceVersions);
            var sourceRows = mapper.sourceEvidence(values);
            var requestedEvidence = new TreeMap<String,String>();
            for (var item : input.original_evidence()) requestedEvidence.put(item.source_version(), item.rights_revision());
            if (sourceRows.size() != sourceVersions.size() || !requestedEvidence.keySet().equals(sourceVersions))
                denied(ErrorCode.LINEAGE_REVIEW_REQUIRED);
            for (var evidence : sourceRows) {
                if (!Objects.equals(requestedEvidence.get(evidence.sourceVersion()), evidence.rightsRevision()))
                    denied(ErrorCode.LINEAGE_REVIEW_REQUIRED);
                sourceAuthority.require(admission, new SourceReadProtocol.ReadRequirement(evidence.sourceVersion(),
                        evidence.rightsRevision(), evidence.ownerProjectId(), null, null, null,
                        SourceReadProtocol.Use.REVIEW, evidence.purpose(), lineagePolicy.environment(),
                        lineagePolicy.region(), null));
            }
            if (!input.rights().allowed_actions().containsAll(List.of("READ", "STORE"))) denied(ErrorCode.RIGHTS_PENDING);
            LocalDateTime validFrom = input.rights().valid_from() == null ? null : utc(input.rights().valid_from());
            LocalDateTime validTo = input.rights().valid_to() == null ? null : utc(input.rights().valid_to());
            if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) denied(ErrorCode.RIGHTS_PENDING);

            var prior = mapper.artifactSubmissionOperation(values);
            if (!prior.isEmpty()) return replayArtifactSubmission(single(prior), values, artifactId);
            String lockedArtifact = jdbc.queryForObject("""
                    SELECT artifact_id FROM artifact
                    WHERE tenant_id=? AND project_key=? AND artifact_id=? FOR UPDATE
                    """, String.class, tenant, artifact.projectId() == null ? "ENTERPRISE" : artifact.projectId(), artifactId);
            if (!artifactId.equals(lockedArtifact)) mismatch();
            var pending = mapper.pendingArtifactSubmission(values);
            if (!pending.isEmpty()) return reuseArtifactSubmission(admission, single(pending), values, artifactId, actor, now);

            String operationId = UUID.randomUUID().toString(), submissionId = UUID.randomUUID().toString();
            String sourceId = UUID.randomUUID().toString(), sourceVersion = UUID.randomUUID().toString();
            String parseVersion = UUID.randomUUID().toString(), rightsRevision = UUID.randomUUID().toString();
            String sharingDecision = UUID.randomUUID().toString();
            values.put("operationId", operationId); values.put("submissionId", submissionId); values.put("sourceId", sourceId);
            values.put("sourceVersion", sourceVersion); values.put("parseVersion", parseVersion);
            values.put("rightsRevision", rightsRevision); values.put("sharingDecision", sharingDecision);
            values.put("artifactOwnerProjectId", artifact.projectId());
            values.put("ownership", input.ownership()); values.put("title", input.title()); values.put("category", input.category());
            values.put("maintainerId", input.maintainer_id()); values.put("artifactSha256", artifact.sha256());
            values.put("objectKey", artifact.objectKey()); values.put("objectVersion", artifact.objectVersion());
            values.put("mediaType", artifact.mediaType()); values.put("sizeBytes", artifact.sizeBytes());
            values.put("publisherVersion", "artifact:" + artifactId); values.put("now", now);

            var evidenceManifest = json.object(); evidenceManifest.put("owner", input.maintainer_id());
            evidenceManifest.put("license", input.rights().license());
            evidenceManifest.set("environments", json.tree(List.of(lineagePolicy.environment())));
            evidenceManifest.set("classifications", json.tree(List.of(artifact.classification())));
            evidenceManifest.set("authorization_evidence", json.tree(List.of(artifact.reference())));
            var rightsBody = json.object(); rightsBody.put("schema_version", "1.0.0"); rightsBody.put("tenant_id", tenant);
            rightsBody.put("rights_revision", rightsRevision); rightsBody.put("source_version", sourceVersion);
            rightsBody.put("purpose", input.rights().purpose()); rightsBody.set("allowed_actions", json.tree(input.rights().allowed_actions()));
            rightsBody.set("providers", json.tree(input.rights().providers())); rightsBody.set("regions", json.tree(input.rights().regions()));
            nullable(rightsBody, "valid_from", validFrom == null ? null : validFrom.toInstant(ZoneOffset.UTC).toString());
            nullable(rightsBody, "valid_to", validTo == null ? null : validTo.toInstant(ZoneOffset.UTC).toString());
            rightsBody.set("evidence_manifest", evidenceManifest); rightsBody.put("classification", artifact.classification());
            String rightsSha256 = DigestUtils.sha256Hex(json.canonical(rightsBody));
            rightsSchema.verify(new SourceReadProtocol.RightsRow(tenant, rightsRevision, sourceVersion,
                    input.rights().purpose(), json.canonical(json.tree(input.rights().allowed_actions())),
                    json.canonical(json.tree(input.rights().providers())), json.canonical(json.tree(input.rights().regions())),
                    validFrom, validTo, "PENDING_REVIEW", json.canonical(evidenceManifest), rightsSha256, "1.0.0",
                    rightsSchema.manifestSha256(), artifact.classification(), rightsSha256, targetProject));
            values.put("rightsPurpose", input.rights().purpose());
            values.put("allowedActions", json.canonical(json.tree(input.rights().allowed_actions())));
            values.put("providers", json.canonical(json.tree(input.rights().providers())));
            values.put("regions", json.canonical(json.tree(input.rights().regions())));
            values.put("validFrom", validFrom); values.put("validTo", validTo);
            values.put("rightsEvidence", json.canonical(evidenceManifest)); values.put("rightsSha256", rightsSha256);
            values.put("rightsSchemaManifest", rightsSchema.manifestSha256());

            var provenance = json.object(); provenance.put("kind", "ARTIFACT_SUBMISSION");
            provenance.put("artifact_id", artifactId); provenance.put("submission_id", submissionId);
            provenance.put("sharing_decision", sharingDecision); values.put("provenance", json.canonical(provenance));
            var originalEvidence = json.object(); originalEvidence.set("sources", json.tree(input.original_evidence()));
            originalEvidence.set("applicable_project_ids", json.tree(input.applicable_project_ids()));
            originalEvidence.put("maintainer_id", input.maintainer_id()); originalEvidence.put("reviewer_id", input.reviewer_id());
            originalEvidence.put("category", input.category()); values.put("originalEvidence", json.canonical(originalEvidence));
            var sharing = json.object(); sharing.put("schema_version", "1.0.0"); sharing.put("sharing_decision", sharingDecision);
            sharing.put("artifact_id", artifactId); sharing.put("target_source_version", sourceVersion);
            sharing.set("request", json.tree(input)); sharing.set("lineage_versions", json.tree(inspected.versions().keySet()));
            values.put("sharingSha256", DigestUtils.sha256Hex(json.canonical(sharing)));
            var processor = json.object(); processor.put("origin", "ARTIFACT_SUBMISSION");
            processor.put("artifact_id", artifactId); processor.put("submission_id", submissionId);
            values.put("processorManifest", json.canonical(processor));

            var upstream = new TreeSet<String>(inspected.versions().keySet());
            var holds = new ArrayList<Map<String,String>>();
            for (String upstreamId : upstream) holds.add(Map.of("holdId", UUID.randomUUID().toString(), "upstreamId", upstreamId));
            values.put("holds", holds);

            one(mapper.insertSubmissionSource(values)); one(mapper.insertSubmissionSourceVersionRegistry(values));
            one(mapper.insertSubmissionSourceVersion(values)); one(mapper.insertSubmissionRightsRegistry(values));
            one(mapper.insertSubmissionRights(values)); one(mapper.insertSubmissionRightsState(values));
            one(mapper.insertParseVersion(values)); one(mapper.insertSourceGate(values));
            one(mapper.insertSubmissionLifecycle(values)); one(mapper.insertSubmissionOrganization(values));
            one(mapper.insertSharingDecisionRegistry(values)); one(mapper.insertArtifactSubmission(values));
            if (mapper.insertSubmissionLineage(values) != holds.size() || mapper.insertSubmissionHolds(values) != holds.size()) mismatch();
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);

            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("submission_id", submissionId); receipt.put("artifact_id", artifactId); receipt.put("source_id", sourceId);
            receipt.put("source_version", sourceVersion); receipt.put("parse_version", parseVersion);
            receipt.put("rights_revision", rightsRevision); receipt.put("sharing_decision", sharingDecision);
            receipt.put("status", "PENDING"); receipt.put("approval_status", "PENDING"); receipt.put("lifecycle", "ACTIVE");
            receipt.put("actor_id", actor); receipt.put("created_at", time(now)); receipt.set("current_context", currentContext);
            var response = schema.envelope("ArtifactSubmissionReceipt", receipt, UUID.randomUUID().toString(), "1");
            values.put("response", json.canonical(response)); one(mapper.saveArtifactSubmissionOperation(values));
            one(permissions.saveAudit(actor, targetProject, UUID.randomUUID().toString(), "artifact.submitted_as_source",
                    json.canonical(sharing), response.required("request_id").textValue(), schema.manifestSha256(), artifact.classification()));
            one(permissions.saveEvent(actor, UUID.randomUUID().toString(), sourceVersion, "1",
                    "artifact.submitted_as_source", json.canonical(receipt)));

            var stored = single(mapper.artifactSubmission(values));
            if (!tenant.equals(stored.tenantId()) || !submissionId.equals(stored.submissionId())
                    || !artifactId.equals(stored.artifactId()) || !sourceId.equals(stored.targetSourceId())
                    || !sourceVersion.equals(stored.targetSourceVersion()) || !parseVersion.equals(stored.parseVersion())
                    || !sharingDecision.equals(stored.sharingDecision())
                    || !"PENDING".equals(stored.status()) || !BigInteger.ONE.equals(stored.revision())) mismatch();
            if (!artifact.equals(single(artifacts.artifact(artifactId)))) mismatch();
            if (!response.equals(replayArtifactSubmission(single(mapper.artifactSubmissionOperation(values)), values, artifactId))) mismatch();
            return response;
        }).get());
    }

    public JsonNode folders(ApplicationSessionIdentity.Verified subject, String context, String projectId) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireFolderAuthority(admission, projectId);
            var values = values(admission, null); values.put("ownerProjectId", projectId);
            var items = json.object().arrayNode();
            for (var row : mapper.folders(values)) items.add(folderView(row));
            var result = json.object(); result.put("schema_version", "1.0.0");
            nullable(result, "owner_project_id", projectId); result.set("items", items);
            return schema.envelope("FolderList", result, UUID.randomUUID().toString(), null);
        }).get());
    }

    public JsonNode createFolder(ApplicationSessionIdentity.Verified subject, String context, String key,
            SourceGovernanceProtocol.FolderCreateInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); requireFolderAuthority(admission, input.owner_project_id());
            var values = values(admission, null); values.put("ownerProjectId", input.owner_project_id());
            values.put("key", key); values.put("operationName", "KN-21");
            values.put("httpStatus", 201);
            values.put("requestHash", schema.folderCreateHash(input, context, subject.sessionId()));
            var prior = mapper.folderOperation(values);
            if (!prior.isEmpty()) return replayFolder(single(prior), values);
            var folders = mapper.folders(values); requireParent(folders, input.parent_id(), input.owner_project_id());
            String folderId = UUID.randomUUID().toString(), operationId = UUID.randomUUID().toString();
            String requestId = UUID.randomUUID().toString(); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("folderId", folderId); values.put("parentId", input.parent_id()); values.put("category", input.category());
            values.put("name", input.name()); values.put("now", now); values.put("classification", identities.classification());
            values.put("schemaManifest", schema.manifestSha256()); values.put("operationId", operationId); values.put("httpStatus", 201);
            one(mapper.insertFolder(values)); var row = single(mapper.folderForUpdate(values));
            var receipt = folderReceipt(operationId, "CREATE", row);
            var response = schema.envelope("FolderReceipt", receipt, requestId, row.revision().toString());
            values.put("response", json.canonical(response)); one(mapper.saveFolderOperation(values));
            saveFolderSignals(subject, row, "source.folder_created", requestId);
            if (!replayFolder(single(mapper.folderOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode decideFolder(ApplicationSessionIdentity.Verified subject, String context, String key,
            String folderId, SourceGovernanceProtocol.FolderDecisionInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(folderId);
            var values = values(admission, null); values.put("folderId", folderId); values.put("key", key);
            values.put("operationName", "KN-22");
            values.put("httpStatus", 200);
            values.put("requestHash", schema.folderDecisionHash(folderId, input, context, subject.sessionId()));
            var prior = mapper.folderOperation(values);
            if (!prior.isEmpty()) {
                var replay = replayFolder(single(prior), values);
                var owner = replay.required("data").required("folder").required("owner_project_id");
                requireFolderAuthority(admission, owner.isNull() ? null : owner.textValue()); return replay;
            }
            var rows = mapper.folderForUpdate(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var before = single(rows); requireFolderAuthority(admission, before.ownerProjectId());
            if (!input.expected_revision().equals(before.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
            values.put("ownerProjectId", before.ownerProjectId()); var folders = mapper.folders(values);
            if ("MOVE".equals(input.decision())) {
                requireParent(folders, input.target_parent_id(), before.ownerProjectId());
                requireAcyclic(folders, folderId, input.target_parent_id());
            }
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("decision", input.decision()); values.put("name", input.name()); values.put("parentId", input.target_parent_id());
            values.put("expectedRevision", input.expected_revision()); values.put("now", now);
            values.put("classification", before.classification()); values.put("schemaManifest", schema.manifestSha256());
            values.put("operationId", operationId); values.put("httpStatus", 200);
            SourceGovernanceProtocol.FolderRow result;
            if ("DELETE".equals(input.decision())) {
                if (folders.stream().anyMatch(row -> folderId.equals(row.parentId()))
                        || Objects.requireNonNull(mapper.folderMemberCount(values)).signum() != 0) denied(ErrorCode.FOLDER_NOT_EMPTY);
                one(mapper.deleteFolder(values)); result = before;
            } else {
                one(mapper.updateFolder(values)); result = single(mapper.folderForUpdate(values));
            }
            var receipt = folderReceipt(operationId, input.decision(), result);
            var response = schema.envelope("FolderReceipt", receipt, requestId, result.revision().toString());
            values.put("response", json.canonical(response)); one(mapper.saveFolderOperation(values));
            saveFolderSignals(subject, result, "source.folder_" + input.decision().toLowerCase(java.util.Locale.ROOT), requestId);
            if (!replayFolder(single(mapper.folderOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode organize(ApplicationSessionIdentity.Verified subject, String context, String key,
            SourceGovernanceProtocol.OrganizationInput input) throws IOException {
        var items = json.object().arrayNode();
        for (var item : input.items()) items.add(organizeItem(subject, context, key, item));
        var result = json.object(); result.put("schema_version", "1.0.0"); result.set("items", items);
        return schema.envelope("OrganizationReceipt", result, UUID.randomUUID().toString(), null);
    }

    private JsonNode organizeItem(ApplicationSessionIdentity.Verified subject, String context, String key,
            SourceGovernanceProtocol.OrganizationItemInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = values(admission, input.source_version());
            values.put("key", DigestUtils.sha256Hex(key + ":" + input.item_id()));
            values.put("requestHash", schema.organizationHash(input, context, subject.sessionId()));
            var rows = mapper.organizationForUpdate(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var before = single(rows); requireOrganizationAuthority(admission, before);
            var prior = mapper.organizationOperation(values);
            if (!prior.isEmpty()) return replayOrganization(single(prior), values).required("data");
            if (List.of("MOVE", "LABEL").contains(input.decision())
                    && !Objects.equals(input.expected_revision(), string(before.revision()))) denied(ErrorCode.REVISION_CONFLICT);
            if ("MOVE".equals(input.decision())) requireOrganizationFolder(values, before, input.folder_id());
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            values.put("sourceId", before.sourceId()); values.put("folderId", input.folder_id());
            values.put("labels", input.labels() == null ? before.labels() : json.canonical(json.tree(input.labels())));
            values.put("decision", input.decision()); values.put("expectedRevision", string(before.revision()));
            values.put("now", now); values.put("classification", before.classification());
            values.put("schemaManifest", schema.manifestSha256());
            if (List.of("MOVE", "LABEL").contains(input.decision())) {
                if (before.revision() == null) one(mapper.insertOrganization(values)); else one(mapper.updateOrganization(values));
            } else if ("BOOKMARK".equals(input.decision())) {
                int changed = mapper.insertBookmark(values); if (changed < 0 || changed > 1) mismatch();
            } else {
                int changed = mapper.deleteBookmark(values); if (changed < 0 || changed > 1) mismatch();
            }
            var after = single(mapper.organizationForUpdate(values));
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var receipt = organizationReceipt(input, operationId, after);
            var response = schema.envelope("OrganizationReceiptItem", receipt, requestId, null);
            values.put("operationId", operationId); values.put("response", json.canonical(response));
            one(mapper.saveOrganizationOperation(values));
            var audit = json.object(); audit.set("organization", receipt);
            one(permissions.saveAudit(subject.user().user_id(), after.ownerProjectId(), UUID.randomUUID().toString(),
                    "source.organization_decided", json.canonical(audit), requestId, schema.manifestSha256(), after.classification()));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), after.sourceVersion(),
                    after.revision() == null ? "1" : after.revision().toString(), "source.organization_decided", json.canonical(audit)));
            if (!replayOrganization(single(mapper.organizationOperation(values)), values).equals(response)) mismatch();
            return receipt;
        }).get());
    }

    public JsonNode usages(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.UsageQuery query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(sourceVersion);
            if (query.datasetWatermark() != null
                    && !admission.context().governance_epoch().equals(query.datasetWatermark())) denied(ErrorCode.SCOPE_STALE);
            var values = values(admission, sourceVersion); var sourceRows = mapper.reviews(values);
            if (sourceRows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var head = sourceRows.get(0); requireRead(admission, head);
            for (var row : sourceRows) verifyHead(head, row);
            authority.require(admission, scope(head), "source.write", "SOURCE_USAGE_READING");
            values.put("projectId", query.projectId()); values.put("usageKind", query.usageKind());
            values.put("after", query.after()); values.put("fetchLimit", query.limit() + 1);
            var rows = new ArrayList<>(mapper.usages(values)); boolean more = rows.size() > query.limit();
            if (more) rows.remove(rows.size() - 1);
            var items = json.object().arrayNode(); String previous = query.after();
            for (var row : rows) {
                if (previous != null && row.usageKey().compareTo(previous) <= 0) mismatch();
                WireValues.uuid(row.resourceId()); WireValues.uuid(row.versionId());
                if (row.projectId() != null) WireValues.uuid(row.projectId());
                if (query.projectId() != null && !query.projectId().equals(row.projectId())
                        || query.usageKind() != null && !query.usageKind().equals(row.usageKind())) mismatch();
                var item = json.object(); item.put("usage_key", row.usageKey()); item.put("usage_kind", row.usageKind());
                item.put("relation_kind", row.relationKind()); nullable(item, "project_id", row.projectId());
                item.put("resource_kind", row.resourceKind()); item.put("resource_id", row.resourceId());
                item.put("version_id", row.versionId());
                nullable(item, "title", usageTitleVisible(admission, row) ? row.title() : null);
                item.put("status", row.status()); item.put("impact_status", row.impactStatus());
                nullable(item, "created_at", row.createdAt() == null ? null : time(row.createdAt()));
                schema.verify("UsageItem", item); items.add(item); previous = row.usageKey();
            }
            var result = json.object(); result.put("schema_version", "1.0.0");
            result.put("source_version", sourceVersion); result.set("items", items);
            result.put("total", Objects.requireNonNull(mapper.usageCount(values)).toString());
            result.put("limit", query.limit());
            if (more) result.put("next_after", rows.get(rows.size() - 1).usageKey()); else result.putNull("next_after");
            result.put("dataset_watermark", admission.context().governance_epoch());
            return schema.envelope("UsagePage", result, UUID.randomUUID().toString(), null);
        }).get());
    }

    public JsonNode content(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.ContentQuery query) throws Exception {
        WireValues.uuid(sourceVersion); WireValues.uuid(query.parseVersion());
        String requestId = UUID.randomUUID().toString();
        return switch (query.representation()) {
            case "ORIGINAL" -> originalContent(subject, context, sourceVersion, query, requestId);
            case "PARSED" -> parsedContent(subject, context, sourceVersion, query, requestId);
            default -> throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        };
    }

    public JsonNode lifecycle(ApplicationSessionIdentity.Verified subject, String context, String key,
            String sourceVersion, SourceGovernanceProtocol.LifecycleInput input) throws Exception {
        WireValues.uuid(sourceVersion);
        if ("PURGE".equals(input.decision()) != (input.policy_revision() != null))
            throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        if ("RESTORE".equals(input.decision())) verifyRestoreObject(subject, context, sourceVersion, input.expected_revision());
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = values(admission, sourceVersion); values.put("key", key);
            values.put("requestHash", schema.lifecycleHash(sourceVersion, input, context, subject.sessionId()));
            values.put("httpStatus", "PURGE".equals(input.decision()) ? 202 : 200);
            values.put("schemaManifest", schema.manifestSha256()); values.put("classification", identities.classification());
            var state = single(mapper.lifecycleStateForUpdate(values)); requireLifecycleAuthority(admission, state);
            if ("PURGE".equals(input.decision())) requirePurgeAuthority(admission, state);
            var prior = mapper.lifecycleOperation(values);
            if (!prior.isEmpty()) return replayLifecycle(single(prior), values);
            if (!input.expected_revision().equals(state.revision().toString())) denied(ErrorCode.REVISION_CONFLICT);
            String toStatus = lifecycleTransition(state.status(), input.decision());
            if (List.of("RECYCLE", "PURGE").contains(input.decision())
                    && (Objects.requireNonNull(mapper.blockingReferenceCount(values)).signum() != 0
                        || Objects.requireNonNull(deletionMapper.additionalReferenceCount(values)).signum() != 0))
                denied(ErrorCode.REFERENCE_HELD);
            if ("RECYCLE".equals(input.decision()) && state.ownerProjectId() != null
                    && !"ACTIVE".equals(state.ownerProjectStatus())) denied(ErrorCode.ACTION_STALE);

            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            SourceGovernanceProtocol.RetentionPolicyRow retention = null;
            if ("PURGE".equals(input.decision())) {
                values.put("policyRevision", input.policy_revision());
                var policies = mapper.retentionPolicy(values);
                if (policies.size() != 1) denied(ErrorCode.RETENTION_UNCONFIGURED);
                retention = policies.get(0); requireRetention(retention, state, now);
                values.put("backupExpiry", retention.backupExpiry());
            } else values.put("policyRevision", null);

            String operationId = UUID.randomUUID().toString(), decisionId = UUID.randomUUID().toString();
            String deletionId = "PURGE".equals(input.decision()) ? UUID.randomUUID().toString() : null;
            String resultingRevision = PermissionSchema.next(input.expected_revision());
            values.put("operationId", operationId); values.put("decisionId", decisionId); values.put("deletionId", deletionId);
            values.put("decision", input.decision()); values.put("fromStatus", state.status()); values.put("toStatus", toStatus);
            values.put("reason", input.reason()); values.put("expectedRevision", input.expected_revision());
            values.put("resultingRevision", resultingRevision); values.put("ownerProjectId", state.ownerProjectId()); values.put("now", now);

            var decision = json.object(); decision.put("schema_version", "1.0.0"); decision.put("source_version", sourceVersion);
            decision.put("decision_id", decisionId); decision.put("decision", input.decision());
            decision.put("previous_status", state.status()); decision.put("current_status", toStatus);
            decision.put("reason", input.reason()); nullable(decision, "policy_revision", input.policy_revision());
            nullable(decision, "deletion_id", deletionId); decision.put("expected_revision", input.expected_revision());
            decision.put("resulting_revision", resultingRevision); decision.put("actor_id", subject.user().user_id());
            decision.put("decided_at", time(now));
            values.put("decisionSha256", DigestUtils.sha256Hex(json.canonical(decision)));

            BigInteger itemCount = BigInteger.ZERO;
            if (deletionId != null) {
                one(mapper.insertDeletionJob(values));
                int inserted = mapper.insertDeletionItems(values); if (inserted < 3) mismatch();
                itemCount = Objects.requireNonNull(mapper.deletionItemCount(values));
                if (!BigInteger.valueOf(inserted).equals(itemCount)) mismatch();
            }
            one(mapper.insertLifecycleVersion(values)); one(mapper.insertLifecycleDecision(values));
            one(mapper.updateLifecycleState(values));
            if (mapper.updateLifecycleGates(values) < 1) mismatch();
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);

            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("decision_id", decisionId); receipt.put("source_version", sourceVersion);
            receipt.put("decision", input.decision()); receipt.put("previous_status", state.status());
            receipt.put("current_status", toStatus); receipt.put("lifecycle_revision", resultingRevision);
            nullable(receipt, "policy_revision", input.policy_revision()); nullable(receipt, "deletion_id", deletionId);
            nullable(receipt, "deletion_status", deletionId == null ? null : "QUEUED");
            receipt.put("deletion_item_count", itemCount.toString()); receipt.put("actor_id", subject.user().user_id());
            receipt.put("decided_at", time(now)); receipt.set("current_context", currentContext);
            var response = schema.envelope("LifecycleReceipt", receipt, UUID.randomUUID().toString(), resultingRevision);
            values.put("response", json.canonical(response)); one(mapper.saveLifecycleOperation(values));

            var audit = json.object(); audit.set("decision", decision); audit.put("operation_id", operationId);
            audit.put("deletion_item_count", itemCount.toString());
            one(permissions.saveAudit(subject.user().user_id(), state.ownerProjectId(), UUID.randomUUID().toString(),
                    "source.lifecycle_decided", json.canonical(audit), response.required("request_id").textValue(),
                    schema.manifestSha256(), identities.classification()));
            var event = json.object(); event.set("decision", decision); event.put("operation_id", operationId);
            event.put("deletion_item_count", itemCount.toString());
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), sourceVersion,
                    resultingRevision, deletionId == null ? "source.lifecycle_decided" : "source.purge.requested",
                    json.canonical(event)));

            var storedState = single(mapper.lifecycleState(values));
            var storedDecision = single(mapper.lifecycleDecision(values));
            if (!toStatus.equals(storedState.status()) || !decisionId.equals(storedState.lastDecisionId())
                    || !new BigInteger(resultingRevision).equals(storedState.revision())
                    || !decisionId.equals(storedDecision.decisionId()) || !input.decision().equals(storedDecision.decision())
                    || !state.status().equals(storedDecision.fromStatus()) || !toStatus.equals(storedDecision.toStatus())
                    || !schema.manifestSha256().equals(storedDecision.schemaManifestSha256())) mismatch();
            if (deletionId != null) {
                var job = single(mapper.deletionJob(values));
                if (!deletionId.equals(job.deletionId()) || !sourceVersion.equals(job.targetVersion())
                        || !input.policy_revision().equals(job.policyRevision()) || !"QUEUED".equals(job.status())
                        || !BigInteger.ONE.equals(job.revision()) || !now.equals(job.updatedAt())) mismatch();
            }
            if (!replayLifecycle(single(mapper.lifecycleOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode confirmDeletion(ApplicationSessionIdentity.Verified subject, String context, String key,
            String itemId, SourceGovernanceProtocol.DeletionReceiptInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(itemId);
            if (!itemId.equals(input.item_id())) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
            var values = values(admission, null); values.put("itemId", itemId); values.put("key", key);
            values.put("requestHash", schema.deletionReceiptHash(itemId, input, context, subject.sessionId()));
            values.put("schemaManifest", schema.manifestSha256());
            var rows = mapper.deletionItemForUpdate(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var before = single(rows); requirePurgeAuthority(admission, before);
            var prior = mapper.deletionReceiptOperation(values);
            if (!prior.isEmpty()) return replayDeletionReceipt(single(prior), values);
            if (!input.deletion_id().equals(before.deletionId()) || !input.store_kind().equals(before.storeKind())
                    || !input.target_sha256().equals(before.targetSha256())) mismatch();
            if (!input.expected_revision().equals(before.itemRevision().toString())
                    || !input.expected_lifecycle_revision().equals(before.lifecycleRevision().toString()))
                denied(ErrorCode.REVISION_CONFLICT);
            if (!List.of("QUEUED", "RUNNING").contains(before.itemStatus())
                    || !List.of("QUEUED", "RUNNING").contains(before.jobStatus())
                    || !"PURGE_PENDING".equals(before.lifecycleStatus())) denied(ErrorCode.ACTION_STALE);
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            var receipt = input.receipt_ref();
            String workerReceipt = deletionMapper.workerReceipt(values);
            if (workerReceipt == null || !schema.read(workerReceipt).equals(receipt)) denied(ErrorCode.ACTION_STALE);
            values.put("sourceVersion", before.sourceVersion()); values.put("deletionId", before.deletionId());
            var job = single(mapper.deletionJob(values)); values.put("policyRevision", job.policyRevision());
            var retention = single(mapper.retentionPolicy(values));
            requireRetention(retention, single(mapper.lifecycleStateForUpdate(values)), now);
            if (!"VERIFIED_DELETE".equals(retention.purgeMethod())) denied(ErrorCode.RETENTION_UNCONFIGURED);
            if (mapper.blockingReferenceCount(values).signum() != 0 || deletionMapper.additionalReferenceCount(values).signum() != 0)
                denied(ErrorCode.REFERENCE_HELD);
            LocalDateTime deleted = utc(receipt.required("deleted_at").textValue());
            LocalDateTime queried = utc(receipt.required("query_confirmed_at").textValue());
            LocalDateTime backup = utc(receipt.required("backup_confirmed_at").textValue());
            if (queried.isBefore(deleted) || backup.isBefore(queried) || backup.isBefore(before.backupExpiry())
                    || backup.isAfter(now)) denied(ErrorCode.ACTION_STALE);
            values.put("sourceVersion", before.sourceVersion()); values.put("deletionId", before.deletionId());
            values.put("expectedRevision", before.itemRevision().toString()); values.put("expectedJobRevision", before.jobRevision().toString());
            values.put("expectedLifecycleRevision", before.lifecycleRevision().toString());
            values.put("receiptRef", json.canonical(receipt)); values.put("now", now); values.put("classification", before.classification());
            one(mapper.confirmDeletionItem(values));
            boolean complete = Objects.requireNonNull(mapper.pendingDeletionItemCount(values)).signum() == 0;
            if (complete && Objects.requireNonNull(mapper.blockingReferenceCount(values)).signum() != 0) denied(ErrorCode.REFERENCE_HELD);
            values.put("jobStatus", complete ? "COMPLETED" : "RUNNING"); one(mapper.updateDeletionJob(values));
            if (complete) {
                one(mapper.completeLifecycleState(values));
                if (mapper.completeLifecycleGates(values) < 1) mismatch();
            }
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var result = json.object(); result.put("schema_version", "1.0.0"); result.put("operation_id", operationId);
            result.put("deletion_id", before.deletionId()); result.put("item_id", itemId); result.put("store_kind", before.storeKind());
            result.put("item_status", "CONFIRMED"); result.put("item_revision", PermissionSchema.next(input.expected_revision()));
            result.put("job_status", complete ? "COMPLETED" : "RUNNING");
            result.put("job_revision", PermissionSchema.next(before.jobRevision().toString())); result.put("source_version", before.sourceVersion());
            result.put("lifecycle_status", complete ? "PURGED" : "PURGE_PENDING");
            result.put("lifecycle_revision", complete ? PermissionSchema.next(input.expected_lifecycle_revision()) : input.expected_lifecycle_revision());
            result.set("current_context", currentContext);
            var response = schema.envelope("DeletionReceipt", result, requestId, result.required("item_revision").textValue());
            values.put("operationId", operationId); values.put("response", json.canonical(response));
            one(mapper.saveDeletionReceiptOperation(values));
            var audit = json.object(); audit.set("receipt", result); audit.put("target_sha256", before.targetSha256());
            one(permissions.saveAudit(subject.user().user_id(), before.ownerProjectId(), UUID.randomUUID().toString(),
                    "source.deletion_confirmed", json.canonical(audit), requestId, schema.manifestSha256(), before.classification()));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), before.sourceVersion(),
                    result.required("lifecycle_revision").textValue(), complete ? "source.purged" : "source.deletion_confirmed",
                    json.canonical(audit)));
            if (!replayDeletionReceipt(single(mapper.deletionReceiptOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode deletionJob(ApplicationSessionIdentity.Verified subject, String context, String deletionId) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(deletionId);
            var values = values(admission, null); values.put("deletionId", deletionId);
            var jobs = mapper.deletionJob(values); if (jobs.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var job = single(jobs); var rows = mapper.deletionItems(values);
            if (rows.isEmpty()) mismatch();
            var head = rows.get(0); requirePurgeAuthority(admission, head);
            if (!deletionId.equals(job.deletionId()) || !job.targetVersion().equals(head.sourceVersion())
                    || !job.status().equals(head.jobStatus()) || !job.revision().equals(head.jobRevision())) mismatch();
            var items = json.object().arrayNode();
            for (var row : rows) {
                if (!head.tenantId().equals(row.tenantId()) || !head.sourceId().equals(row.sourceId())
                        || !head.sourceVersion().equals(row.sourceVersion()) || !deletionId.equals(row.deletionId())
                        || !head.lifecycleStatus().equals(row.lifecycleStatus())
                        || !head.lifecycleRevision().equals(row.lifecycleRevision())
                        || !job.status().equals(row.jobStatus()) || !job.revision().equals(row.jobRevision())) mismatch();
                var item = json.object(); item.put("item_id", row.itemId()); item.put("store_kind", row.storeKind());
                item.set("target_ref", schema.read(row.targetRef())); item.put("target_sha256", row.targetSha256());
                item.put("status", row.itemStatus());
                if (row.receiptRef() == null) item.putNull("receipt_ref"); else item.set("receipt_ref", schema.read(row.receiptRef()));
                if (row.backupExpiry() == null) item.putNull("backup_expiry"); else item.put("backup_expiry", time(row.backupExpiry()));
                item.put("revision", row.itemRevision().toString()); schema.verify("DeletionItemView", item); items.add(item);
            }
            var result = json.object(); result.put("schema_version", "1.0.0"); result.put("deletion_id", deletionId);
            result.put("source_version", job.targetVersion()); result.put("policy_revision", job.policyRevision());
            result.put("status", job.status()); result.put("revision", job.revision().toString());
            result.put("updated_at", time(job.updatedAt())); result.put("lifecycle_status", head.lifecycleStatus());
            result.put("lifecycle_revision", head.lifecycleRevision().toString()); result.set("items", items);
            return schema.envelope("DeletionJobView", result, UUID.randomUUID().toString(), job.revision().toString());
        }).get());
    }

    private void verifyRestoreObject(ApplicationSessionIdentity.Verified subject, String context,
            String sourceVersion, String expectedRevision) throws Exception {
        var initial = authorizeLifecycle(subject, context, sourceVersion, expectedRevision, "RECYCLED");
        long size = initial.sizeBytes().longValueExact(); if (size > Integer.MAX_VALUE) denied(ErrorCode.FILE_TOO_LARGE);
        var reference = new InterfaceProtocol.ObjectRef(initial.objectKey(), initial.objectVersion(),
                initial.originalSha256(), initial.sizeBytes().toString(), initial.mediaType());
        String verified = transport.readDocument(subject, initial.tenantId(), Math.toIntExact(size),
                () -> authorizeLifecycle(subject, context, sourceVersion, expectedRevision, "RECYCLED"),
                (reader, admission) -> {
                    try (var object = reader.readVerified(reference, admission)) {
                        String digest = object.fullSha256(); admission.requireCurrent(); return digest;
                    }
                });
        if (!initial.originalSha256().equals(verified)) mismatch();
    }

    private SourceGovernanceProtocol.LifecycleStateRow authorizeLifecycle(ApplicationSessionIdentity.Verified subject,
            String context, String sourceVersion, String expectedRevision, String requiredStatus) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); var state = single(mapper.lifecycleState(values(admission, sourceVersion)));
            requireLifecycleAuthority(admission, state);
            if (!expectedRevision.equals(state.revision().toString()) || !requiredStatus.equals(state.status()))
                denied(ErrorCode.REVISION_CONFLICT);
            return state;
        }).get());
    }

    private JsonNode originalContent(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.ContentQuery query, String requestId) throws Exception {
        var initial = authorizeContent(subject, context, sourceVersion, query);
        long size = initial.head().sizeBytes().longValueExact();
        if (size > Integer.MAX_VALUE) denied(ErrorCode.FILE_TOO_LARGE);
        long start = query.startByte() == null ? 0 : query.startByte().longValueExact();
        long end = query.endByte() == null ? Math.min(size, start + MAXIMUM_CONTENT_PAGE_BYTES) : query.endByte().longValueExact();
        if (start < 0 || end <= start || end > size || end - start > MAXIMUM_CONTENT_PAGE_BYTES) denied(ErrorCode.LOCATOR_INVALID);
        var reference = new InterfaceProtocol.ObjectRef(initial.head().objectKey(), initial.head().objectVersion(),
                initial.head().originalSha256(), initial.head().sizeBytes().toString(), initial.head().mediaType());
        var delivered = transport.readDocument(subject, initial.head().tenantId(), Math.toIntExact(size),
                () -> requireCurrentContent(subject, context, sourceVersion, query, initial.authorization()),
                (reader, admission) -> {
                    try (var verified = reader.readVerified(reference, admission)) {
                        byte[] bytes = verified.chunk(start, Math.toIntExact(end - start));
                        admission.requireCurrent();
                        return new OriginalBytes(bytes, verified.fullSha256());
                    }
                });
        var locator = json.object(); locator.put("representation", "ORIGINAL"); locator.put("parse_version", query.parseVersion());
        locator.put("start_byte", Long.toString(start)); locator.put("end_byte", Long.toString(end));
        String selection = DigestUtils.sha256Hex(json.canonical(locator));
        String chunkHash = DigestUtils.sha256Hex(delivered.bytes());
        LocalDateTime readAt = recordRead(subject, context, sourceVersion, query, initial.authorization(), locator,
                selection, chunkHash, requestId);
        var result = contentBase(initial.head(), query); result.put("start_byte", Long.toString(start));
        result.put("end_byte", Long.toString(end)); result.put("size_bytes", Long.toString(size));
        result.put("encoding", "BASE64"); result.put("content", Base64.getEncoder().encodeToString(delivered.bytes()));
        result.put("chunk_sha256", chunkHash); result.put("full_sha256", delivered.fullSha256());
        if (end < size) result.put("next_start_byte", Long.toString(end)); else result.putNull("next_start_byte");
        result.put("read_at", time(readAt));
        return schema.envelope("OriginalContent", result, requestId, null);
    }

    private JsonNode parsedContent(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.ContentQuery query, String requestId) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var current = authorizeContent(admission, context, sourceVersion, query);
            if (!List.of("COMPLETE", "PARTIAL_FAILED").contains(current.head().parseStatus())) denied(ErrorCode.SOURCE_MISSING);
            var values = values(admission, sourceVersion); values.put("parseVersion", query.parseVersion());
            values.put("chunkId", query.chunkId()); values.put("afterOrdinal", query.afterOrdinal());
            values.put("fetchLimit", query.chunkId() == null ? query.limit() + 1 : 2);
            var fetched = new ArrayList<>(mapper.contentChunks(values));
            if (query.chunkId() != null && fetched.size() != 1) denied(ErrorCode.LOCATOR_INVALID);
            if (query.chunkId() == null && query.afterOrdinal() == null && fetched.isEmpty()) denied(ErrorCode.SOURCE_MISSING);
            boolean more = query.chunkId() == null && fetched.size() > query.limit();
            if (more) fetched.remove(fetched.size() - 1);
            var items = json.object().arrayNode();
            for (var row : fetched) items.add(contentItem(current.head(), row, query));
            String next = more ? fetched.get(fetched.size() - 1).ordinal().toString() : null;
            var locator = json.object(); locator.put("representation", "PARSED"); locator.put("parse_version", query.parseVersion());
            if (query.chunkId() == null) {
                if (query.afterOrdinal() == null) locator.putNull("after_ordinal");
                else locator.put("after_ordinal", query.afterOrdinal().toString());
                locator.put("limit", query.limit());
            } else {
                locator.put("chunk_id", query.chunkId()); locator.put("locator_sha256", query.locatorSha256());
            }
            String selection = DigestUtils.sha256Hex(json.canonical(locator));
            String delivered = DigestUtils.sha256Hex(json.canonical(items));
            LocalDateTime readAt = saveRead(admission, current, locator, selection, delivered, requestId);
            var result = contentBase(current.head(), query); result.set("items", items);
            nullable(result, "next_after_ordinal", next); result.put("read_at", time(readAt));
            return schema.envelope("ParsedContent", result, requestId, null);
        }).get());
    }

    private com.fasterxml.jackson.databind.node.ObjectNode contentItem(SourceGovernanceProtocol.ContentHeadRow head,
            SourceGovernanceProtocol.ContentChunkRow row, SourceGovernanceProtocol.ContentQuery query) throws IOException {
        if (!head.sourceVersion().equals(row.sourceVersion()) || !head.parseVersion().equals(row.parseVersion())) mismatch();
        WireValues.uuid(row.chunkId()); WireValues.uuid(row.nodeId()); WireValues.hash(row.nodeHash()); WireValues.hash(row.quoteHash());
        if (!row.quoteHash().equals(DigestUtils.sha256Hex(row.rawText().getBytes(StandardCharsets.UTF_8)))) mismatch();
        var locator = schema.read(row.locator());
        TypedSchemaBindings.bind(catalogue, DatasetBindings.LOCATOR, locator);
        String locatorHash = DigestUtils.sha256Hex(json.canonical(locator));
        if (query.locatorSha256() != null && !query.locatorSha256().equals(locatorHash)) denied(ErrorCode.LOCATOR_INVALID);
        var contentRef = schema.read(row.contentRef()); if (!contentRef.isObject()) mismatch();
        var item = json.object(); item.put("chunk_id", row.chunkId()); item.put("ordinal", row.ordinal().toString());
        item.put("node_id", row.nodeId()); item.put("node_kind", row.nodeKind()); item.set("locator", locator);
        item.put("locator_sha256", locatorHash); item.set("content_ref", contentRef); item.put("node_hash", row.nodeHash());
        item.put("quote_hash", row.quoteHash()); item.put("raw_text", row.rawText());
        schema.verify("ParsedContentItem", item); return item;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode contentBase(SourceGovernanceProtocol.ContentHeadRow head,
            SourceGovernanceProtocol.ContentQuery query) {
        var result = json.object(); result.put("schema_version", "1.0.0"); result.put("source_id", head.sourceId());
        result.put("source_version", head.sourceVersion()); result.put("parse_version", head.parseVersion());
        result.put("representation", query.representation()); result.put("purpose", query.purpose());
        result.put("media_type", head.mediaType()); result.put("original_sha256", head.originalSha256());
        result.put("content_sha256", head.contentSha256()); return result;
    }

    public JsonNode process(ApplicationSessionIdentity.Verified subject, String context, String key,
            String sourceVersion, SourceGovernanceProtocol.ProcessingInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(sourceVersion);
            var values = values(admission, sourceVersion); values.put("key", key);
            values.put("projectId", input.project_id()); values.put("configRevision", input.config_revision());
            values.put("requestHash", schema.processingHash(sourceVersion, input, context, subject.sessionId()));
            values.put("schemaManifest", schema.manifestSha256()); values.put("classification", identities.classification());
            var rows = mapper.reviews(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var active = rows.stream().filter(row -> "ACTIVE".equals(row.lifecycle())).toList();
            if (active.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var source = active.get(0); requireRead(admission, source);
            for (var row : rows) verifyHead(source, row);
            if (source.ownerProjectId() != null && !source.ownerProjectId().equals(input.project_id())) denied(ErrorCode.ACCESS_DENIED);
            var projectStates = permissions.projectStatus(input.project_id());
            if (projectStates.size() != 1 || !"ACTIVE".equals(projectStates.get(0))) denied(ErrorCode.ACCESS_DENIED);
            authority.require(admission, scope(source), "source.write", "SOURCE_PROCESSING");
            authority.require(admission, new PermissionProtocol.Scope(source.tenantId(), input.project_id(), "PROJECT", input.project_id()),
                    "project.read", "SOURCE_PROCESSING");
            var configs = mapper.processingConfig(values); if (configs.size() != 1) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var config = configs.get(0); requireProcessingConfig(admission, input, config);
            var prior = mapper.processingOperation(values);
            if (!prior.isEmpty()) return replayProcessing(single(prior), values);

            String operationId = UUID.randomUUID().toString(), attemptId = UUID.randomUUID().toString();
            String parseVersion = UUID.randomUUID().toString(), runId = UUID.randomUUID().toString();
            String requestId = UUID.randomUUID().toString();
            LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
            var requestManifest = json.object(); requestManifest.put("schema_version", "1.0.0");
            requestManifest.put("project_id", input.project_id()); requestManifest.put("source_version", sourceVersion);
            requestManifest.put("config_revision", input.config_revision()); requestManifest.put("stage", input.stage());
            requestManifest.put("reason", input.reason()); requestManifest.put("request_sha256", values.get("requestHash").toString());
            var processorManifest = json.object(); processorManifest.put("schema_version", "1.0.0");
            processorManifest.put("config_revision", config.versionId()); processorManifest.put("config_sha256", config.contentSha256());
            processorManifest.put("stage", input.stage()); processorManifest.put("source_version", sourceVersion);
            processorManifest.put("original_sha256", source.originalSha256());
            var scopeManifest = json.object(); scopeManifest.put("schema_version", "1.0.0");
            scopeManifest.put("attempt_id", attemptId); scopeManifest.put("source_version", sourceVersion);
            scopeManifest.put("parse_version", parseVersion); scopeManifest.put("config_revision", config.versionId());
            scopeManifest.put("stage", input.stage());
            values.put("operationId", operationId); values.put("attemptId", attemptId); values.put("parseVersion", parseVersion);
            values.put("runId", runId); values.put("now", now); values.put("requestManifest", json.canonical(requestManifest));
            values.put("processorManifest", json.canonical(processorManifest)); values.put("scopeManifest", json.canonical(scopeManifest));
            one(mapper.insertExecution(values)); one(mapper.insertParseVersion(values)); one(mapper.insertSourceGate(values));
            one(mapper.insertProcessingAttempt(values));
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);

            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("attempt_id", attemptId); receipt.put("run_id", runId); receipt.put("project_id", input.project_id());
            receipt.put("source_version", sourceVersion); receipt.put("parse_version", parseVersion);
            receipt.put("config_revision", input.config_revision()); receipt.put("job_type", "PARSE");
            receipt.put("stage", input.stage()); receipt.put("status", "QUEUED");
            receipt.put("actor_id", subject.user().user_id()); receipt.put("queued_at", time(now));
            receipt.set("current_context", currentContext);
            var response = schema.envelope("ProcessingReceipt", receipt, requestId, "1");
            values.put("response", json.canonical(response)); one(mapper.saveProcessingOperation(values));

            var audit = json.object(); audit.set("request", requestManifest); audit.put("attempt_id", attemptId);
            audit.put("parse_version", parseVersion); audit.put("run_id", runId);
            one(permissions.saveAudit(subject.user().user_id(), input.project_id(), UUID.randomUUID().toString(),
                    "source.processing.requested", json.canonical(audit), requestId, schema.manifestSha256(), identities.classification()));
            var event = json.object(); event.set("request", requestManifest); event.put("operation_id", operationId);
            event.put("attempt_id", attemptId); event.put("run_id", runId); event.put("parse_version", parseVersion);
            event.put("original_sha256", source.originalSha256()); event.put("media_type", source.mediaType());
            event.put("size_bytes", source.sizeBytes().toString()); event.put("requested_by", subject.user().user_id());
            event.put("requested_at", time(now));
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), attemptId,
                    "1", "source.processing.requested", json.canonical(event)));

            var stored = single(mapper.processingAttempt(values));
            if (!attemptId.equals(stored.attemptId()) || !sourceVersion.equals(stored.sourceVersion())
                    || !parseVersion.equals(stored.parseVersion()) || !runId.equals(stored.runId())
                    || !input.project_id().equals(stored.projectId()) || !input.config_revision().equals(stored.configRevision())
                    || !"PARSE".equals(stored.jobType()) || !"QUEUED".equals(stored.status())
                    || !"QUEUED".equals(stored.parseStatus()) || !BigInteger.ONE.equals(stored.revision())
                    || !subject.user().user_id().equals(stored.createdBy()) || !now.equals(stored.createdAt())
                    || !now.equals(stored.updatedAt())
                    || !"1.0.0".equals(schema.read(stored.failureManifest()).required("schema_version").textValue())
                    || !schema.read(stored.failureManifest()).required("issues").isArray()
                    || !schema.read(stored.failureManifest()).required("issues").isEmpty()
                    || !schema.read(stored.requestManifest()).equals(requestManifest)
                    || !schema.read(stored.processorManifest()).equals(processorManifest)) mismatch();
            if (!replayProcessing(single(mapper.processingOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode decide(ApplicationSessionIdentity.Verified subject, String context, String key,
            String sourceVersion, SourceGovernanceProtocol.DecisionInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context); WireValues.uuid(sourceVersion);
            var values = values(admission, sourceVersion); values.put("key", key);
            values.put("requestHash", schema.requestHash(sourceVersion, input, context, subject.sessionId()));
            var rows = mapper.reviews(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var selected = rows.stream().filter(row -> input.parse_version().equals(row.parseVersion())
                    && input.index_version().equals(row.indexVersion())).toList();
            if (selected.size() != 1) denied(ErrorCode.SOURCE_NOT_READY);
            var before = selected.get(0); requireRead(admission, before);
            authority.require(admission, scope(before), "source.approve", PURPOSE);
            if (!input.rights_revision().equals(before.rightsRevision())) denied(ErrorCode.SCOPE_STALE);
            if (input.binding_revision() != null) {
                values.put("bindingRevision", input.binding_revision());
                if (mapper.bindingCount(values) != 1) denied(ErrorCode.APPLICABILITY_UNKNOWN);
            }
            var prior = mapper.operation(values);
            if (!prior.isEmpty()) return replay(single(prior), values);
            if (!input.expected_revision().equals(before.gateRevision().toString())) denied(ErrorCode.REVISION_CONFLICT);
            var currentManifest = manifest(before);
            if (!input.reviewed_manifest_sha256().equals(DigestUtils.sha256Hex(json.canonical(currentManifest))))
                denied(ErrorCode.SCOPE_STALE);
            String approvalStatus = transition(before, input.decision());
            if ("APPROVE".equals(input.decision())) requireReady(admission, before);
            String approvalRevision = UUID.randomUUID().toString();
            String resultingRevision = PermissionSchema.next(input.expected_revision());
            var now = Objects.requireNonNull(permissions.databaseTime());
            values.put("parseVersion", input.parse_version()); values.put("approvalRevision", approvalRevision);
            values.put("decision", input.decision()); values.put("reviewedManifest", input.reviewed_manifest_sha256());
            values.put("reason", input.reason()); values.put("now", now); values.put("approvalStatus", approvalStatus);
            values.put("expectedRevision", input.expected_revision()); values.put("resultingRevision", resultingRevision);
            var decisionManifest = json.object(); decisionManifest.put("schema_version", "1.0.0");
            decisionManifest.put("tenant_id", admission.context().tenant_id()); decisionManifest.put("source_version", sourceVersion);
            decisionManifest.put("parse_version", input.parse_version()); decisionManifest.put("approval_revision", approvalRevision);
            decisionManifest.put("decision", input.decision()); decisionManifest.put("reviewer_id", subject.user().user_id());
            decisionManifest.put("reviewed_manifest_sha256", input.reviewed_manifest_sha256());
            decisionManifest.put("reason", input.reason()); decisionManifest.put("decided_at", time(now));
            values.put("ownerProjectId", before.ownerProjectId());
            values.put("decisionSha256", DigestUtils.sha256Hex(json.canonical(decisionManifest)));
            one(mapper.insertDecisionVersion(values)); one(mapper.insertDecision(values)); one(mapper.updateGate(values));
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("decision_id", approvalRevision); receipt.put("source_version", sourceVersion);
            receipt.put("parse_version", input.parse_version()); receipt.put("index_version", input.index_version());
            receipt.put("decision", input.decision()); receipt.put("approval_status", approvalStatus);
            receipt.put("approval_revision", approvalRevision); receipt.put("gate_revision", resultingRevision);
            receipt.put("actor_id", subject.user().user_id()); receipt.put("decided_at", time(now));
            receipt.set("current_context", currentContext);
            var response = schema.envelope("Receipt", receipt, requestId, resultingRevision);
            values.put("operationId", operationId); values.put("response", json.canonical(response));
            values.put("schemaManifest", schema.manifestSha256()); values.put("classification", identities.classification());
            one(mapper.saveOperation(values));
            var audit = json.object(); audit.put("source_version", sourceVersion); audit.put("parse_version", input.parse_version());
            audit.put("index_version", input.index_version()); audit.put("decision", input.decision());
            audit.put("reviewed_manifest_sha256", input.reviewed_manifest_sha256()); audit.put("gate_revision", resultingRevision);
            one(permissions.saveAudit(subject.user().user_id(), before.ownerProjectId(), UUID.randomUUID().toString(),
                    "source.approval_decided", json.canonical(audit), requestId, schema.manifestSha256(), identities.classification()));
            var event = json.object(); event.set("decision", receipt);
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), sourceVersion,
                    resultingRevision, "source.approval_decided", json.canonical(event)));
            var after = mapper.reviews(values).stream().filter(row -> input.parse_version().equals(row.parseVersion())
                    && input.index_version().equals(row.indexVersion())).toList();
            if (after.size() != 1 || !approvalRevision.equals(after.get(0).approvalRevision())
                    || !approvalStatus.equals(after.get(0).approvalStatus())
                    || !new BigInteger(resultingRevision).equals(after.get(0).gateRevision())
                    || !input.decision().equals(after.get(0).currentDecision())) mismatch();
            if (!replay(single(mapper.operation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    public JsonNode bind(ApplicationSessionIdentity.Verified subject, String context, String key,
            String projectId, SourceGovernanceProtocol.BindingInput input) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            current(admission, context);
            var values = values(admission, input.source_version()); values.put("projectId", projectId); values.put("key", key);
            values.put("requestHash", schema.bindingHash(projectId, input, context, subject.sessionId()));
            var rows = mapper.reviews(values); if (rows.isEmpty()) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var source = rows.get(0); requireRead(admission, source);
            if (source.ownerProjectId() != null && !source.ownerProjectId().equals(projectId)) denied(ErrorCode.ACCESS_DENIED);
            var profile = projects.fixedProfile(admission, projectId, input.profile_revision(), "PROJECT_PROFILE_CONFIRMATION");
            authority.require(admission, new PermissionProtocol.Scope(source.tenantId(), projectId, "PROJECT", projectId),
                    "profile.confirm", "PROJECT_PROFILE_CONFIRMATION");
            if (!List.of("PENDING", "ACTIVE").contains(profile.project().status())) denied(ErrorCode.ACCESS_DENIED);
            if (!profile.sha256().equals(input.profile_sha256())) denied(ErrorCode.SCOPE_STALE);
            var prior = mapper.bindingOperation(values);
            if (!prior.isEmpty()) return replayBinding(single(prior), values);
            var currentBindings = mapper.currentBinding(values);
            if (currentBindings.size() > 1) mismatch();
            String currentBinding = currentBindings.isEmpty() ? null : currentBindings.get(0).bindingRevision();
            if (!Objects.equals(currentBinding, input.expected_binding_revision())) denied(ErrorCode.REVISION_CONFLICT);
            String decisionId = UUID.randomUUID().toString(), bindingRevision = UUID.randomUUID().toString();
            var now = Objects.requireNonNull(permissions.databaseTime());
            var basis = json.object(); basis.put("version_id", profile.project().profileRevision()); basis.put("content_sha256", profile.sha256());
            var evidence = json.object().arrayNode(); evidence.add(basis);
            var manifest = json.object(); manifest.put("schema_version", "1.0.0"); manifest.put("project_id", projectId);
            manifest.put("source_version", input.source_version()); manifest.put("binding_revision", bindingRevision);
            manifest.put("decision_id", decisionId); manifest.put("profile_revision", input.profile_revision());
            manifest.put("profile_sha256", input.profile_sha256()); manifest.put("applicability", input.applicability());
            manifest.put("reason", input.reason()); manifest.set("evidence_refs", evidence);
            manifest.put("actor_id", subject.user().user_id()); manifest.put("decided_at", time(now));
            values.put("decisionId", decisionId); values.put("bindingRevision", bindingRevision);
            values.put("profileRevision", input.profile_revision()); values.put("applicability", input.applicability());
            values.put("reason", input.reason()); values.put("evidenceRefs", json.canonical(evidence));
            values.put("decisionManifest", json.canonical(manifest)); values.put("expectedBindingRevision", currentBinding);
            values.put("now", now); values.put("schemaManifest", schema.manifestSha256());
            values.put("classification", identities.classification());
            one(mapper.insertApplicability(values));
            if (currentBinding != null) one(mapper.clearCurrentBinding(values));
            values.put("decisionSha256", DigestUtils.sha256Hex(json.canonical(manifest)));
            one(mapper.insertBindingVersion(values));
            one(mapper.insertBinding(values));
            values.put("governanceEpoch", admission.context().governance_epoch());
            var currentContext = advanceContext(admission, values);
            String operationId = UUID.randomUUID().toString(), requestId = UUID.randomUUID().toString();
            var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
            receipt.put("decision_id", decisionId); receipt.put("project_id", projectId);
            receipt.put("source_version", input.source_version()); receipt.put("binding_revision", bindingRevision);
            receipt.put("profile_revision", input.profile_revision()); receipt.put("applicability", input.applicability());
            receipt.put("actor_id", subject.user().user_id()); receipt.put("decided_at", time(now)); receipt.set("current_context", currentContext);
            var response = schema.envelope("BindingReceipt", receipt, requestId, null);
            values.put("operationId", operationId); values.put("response", json.canonical(response)); one(mapper.saveBindingOperation(values));
            var audit = json.object(); audit.set("binding", manifest); audit.put("previous_binding_revision", currentBinding);
            one(permissions.saveAudit(subject.user().user_id(), projectId, UUID.randomUUID().toString(),
                    "source.applicability_decided", json.canonical(audit), requestId, schema.manifestSha256(), identities.classification()));
            var event = json.object(); event.set("binding", manifest);
            one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), input.source_version(),
                    currentContext.required("governance_epoch").textValue(), "source.applicability_decided", json.canonical(event)));
            var stored = single(mapper.currentBinding(values));
            if (!bindingRevision.equals(stored.bindingRevision()) || !input.applicability().equals(stored.applicability())
                    || !schema.read(stored.decisionManifest()).equals(manifest)) mismatch();
            if (!replayBinding(single(mapper.bindingOperation(values)), values).equals(response)) mismatch();
            return response;
        }).get());
    }

    private JsonNode replay(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !operation.targetId().equals(values.get("sourceVersion"))
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        return schema.receipt(operation.responseRef());
    }

    private JsonNode replayProcessing(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(202).equals(operation.httpStatus())
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        var response = schema.processingReceipt(operation.responseRef()); var data = response.required("data");
        if (!operation.targetId().equals(data.required("attempt_id").textValue())
                || !values.get("sourceVersion").equals(data.required("source_version").textValue())
                || !values.get("projectId").equals(data.required("project_id").textValue())
                || !values.get("configRevision").equals(data.required("config_revision").textValue())
                || !"1".equals(response.required("resource_revision").textValue())) mismatch();
        values.put("attemptId", operation.targetId()); return response;
    }

    private JsonNode replayArtifactSubmission(SelectionOperationRow operation, Map<String,Object> values,
            String artifactId) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(201).equals(operation.httpStatus())
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        var response = schema.artifactSubmissionReceipt(operation.responseRef()); var data = response.required("data");
        if (!operation.targetId().equals(data.required("source_version").textValue())
                || !artifactId.equals(data.required("artifact_id").textValue())
                || !"1".equals(response.required("resource_revision").textValue())) mismatch();
        values.put("sourceVersion", operation.targetId()); return response;
    }

    private JsonNode reuseArtifactSubmission(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.ArtifactSubmissionRow stored, Map<String,Object> values,
            String artifactId, String actor, LocalDateTime now) throws IOException {
        if (!admission.context().tenant_id().equals(stored.tenantId()) || !artifactId.equals(stored.artifactId())
                || !Objects.equals(values.get("targetProjectId"), stored.projectId()) || !"PENDING".equals(stored.status())
                || !BigInteger.ONE.equals(stored.revision()) || stored.parseVersion() == null) mismatch();
        WireValues.uuid(stored.parseVersion());
        String operationId = UUID.randomUUID().toString(); values.put("operationId", operationId);
        values.put("sourceVersion", stored.targetSourceVersion()); values.put("classification", stored.classification());
        var receipt = json.object(); receipt.put("schema_version", "1.0.0"); receipt.put("operation_id", operationId);
        receipt.put("submission_id", stored.submissionId()); receipt.put("artifact_id", artifactId);
        receipt.put("source_id", stored.targetSourceId()); receipt.put("source_version", stored.targetSourceVersion());
        receipt.put("parse_version", stored.parseVersion()); receipt.put("rights_revision", stored.rightsRevision());
        receipt.put("sharing_decision", stored.sharingDecision()); receipt.put("status", "PENDING");
        receipt.put("approval_status", "PENDING"); receipt.put("lifecycle", "ACTIVE"); receipt.put("actor_id", actor);
        receipt.put("created_at", time(stored.createdAt())); receipt.set("current_context", currentContext(admission));
        var response = schema.envelope("ArtifactSubmissionReceipt", receipt, UUID.randomUUID().toString(), "1");
        values.put("response", json.canonical(response)); one(mapper.saveArtifactSubmissionOperation(values));
        var audit = json.object(); audit.put("artifact_id", artifactId); audit.put("submission_id", stored.submissionId());
        audit.put("source_version", stored.targetSourceVersion());
        one(permissions.saveAudit(actor, stored.projectId(), UUID.randomUUID().toString(), "artifact.submission_reused",
                json.canonical(audit), response.required("request_id").textValue(), schema.manifestSha256(), stored.classification()));
        if (!response.equals(replayArtifactSubmission(single(mapper.artifactSubmissionOperation(values)), values, artifactId))) mismatch();
        return response;
    }

    private JsonNode replayLifecycle(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !values.get("httpStatus").equals(operation.httpStatus())
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        var response = schema.lifecycleReceipt(operation.responseRef());
        if (!operation.targetId().equals(response.required("data").required("decision_id").textValue())) mismatch();
        return response;
    }

    private JsonNode replayBinding(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        return schema.bindingReceipt(operation.responseRef());
    }

    private JsonNode replayFolder(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !values.get("httpStatus").equals(operation.httpStatus())
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        var response = schema.folderReceipt(operation.responseRef());
        if (!operation.targetId().equals(response.required("data").required("folder").required("folder_id").textValue())) mismatch();
        return response;
    }

    private JsonNode replayOrganization(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !operation.targetId().equals(values.get("sourceVersion"))
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        return schema.organizationReceipt(operation.responseRef());
    }

    private JsonNode replayDeletionReceipt(SelectionOperationRow operation, Map<String,Object> values) throws IOException {
        if (!operation.requestSha256().equals(values.get("requestHash"))) denied(ErrorCode.IDEMPOTENCY_CONFLICT);
        if (!"COMPLETED".equals(operation.state()) || !Integer.valueOf(200).equals(operation.httpStatus())
                || !operation.targetId().equals(values.get("itemId"))
                || !operation.schemaManifestSha256().equals(schema.manifestSha256())) mismatch();
        return schema.deletionReceiptResponse(operation.responseRef());
    }

    private JsonNode review(SourceGovernanceProtocol.ReviewRow row) throws IOException {
        var result = json.object(); result.put("parse_version", row.parseVersion()); result.put("parse_status", row.parseStatus());
        nullable(result, "index_version", row.indexVersion()); nullable(result, "index_mode", row.indexMode());
        nullable(result, "index_status", row.indexStatus()); nullable(result, "expected_count", string(row.expectedCount()));
        nullable(result, "verified_count", string(row.verifiedCount())); nullable(result, "approval_revision", row.approvalRevision());
        result.put("approval_status", row.approvalStatus()); result.put("upload_status", row.uploadStatus());
        result.put("lifecycle", row.lifecycle()); result.put("gate_revision", row.gateRevision().toString());
        nullable(result, "current_decision", row.currentDecision()); nullable(result, "reviewed_manifest_sha256", row.reviewedManifestSha256());
        nullable(result, "decided_at", row.decidedAt() == null ? null : time(row.decidedAt()));
        var manifest = manifest(row); result.set("review_manifest", manifest);
        result.put("review_manifest_sha256", DigestUtils.sha256Hex(json.canonical(manifest)));
        schema.verify("Review", result); return result;
    }

    private JsonNode manifest(SourceGovernanceProtocol.ReviewRow row) {
        var value = json.object(); value.put("source_version", row.sourceVersion()); value.put("original_sha256", row.originalSha256());
        value.put("rights_revision", row.rightsRevision()); value.put("rights_status", row.rightsStatus());
        value.put("parse_version", row.parseVersion()); value.put("parse_status", row.parseStatus());
        nullable(value, "index_version", row.indexVersion()); nullable(value, "index_mode", row.indexMode());
        nullable(value, "index_status", row.indexStatus()); nullable(value, "expected_count", string(row.expectedCount()));
        nullable(value, "verified_count", string(row.verifiedCount())); value.put("upload_status", row.uploadStatus());
        value.put("approval_status", row.approvalStatus()); value.put("lifecycle", row.lifecycle());
        value.put("gate_revision", row.gateRevision().toString()); return value;
    }

    private void requireRead(EnterpriseAccess.Admission admission, SourceGovernanceProtocol.ReviewRow row) throws IOException {
        authority.require(admission, scope(row), "source.read", row.rightsPurpose());
        if (row.ownerProjectId() != null) authority.require(admission,
                new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "PROJECT", row.ownerProjectId()),
                "project.read", "PROJECT_READING");
    }

    private void requireReady(EnterpriseAccess.Admission admission, SourceGovernanceProtocol.ReviewRow row) throws IOException {
        sourceAuthority.require(admission, new SourceReadProtocol.ReadRequirement(row.sourceVersion(), row.rightsRevision(),
                row.ownerProjectId(), null, null, null, SourceReadProtocol.Use.REVIEW, row.rightsPurpose(),
                policy.environment(), policy.region(), null));
        if (!"RECEIVED".equals(row.uploadStatus()) || !"COMPLETE".equals(row.parseStatus())
                || !"READY".equals(row.indexStatus()) || row.expectedCount() == null || row.expectedCount().signum() <= 0
                || !row.expectedCount().equals(row.verifiedCount()) || !"ACTIVE".equals(row.lifecycle()))
            denied(ErrorCode.SOURCE_NOT_READY);
    }

    private void requireProcessingConfig(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.ProcessingInput input, SourceGovernanceProtocol.ProcessingConfigRow config) throws IOException {
        WireValues.uuid(config.configId()); WireValues.uuid(config.versionId());
        WireValues.hash(config.registeredSha256()); WireValues.hash(config.contentSha256());
        if (!admission.context().tenant_id().equals(config.tenantId())
                || !input.config_revision().equals(config.versionId())
                || !config.registeredSha256().equals(config.contentSha256())
                || !"CAPABILITY_VERSION".equals(config.entityKind()) || !"CAPABILITY".equals(config.configurationKind())
                || !"TOOL".equals(config.capabilityKind()) || !"PUBLISHED".equals(config.status())
                || config.projectId() != null && !input.project_id().equals(config.projectId()))
            denied(ErrorCode.OBJECT_UNAVAILABLE);
        processingDefinition.requirePublishedDefinition(config);
        if ("PARSE_AND_INDEX".equals(input.stage())) {
            String indexRevision=processingDefinition.indexConfiguration(config);
            if(indexRevision==null) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var indexValues=values(admission,null); indexValues.put("configRevision",indexRevision);
            var indexRows=mapper.processingConfig(indexValues); if(indexRows.size()!=1) denied(ErrorCode.OBJECT_UNAVAILABLE);
            var index=indexRows.get(0);
            if(!"PUBLISHED".equals(index.status()) || !"CAPABILITY_VERSION".equals(index.entityKind())
                    || !"TOOL".equals(index.capabilityKind()) || !index.contentSha256().equals(index.registeredSha256())
                    || index.projectId()!=null && !input.project_id().equals(index.projectId())) denied(ErrorCode.OBJECT_UNAVAILABLE);
            authority.require(admission,new PermissionProtocol.Scope(index.tenantId(),index.projectId(),"CONFIGURATION",index.configId()),
                "configuration.read","SOURCE_PROCESSING");
        }
        authority.require(admission, new PermissionProtocol.Scope(config.tenantId(), config.projectId(),
                "CONFIGURATION", config.configId()), "configuration.read", "SOURCE_PROCESSING");
    }

    private boolean usageTitleVisible(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.UsageRow row) throws IOException {
        String tenant = admission.context().tenant_id();
        PermissionProtocol.Scope target;
        String action;
        String purpose;
        switch (row.resourceKind()) {
            case "PROJECT" -> {
                target = new PermissionProtocol.Scope(tenant, row.projectId(), "PROJECT", row.resourceId());
                action = "project.read"; purpose = "PROJECT_READING";
            }
            case "DOCUMENT" -> {
                target = new PermissionProtocol.Scope(tenant, row.projectId(), "DOCUMENT", row.resourceId());
                action = "document.read"; purpose = "DOCUMENT_READING";
            }
            case "COLLECTION" -> {
                target = new PermissionProtocol.Scope(tenant, row.projectId(), row.projectId() == null ? "ENTERPRISE" : "PROJECT",
                        row.projectId() == null ? tenant : row.projectId());
                action = "collection.read"; purpose = "SOURCE_USAGE_READING";
            }
            case "VERSION" -> {
                target = new PermissionProtocol.Scope(tenant, row.projectId(), row.projectId() == null ? "ENTERPRISE" : "PROJECT",
                        row.projectId() == null ? tenant : row.projectId());
                action = "dependency.read"; purpose = "SOURCE_USAGE_READING";
            }
            default -> { return false; }
        }
        return authority.find(admission, target, action, purpose).isPresent();
    }

    private CurrentContent authorizeContent(ApplicationSessionIdentity.Verified subject, String context,
            String sourceVersion, SourceGovernanceProtocol.ContentQuery query) {
        return access.execute(subject, admission -> ThrowingSupplier.of(
                () -> authorizeContent(admission, context, sourceVersion, query)).get());
    }

    private CurrentContent authorizeContent(EnterpriseAccess.Admission admission, String context,
            String sourceVersion, SourceGovernanceProtocol.ContentQuery query) throws Exception {
        current(admission, context);
        var values = values(admission, sourceVersion); values.put("parseVersion", query.parseVersion());
        var rows = mapper.contentHead(values); if (rows.isEmpty()) denied(ErrorCode.SOURCE_MISSING);
        var head = single(rows); verifyContentHead(admission, head, sourceVersion, query);
        var grant = authority.require(admission, scope(head), "source.read", query.purpose());
        PermissionProtocol.GrantRow projectGrant = null;
        if (head.ownerProjectId() != null) projectGrant = authority.require(admission,
                new PermissionProtocol.Scope(head.tenantId(), head.ownerProjectId(), "PROJECT", head.ownerProjectId()),
                "project.read", "PROJECT_READING");
        if (!"SESSION".equals(head.ownership())) sourceAuthority.require(admission,
                new SourceReadProtocol.ReadRequirement(head.sourceVersion(), head.rightsRevision(), head.ownerProjectId(),
                    null, null, null, SourceReadProtocol.Use.REVIEW, head.rightsPurpose(), policy.environment(), policy.region(), null));
        var identity = new LinkedHashMap<String,Object>(); identity.put("schema_version", "1.0.0");
        identity.put("subject_id", admission.subject().user().user_id()); identity.put("session_id", admission.subject().sessionId());
        identity.put("identity_binding", admission.subject().binding().view()); identity.put("context_id", admission.contextId());
        identity.put("context", admission.context()); identity.put("watermarks", admission.watermarks());
        identity.put("head", head); identity.put("query", query); identity.put("source_grant", grant);
        identity.put("project_grant", projectGrant);
        String authorization = WireValues.sha256(ContentReadAuthorization.canonicalBytes(identity));
        return new CurrentContent(head, authorization);
    }

    private void requireCurrentContent(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.ContentQuery query, String authorization) {
        var current = authorizeContent(subject, context, sourceVersion, query);
        if (!authorization.equals(current.authorization())) denied(ErrorCode.SCOPE_STALE);
    }

    private LocalDateTime recordRead(ApplicationSessionIdentity.Verified subject, String context, String sourceVersion,
            SourceGovernanceProtocol.ContentQuery query, String authorization,
            com.fasterxml.jackson.databind.node.ObjectNode locator, String selection, String delivered, String requestId) {
        return access.execute(subject, admission -> ThrowingSupplier.of(() -> {
            var current = authorizeContent(admission, context, sourceVersion, query);
            if (!authorization.equals(current.authorization())) denied(ErrorCode.SCOPE_STALE);
            return saveRead(admission, current, locator, selection, delivered, requestId);
        }).get());
    }

    private LocalDateTime saveRead(EnterpriseAccess.Admission admission, CurrentContent current,
            com.fasterxml.jackson.databind.node.ObjectNode locator, String selection, String delivered, String requestId) throws IOException {
        String user = admission.subject().user().user_id(); var head = current.head();
        var values = values(admission, head.sourceVersion()); LocalDateTime now = Objects.requireNonNull(permissions.databaseTime());
        values.put("now", now); values.put("locator", json.canonical(locator)); values.put("schemaManifest", schema.manifestSha256());
        values.put("classification", head.classification());
        var previous = mapper.readHistory(values); if (previous.size() > 1) mismatch();
        String resultingRevision = previous.isEmpty() ? "1" : PermissionSchema.next(previous.get(0).revision().toString());
        values.put("resultingRevision", resultingRevision);
        if (previous.isEmpty()) one(mapper.insertReadHistory(values));
        else {
            values.put("expectedRevision", previous.get(0).revision());
            one(mapper.updateReadHistory(values));
        }
        var history = single(mapper.readHistory(values));
        if (!user.equals(history.userId()) || !head.sourceVersion().equals(history.sourceVersion()) || !now.equals(history.lastReadAt())
                || !"1.0.0".equals(history.schemaVersion()) || !schema.manifestSha256().equals(history.schemaManifestSha256())
                || !schema.read(history.locator()).equals(locator) || !resultingRevision.equals(history.revision().toString())) mismatch();
        WireValues.revision(history.revision().toString());
        var resource = json.object(); resource.put("source_id", head.sourceId()); resource.put("source_version", head.sourceVersion());
        resource.put("parse_version", head.parseVersion()); resource.put("representation", locator.required("representation").textValue());
        resource.put("authorization_sha256", current.authorization()); resource.put("selection_sha256", selection);
        resource.put("delivered_sha256", delivered); resource.set("locator", locator);
        one(permissions.saveAudit(user, head.ownerProjectId(), UUID.randomUUID().toString(), "source.content.read",
                json.canonical(resource), requestId, schema.manifestSha256(), head.classification()));
        return now;
    }

    private static void verifyContentHead(EnterpriseAccess.Admission admission, SourceGovernanceProtocol.ContentHeadRow head,
            String sourceVersion, SourceGovernanceProtocol.ContentQuery query) {
        if (!admission.context().tenant_id().equals(head.tenantId()) || !sourceVersion.equals(head.sourceVersion())
                || !query.parseVersion().equals(head.parseVersion()) || !query.purpose().equals(head.rightsPurpose())
                || !List.of("ENTERPRISE", "PROJECT", "SESSION").contains(head.ownership())
                || "ENTERPRISE".equals(head.ownership()) != (head.ownerProjectId() == null)
                || List.of("PURGE_PENDING", "PURGED").contains(head.lifecycle())
                || "DISABLED".equals(head.rightsStatus())) denied(ErrorCode.OBJECT_UNAVAILABLE);
        WireValues.uuid(head.sourceId()); WireValues.uuid(head.sourceVersion()); WireValues.uuid(head.parseVersion());
        WireValues.uuid(head.rightsRevision()); WireValues.hash(head.originalSha256()); WireValues.hash(head.contentSha256());
        WireValues.revision(head.sizeBytes().toString());
    }

    private JsonNode advanceContext(EnterpriseAccess.Admission admission, Map<String,Object> values) {
        String governance = PermissionSchema.next(admission.context().governance_epoch());
        String revision = PermissionSchema.next(admission.context().context_revision()); one(mapper.advanceGovernance(values));
        one(jdbc.update("""
                UPDATE platform_session_context SET context_revision=?,governance_epoch=?,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE context_id=? AND session_id=? AND user_id=? AND tenant_id=? AND context_revision=? AND auth_epoch=? AND governance_epoch=?
                """, revision, governance, admission.contextId(), admission.subject().sessionId(), admission.subject().user().user_id(),
                admission.context().tenant_id(), admission.context().context_revision(), admission.context().auth_epoch(), admission.context().governance_epoch()));
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id());
        current.put("context_revision", revision); current.put("auth_epoch", admission.context().auth_epoch());
        current.put("governance_epoch", governance); return current;
    }

    private JsonNode currentContext(EnterpriseAccess.Admission admission) {
        var current = json.object(); current.put("tenant_id", admission.context().tenant_id());
        current.put("context_revision", admission.context().context_revision());
        current.put("auth_epoch", admission.context().auth_epoch());
        current.put("governance_epoch", admission.context().governance_epoch()); return current;
    }

    private static String transition(SourceGovernanceProtocol.ReviewRow row, String decision) {
        return switch (decision) {
            case "APPROVE" -> { if (!"PENDING".equals(row.approvalStatus())) denied(ErrorCode.REVISION_CONFLICT); yield "APPROVED"; }
            case "RETURN" -> { if (!List.of("PENDING", "APPROVED").contains(row.approvalStatus())) denied(ErrorCode.REVISION_CONFLICT); yield "PENDING"; }
            case "DISABLE" -> { if (!List.of("PENDING", "APPROVED").contains(row.approvalStatus())) denied(ErrorCode.REVISION_CONFLICT); yield "DISABLED"; }
            default -> throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        };
    }

    private static String lifecycleTransition(String status, String decision) {
        return switch (decision) {
            case "DISABLE" -> { if (!"ACTIVE".equals(status)) denied(ErrorCode.ACTION_STALE); yield "DISABLED"; }
            case "RECYCLE" -> { if (!List.of("ACTIVE", "DISABLED").contains(status)) denied(ErrorCode.ACTION_STALE); yield "RECYCLED"; }
            case "RESTORE" -> { if (!"RECYCLED".equals(status)) denied(ErrorCode.ACTION_STALE); yield "ACTIVE"; }
            case "PURGE" -> { if (!"RECYCLED".equals(status)) denied(ErrorCode.ACTION_STALE); yield "PURGE_PENDING"; }
            default -> throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        };
    }

    private static void requireRetention(SourceGovernanceProtocol.RetentionPolicyRow policy,
            SourceGovernanceProtocol.LifecycleStateRow state, LocalDateTime now) {
        if (!"SOURCE".equals(policy.category()) || !"SOURCE_CREATED".equals(policy.startEvent())
                || !"APPROVED".equals(policy.status()) || policy.effectiveFrom().isAfter(now)
                || !"VERIFIED_DELETE".equals(policy.purgeMethod())
                || policy.retentionDuration().signum() <= 0 || policy.backupExpiry().signum() <= 0)
            denied(ErrorCode.RETENTION_UNCONFIGURED);
        if (state.sourceCreatedAt().plusSeconds(policy.retentionDuration().longValueExact()).isAfter(now))
            denied(ErrorCode.ACTION_STALE);
    }

    private void requireLifecycleAuthority(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.LifecycleStateRow row) throws IOException {
        var scope = scope(row);
        authority.require(admission, scope, "source.read", "SOURCE_LIFECYCLE");
        authority.require(admission, scope, "source.write", "SOURCE_LIFECYCLE");
    }

    private void requirePurgeAuthority(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.LifecycleStateRow row) throws IOException {
        authority.require(admission, scope(row), "source.purge", "SOURCE_PURGE");
    }

    private void requirePurgeAuthority(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.DeletionItemRow row) throws IOException {
        authority.require(admission, new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId()),
                "source.purge", "SOURCE_PURGE");
    }

    private void requireFolderAuthority(EnterpriseAccess.Admission admission, String projectId) throws IOException {
        permissionAuthority.requireProject(admission.context().tenant_id(), admission.subject().binding().view().mode(),
                projectId, "source.write", "SOURCE_ORGANIZATION",
                permissions.resourceGrants(admission.subject().user().user_id(), "source.write"));
    }

    private void requireOrganizationAuthority(EnterpriseAccess.Admission admission,
            SourceGovernanceProtocol.OrganizationRow row) throws IOException {
        var scope = new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
        authority.require(admission, scope, "source.read", "SOURCE_ORGANIZATION");
        authority.require(admission, scope, "source.write", "SOURCE_ORGANIZATION");
    }

    private void requireOrganizationFolder(Map<String,Object> values,
            SourceGovernanceProtocol.OrganizationRow source, String folderId) {
        if (folderId == null) return;
        values.put("ownerProjectId", source.ownerProjectId());
        var folders = mapper.folders(values);
        var target = folders.stream().filter(row -> folderId.equals(row.folderId())).toList();
        if (target.size() != 1 || !Objects.equals(source.ownerProjectId(), target.get(0).ownerProjectId())
                || "SESSION".equals(source.ownership())) denied(ErrorCode.ACCESS_DENIED);
    }

    private static void requireParent(List<SourceGovernanceProtocol.FolderRow> folders,
            String parentId, String ownerProjectId) {
        if (parentId == null) return;
        var matches = folders.stream().filter(row -> parentId.equals(row.folderId())).toList();
        if (matches.size() != 1 || !Objects.equals(ownerProjectId, matches.get(0).ownerProjectId()))
            denied(ErrorCode.OBJECT_UNAVAILABLE);
    }

    private static void requireAcyclic(List<SourceGovernanceProtocol.FolderRow> folders,
            String folderId, String parentId) {
        var parents = new HashMap<String,String>();
        for (var row : folders) parents.put(row.folderId(), row.parentId());
        var visited = new java.util.HashSet<String>(); String current = parentId;
        while (current != null) {
            if (!visited.add(current) || folderId.equals(current)) denied(ErrorCode.REVISION_CONFLICT);
            current = parents.get(current);
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode folderView(SourceGovernanceProtocol.FolderRow row) {
        var result = json.object(); result.put("folder_id", row.folderId()); nullable(result, "parent_id", row.parentId());
        nullable(result, "owner_project_id", row.ownerProjectId());
        result.put("ownership", row.ownerProjectId() == null ? "ENTERPRISE" : "PROJECT");
        result.put("category", row.category()); result.put("name", row.name()); result.put("revision", row.revision().toString());
        result.put("created_at", time(row.createdAt())); result.put("updated_at", time(row.updatedAt()));
        schema.verify("FolderView", result); return result;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode folderReceipt(String operationId, String decision,
            SourceGovernanceProtocol.FolderRow row) {
        var result = json.object(); result.put("schema_version", "1.0.0"); result.put("operation_id", operationId);
        result.put("decision", decision); result.set("folder", folderView(row)); return result;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode organizationReceipt(
            SourceGovernanceProtocol.OrganizationItemInput input, String operationId,
            SourceGovernanceProtocol.OrganizationRow row) throws IOException {
        var result = json.object(); result.put("item_id", input.item_id()); result.put("operation_id", operationId);
        result.put("status", "COMPLETED"); result.put("source_version", row.sourceVersion()); result.put("decision", input.decision());
        nullable(result, "folder_id", row.folderId()); result.set("labels", schema.read(row.labels()));
        nullable(result, "organization_revision", string(row.revision())); result.put("bookmarked", row.bookmarked());
        schema.verify("OrganizationReceiptItem", result); return result;
    }

    private void saveFolderSignals(ApplicationSessionIdentity.Verified subject,
            SourceGovernanceProtocol.FolderRow row, String eventType, String requestId) throws IOException {
        var resource = folderView(row);
        one(permissions.saveAudit(subject.user().user_id(), row.ownerProjectId(), UUID.randomUUID().toString(),
                eventType, json.canonical(resource), requestId, schema.manifestSha256(), row.classification()));
        one(permissions.saveEvent(subject.user().user_id(), UUID.randomUUID().toString(), row.folderId(),
                row.revision().toString(), eventType, json.canonical(resource)));
    }

    private static PermissionProtocol.Scope scope(SourceGovernanceProtocol.ReviewRow row) {
        return new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
    }
    private static PermissionProtocol.Scope scope(SourceGovernanceProtocol.CatalogueRow row) {
        return new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
    }
    private static PermissionProtocol.Scope scope(SourceGovernanceProtocol.ContentHeadRow row) {
        return new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
    }
    private static PermissionProtocol.Scope scope(SourceGovernanceProtocol.LifecycleStateRow row) {
        return new PermissionProtocol.Scope(row.tenantId(), row.ownerProjectId(), "SOURCE", row.sourceId());
    }
    private JsonNode catalogueItem(SourceGovernanceProtocol.CatalogueRow row) throws IOException {
        var item = json.object(); item.put("source_id", row.sourceId()); item.put("source_version", row.sourceVersion());
        nullable(item, "owner_project_id", row.ownerProjectId()); item.put("ownership", row.ownership());
        item.put("title", row.title()); item.put("classification", row.classification()); item.put("media_type", row.mediaType());
        item.put("size_bytes", row.sizeBytes().toString()); item.put("rights_revision", row.rightsRevision());
        item.put("rights_status", row.rightsStatus()); nullable(item, "folder_id", row.folderId());
        nullable(item, "category", row.category()); item.set("labels", schema.read(row.labels()));
        nullable(item, "organization_revision", string(row.organizationRevision())); item.put("bookmarked", row.bookmarked());
        nullable(item, "processing_status", row.processingStatus()); nullable(item, "approval_status", row.approvalStatus());
        nullable(item, "lifecycle", row.lifecycle()); item.put("created_at", time(row.createdAt()));
        schema.verify("CatalogueItem", item); return item;
    }
    private static Map<String,Object> values(EnterpriseAccess.Admission admission, String sourceVersion) {
        var values = new HashMap<String,Object>(); values.put("userId", admission.subject().user().user_id());
        values.put("sourceVersion", sourceVersion); return values;
    }
    private static void verifyHead(SourceGovernanceProtocol.ReviewRow expected, SourceGovernanceProtocol.ReviewRow actual) {
        if (!expected.tenantId().equals(actual.tenantId()) || !expected.sourceId().equals(actual.sourceId())
                || !expected.sourceVersion().equals(actual.sourceVersion()) || !Objects.equals(expected.ownerProjectId(), actual.ownerProjectId())
                || !expected.originalSha256().equals(actual.originalSha256()) || !expected.rightsRevision().equals(actual.rightsRevision())) mismatch();
    }
    private static void current(EnterpriseAccess.Admission admission, String context) {
        if (!admission.context().context_revision().equals(context)) denied(ErrorCode.CONTEXT_CHANGED);
    }
    private static String string(BigInteger value) { return value == null ? null : value.toString(); }
    private void activeMember(String userId, LocalDateTime now) {
        var member = single(permissions.targetMember(userId));
        if (!"ACTIVE".equals(member.status()) || member.validFrom() == null || member.validFrom().isAfter(now)
                || member.validTo() != null && !member.validTo().isAfter(now)) denied(ErrorCode.ACCESS_DENIED);
    }
    private static void nullable(com.fasterxml.jackson.databind.node.ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }
    private static String time(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC).toString(); }
    private static LocalDateTime utc(String value) { return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private static void one(int value) { if (value != 1) mismatch(); }
    private static <T> T single(List<T> values) { if (values.size() != 1) mismatch(); return values.get(0); }
    private static void denied(ErrorCode code) { throw new TenantAccessException(code); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private record CurrentContent(SourceGovernanceProtocol.ContentHeadRow head, String authorization) { }
    private record OriginalBytes(byte[] bytes, String fullSha256) {
        private OriginalBytes { bytes = Arrays.copyOf(bytes, bytes.length); WireValues.hash(fullSha256); }
        @Override public byte[] bytes() { return Arrays.copyOf(bytes, bytes.length); }
    }
}

package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.VersionResourceMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public final class VersionResourceAuthority {
    private static final List<String> DOMAINS = Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList();
    private final VersionResourceMapper mapper;
    private final ResourceReadAuthority resources;
    private final ArtifactRegistry registry;

    public VersionResourceAuthority(VersionResourceMapper mapper, ResourceReadAuthority resources, ArtifactRegistry registry) {
        this.mapper = mapper; this.resources = resources; this.registry = registry;
    }

    record VerifiedVersion(ArtifactRegistryProtocol.VersionRow version, VersionResourceProtocol.Domain domain,
            VersionResourceProtocol.OwnerRow owner, PermissionProtocol.GrantRow resourceGrant,
            PermissionProtocol.GrantRow versionProjectGrant,
            ArtifactRegistryProtocol.Registration artifactRegistration) { }

    Map<String, VerifiedVersion> require(EnterpriseAccess.Admission admission, DependencyLineage.VerifiedLineage lineage,
            String purpose, VersionResourceProtocol.ReadContext context,
            Map<String, ArtifactRegistryProtocol.ReadRequirement> artifactRequirements) throws IOException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.VersionResourceMapper.owners");
        Objects.requireNonNull(admission);Objects.requireNonNull(lineage);Objects.requireNonNull(context);Objects.requireNonNull(artifactRequirements);
        if(purpose==null||!purpose.matches("[A-Z][A-Z0-9_]{0,127}"))throw new IllegalArgumentException("派生内容用途无效");
        var purposes=new LinkedHashMap<String,String>();lineage.versions().keySet().forEach(id->purposes.put(id,purpose));
        return requireWithPurposes(admission,lineage,purposes,context,artifactRequirements);
    }

    /** Only typed, approved business readers may supply an exhaustive per-version purpose plan.
     * This method never derives purposes from stored rights and does not omit any ancestor. */
    Map<String, VerifiedVersion> requireWithPurposes(EnterpriseAccess.Admission admission, DependencyLineage.VerifiedLineage lineage,
            Map<String,String> purposes, VersionResourceProtocol.ReadContext context,
            Map<String,ArtifactRegistryProtocol.ReadRequirement> artifactRequirements)throws IOException {
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.VersionResourceMapper.owners");
        Objects.requireNonNull(admission);Objects.requireNonNull(lineage);Objects.requireNonNull(context);Objects.requireNonNull(artifactRequirements);
        if(purposes==null||!purposes.keySet().equals(lineage.versions().keySet()))mismatch();
        for(String purpose:purposes.values())if(purpose==null||!purpose.matches("[A-Z][A-Z0-9_]{0,127}"))mismatch();
        TenantScope.requireSqlAccess("com.ruoyi.medical.persistence.mapper.VersionResourceMapper.owners");
        Objects.requireNonNull(admission); Objects.requireNonNull(lineage); Objects.requireNonNull(context);
        Objects.requireNonNull(artifactRequirements);
        if (!TenantScope.requireCurrent().tenantId().equals(admission.context().tenant_id())) denied();
        var verified = new LinkedHashMap<String, VerifiedVersion>(); var actualArtifacts = new TreeSet<String>();
        var domainOwners = mapper.ownersAcrossDomains(lineage.versions().keySet().stream().sorted().toList(), DOMAINS);
        for (var row : domainOwners) if (!lineage.versions().containsKey(row.owner().versionId())) mismatch();
        var ownersByVersion = domainOwners.stream().collect(java.util.stream.Collectors.groupingBy(row -> row.owner().versionId()));
        for (var version : lineage.versions().values()) {
            String purpose=purposes.get(version.id());
            // 全部领域在同一次企业查询中核查，继续要求版本具有唯一实际身份。
            var actualOwners = ownersByVersion.getOrDefault(version.id(), List.of());
            if (actualOwners.stream().map(VersionResourceProtocol.DomainOwnerRow::domain).distinct().count() > 1) mismatch();
            if (actualOwners.size() != 1) denied();
            var actualDomain = VersionResourceProtocol.Domain.valueOf(actualOwners.get(0).domain());
            var owner = actualOwners.get(0).owner();
            requireOwner(admission, version, owner, context);
            var scope = new PermissionProtocol.Scope(owner.tenantId(), owner.resourceProjectId(), owner.resourceKind(), owner.resourceId());
            var grant = resources.require(admission, scope, action(actualDomain, owner.resourceKind()), purpose);
            PermissionProtocol.GrantRow versionProjectGrant = null;
            if (owner.versionProjectId() != null && !owner.versionProjectId().equals(owner.resourceProjectId())) {
                versionProjectGrant = resources.require(admission,
                    new PermissionProtocol.Scope(owner.tenantId(), owner.versionProjectId(), "PROJECT", owner.versionProjectId()), "project.read", purpose);
            }
            ArtifactRegistryProtocol.Registration artifact = null;
            if (actualDomain == VersionResourceProtocol.Domain.ARTIFACT) {
                actualArtifacts.add(version.id()); var required = artifactRequirements.get(version.id());
                if (required == null || !purpose.equals(required.purpose())) mismatch();
                // 每个上游制品继续使用领域消费者固定的Schema和主资源要求。
                artifact = registry.requireRegistration(admission, version.id(), required);
                for (var upstream : artifact.lineage().versions().values()) {
                    if (!upstream.equals(lineage.versions().get(upstream.id()))) mismatch();
                }
            }
            verified.put(version.id(), new VerifiedVersion(version, actualDomain, owner, grant, versionProjectGrant, artifact));
        }
        if (!actualArtifacts.equals(artifactRequirements.keySet())) mismatch();
        return Map.copyOf(verified);
    }

    private static void requireOwner(EnterpriseAccess.Admission admission, ArtifactRegistryProtocol.VersionRow version,
            VersionResourceProtocol.OwnerRow owner, VersionResourceProtocol.ReadContext context) {
        if (!admission.context().tenant_id().equals(owner.tenantId()) || !version.id().equals(owner.versionId())
                || !Objects.equals(version.projectId(), owner.versionProjectId())) mismatch();
        WireValues.uuid(owner.resourceId());
        if (owner.resourceProjectId() != null) WireValues.uuid(owner.resourceProjectId());
        // 缺少独立摘要列的决定保留原版本登记，内容解释仍由领域服务核验。
        if (owner.contentSha256() != null) {
            WireValues.hash(owner.contentSha256());
            if (!version.contentSha256().equals(owner.contentSha256())) mismatch();
        }
        if (owner.sourceVersion() != null) WireValues.uuid(owner.sourceVersion());
        if (owner.conversationId() != null) {
            if (!owner.conversationId().equals(context.conversationId())
                    || !admission.actorId().equals(owner.conversationOwnerId())) denied();
            boolean active = "ACTIVE".equals(owner.conversationStatus());
            boolean history = context.use() == SourceReadProtocol.Use.REVIEW || context.use() == SourceReadProtocol.Use.HISTORY;
            if (!active && !(history && "ARCHIVED".equals(owner.conversationStatus()))) denied();
        } else if (owner.conversationOwnerId() != null || owner.conversationStatus() != null) mismatch();
    }

    private static String action(VersionResourceProtocol.Domain domain, String kind) {
        return switch (domain) {
            case COLLECTION_VERSION -> "collection.read";
            case SHARED_FACT -> "fact.read";
            case DEPENDENCY_ACCESS_SET -> "dependency.read";
            case CONTEXT_MANIFEST -> "context.read";
            case ROLE_DEFINITION -> "role.read";
            case MANAGEMENT_RELEASE -> "management.read";
            case IMPACT_MANIFEST -> "impact.read";
            case TASK_SUMMARY -> "summary.read";
            case CONVERSATION_MESSAGE -> "conversation.read";
            default -> switch (kind) {
                case "SOURCE" -> "source.read";
                case "PROJECT" -> "project.read";
                case "TASK" -> "task.read";
                case "DOCUMENT" -> "document.read";
                case "ARTIFACT" -> "artifact.read";
                case "CONFIGURATION" -> "configuration.read";
                case "TEMPLATE" -> "template.read";
                case "RULE" -> "rule.read";
                case "BUDGET" -> "budget.read";
                case "EVALUATION" -> "evaluation.read";
                default -> throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            };
        };
    }

    private static void denied() { throw new TenantAccessException(ErrorCode.ACCESS_DENIED); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

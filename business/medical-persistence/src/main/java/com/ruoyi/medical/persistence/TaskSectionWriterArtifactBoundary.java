package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import java.util.Set;

/** N04 control inputs and envelopes are dedicated-only. Immutable ownership also
 * blocks prepared objects and relabelled registrations, without hiding public output. */
public final class TaskSectionWriterArtifactBoundary {
    private TaskSectionWriterArtifactBoundary() { }
    private static final Set<String> KINDS = Set.of("TASK_SECTION_WRITER_INPUT", "TASK_SECTION_WRITER_EXACT_INPUT");
    private static final Set<String> SCHEMAS = Set.of(TaskSectionWriterSchema.INPUT_REF,
            TaskSectionWriterInvocationSchema.EXACT_INPUT_REF, TaskSectionWriterDispatchJournal.ENVELOPE_REF);

    static void requireGenericAllowed(ArtifactRegistryProtocol.ArtifactRow artifact,
            ArtifactRegistryProtocol.BindingRow binding, List<String> inputOwners, List<String> exactOwners) {
        if (artifact == null || binding == null || artifact.artifactKind() == null
                || inputOwners == null || exactOwners == null || !inputOwners.isEmpty() || !exactOwners.isEmpty()
                || KINDS.contains(artifact.artifactKind())
                || binding.schemaRef() != null && SCHEMAS.contains(binding.schemaRef()))
            throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
    }
}

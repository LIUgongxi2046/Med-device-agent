package com.ruoyi.medical.persistence;

import java.util.Set;

/** Additive pre-RN01 proposed-field contract. Public structures are data, never authority. */
public final class TaskFieldTargetProtocol {
    private TaskFieldTargetProtocol() { }
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String SCHEMA_REF = "urn:medical:task-field-target:1.0.0";
    public static final String ARTIFACT_KIND = "STRICT_FIELD_TARGET";
    public static final String MAPPING_WRITE = "task.field_mapping.write";
    public static final String TARGET_PRODUCE = "task.field_target.produce";
    public static final String PREPARATION_PURPOSE = "STRICT_FIELD_PREPARATION";
    public static final String INPUT_READ = "task.field_target_input.read";
    public static final String INPUT_WRITE = "task.field_target_input.write";
    public static final String INPUT_APPROVE = "task.field_target_input.approve";
    public static final String INPUT_PURPOSE = "TASK_FIELD_TARGET_INPUT";
    public static final Set<String> SOURCE_EXACT_KINDS = Set.of("MODEL", "PTR_NUMBER",
        "DOCUMENT_NUMBER", "STANDARD_NUMBER", "STANDARD_YEAR");
    public enum MappingStatus { PROPOSED }
    public enum InventoryState { MISSING_MAPPING, MISSING_SOURCE, UNRESOLVED_CONTEXT, UNKNOWN_CONDITION, UNSUPPORTED_TYPE, UNASSIGNED_SECTION, NOT_APPLICABLE, MAPPED }
    public enum ProductionState { NOT_STARTED, INTENT_PERSISTED, TRANSFER_UNCERTAIN, REGISTERED, WAITING_INPUT }
    public enum InputStatus { DRAFT, APPROVED, DISABLED }
    public enum InputDecision { APPROVE, DISABLE }
    public enum ContextState { KNOWN_GLOBAL, KNOWN_SCOPED, UNRESOLVED }
    public enum Applicability { APPLICABLE, UNKNOWN }
    public record VersionRef(String versionId, String contentSha256) { }
    public record SourceExactRef(String sourceVersion, String fieldRevision, String manifestSha256,
            int ordinal, String fieldKind) { }
}

package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.DatasetProtocol;
import java.util.List;

/**
 * Additive, computation-only inputs. None of these public records is an authority token.
 * A host must independently verify template completeness, assignment ownership, live source
 * access, read receipts, parser publication and approved rule authority before persisting a
 * business result. No target artifact, approval, receipt or applicability proof is created here.
 */
public final class StrictFieldComparisonProtocol {
    private StrictFieldComparisonProtocol() { }
    public enum Applicability { APPLICABLE, UNKNOWN }
    public enum ContextState { KNOWN_SCOPED, KNOWN_GLOBAL, UNRESOLVED }
    public enum Outcome { COMPUTED, UNSUPPORTED }
    public enum Reason {
        EXACT_EQUAL, VALUE_MISMATCH, MODEL_SET_MISMATCH, CONDITION_SET_MISMATCH,
        UNIT_MISMATCH, PRECISION_MISMATCH, SOURCE_IDENTITY_MISMATCH, EVIDENCE_SCOPE_MISMATCH,
        RAW_LEXICAL_EVIDENCE_MISMATCH, DECIMAL_LEXICAL_MISMATCH,
        UNKNOWN_APPLICABILITY, UNRESOLVED_MODEL_CONTEXT, UNRESOLVED_CONDITION_CONTEXT, SOURCE_OBSERVATION_MISSING, SOURCE_EXPECTATION_MISSING,
        CANDIDATE_PROVENANCE_MISSING, SOURCE_PROVENANCE_MISSING, ORIGINAL_EVIDENCE_MISSING,
        READ_RECEIPTS_MISSING, SOURCE_EXACT_REFERENCE_MISSING, PARSED_DECIMAL_EVIDENCE_MISSING,
        RAW_VALUE_MISSING, UNSUPPORTED_OPERATOR, UNSUPPORTED_VALUE_KIND,
        UNSUPPORTED_PROVENANCE, UNSUPPORTED_PARSER, UNSUPPORTED_DECIMAL_LEXICAL,
        UNSUPPORTED_VALUE_COMPONENTS
    }
    public record Identity(String tenantId, String projectId, String taskId, String taskRevision,
            String planRevision, String templateVersion, String templateSha256,
            String assignmentId, String assignmentSha256) { }
    public record Section(String sectionId, List<String> requirementNodeIds) {
        public Section { requirementNodeIds = List.copyOf(requirementNodeIds); }
    }
    /** A fixed source-exact manifest entry, not an alias or a normalized search result. */
    public record SourceExactReference(String fieldRevision, String fieldManifestSha256, int ordinal,
            String fieldKind) { }
    /** Caller-supplied immutable parse references, never proof that a parser actually ran. */
    public record ParsedDecimalEvidence(String evidenceRevision, String evidenceSha256,
            String parserRevision, String parserSha256, String parserContract,
            String rawTextSha256, String decimalValue, long precision) { }
    public record SourceIdentity(String kind, DatasetProtocol.EvidenceRef evidenceRef,
            SourceExactReference sourceExact, ParsedDecimalEvidence parsedDecimal) { }
    /** Intervals are code-point offsets in the original referenced chunk; no normalization. */
    public record Provenance(SourceIdentity source, List<String> readReceiptIds,
            int startCodepoint, int endCodepoint, String rawTextSha256) {
        public Provenance { readReceiptIds = List.copyOf(readReceiptIds); }
    }
    /** fieldId is the target occurrence identity; fieldDefinitionId is the template identity. */
    public record TargetEntry(String entryId, String fieldId, String fieldDefinitionId,
            String sectionId, String nodeId, String rowId, List<String> modelIds,
            List<String> conditionIds, ContextState modelContext, ContextState conditionContext, Applicability applicability,
            ArtifactProtocol.FieldValue candidateValue, Provenance provenance) {
        public TargetEntry { modelIds = List.copyOf(modelIds); conditionIds = List.copyOf(conditionIds); }
    }
    public record Target(String schemaVersion, String assessment, Identity identity,
            List<Section> sections, List<TargetEntry> entries) {
        public Target { sections = List.copyOf(sections); entries = List.copyOf(entries); }
    }
    public record Rule(String ruleRevision, String ruleId, String ruleSha256, String operator) { }
    /** Independently supplied, never populated from the candidate by the engine. */
    public record SourceObservation(List<String> modelIds, List<String> conditionIds,
            ContextState modelContext, ContextState conditionContext, Applicability applicability, ArtifactProtocol.FieldValue value, Provenance provenance) {
        public SourceObservation { modelIds = List.copyOf(modelIds); conditionIds = List.copyOf(conditionIds); }
    }
    public record EntryRequest(String entryId, String targetEntrySha256, Rule rule,
            SourceIdentity expectedSource, SourceObservation sourceObservation) { }
    public record Request(String schemaVersion, String targetSha256, Identity expectedIdentity,
            List<EntryRequest> entries) {
        public Request { entries = List.copyOf(entries); }
    }
    /** Unsupported never maps to a business success or to NOT_APPLICABLE. */
    public record EntryResult(String entryId, String targetEntrySha256, String fieldId,
            String fieldDefinitionId, String sectionId, String nodeId, String rowId,
            Rule rule, Outcome outcome, ArtifactProtocol.ComparisonResult result, Reason reason,
            ArtifactProtocol.FieldComparison comparison) { }
    /** Coverage describes only supplied target entries, NOT template/business completeness. */
    public record Result(String schemaVersion, String assessment, String coverage,
            String targetSha256, String requestSha256, String schemaSha256, Identity identity,
            List<EntryResult> entries) {
        public Result { entries = List.copyOf(entries); }
    }
}

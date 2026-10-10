package com.ruoyi.medical.contracts;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import static com.ruoyi.medical.contracts.TypedSchemaBindings.definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.Definition;
import com.ruoyi.medical.contracts.TypedSchemaBindings.ScalarWire;

public final class ArtifactBindings {
    private ArtifactBindings() { }
    public static final Definition<ArtifactProtocol.ProfessionalArtifact> ARTIFACT = definition("artifacts", null, ArtifactProtocol.ProfessionalArtifact.class);
    public static final Definition<String> ID = definition("artifacts", "Id", String.class, ScalarWire.UUID);
    public static final Definition<String> NULLABLE_ID = definition("artifacts", "NullableId", String.class, ScalarWire.NULLABLE_UUID);
    public static final Definition<List<String>> IDS = definition("artifacts", "Ids", new TypeReference<List<String>>() { }, ScalarWire.UUIDS);
    public static final Definition<String> TEXT = definition("artifacts", "Text", String.class);
    public static final Definition<String> NULLABLE_TEXT = definition("artifacts", "NullableText", String.class);
    public static final Definition<String> HASH = definition("artifacts", "Hash", String.class);
    public static final Definition<String> DECIMAL = definition("artifacts", "Decimal", String.class, ScalarWire.MEASUREMENT_DECIMAL);
    public static final Definition<String> KEY = definition("artifacts", "Key", String.class);
    public static final Definition<List<String>> KEYS = definition("artifacts", "Keys", new TypeReference<List<String>>() { }, ScalarWire.NONE);
    public static final Definition<DatasetProtocol.EvidenceRef> EVIDENCE_REF = definition("artifacts", "EvidenceRef", DatasetProtocol.EvidenceRef.class);
    public static final Definition<List<DatasetProtocol.EvidenceRef>> EVIDENCE_REFS = definition("artifacts", "EvidenceRefs", new TypeReference<List<DatasetProtocol.EvidenceRef>>() { }, ScalarWire.NONE);
    public static final Definition<ArtifactProtocol.ObjectRef> OBJECT_REF = definition("artifacts", "ObjectRef", ArtifactProtocol.ObjectRef.class);
    public static final Definition<ArtifactProtocol.ArtifactOutput> OUTPUT = definition("artifacts", "ArtifactOutput", ArtifactProtocol.ArtifactOutput.class);
    public static final Definition<ArtifactProtocol.PlanOutput> PLAN_OUTPUT = definition("artifacts", "PlanOutput", ArtifactProtocol.PlanOutput.class);
    public static final Definition<ArtifactProtocol.EvidenceOutput> EVIDENCE_OUTPUT = definition("artifacts", "EvidenceOutput", ArtifactProtocol.EvidenceOutput.class);
    public static final Definition<ArtifactProtocol.FieldOutput> FIELD_OUTPUT = definition("artifacts", "FieldOutput", ArtifactProtocol.FieldOutput.class);
    public static final Definition<ArtifactProtocol.SectionOutput> SECTION_OUTPUT = definition("artifacts", "SectionOutput", ArtifactProtocol.SectionOutput.class);
    public static final Definition<ArtifactProtocol.ClaimOutput> CLAIM_OUTPUT = definition("artifacts", "ClaimOutput", ArtifactProtocol.ClaimOutput.class);
    public static final Definition<ArtifactProtocol.ReviewOutput> REVIEW_OUTPUT = definition("artifacts", "ReviewOutput", ArtifactProtocol.ReviewOutput.class);
    public static final Definition<ArtifactProtocol.AssemblyOutput> ASSEMBLY_OUTPUT = definition("artifacts", "AssemblyOutput", ArtifactProtocol.AssemblyOutput.class);
    public static final Definition<ArtifactProtocol.HandoffOutput> HANDOFF_OUTPUT = definition("artifacts", "HandoffOutput", ArtifactProtocol.HandoffOutput.class);
    public static final Definition<ArtifactProtocol.PlanCandidate> PLAN_CANDIDATE = definition("artifacts", "PlanCandidate", ArtifactProtocol.PlanCandidate.class);
    public static final Definition<ArtifactProtocol.PlanSection> PLAN_SECTION = definition("artifacts", "PlanSection", ArtifactProtocol.PlanSection.class);
    public static final Definition<ArtifactProtocol.ResearchQuestion> RESEARCH_QUESTION = definition("artifacts", "ResearchQuestion", ArtifactProtocol.ResearchQuestion.class);
    public static final Definition<ArtifactProtocol.EvidenceLink> EVIDENCE_LINK = definition("artifacts", "EvidenceLink", ArtifactProtocol.EvidenceLink.class);
    public static final Definition<ArtifactProtocol.EvidenceBundle> EVIDENCE_BUNDLE = definition("artifacts", "EvidenceBundle", ArtifactProtocol.EvidenceBundle.class);
    public static final Definition<ArtifactProtocol.QuestionCoverage> QUESTION_COVERAGE = definition("artifacts", "QuestionCoverage", ArtifactProtocol.QuestionCoverage.class);
    public static final Definition<ArtifactProtocol.FieldValue> FIELD_VALUE = definition("artifacts", "FieldValue", ArtifactProtocol.FieldValue.class);
    public static final Definition<ArtifactProtocol.FieldCheckResult> FIELD_CHECK_RESULT = definition("artifacts", "FieldCheckResult", ArtifactProtocol.FieldCheckResult.class);
    public static final Definition<ArtifactProtocol.FieldComparison> FIELD_COMPARISON = definition("artifacts", "FieldComparison", ArtifactProtocol.FieldComparison.class);
    public static final Definition<ArtifactProtocol.MethodCoverage> METHOD_COVERAGE = definition("artifacts", "MethodCoverage", ArtifactProtocol.MethodCoverage.class);
    public static final Definition<ArtifactProtocol.TextAnchor> TEXT_ANCHOR = definition("artifacts", "TextAnchor", ArtifactProtocol.TextAnchor.class);
    public static final Definition<ArtifactProtocol.StructuredAnchor> STRUCTURED_ANCHOR = definition("artifacts", "StructuredAnchor", ArtifactProtocol.StructuredAnchor.class);
    public static final Definition<ArtifactProtocol.Anchor> ANCHOR = definition("artifacts", "Anchor", ArtifactProtocol.Anchor.class);
    public static final Definition<ArtifactProtocol.ClaimRecord> CLAIM_RECORD = definition("artifacts", "ClaimRecord", ArtifactProtocol.ClaimRecord.class);
    public static final Definition<ArtifactProtocol.NodeManifestItem> NODE_MANIFEST_ITEM = definition("artifacts", "NodeManifestItem", ArtifactProtocol.NodeManifestItem.class);
    public static final Definition<ArtifactProtocol.FieldAssignment> FIELD_ASSIGNMENT = definition("artifacts", "FieldAssignment", ArtifactProtocol.FieldAssignment.class);
    public static final Definition<ArtifactProtocol.AttachmentBinding> ATTACHMENT_BINDING = definition("artifacts", "AttachmentBinding", ArtifactProtocol.AttachmentBinding.class);
    public static final Definition<ArtifactProtocol.IssueProposal> ISSUE_PROPOSAL = definition("artifacts", "IssueProposal", ArtifactProtocol.IssueProposal.class);
    public static final Definition<ArtifactProtocol.SectionDraft> SECTION_DRAFT = definition("artifacts", "SectionDraft", ArtifactProtocol.SectionDraft.class);
    public static final Definition<ArtifactProtocol.SectionCandidate> SECTION_CANDIDATE = definition("artifacts", "SectionCandidate", ArtifactProtocol.SectionCandidate.class);
    public static final Definition<ArtifactProtocol.ClaimCheck> CLAIM_CHECK = definition("artifacts", "ClaimCheck", ArtifactProtocol.ClaimCheck.class);
    public static final Definition<ArtifactProtocol.RuleCheck> RULE_CHECK = definition("artifacts", "RuleCheck", ArtifactProtocol.RuleCheck.class);
    public static final Definition<ArtifactProtocol.ReviewFindings> REVIEW_FINDINGS = definition("artifacts", "ReviewFindings", ArtifactProtocol.ReviewFindings.class);
    public static final Definition<ArtifactProtocol.SectionVersion> SECTION_VERSION = definition("artifacts", "SectionVersion", ArtifactProtocol.SectionVersion.class);
    public static final Definition<ArtifactProtocol.AssemblyManifest> ASSEMBLY_MANIFEST = definition("artifacts", "AssemblyManifest", ArtifactProtocol.AssemblyManifest.class);
    public static final Definition<ArtifactProtocol.FailedAction> FAILED_ACTION = definition("artifacts", "FailedAction", ArtifactProtocol.FailedAction.class);
    public static final Definition<ArtifactProtocol.RemainingLimits> REMAINING_LIMITS = definition("artifacts", "RemainingLimits", ArtifactProtocol.RemainingLimits.class);
    public static final Definition<ArtifactProtocol.HandoffRecord> HANDOFF_RECORD = definition("artifacts", "HandoffRecord", ArtifactProtocol.HandoffRecord.class);
    public static List<Definition<?>> definitions() {
        return List.of(ID, NULLABLE_ID, IDS, TEXT, NULLABLE_TEXT, HASH, DECIMAL, KEY, KEYS, EVIDENCE_REF, EVIDENCE_REFS,
            OBJECT_REF, OUTPUT, PLAN_OUTPUT, EVIDENCE_OUTPUT, FIELD_OUTPUT, SECTION_OUTPUT, CLAIM_OUTPUT, REVIEW_OUTPUT, ASSEMBLY_OUTPUT, HANDOFF_OUTPUT,
            PLAN_CANDIDATE, PLAN_SECTION, RESEARCH_QUESTION, EVIDENCE_LINK, EVIDENCE_BUNDLE, QUESTION_COVERAGE, FIELD_VALUE, FIELD_CHECK_RESULT,
            FIELD_COMPARISON, METHOD_COVERAGE, TEXT_ANCHOR, STRUCTURED_ANCHOR, ANCHOR, CLAIM_RECORD, NODE_MANIFEST_ITEM, FIELD_ASSIGNMENT,
            ATTACHMENT_BINDING, ISSUE_PROPOSAL, SECTION_DRAFT, SECTION_CANDIDATE, CLAIM_CHECK, RULE_CHECK, REVIEW_FINDINGS, SECTION_VERSION,
            ASSEMBLY_MANIFEST, FAILED_ACTION, REMAINING_LIMITS, HANDOFF_RECORD);
    }
}

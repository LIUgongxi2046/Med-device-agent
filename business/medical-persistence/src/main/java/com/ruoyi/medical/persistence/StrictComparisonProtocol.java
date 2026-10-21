package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.DatasetProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.util.Objects;

/** Separate from Boolean RuleVersion and from the 096 TEXT-only mapping family. */
public final class StrictComparisonProtocol {
    private StrictComparisonProtocol() { }
    public record Scope(String taskId, String taskRevision, String planRevision, String templateVersion, String scopeSnapshot) {
        public Scope { WireValues.uuid(taskId); WireValues.uuid(taskRevision); WireValues.uuid(planRevision); WireValues.uuid(templateVersion); WireValues.uuid(scopeSnapshot); }
    }
    public record Author(String schemaVersion, Scope scope, StrictComparisonPolicyDefinition.Definition definition, String reason) {
        public Author { version(schemaVersion); Objects.requireNonNull(scope); Objects.requireNonNull(definition); StrictComparisonProtocol.reason(reason); }
    }
    public enum Decision { APPROVE, REJECT, DISABLE }
    public record Decide(String schemaVersion, String expectedRevision, String definitionSha256,
            String baselineSha256, Decision decision, String reason) {
        public Decide { version(schemaVersion); WireValues.revision(expectedRevision); WireValues.hash(definitionSha256); WireValues.hash(baselineSha256); Objects.requireNonNull(decision); StrictComparisonProtocol.reason(reason); }
    }
    public record Span(int startCodepoint, int endCodepoint) {
        public Span { if (startCodepoint < 0 || endCodepoint <= startCodepoint) throw new IllegalArgumentException("SPAN_INVALID"); }
    }
    public enum UnitBasis { SOURCE_SPAN, UNITLESS, UNKNOWN }
    public record UnitSource(UnitBasis basis, Span span) {
        public UnitSource { Objects.requireNonNull(basis); if ((basis == UnitBasis.SOURCE_SPAN) != (span != null)) throw new IllegalArgumentException("UNIT_SPAN_INVALID"); }
    }
    public record Parse(String schemaVersion, Scope scope, DatasetProtocol.EvidenceRef evidence,
            String rightsRevision, String purpose, Span numericSpan, UnitSource unit,
            StrictComparisonPolicyDefinition.Version parser, String reason) {
        public Parse { if(!java.util.Set.of("1.0.0","2.0.0").contains(schemaVersion))throw new IllegalArgumentException("SCHEMA_UNSUPPORTED"); Objects.requireNonNull(scope); Objects.requireNonNull(evidence); if(!schemaVersion.equals(evidence.schemaVersion()))throw new IllegalArgumentException("EVIDENCE_CONTRACT_MISMATCH"); WireValues.uuid(rightsRevision);
            if (purpose == null || !purpose.matches("[A-Z][A-Z0-9_]{0,127}")) throw new IllegalArgumentException("PURPOSE_INVALID");
            Objects.requireNonNull(numericSpan); Objects.requireNonNull(unit); Objects.requireNonNull(parser); StrictComparisonProtocol.reason(reason); }
    }
    private static void reason(String text) { if (text == null || text.isBlank() || text.length() > 2000) throw new IllegalArgumentException("REASON_REQUIRED"); }
    private static void version(String version) { if (!"1.0.0".equals(version)) throw new IllegalArgumentException("SCHEMA_UNSUPPORTED"); }
}

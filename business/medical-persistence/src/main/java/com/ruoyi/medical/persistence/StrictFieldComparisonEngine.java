package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ArtifactProtocol;
import com.ruoyi.medical.contracts.DatasetProtocol;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import static com.ruoyi.medical.contracts.ArtifactProtocol.ComparisonResult.*;
import static com.ruoyi.medical.persistence.StrictFieldComparisonProtocol.*;

/**
 * Bounded deterministic calculation over explicitly supplied values. No database, provider,
 * bean, source reader, grant, approval or artifact writer. MATCH means only that the supplied
 * values compare equal under this local algorithm, never that either value is a verified fact.
 */
public final class StrictFieldComparisonEngine {
    /** Local to explicitly pinned parse evidence, NOT a global FieldValue interpretation. */
    public static final String DECIMAL_PARSER_CONTRACT = "PLAIN_DECIMAL_SCALE_V1";
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");
    private final StrictFieldComparisonSchema schema;

    public record Limits(int maximumEntries, int maximumSections, int maximumContextIds,
            int maximumInputBytes, int maximumResultBytes, int maximumTextCodepoints,
            long maximumWork, long maximumMilliseconds) {
        public Limits {
            if (maximumEntries < 1 || maximumSections < 1 || maximumContextIds < 1
                    || maximumInputBytes < 1 || maximumResultBytes < 1 || maximumTextCodepoints < 1
                    || maximumWork < 1 || maximumMilliseconds < 1 || maximumMilliseconds > 86_400_000)
                throw new IllegalArgumentException("COMPARISON_CAPACITY_REQUIRED");
        }
    }
    public StrictFieldComparisonEngine(StrictFieldComparisonSchema schema) {
        this.schema = Objects.requireNonNull(schema);
    }
    public Result compare(Target suppliedTarget, Request suppliedRequest, Limits limits) throws Exception {
        Objects.requireNonNull(suppliedTarget); Objects.requireNonNull(suppliedRequest);
        var budget = new Budget(Objects.requireNonNull(limits));
        if (suppliedTarget.entries().size() > limits.maximumEntries()
                || suppliedRequest.entries().size() > limits.maximumEntries()
                || suppliedTarget.sections().size() > limits.maximumSections()) capacity();
        // Callers already allocated their records. Bound traversal and raw strings before
        // allocating trees/canonical buffers; exact byte budgets are checked immediately after.
        preflight(suppliedTarget, budget, 0); preflight(suppliedRequest, budget, 0);
        for (var section : suppliedTarget.sections())
            if (section.requirementNodeIds().size() > limits.maximumContextIds()) capacity();
        for (var entry : suppliedTarget.entries()) {
            budget.context(entry.modelIds(), entry.conditionIds());
            if (entry.candidateValue() != null) budget.text(entry.candidateValue().rawText());
        }
        for (var entry : suppliedRequest.entries()) if (entry.sourceObservation() != null) {
            budget.context(entry.sourceObservation().modelIds(), entry.sourceObservation().conditionIds());
            if (entry.sourceObservation().value() != null) budget.text(entry.sourceObservation().value().rawText());
        }
        var targetTree = schema.tree(suppliedTarget); var requestTree = schema.tree(suppliedRequest);
        budget.input(schema.canonical(targetTree).getBytes(StandardCharsets.UTF_8).length);
        budget.input(schema.canonical(requestTree).getBytes(StandardCharsets.UTF_8).length);
        // Records are public caller data. Always revalidate; construction is not authorization.
        Target target = schema.target(targetTree); Request request = schema.request(requestTree);
        String targetHash = schema.hash(target);
        if (!target.schemaVersion().equals(request.schemaVersion()) || !targetHash.equals(request.targetSha256()) || !target.identity().equals(request.expectedIdentity())) mismatch();
        if (target.entries().size() != request.entries().size()) mismatch();
        var requests = new HashMap<String, EntryRequest>();
        for (var entry : request.entries()) { budget.take(1); requests.put(entry.entryId(), entry); }
        var results = new ArrayList<EntryResult>();
        for (var entry : target.entries()) {
            budget.take(1); var input = requests.remove(entry.entryId());
            String entryHash = schema.hash(entry);
            if (input == null || !entryHash.equals(input.targetEntrySha256())) mismatch();
            Decision decision = decide(target.identity(), entry, input);
            ArtifactProtocol.FieldComparison comparison = null;
            // Absence/unsupported cannot be disguised as an invented EMPTY source value.
            if (decision.result != null && input.sourceObservation() != null) {
                var observed = input.sourceObservation();
                List<DatasetProtocol.EvidenceRef> refs = observed.provenance() != null
                        && observed.provenance().source() != null
                        && observed.provenance().source().evidenceRef() != null
                    ? List.of(observed.provenance().source().evidenceRef()) : List.of();
                comparison = new ArtifactProtocol.FieldComparison(entry.fieldId(), entry.rowId(),
                    entry.modelIds(), entry.conditionIds(), observed.value(), entry.candidateValue(),
                    input.rule().ruleId(), null, decision.result, refs);
            }
            results.add(new EntryResult(entry.entryId(), entryHash, entry.fieldId(), entry.fieldDefinitionId(),
                entry.sectionId(), entry.nodeId(), entry.rowId(), input.rule(),
                decision.result == null ? Outcome.UNSUPPORTED : Outcome.COMPUTED,
                decision.result, decision.reason, comparison));
        }
        if (!requests.isEmpty()) mismatch();
        var result = new Result(target.schemaVersion(), "COMPUTATION_ONLY", "EXACT_SUPPLIED_TARGET_ENTRIES", targetHash,
            schema.hash(request), schema.schemaSha256(target.schemaVersion()), target.identity(), results);
        budget.output(schema.canonical(result).getBytes(StandardCharsets.UTF_8).length);
        Result checked = schema.result(schema.tree(result)); budget.take(1); return checked;
    }
    /** Recompute from caller-supplied fixed inputs. Still not a business-authority verification. */
    public Result verifyAgainstInputs(Target target, Request request, Result candidate, Limits limits) throws Exception {
        Objects.requireNonNull(candidate); Objects.requireNonNull(limits);
        if (candidate.entries().size() > limits.maximumEntries()) capacity();
        var outputBudget = new Budget(new Limits(limits.maximumEntries(), limits.maximumSections(), limits.maximumContextIds(),
            limits.maximumResultBytes(), limits.maximumResultBytes(), limits.maximumTextCodepoints(), limits.maximumWork(), limits.maximumMilliseconds()));
        preflight(candidate, outputBudget, 0);
        if (schema.canonical(candidate).getBytes(StandardCharsets.UTF_8).length > limits.maximumResultBytes()) capacity();
        Result actual = compare(target, request, limits);
        if (!actual.equals(candidate)) mismatch();
        return actual;
    }
    private Decision decide(Identity identity, TargetEntry target, EntryRequest request) {
        String operator = request.rule().operator();
        if (!operator.equals("TEXT_EXACT") && !operator.equals("DECIMAL_EXACT")) return unsupported(Reason.UNSUPPORTED_OPERATOR);
        // A missing observation takes precedence over context evaluation and has no synthetic comparison.
        var source = request.sourceObservation();
        if (source == null) return missing(Reason.SOURCE_OBSERVATION_MISSING);
        if (target.applicability() == Applicability.UNKNOWN) return unknown(Reason.UNKNOWN_APPLICABILITY);
        if (target.modelContext() == ContextState.UNRESOLVED) return unknown(Reason.UNRESOLVED_MODEL_CONTEXT);
        if (target.conditionContext() == ContextState.UNRESOLVED) return unknown(Reason.UNRESOLVED_CONDITION_CONTEXT);
        if (source.applicability() == Applicability.UNKNOWN) return unknown(Reason.UNKNOWN_APPLICABILITY);
        if (source.modelContext() == ContextState.UNRESOLVED) return unknown(Reason.UNRESOLVED_MODEL_CONTEXT);
        if (source.conditionContext() == ContextState.UNRESOLVED) return unknown(Reason.UNRESOLVED_CONDITION_CONTEXT);
        if (!new HashSet<>(target.modelIds()).equals(new HashSet<>(source.modelIds()))) return conflict(Reason.MODEL_SET_MISMATCH);
        if (!new HashSet<>(target.conditionIds()).equals(new HashSet<>(source.conditionIds()))) return conflict(Reason.CONDITION_SET_MISMATCH);
        var candidate = target.candidateValue(); var observation = source.value();
        ArtifactProtocol.ValueKind kind = operator.equals("TEXT_EXACT") ? ArtifactProtocol.ValueKind.TEXT : ArtifactProtocol.ValueKind.DECIMAL;
        if (candidate.valueKind() != kind || observation.valueKind() != kind) return unsupported(Reason.UNSUPPORTED_VALUE_KIND);
        if (!Objects.equals(candidate.unit(), observation.unit())) return conflict(Reason.UNIT_MISMATCH);
        if (!Objects.equals(candidate.precision(), observation.precision())) return conflict(Reason.PRECISION_MISMATCH);
        if (candidate.objectRef() != null || observation.objectRef() != null
                || !candidate.selectedIds().isEmpty() || !observation.selectedIds().isEmpty()
                || kind == ArtifactProtocol.ValueKind.TEXT && (candidate.decimalValue() != null || observation.decimalValue() != null))
            return unsupported(Reason.UNSUPPORTED_VALUE_COMPONENTS);
        if (candidate.rawText() == null || observation.rawText() == null) return missing(Reason.RAW_VALUE_MISSING);
        if (target.provenance() == null) return missing(Reason.CANDIDATE_PROVENANCE_MISSING);
        if (source.provenance() == null) return missing(Reason.SOURCE_PROVENANCE_MISSING);
        if (request.expectedSource() == null) return missing(Reason.SOURCE_EXPECTATION_MISSING);
        Decision issue = sourceIdentity(identity, request.expectedSource(), kind);
        if (issue != null) return issue;
        issue = provenance(identity, target.provenance(), candidate, kind);
        if (issue != null) return issue;
        issue = provenance(identity, source.provenance(), observation, kind);
        if (issue != null) return issue;
        if (!request.expectedSource().equals(source.provenance().source())) return conflict(Reason.SOURCE_IDENTITY_MISMATCH);
        if (kind == ArtifactProtocol.ValueKind.TEXT)
            return candidate.rawText().equals(observation.rawText()) ? match() : conflict(Reason.VALUE_MISMATCH);
        // Both sides have explicitly declared PLAIN_DECIMAL_SCALE_V1 evidence. No guessed parse,
        // float, conversion, rounding, exponent, locale or Unicode normalization is used.
        return new BigDecimal(candidate.decimalValue()).compareTo(new BigDecimal(observation.decimalValue())) == 0
            ? match() : conflict(Reason.VALUE_MISMATCH);
    }
    private Decision sourceIdentity(Identity identity, SourceIdentity source, ArtifactProtocol.ValueKind kind) {
        if (source == null || source.evidenceRef() == null) return missing(Reason.ORIGINAL_EVIDENCE_MISSING);
        var evidence = source.evidenceRef();
        if (!identity.tenantId().equals(evidence.tenantId()) || !identity.projectId().equals(evidence.projectId()))
            return conflict(Reason.EVIDENCE_SCOPE_MISMATCH);
        if (kind == ArtifactProtocol.ValueKind.TEXT) {
            if (!source.kind().equals("SOURCE_EXACT_REVISION") || source.parsedDecimal() != null)
                return unsupported(Reason.UNSUPPORTED_PROVENANCE);
            if (source.sourceExact() == null) return missing(Reason.SOURCE_EXACT_REFERENCE_MISSING);
        } else {
            if (!source.kind().equals("PARSED_DECIMAL_REVISION") || source.sourceExact() != null)
                return unsupported(Reason.UNSUPPORTED_PROVENANCE);
            if (source.parsedDecimal() == null) return missing(Reason.PARSED_DECIMAL_EVIDENCE_MISSING);
            if (!DECIMAL_PARSER_CONTRACT.equals(source.parsedDecimal().parserContract())) return unsupported(Reason.UNSUPPORTED_PARSER);
        }
        return null;
    }
    private Decision provenance(Identity identity, Provenance provenance, ArtifactProtocol.FieldValue value,
            ArtifactProtocol.ValueKind kind) {
        Decision issue = sourceIdentity(identity, provenance.source(), kind);
        if (issue != null) return issue;
        if (provenance.readReceiptIds().isEmpty()) return missing(Reason.READ_RECEIPTS_MISSING);
        String raw = value.rawText();
        String rawHash = DigestUtils.sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
        if (provenance.endCodepoint() - provenance.startCodepoint() != raw.codePointCount(0, raw.length())
                || !rawHash.equals(provenance.rawTextSha256())) return conflict(Reason.RAW_LEXICAL_EVIDENCE_MISMATCH);
        if (kind == ArtifactProtocol.ValueKind.DECIMAL) {
            if (!PLAIN_DECIMAL.matcher(raw).matches()) return unsupported(Reason.UNSUPPORTED_DECIMAL_LEXICAL);
            var parsed = provenance.source().parsedDecimal();
            if (!raw.equals(value.decimalValue()) || !raw.equals(parsed.decimalValue())
                    || !rawHash.equals(parsed.rawTextSha256())) return conflict(Reason.DECIMAL_LEXICAL_MISMATCH);
            if (new BigDecimal(raw).scale() != value.precision() || parsed.precision() != value.precision())
                return conflict(Reason.PRECISION_MISMATCH);
        }
        return null;
    }
    private static void preflight(Object value, Budget budget, int depth) throws Exception {
        if (depth > 32) capacity();
        budget.take(1);
        if (++budget.minimumInputBytes > budget.limits.maximumInputBytes()) capacity();
        if (value == null || value instanceof Number || value instanceof Enum<?>) return;
        if (value instanceof String text) { budget.string(text); return; }
        if (value instanceof List<?> list) {
            if (list.size() > Math.max(budget.limits.maximumEntries(), Math.max(budget.limits.maximumSections(), budget.limits.maximumContextIds()))) capacity();
            if (list.size() > budget.limits.maximumWork() - budget.work) capacity();
            for (var item : list) preflight(item, budget, depth + 1);
            return;
        }
        // The protocol and all shared value/evidence/locator types are closed immutable records.
        // This is only a size walk, not reflection-based deserialization or an authority check.
        if (!value.getClass().isRecord()) throw new com.ruoyi.medical.contracts.SchemaRejection(ErrorCode.SCHEMA_INVALID);
        for (var component : value.getClass().getRecordComponents())
            preflight(component.getAccessor().invoke(value), budget, depth + 1);
    }
    private record Decision(ArtifactProtocol.ComparisonResult result, Reason reason) { }
    private static Decision match() { return new Decision(MATCH, Reason.EXACT_EQUAL); }
    private static Decision conflict(Reason reason) { return new Decision(CONFLICT, reason); }
    private static Decision missing(Reason reason) { return new Decision(MISSING, reason); }
    private static Decision unknown(Reason reason) { return new Decision(UNKNOWN_CONDITION, reason); }
    private static Decision unsupported(Reason reason) { return new Decision(null, reason); }
    private static final class Budget {
        final Limits limits; final long started = System.nanoTime(); long work, text, input, minimumInputBytes;
        Budget(Limits limits) { this.limits = limits; }
        void take(long amount) {
            if (amount < 0 || amount > limits.maximumWork() - work) capacity();
            work += amount;
            if (System.nanoTime() - started > limits.maximumMilliseconds() * 1_000_000) capacity();
        }
        void string(String value) {
            // UTF-16 length is a lower bound for the UTF-8 JSON string byte count.
            // Check before code-point counting or allocating an encoded copy.
            if (value.length() > limits.maximumInputBytes() - minimumInputBytes) capacity();
            minimumInputBytes += value.length(); take(value.length());
        }
        void input(int bytes) { if (bytes > limits.maximumInputBytes() - input) capacity(); input += bytes; take(bytes); }
        void output(int bytes) { if (bytes > limits.maximumResultBytes()) capacity(); take(bytes); }
        void text(String value) {
            if (value == null) return;
            int size = value.codePointCount(0, value.length());
            if (size > limits.maximumTextCodepoints() - text) capacity(); text += size; take(size);
        }
        void context(List<String> models, List<String> conditions) {
            if ((long) models.size() + conditions.size() > limits.maximumContextIds()) capacity();
            take((long) models.size() + conditions.size());
        }
    }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
    private static void capacity() { throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT); }
}

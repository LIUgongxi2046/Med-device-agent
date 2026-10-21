package com.ruoyi.medical.persistence;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Typed authoring value for N03. This is deliberately not a published policy or an approval
 * token. Boolean applicability rules cannot be passed as this type. Persistence/approval
 * consumers must resolve a separately governed comparison-policy revision before use.
 */
public final class StrictComparisonPolicyDefinition {
    private StrictComparisonPolicyDefinition() { }
    public enum Operator { TEXT_EXACT, DECIMAL_EXACT }
    public enum PrecisionMode { NOT_APPLICABLE, EXACT_SCALE }
    public enum UnitMode { EXACT_DECLARED }
    public enum ContextMode { GLOBAL, SCOPED }
    public enum Applicability { APPLICABLE, UNKNOWN }
    public enum CoverageMode { EXACT_DECLARED_COMBINATIONS }
    public record Version(String revision, String sha256) {
        public Version { required(revision); hash(sha256); }
    }
    public record Precision(PrecisionMode mode, Integer scale) {
        public Precision {
            Objects.requireNonNull(mode);
            if (mode == PrecisionMode.NOT_APPLICABLE ? scale != null : scale == null || scale < 0 || scale > 12)
                reject("PRECISION_POLICY_INVALID");
        }
    }
    /** Null unit explicitly means unitless; empty string is never an alias for unitless. */
    public record Unit(UnitMode mode, String literal) {
        public Unit { Objects.requireNonNull(mode); if (literal != null) required(literal); }
    }
    public record Context(ContextMode mode, List<String> ids) {
        public Context {
            Objects.requireNonNull(mode); ids = unique(ids);
            if ((mode == ContextMode.GLOBAL) != ids.isEmpty()) reject("CONTEXT_POLICY_INVALID");
        }
    }
    /** One applicable indicator/model/condition combination, not a candidate-derived default. */
    public record Combination(String fieldDefinitionId, Context models, Context conditions) {
        public Combination { required(fieldDefinitionId); Objects.requireNonNull(models); Objects.requireNonNull(conditions); }
    }
    public record ApplicabilityScope(Applicability state, Version rule, Version scope) {
        public ApplicabilityScope { Objects.requireNonNull(state); Objects.requireNonNull(scope); }
    }
    public record Definition(String schemaVersion, Operator operator, Precision precision, Unit unit,
            ApplicabilityScope applicability, CoverageMode coverageMode, List<Combination> coverage,
            Version implementation, List<Version> basis) {
        public Definition {
            if (!"1.0.0".equals(schemaVersion)) reject("POLICY_SCHEMA_UNSUPPORTED");
            Objects.requireNonNull(operator); Objects.requireNonNull(precision); Objects.requireNonNull(unit);
            Objects.requireNonNull(applicability); Objects.requireNonNull(coverageMode); Objects.requireNonNull(implementation);
            coverage = List.copyOf(coverage); basis = List.copyOf(basis);
            if (coverage.isEmpty() || basis.isEmpty() || new HashSet<>(basis).size() != basis.size()) reject("POLICY_BASIS_OR_COVERAGE_INVALID");
            Set<Key> combinations = new HashSet<>();
            for (Combination item : coverage) if (!combinations.add(key(item))) reject("DUPLICATE_POLICY_COMBINATION");
            if ((operator == Operator.DECIMAL_EXACT) != (precision.mode() == PrecisionMode.EXACT_SCALE))
                reject("OPERATOR_PRECISION_MISMATCH");
        }
        /** Checks author-declared coverage against an independently resolved complete inventory. */
        public void requireCoverage(List<Combination> independentInventory) {
            Set<Key> actual = new HashSet<>();
            for (Combination item : independentInventory) if (!actual.add(key(item))) reject("DUPLICATE_INVENTORY_COMBINATION");
            Set<Key> expected = new HashSet<>(); for (Combination item : coverage) expected.add(key(item));
            if (!expected.equals(actual)) reject("POLICY_COVERAGE_MISMATCH");
        }
        /** Definition compatibility only. Does not establish publication or professional approval. */
        public void requireImplementation(Version installed) {
            if (!implementation.equals(installed)) reject("COMPARISON_IMPLEMENTATION_MISMATCH");
        }
    }
    private record Key(String field, ContextMode modelMode, Set<String> models, ContextMode conditionMode, Set<String> conditions) { }
    private static Key key(Combination c) {
        return new Key(c.fieldDefinitionId(), c.models().mode(), Set.copyOf(c.models().ids()), c.conditions().mode(), Set.copyOf(c.conditions().ids()));
    }
    private static List<String> unique(List<String> values) {
        List<String> copy = List.copyOf(values); copy.forEach(StrictComparisonPolicyDefinition::required);
        if (new HashSet<>(copy).size() != copy.size()) reject("DUPLICATE_CONTEXT_ID"); return copy;
    }
    private static void required(String value) { if (value == null || value.isBlank()) reject("POLICY_VALUE_REQUIRED"); }
    private static void hash(String value) { if (value == null || !value.matches("[a-f0-9]{64}")) reject("POLICY_DIGEST_INVALID"); }
    private static void reject(String reason) { throw new IllegalArgumentException(reason); }
}

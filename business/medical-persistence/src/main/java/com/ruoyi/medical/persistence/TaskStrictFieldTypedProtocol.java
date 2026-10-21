package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import java.util.*;

/** Explicit typed target/context family. It never changes the TEXT-only 096 wire contract. */
public final class TaskStrictFieldTypedProtocol {
    private TaskStrictFieldTypedProtocol(){}
    public static final String TARGET_REF="urn:medical:strict-field-typed-target:1.0.0";
    public static final String TARGET_REF_V2="urn:medical:strict-field-typed-target:2.0.0";
    static boolean typed(String ref){return TARGET_REF.equals(ref)||TARGET_REF_V2.equals(ref);}
    static String targetRef(String version){version(version);return "2.0.0".equals(version)?TARGET_REF_V2:TARGET_REF;}
    public enum Mode { GLOBAL, SCOPED }
    /** An independently approved interpretation of a real original-source span, not an ID assertion alone. */
    public record Label(String id,StrictComparisonProtocol.Span evidenceSpan,String literal){
        public Label{WireValues.uuid(id);Objects.requireNonNull(evidenceSpan);if(literal==null||literal.isBlank()||literal.length()>4096)bad();}
    }
    public record Context(Mode mode,List<Label> labels,StrictComparisonProtocol.Span globalEvidenceSpan,String globalLiteral){
        public Context{Objects.requireNonNull(mode);labels=List.copyOf(labels);if(labels.size()>1000||labels.stream().map(Label::id).distinct().count()!=labels.size())bad();
            if(mode==Mode.GLOBAL){if(!labels.isEmpty()||globalEvidenceSpan==null||globalLiteral==null||globalLiteral.isBlank())bad();}
            else if(labels.isEmpty()||globalEvidenceSpan!=null||globalLiteral!=null)bad();}
        public List<String> ids(){return labels.stream().map(Label::id).toList();}
    }
    public record ContextAuthor(String schemaVersion,StrictComparisonProtocol.Scope scope,DatasetProtocol.EvidenceRef evidence,
            String rightsRevision,String purpose,Context models,Context conditions,String reason){
        public ContextAuthor{version(schemaVersion);Objects.requireNonNull(scope);Objects.requireNonNull(evidence);if(!schemaVersion.equals(evidence.schemaVersion()))bad();WireValues.uuid(rightsRevision);
            if(purpose==null||!purpose.matches("[A-Z][A-Z0-9_]{0,127}"))bad();Objects.requireNonNull(models);Objects.requireNonNull(conditions);TaskStrictFieldTypedProtocol.reason(reason);}
    }
    /** Full target occurrences are derived from the actual plan/template, never from omitted caller lists. */
    public record Entry(String fieldId,String fieldDefinitionId,String sectionId,String nodeId,String rowId,
            PermissionProtocol.Basis candidateDecimal,PermissionProtocol.Basis candidateContext,
            PermissionProtocol.Basis observedDecimal,PermissionProtocol.Basis observedContext,PermissionProtocol.Basis comparisonPolicy,
            StrictFieldComparisonProtocol.SourceExactReference candidateExact,StrictFieldComparisonProtocol.SourceExactReference observedExact){
        public Entry{WireValues.uuid(fieldId);WireValues.uuid(fieldDefinitionId);WireValues.uuid(sectionId);WireValues.uuid(nodeId);if(rowId!=null)WireValues.uuid(rowId);
            Objects.requireNonNull(candidateContext);Objects.requireNonNull(observedContext);Objects.requireNonNull(comparisonPolicy);if((candidateDecimal==null)==(candidateExact==null)||(observedDecimal==null)==(observedExact==null)||(candidateDecimal==null)!=(observedDecimal==null))bad();}
    }
    public enum RequirementKind { FIELD_ONLY, METHOD }
    public record MethodEvidence(String methodId,PermissionProtocol.Basis sourceContext,StrictComparisonProtocol.Span methodSpan,String methodLiteral,StrictComparisonProtocol.Span standardSpan,String standardLiteral){
        public MethodEvidence{WireValues.uuid(methodId);Objects.requireNonNull(sourceContext);Objects.requireNonNull(methodSpan);Objects.requireNonNull(standardSpan);if(methodLiteral==null||methodLiteral.isBlank()||standardLiteral==null||standardLiteral.isBlank())bad();}
    }
    public record Indicator(String fieldDefinitionId,List<MethodEvidence> methods){public Indicator{WireValues.uuid(fieldDefinitionId);methods=List.copyOf(methods);if(methods.size()>1000||methods.stream().map(MethodEvidence::methodId).distinct().count()!=methods.size())bad();}}
    public record RequirementBinding(String requirementNodeId,RequirementKind kind,List<Indicator> indicators){public RequirementBinding{WireValues.uuid(requirementNodeId);Objects.requireNonNull(kind);indicators=List.copyOf(indicators);if(indicators.size()>1000||indicators.stream().map(Indicator::fieldDefinitionId).distinct().count()!=indicators.size()||(kind==RequirementKind.FIELD_ONLY&&!indicators.isEmpty())||(kind==RequirementKind.METHOD&&indicators.isEmpty()))bad();}}
    public record Author(String schemaVersion,StrictComparisonProtocol.Scope scope,String releaseId,String publicationApprovalSha256,PermissionProtocol.Basis roleVersion,PermissionProtocol.Basis executionPolicyVersion,long maximumReadBytes,List<Entry> entries,List<RequirementBinding> requirements,String reason){
        public Author{version(schemaVersion);Objects.requireNonNull(scope);WireValues.uuid(releaseId);WireValues.hash(publicationApprovalSha256);Objects.requireNonNull(roleVersion);Objects.requireNonNull(executionPolicyVersion);if(maximumReadBytes<1||maximumReadBytes>8388608)bad();entries=List.copyOf(entries);if(entries.isEmpty()||entries.size()>10000||entries.stream().map(Entry::fieldId).distinct().count()!=entries.size())bad();requirements=List.copyOf(requirements);if(requirements.size()>10000||requirements.stream().map(RequirementBinding::requirementNodeId).distinct().count()!=requirements.size())bad();TaskStrictFieldTypedProtocol.reason(reason);}
        public Author(String schemaVersion,StrictComparisonProtocol.Scope scope,String releaseId,String publicationApprovalSha256,PermissionProtocol.Basis roleVersion,PermissionProtocol.Basis executionPolicyVersion,long maximumReadBytes,List<Entry> entries,String reason){this(schemaVersion,scope,releaseId,publicationApprovalSha256,roleVersion,executionPolicyVersion,maximumReadBytes,entries,List.of(),reason);}
    }
    public record Decide(String schemaVersion,String expectedRevision,String contentSha256,String baselineSha256,StrictComparisonProtocol.Decision decision,String reason){
        public Decide{version(schemaVersion);WireValues.revision(expectedRevision);WireValues.hash(contentSha256);WireValues.hash(baselineSha256);Objects.requireNonNull(decision);TaskStrictFieldTypedProtocol.reason(reason);}
    }
    static String span(String original,StrictComparisonProtocol.Span span){int count=original.codePointCount(0,original.length());if(span.endCodepoint()>count)bad();return original.substring(original.offsetByCodePoints(0,span.startCodepoint()),original.offsetByCodePoints(0,span.endCodepoint()));}
    static void requireContext(Context context,String original){if(context.mode()==Mode.GLOBAL){if(!context.globalLiteral().equals(span(original,context.globalEvidenceSpan())))bad();}else for(var label:context.labels())if(!label.literal().equals(span(original,label.evidenceSpan())))bad();}
    private static void version(String v){if(!Set.of("1.0.0","2.0.0").contains(v))bad();}private static void reason(String r){if(r==null||r.isBlank()||r.length()>2000)bad();}
    private static void bad(){throw new IllegalArgumentException("TYPED_STRICT_FIELD_INVALID");}
}

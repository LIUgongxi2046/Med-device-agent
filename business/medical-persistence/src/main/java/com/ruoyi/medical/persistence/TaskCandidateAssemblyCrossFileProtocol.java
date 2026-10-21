package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** Explicit independently approved selectors. These values never grant read authority. */
public final class TaskCandidateAssemblyCrossFileProtocol {
    private TaskCandidateAssemblyCrossFileProtocol(){}
    public record Document(String documentId,PermissionProtocol.Basis documentRevision,String documentType,
            PermissionProtocol.Basis bodyArtifact,PermissionProtocol.Basis templateVersion,List<PermissionProtocol.Basis> factVersions){
        public Document{WireValues.uuid(documentId);Objects.requireNonNull(documentRevision);Objects.requireNonNull(bodyArtifact);Objects.requireNonNull(templateVersion);factVersions=List.copyOf(factVersions);}
    }
    public record Selector(String inputKey,String sourceKind,String documentRevision,String fieldId,String valueSlot,PermissionProtocol.Basis factVersion){
        public Selector{WireValues.uuid(fieldId);if(!Set.of("CANDIDATE","DOCUMENT").contains(sourceKind)||!Set.of("ORIGINAL_VALUE","DECIMAL_VALUE","UNIT","MODEL","CONDITION").contains(valueSlot))throw new IllegalArgumentException("CROSS_FILE_SELECTOR_REQUIRED");if("CANDIDATE".equals(sourceKind)&&documentRevision!=null||"DOCUMENT".equals(sourceKind)&&documentRevision==null)throw new IllegalArgumentException("CROSS_FILE_DOCUMENT_BINDING_REQUIRED");if(documentRevision!=null)WireValues.uuid(documentRevision);}
    }
    public record Rule(String ruleId,PermissionProtocol.Basis ruleRevision,List<Selector> inputs){public Rule{WireValues.uuid(ruleId);Objects.requireNonNull(ruleRevision);inputs=List.copyOf(inputs);}}
    public record Input(String schemaVersion,List<Document> documents,List<Rule> rules){public Input{if(!"1.0.0".equals(schemaVersion))throw new IllegalArgumentException("CROSS_FILE_VERSION_REQUIRED");documents=List.copyOf(documents);rules=List.copyOf(rules);}}
}

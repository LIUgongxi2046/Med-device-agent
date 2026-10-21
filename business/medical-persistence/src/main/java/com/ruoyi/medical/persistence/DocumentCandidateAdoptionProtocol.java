package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.util.*;

/** DC03 additive public contract. Confirmation is an explicit authenticated request,
 * never a model/tool boolean, candidate PASS, or N06 completion receipt. */
public final class DocumentCandidateAdoptionProtocol {
    private DocumentCandidateAdoptionProtocol() { }
    public enum Mode { INITIAL, SELECTED_NODES }
    public record PreviewInput(String schemaVersion,String documentId,String baseDocumentRevision,
            String expectedDocumentRevision,String candidateSha256,Mode mode,List<String> selectedNodeIds) {
        public PreviewInput { version(schemaVersion);WireValues.uuid(documentId);WireValues.uuid(baseDocumentRevision);
            revision(expectedDocumentRevision);WireValues.hash(candidateSha256);Objects.requireNonNull(mode);
            selectedNodeIds=ids(selectedNodeIds);if(mode==Mode.SELECTED_NODES&&selectedNodeIds.isEmpty())bad();
            if(mode==Mode.INITIAL&&!selectedNodeIds.isEmpty())bad(); }
    }
    public record ConfirmInput(String schemaVersion,String pendingActionId,String argumentsSha256,
            String expectedActionRevision,boolean confirm) {
        public ConfirmInput {version(schemaVersion);WireValues.uuid(pendingActionId);WireValues.hash(argumentsSha256);
            revision(expectedActionRevision);if(!confirm)bad();}
    }
    public record Preview(String schemaVersion,String candidateId,String pendingActionId,String actionRevision,
            String argumentsSha256,String expiresAt,Mode mode,String documentId,String baseDocumentRevision,
            String expectedDocumentRevision,String candidateSha256,String resultBodySha256,List<String> selectedNodeIds,
            List<String> replacedNodeIds,List<String> insertedNodeIds,List<String> checkIds,boolean professionalApproval) {
        public Preview {version(schemaVersion);WireValues.uuid(candidateId);WireValues.uuid(pendingActionId);
            revision(actionRevision);WireValues.hash(argumentsSha256);java.time.Instant.parse(expiresAt);Objects.requireNonNull(mode);
            WireValues.uuid(documentId);WireValues.uuid(baseDocumentRevision);revision(expectedDocumentRevision);
            WireValues.hash(candidateSha256);WireValues.hash(resultBodySha256);selectedNodeIds=ids(selectedNodeIds);
            replacedNodeIds=ids(replacedNodeIds);insertedNodeIds=ids(insertedNodeIds);checkIds=ids(checkIds);if(professionalApproval)bad();}
    }
    public record Result(String schemaVersion,String adoptionId,String candidateId,String documentId,String documentRevision,
            String pendingActionId,String operationId,String bodySha256,String checkDocumentIntentId,boolean professionalApproval) {
        public Result {version(schemaVersion);for(String id:List.of(adoptionId,candidateId,documentId,documentRevision,pendingActionId,operationId,checkDocumentIntentId))WireValues.uuid(id);
            WireValues.hash(bodySha256);if(professionalApproval)bad();}
    }
    private static List<String> ids(List<String> values){var result=List.copyOf(values);result.forEach(WireValues::uuid);if(new HashSet<>(result).size()!=result.size())bad();return result;}
    private static void version(String value){if(!"1.0.0".equals(value))bad();}
    private static void revision(String value){if(value==null||!value.matches("[1-9][0-9]{0,18}"))bad();}
    private static void bad(){throw new IllegalArgumentException("DOCUMENT_ADOPTION_CONTRACT_INVALID");}
}

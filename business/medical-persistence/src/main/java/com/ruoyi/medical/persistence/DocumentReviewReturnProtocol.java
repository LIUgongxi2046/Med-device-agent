package com.ruoyi.medical.persistence;

import java.math.BigInteger;

/** Separate human work identity: never an agent run/step waiting item. */
public final class DocumentReviewReturnProtocol {
    private DocumentReviewReturnProtocol() { }
    public record Task(String taskId, String taskRevision, String status, BigInteger revision) { }
    public record QueueCandidate(String assignmentId, String projectId, String documentId,
            String documentRevision, String taskId, String taskRevision, String assigneeId, String returnId, String operationId) { }
    public record Stored(String assignmentId, String returnId, String operationId,
            String request, String requestHash, String receipt, String manifest) { }
}

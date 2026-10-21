package com.ruoyi.medical.persistence;
import java.util.Set;
/** Bounded production data contract; no public value is an authority proof. */
public final class TaskNodeProducerProtocol {
 private TaskNodeProducerProtocol() {}
 public static final String SCHEMA_REF="urn:medical:task-node-producer:1.0.0";
 public static final String PURPOSE="TASK_NODE_PRODUCTION";
 public static final Set<String> OPERATIONS=Set.of("NODE_PRODUCE","HOST_INPUT_CONSUME","HOST_PREDECESSOR_CONSUME","HOST_ENVELOPE_CONSUME","SUPERVISOR_PREPARE_READ","MODEL_INPUT_CONSUME");
 public static final Set<String> KINDS=Set.of("TASK_NODE_INPUT","STEP_ENVELOPE","EVIDENCE_BUNDLE","STEP_RESULT","SESSION_EXPORT","TASK_EXACT_INPUT","MODEL_RESULT","PROVIDER_CAPTURE");
}

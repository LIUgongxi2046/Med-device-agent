package com.ruoyi.medical.persistence;
import java.util.Set;
/** Closed host-selected profiles. A caller cannot select a node kind through JSON. */
public enum TaskNodeProducerProfile {
 RESEARCH("N02","question_ids","/task-node-producer.schema.json","task_node_producer_","TASK-NODE-PRODUCER-",TaskNodeProducerProtocol.KINDS),
 WRITER("N04","section_ids","/task-section-writer-producer.schema.json","task_section_writer_producer_","TASK-SECTION-WRITER-PRODUCER-",Set.of("TASK_SECTION_WRITER_INPUT","TASK_SECTION_WRITER_EXACT_INPUT","SECTION_BODY","SECTION_CANDIDATE","FIELD_CHECK_RESULT","EVIDENCE_BUNDLE","DOCUMENT_BODY","STEP_ENVELOPE","SESSION_EXPORT","MODEL_RESULT","PROVIDER_CAPTURE")),
 VERIFIER("N05","section_ids","/task-source-verifier-producer.schema.json","task_source_verifier_producer_","TASK-SOURCE-VERIFIER-PRODUCER-",Set.of("TASK_SOURCE_VERIFIER_INPUT","TASK_SOURCE_VERIFIER_EXACT_INPUT","SECTION_BODY","SECTION_CANDIDATE","REVIEW_FINDINGS","REVIEW_COVERAGE","STEP_ENVELOPE","SESSION_EXPORT","MODEL_RESULT","PROVIDER_CAPTURE")),
 ASSEMBLY("N06","section_ids","/task-candidate-assembly-producer.schema.json","task_candidate_assembly_producer_","TASK-CANDIDATE-ASSEMBLY-PRODUCER-",Set.of("ASSEMBLY_MANIFEST","DOCUMENT_BODY","ASSEMBLY_COMPANION","CROSS_FILE_CHECK_REPORT"));
 private final String node,scope,resource,table,operation;private final Set<String> kinds;
 TaskNodeProducerProfile(String n,String s,String r,String t,String o,Set<String> k){node=n;scope=s;resource=r;table=t;operation=o;kinds=k;}
 public String node(){return node;}public String scope(){return scope;}public String resource(){return resource;}public String table(){return table;}public String operation(){return operation;}public Set<String> kinds(){return kinds;}
}

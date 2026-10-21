package com.ruoyi.medical.persistence;
import java.util.List;
import java.util.Map;
/** Independent original completion contract. The concrete professional/deterministic producer/consumer rechecks the
 * runtime private completion proof, immutable receipt, original bytes, professional closure or deterministic N03 provenance, and exact outputs.
 * A configured consumer alone never supplies missing runtime evidence. */
interface FormalEvaluationProfessionalCompletions {
 void requireCurrent(EnterpriseAccess.Admission admission,List<FormalEvaluationExecutionProtocol.Record> records,
   Map<String,FormalEvaluationOriginalObjects.Verified> actualOriginals)throws Exception;
 /** Require original business outcome classification and exact cell/output lineage; N03 may be deterministic, N00/connection results are ineligible. */
 void requireProfessionalOutput(EnterpriseAccess.Admission admission,List<FormalEvaluationExecutionProtocol.Record> records,
   Map<String,FormalEvaluationOriginalObjects.Verified> actualOriginals,EvaluationProtocol.Cell cell,PermissionProtocol.Basis output)throws Exception;
}

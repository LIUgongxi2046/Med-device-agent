package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
/** Trusted internal adapter to the actual 083 execution, 081 qualified-score and 084 approved-statistics
 * authorities. Preparation performs original GET outside transactions; implementation-private state
 * must be rebound and rechecked for each current Admission. No caller-supplied report is a proof. */
public interface PublicationEvidenceRevalidation {
 interface Prepared { }
 Prepared prepare(ApplicationSessionIdentity.Verified subject,String context,JsonNode exactStoredAcceptance,boolean historicalRead)throws Exception;
 Prepared prepare(EvidenceReadAccess reads,String context,JsonNode exactStoredAcceptance,boolean historicalRead)throws Exception;
 java.util.List<String> requireCurrentDependencies(EnterpriseAccess.Admission admission,JsonNode exactStoredAcceptance,boolean historicalRead,Prepared prepared)throws Exception;
 void requireCurrent(EnterpriseAccess.Admission admission,JsonNode exactStoredAcceptance,boolean historicalRead,Prepared prepared)throws Exception;
}

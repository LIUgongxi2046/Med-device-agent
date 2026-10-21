package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/** B06 transaction seam: only a persisted complete impact and human decision can mint a proof. */
@Component
public final class SharedFactReplacementImpact {
 private final VersionChangeImpactService service;
 public SharedFactReplacementImpact(VersionChangeImpactService service){this.service=service;}
 static final class VerifiedImpact {
  private final EnterpriseAccess.Admission admission;
  private final String project,oldVersion,proposal,proposalHash;
  private final JsonNode snapshot;
  private VerifiedImpact(EnterpriseAccess.Admission admission,String project,String oldVersion,String proposal,String proposalHash,JsonNode snapshot){
   this.admission=admission;this.project=project;this.oldVersion=oldVersion;this.proposal=proposal;this.proposalHash=proposalHash;this.snapshot=snapshot.deepCopy();
  }
  void require(EnterpriseAccess.Admission a,String project,String oldVersion,String proposal,String proposalHash){
   if(a!=admission||!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()||!this.project.equals(project)||!this.oldVersion.equals(oldVersion)||!this.proposal.equals(proposal)||!this.proposalHash.equals(proposalHash))throw new TenantAccessException(ErrorCode.REVIEW_STALE);
  }
  JsonNode snapshot(){return snapshot.deepCopy();}
 }
 VerifiedImpact require(EnterpriseAccess.Admission admission,String project,String oldVersion,String proposal,String proposalHash)throws Exception{
  var snapshot=service.applyFactReplacement(admission,project,oldVersion,proposal,proposalHash);
  return new VerifiedImpact(admission,project,oldVersion,proposal,proposalHash,snapshot);
 }
}

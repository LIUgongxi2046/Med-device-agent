package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import java.time.Instant;
import java.util.*;

/** RUN-owned original artifact preservation basis. STREAM terms describe public replay,
 * not provider capture. This class never converts retention into a delete/expiry deadline. */
final class TaskInvocationOutputPolicy {
 private TaskInvocationOutputPolicy(){}
 static JsonNode forRun(String run,Instant created,JsonNode retention){
  WireValues.uuid(run);WireValues.uuid(text(retention,"policy_revision"));WireValues.hash(text(retention,"content_sha256"));Objects.requireNonNull(created);
  var terms=retention.required("terms");if(!terms.isArray())bad();JsonNode term=null;for(var candidate:terms)if("RUN".equals(text(candidate,"category"))){if(term!=null)bad();term=candidate;}
  if(term==null||!"RUN_CREATED".equals(text(term,"start_event"))||!term.required("hold_override").isBoolean()||term.required("hold_override").booleanValue())bad();
  if(!text(term,"retention_duration_seconds").matches("[1-9][0-9]{0,17}")||!text(term,"backup_expiry_seconds").matches("[1-9][0-9]{0,17}")||!Set.of("CRYPTOGRAPHIC_ERASURE","VERIFIED_DELETE").contains(text(term,"purge_method")))bad();
  var result=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("policy_revision",text(retention,"policy_revision")).put("content_sha256",text(retention,"content_sha256")).put("category","RUN").put("start_event","RUN_CREATED").put("run_id",run).put("run_created_at",created.toString());result.set("term",term.deepCopy());return result;
 }
 static void requireOriginal(JsonNode original,String run,Instant created,JsonNode retention){if(!FormalTaskNodeAuthority.sameEvidence(original,forRun(run,created,retention)))bad();}
 /** This is the original producer operation cutoff only, not preservation duration. */
 static void requireWriteCutoff(String originalValidUntil,Instant now){if(!Objects.requireNonNull(now).isBefore(Instant.parse(originalValidUntil)))throw new TenantAccessException(ErrorCode.ACTION_STALE);}
 private static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
 private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

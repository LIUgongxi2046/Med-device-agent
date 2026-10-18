package com.ruoyi.medical.persistence;
import java.time.*;import com.ruoyi.medical.contracts.*;import com.fasterxml.jackson.databind.node.ObjectNode;
record BusinessAuditQuery(String projectId,String action,Instant from,Instant to,String actorId,String documentId,int limit){
 static BusinessAuditQuery parse(String project,String action,String from,String to,String actor,String document,int limit,BusinessAuditPolicy policy){
  policy.requireConfigured();WireValues.uuid(project);if(actor!=null)WireValues.uuid(actor);if(document!=null)WireValues.uuid(document);
  if(!"document.revision.saved".equals(action)||limit<1||limit>policy.maximumPageSize())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  try{Instant start=Instant.parse(from),end=Instant.parse(to);if(start.atOffset(ZoneOffset.UTC).getYear()<1000||end.atOffset(ZoneOffset.UTC).getYear()>9999||!from.endsWith("Z")||!to.endsWith("Z")||start.getNano()%1000!=0||end.getNano()%1000!=0||!start.isBefore(end)||Duration.between(start,end).compareTo(Duration.ofSeconds(policy.maximumWindowSeconds()))>0)throw new IllegalArgumentException();return new BusinessAuditQuery(project,action,start,end,actor,document,limit);}catch(RuntimeException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 ObjectNode tree(PermissionSchema json){var n=json.object().put("project_id",projectId).put("action",action).put("from",from.toString()).put("to",to.toString()).put("limit",limit);if(actorId==null)n.putNull("actor_id");else n.put("actor_id",actorId);if(documentId==null)n.putNull("document_id");else n.put("document_id",documentId);return n;}
}

package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
/** Isolated executable regression: no JUnit runner, build, server, database, or external storage. */
public final class GruRecordExportSchemaTest {
 static final JsonMapper JSON=new JsonMapper();
 static final String ID="00000000-0000-4000-8000-000000000001", HASH="a".repeat(64);
 static final GruRecordExportSchema S;
 static{try{S=new GruRecordExportSchema();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 static ObjectNode basis(int count)throws Exception{
  var n=JSON.createObjectNode();n.put("schema_version","1.0.0");
  for(String k:new String[]{"tenant_id","project_id","conversation_id","owner_id","document_id","document_revision","scope_snapshot_id"})n.put(k,ID);
  n.put("conversation_revision",Integer.toString(count+1));n.put("body_sha256",HASH);n.put("scope_sha256",HASH);n.put("mode","RECORD_ONLY");n.put("execution_state","NOT_EXECUTED");n.put("message_count",count);n.put("complete",true);n.putNull("next_cursor");
  var m=n.putArray("message_manifest");for(int i=0;i<count;i++)m.addObject().put("message_id",String.format("00000000-0000-4000-8000-%012d",i+1)).put("sequence",Integer.toString(i+2)).put("content_sha256",HASH);
  n.put("message_manifest_sha256",S.hash(m));n.putObject("current_context").put("tenant_id",ID).put("context_revision","1").put("auth_epoch","1").put("governance_epoch","1");return n;
 }
 interface Attempt{void run()throws Exception;}
 static int assertions;
 static void reject(Attempt a)throws Exception{try{a.run();throw new AssertionError("accepted invalid value");}catch(com.ruoyi.medical.contracts.SchemaRejection expected){assertions++;}}
 @org.junit.jupiter.api.Test void focusedRegressions()throws Exception{main(new String[0]);}
 public static void main(String[] args)throws Exception{
  for(int count:new int[]{0,1,500}){S.validate("Basis",basis(count));assertions++;}
  reject(()->S.validate("Basis",basis(501)));
  var request=JSON.createObjectNode().put("schema_version","1.0.0").put("reason","Export my user records").put("purpose","GRU_RECORD_EXPORT").put("format","JSON");request.set("basis",basis(1));
  S.request(S.canonical(request).getBytes(StandardCharsets.UTF_8));assertions++;
  var unknown=request.deepCopy().put("object_ref","secret");reject(()->S.validate("Request",unknown));
  var bad=basis(2);bad.withArray("message_manifest").get(1).deepCopy();((ObjectNode)bad.withArray("message_manifest").get(1)).put("sequence","2");bad.put("message_manifest_sha256",S.hash(bad.required("message_manifest")));reject(()->S.validate("Basis",bad));
  var duplicate=basis(2);((ObjectNode)duplicate.withArray("message_manifest").get(1)).put("message_id",ID);duplicate.put("message_manifest_sha256",S.hash(duplicate.required("message_manifest")));reject(()->S.validate("Basis",duplicate));
  reject(()->S.validate("Basis",basis(0).put("message_manifest_sha256",HASH)));
  reject(()->S.validate("Basis",basis(1).put("conversation_revision","3")));
  var doc=basis(1);doc.remove("current_context");doc.put("purpose","GRU_RECORD_EXPORT").put("format","JSON");var msg=doc.putArray("messages").addObject().put("message_id",ID).put("sequence","2").put("text","Actual user text").put("intent","QUERY").put("execution_state","NOT_EXECUTED").put("created_at","2026-10-18T00:00:00Z").put("content_sha256",HASH);
  S.validate("Document",doc);assertions++;msg.put("sender_kind","AGENT");reject(()->S.validate("Document",doc));msg.remove("sender_kind");msg.put("content_sha256","b".repeat(64));reject(()->S.validate("Document",doc));
  reject(()->S.request("{\"schema_version\":\"1.0.0\",\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8)));
  reject(()->S.request((S.canonical(request)+" {}").getBytes(StandardCharsets.UTF_8)));
  System.out.println("PASS "+assertions+" isolated export contract assertions");
 }
}

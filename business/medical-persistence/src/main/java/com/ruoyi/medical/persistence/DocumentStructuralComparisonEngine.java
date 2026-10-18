package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Pure, complete fixed-body structural comparison; no permission, artifact, clinical or merge claim. */
public final class DocumentStructuralComparisonEngine {
 public record Limits(int maximumNodes,int maximumCells,int maximumRelationships,int maximumInputBytes,int maximumResultBytes,int maximumRecords,int maximumTextCodePoints,long maximumWork,long maximumMilliseconds){
  public Limits{if(maximumNodes<1||maximumCells<1||maximumRelationships<1||maximumInputBytes<1||maximumResultBytes<1||maximumRecords<1||maximumTextCodePoints<1||maximumWork<1||maximumMilliseconds<1||maximumMilliseconds>86_400_000)throw new IllegalArgumentException("COMPARISON_CAPACITY_REQUIRED");}
 }
 private final DocumentStructuralComparisonSchema schema;private final PermissionSchema json=new PermissionSchema();
 public DocumentStructuralComparisonEngine(DocumentStructuralComparisonSchema schema)throws Exception{this.schema=Objects.requireNonNull(schema);}
 /** Complete provenance check: the trusted request, fixed bodies and registered limits must come from the caller's real authorities. */
 public ObjectNode verifyAgainstInputs(InterfaceProtocol.CompareRequest request,InterfaceProtocol.DocumentBody left,InterfaceProtocol.DocumentBody right,JsonNode candidate,Limits limits)throws Exception {
  Objects.requireNonNull(candidate);if(ContentReadAuthorization.canonicalBytes(candidate).length>limits.maximumResultBytes())capacity();
  var actual=compare(request,left,right,limits);
  if(!schema.canonical(actual).equals(schema.canonical(candidate)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  return actual;
 }
 public ObjectNode compare(InterfaceProtocol.CompareRequest request,InterfaceProtocol.DocumentBody left,InterfaceProtocol.DocumentBody right,Limits limits)throws Exception {
  Objects.requireNonNull(request);Objects.requireNonNull(left);Objects.requireNonNull(right);Objects.requireNonNull(limits);
  var budget=new Budget(limits);preflight(left,budget);preflight(right,budget);var l=json.tree(left);var r=json.tree(right);
  for(var body:List.of(l,r)){byte[] bytes=ContentReadAuthorization.canonicalBytes(body);if(bytes.length>limits.maximumInputBytes())capacity();budget.take(bytes.length);DocumentBodySemantics.verify(schema.catalogue,body,new DocumentBodySemantics.Limits(limits.maximumNodes(),limits.maximumCells(),limits.maximumRelationships()));for(var node:body.path("nodes"))if(node.at("/content/kind").asText().equals("TEXT")){String text=node.at("/content/text").asText();budget.text(text.codePointCount(0,text.length()));}}
  if(request.leftRevision().equals(request.rightRevision())&&!schema.hash(l).equals(schema.hash(r)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
  var output=json.object().put("schema_version","1.0.0").put("kind","FIXED_DOCUMENT_STRUCTURE_CORE").put("algorithm_id","urn:medical:fixed-structure:stable-ids-prefix-suffix:1.0.0")
   .put("implementation_sha256",implementation()).put("schema_sha256",schema.schemaSha256()).put("body_schema_sha256",schema.catalogue.schemaDigest("interfaces"))
   .put("left_body_sha256",schema.hash(l)).put("right_body_sha256",schema.hash(r)).put("semantic_assessment","NOT_PERFORMED");
  output.set("request",json.tree(request));output.set("limits",json.tree(limits));output.put("input_sha256",schema.hash(output));
  var records=output.putArray("records");var affected=new TreeSet<String>();
  var ln=index(l.path("nodes"),"node_id");var rn=index(r.path("nodes"),"node_id");
  emit(records,"BODY_METADATA","body",metadata(l),metadata(r),null,budget);
  compareCategory(records,"NODE",ln,rn,affected,budget);
  compareCategory(records,"FIELD",index(l.path("fields"),"field_id"),index(r.path("fields"),"field_id"),affected,budget);
  compareCategory(records,"EVIDENCE",index(l.path("evidence_links"),"evidence_link_id"),index(r.path("evidence_links"),"evidence_link_id"),affected,budget);
  compareCategory(records,"FACT",facts(l.path("fact_usages")),facts(r.path("fact_usages")),affected,budget);
  for(String category:List.of("nodes","fields","evidence_links","fact_usages")){
   String id=switch(category){case "nodes"->"node_id";case "fields"->"field_id";case "evidence_links"->"evidence_link_id";default->"fact_version_id";};
   emit(records,"ORDER",category,ids(l.path(category),id),ids(r.path(category),id),null,budget);
  }
  for(String id:union(ln,rn)){JsonNode a=table(ln.get(id)),b=table(rn.get(id));if(a==null&&b==null)continue;
   tableAxis(records,id,"TABLE_ROW","row_ids",a,b,budget);tableAxis(records,id,"TABLE_COLUMN","column_ids",a,b,budget);
   var ac=index(a==null?json.object().arrayNode():a.path("cells"),"cell_id");var bc=index(b==null?json.object().arrayNode():b.path("cells"),"cell_id");
   for(String cell:union(ac,bc))emit(records,"TABLE_CELL",id+":"+cell,ac.get(cell),bc.get(cell),null,budget);
   emit(records,"ORDER",id+":cells",a==null?null:ids(a.path("cells"),"cell_id"),b==null?null:ids(b.path("cells"),"cell_id"),null,budget);
  }
  // Resolve changed field/fact associations to their actual nodes on each side, without inventing fact lineage.
  for(var rec:records)if(!"UNCHANGED".equals(rec.path("status").asText()))for(String side:List.of("before_json","after_json"))if(!rec.path(side).isNull()){
   JsonNode value=new com.fasterxml.jackson.databind.json.JsonMapper().readTree(rec.path(side).asText());
   for(var item:items(value)){if(item.has("node_id"))affected.add(item.path("node_id").asText());for(var n:item.path("node_ids"))affected.add(n.asText());
   for(var f:item.path("field_ids"))for(var body:List.of(l,r))for(var field:body.path("fields")){budget.take(1);if(f.asText().equals(field.path("field_id").asText()))affected.add(field.path("node_id").asText());}}
  }
  // A raw serialization order change is retained even when stable node ordinals are unchanged.
  for(String category:List.of("nodes","fields","evidence_links","fact_usages")){
   String idKey=switch(category){case "nodes"->"node_id";case "fields"->"field_id";case "evidence_links"->"evidence_link_id";default->"fact_version_id";};
   var a=l.path(category);var b=r.path(category);for(int i=0;i<Math.max(a.size(),b.size());i++){
    budget.take(1);var x=i<a.size()?a.get(i):null;var y=i<b.size()?b.get(i):null;if(x!=null&&y!=null&&x.path(idKey).equals(y.path(idKey)))continue;
    for(var item:java.util.Arrays.asList(x,y))if(item!=null){if(item.has("node_id"))affected.add(item.path("node_id").asText());for(var n:item.path("node_ids"))affected.add(n.asText());for(var f:item.path("field_ids"))for(var body:List.of(l,r))for(var field:body.path("fields")){budget.take(1);if(f.asText().equals(field.path("field_id").asText()))affected.add(field.path("node_id").asText());}}
   }
  }
  output.set("affected_node_ids",json.tree(affected));output.set("left_affected_section_ids",json.tree(sections(ln,affected,budget)));output.set("right_affected_section_ids",json.tree(sections(rn,affected,budget)));
  output.put("record_count",records.size());output.putObject("coverage").put("selection_sha256",output.path("input_sha256").asText()).put("delivered_sha256",schema.hash(records)).put("complete",true).putNull("next_cursor");
  budget.take(1);var envelope=json.object();output.fields().forEachRemaining(e->{if(!e.getKey().equals("records"))envelope.set(e.getKey(),e.getValue());});envelope.putArray("records");
  budget.output(ContentReadAuthorization.canonicalBytes(envelope).length);var verified=schema.validate(output);budget.take(1);return verified;
 }
 /** Charge the actual quadratic rectangle overlap work before entering the frozen validator. */
 private void preflight(InterfaceProtocol.DocumentBody body,Budget budget){
  if(body.nodes().size()>budget.limits.maximumNodes())capacity();
  budget.take((long)body.nodes().size()+body.fields().size()+body.evidenceLinks().size()+body.factUsages().size());long cells=0;
  for(var node:body.nodes())if(node.content() instanceof InterfaceProtocol.TableContent table){
   long n=table.cells().size();if(n>budget.limits.maximumCells()-cells)capacity();cells+=n;
   budget.take(n*(n-1)/2);budget.take((long)table.rowIds().size()+table.columnIds().size()+n);
  }
 }
 private void compareCategory(ArrayNode out,String category,Map<String,JsonNode> left,Map<String,JsonNode> right,Set<String> affected,Budget budget)throws Exception {
  for(String id:union(left,right)){var a=left.get(id);var b=right.get(id);JsonNode text=null;
   if(category.equals("NODE")&&a!=null&&b!=null&&a.at("/content/kind").asText().equals("TEXT")&&b.at("/content/kind").asText().equals("TEXT"))text=text(id,a.at("/content/text").asText(),b.at("/content/text").asText(),budget);
   emit(out,category,id,a,b,text,budget);if(category.equals("NODE")&&!Objects.equals(a,b))affected.add(id);
  }
 }
 private void tableAxis(ArrayNode out,String node,String category,String key,JsonNode a,JsonNode b,Budget budget)throws Exception {
  var left=indexIds(a==null?json.object().arrayNode():a.path(key));var right=indexIds(b==null?json.object().arrayNode():b.path(key));
  for(String id:union(left,right))emit(out,category,node+":"+id,left.get(id),right.get(id),null,budget);
  emit(out,"ORDER",node+":"+key,a==null?null:a.path(key),b==null?null:b.path(key),null,budget);
 }
 private ObjectNode text(String id,String a,String b,Budget budget)throws Exception{
  int[] x=a.codePoints().toArray(),y=b.codePoints().toArray();budget.take((long)x.length+y.length);int start=0;
  while(start<x.length&&start<y.length&&x[start]==y[start]){budget.take(1);start++;}
  int endX=x.length,endY=y.length;while(endX>start&&endY>start&&x[endX-1]==y[endY-1]){budget.take(1);endX--;endY--;}
  var result=json.object().put("node_id",id);result.set("left",selection(start,endX,new String(x,start,endX-start)));result.set("right",selection(start,endY,new String(y,start,endY-start)));return result;
 }
 private ObjectNode selection(int start,int end,String quote)throws Exception{return json.object().put("start",start).put("end",end).put("quote",quote).put("quote_sha256",WireValues.sha256(quote.getBytes(StandardCharsets.UTF_8)));}
 private void emit(ArrayNode out,String category,String id,JsonNode a,JsonNode b,JsonNode text,Budget budget)throws Exception {
  budget.take(1);if(out.size()>=budget.limits.maximumRecords())capacity();var row=json.object().put("category",category).put("identity",id).put("status",a==null?"ADDED":b==null?"REMOVED":a.equals(b)?"UNCHANGED":"CHANGED");
  for(String side:List.of("before","after")){var value=side.equals("before")?a:b;if(value==null){row.putNull(side+"_json").putNull(side+"_sha256");}else{String canonical=schema.canonical(value);budget.take(canonical.length());row.put(side+"_json",canonical).put(side+"_sha256",WireValues.sha256(canonical.getBytes(StandardCharsets.UTF_8)));}}
  row.set("text_change",text==null?NullNode.instance:text);
  budget.output((long)ContentReadAuthorization.canonicalBytes(row).length+(out.isEmpty()?0:1));out.add(row);
 }
 private JsonNode metadata(JsonNode body){return json.object().put("schema_version",body.path("schema_version").asText()).put("template_version",body.path("template_version").asText());}
 private Map<String,JsonNode> index(JsonNode items,String key){var m=new TreeMap<String,JsonNode>();for(var item:items)if(m.put(item.path(key).asText(),item)!=null)throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);return m;}
 private List<JsonNode> items(JsonNode value){var out=new ArrayList<JsonNode>();if(value.isArray())value.forEach(out::add);else out.add(value);return out;}
 private Map<String,JsonNode> facts(JsonNode items){var out=new TreeMap<String,JsonNode>();for(var item:items)((ArrayNode)out.computeIfAbsent(item.path("fact_version_id").asText(),k->json.object().arrayNode())).add(item);return out;}
 private Map<String,JsonNode> indexIds(JsonNode items){var m=new TreeMap<String,JsonNode>();for(var item:items)m.put(item.asText(),json.object().put("id",item.asText()));return m;}
 private ArrayNode ids(JsonNode items,String key){var out=json.object().arrayNode();for(var item:items)out.add(item.path(key).asText());return out;}
 private static Set<String> union(Map<String,JsonNode>a,Map<String,JsonNode>b){var out=new TreeSet<>(a.keySet());out.addAll(b.keySet());return out;}
 private JsonNode table(JsonNode node){return node!=null&&node.at("/content/kind").asText().equals("TABLE")?node.path("content"):null;}
 private Set<String> sections(Map<String,JsonNode> nodes,Set<String> affected,Budget budget){var out=new TreeSet<String>();for(String id:affected){var node=nodes.get(id);while(node!=null){budget.take(1);if(node.at("/content/kind").asText().equals("SECTION"))out.add(node.path("node_id").asText());node=nodes.get(node.path("parent_id").asText());}}return out;}
 private String implementation()throws Exception{var hashes=new TreeMap<String,String>();for(var type:List.of(DocumentStructuralComparisonEngine.class,Budget.class,Limits.class,DocumentStructuralComparisonSchema.class,DocumentBodySemantics.class)){try(var in=Objects.requireNonNull(type.getResourceAsStream(type.getName().substring(type.getName().lastIndexOf('.')+1)+".class"))){hashes.put(type.getName(),WireValues.sha256(in.readAllBytes()));}}return schema.hash(hashes);}
 private static final class Budget{final Limits limits;final long start=System.nanoTime();long work,text,output;Budget(Limits l){limits=l;}void take(long n){if(n>limits.maximumWork()-work)capacity();work+=n;if(System.nanoTime()-start>limits.maximumMilliseconds()*1_000_000)capacity();}void output(long n){if(n>limits.maximumResultBytes()-output)capacity();output+=n;take(1);}void text(long n){if(n>limits.maximumTextCodePoints()-text)capacity();text+=n;take(n);}}
 private static void capacity(){throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);}
}

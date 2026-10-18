package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.AgentExecutionPolicyProtocol.*;

@Component public final class AgentExecutionPolicySchema {
 public static final String ID="urn:medical:agent-execution-policy:1.0.0";
 public static final String PURPOSE="AGENT_PUBLICATION";
 public static final int MAXIMUM_BYTES=1048576;
 private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
   DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
 private final PermissionSchema json;private final JsonSchema schema;private final SchemaCatalogue catalogue;private final String digest,manifest;
 public AgentExecutionPolicySchema(PermissionSchema json)throws Exception {
  this.json=Objects.requireNonNull(json);catalogue=SchemaCatalogue.fromClasspath();byte[] bytes;
  try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/agent-execution-policy.schema.json"))){bytes=input.readAllBytes();}
  digest=WireValues.sha256(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
  schema.initializeValidators();manifest=hash(Map.of("schema_version","1.0.0","schema_sha256",digest,"permission_manifest",json.manifestSha256()));
 }
 public Author author(byte[] body)throws IOException {var request=bind("Author",body,Author.class);ProjectSchema.text(request.reason(),2000);input(request.definition());return request;}
 public Decide decide(byte[] body)throws IOException {var request=bind("Decide",body,Decide.class);ProjectSchema.text(request.reason(),2000);positive(request.expectedRevision());return request;}
 public Definition definition(String value)throws IOException {var d=bind("Definition",value.getBytes(StandardCharsets.UTF_8),Definition.class);input(asInput(d));components(d);return d;}
 public Fixed fixed(String value)throws IOException {var f=bind("Fixed",value.getBytes(StandardCharsets.UTF_8),Fixed.class);definition(canonical(f.definition()));if(!hash(f.definition()).equals(f.definitionSha256()))bad();return f;}
 public Decision decision(String value)throws IOException {var d=bind("Decision",value.getBytes(StandardCharsets.UTF_8),Decision.class);positive(d.previousRevision());positive(d.revision());positive(d.authorityRevision());ProjectSchema.text(d.reason(),2000);return d;}
 public <T>T bind(String name,byte[] body,Class<T> type)throws IOException {
  if(body==null||body.length==0||body.length>MAXIMUM_BYTES)invalid();
  try {var node=reader.readTree(body);validate(name,node);return reader.treeToValue(node,type);}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 public JsonNode validate(String name,JsonNode node){if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty())invalid();return node;}
 public JsonNode tree(Object value){return json.tree(value);}public JsonNode read(String value)throws IOException{return reader.readTree(value);}
 public String canonical(Object value)throws IOException{return json.canonicalObject(value);}public String hash(Object value)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(canonical(value).getBytes(StandardCharsets.UTF_8));}
 public String manifest(){return manifest;}public String schemaSha256(){return digest;}
 public Definition materialize(DefinitionInput input)throws IOException {
  input(input);var source=new SourceSelection(new PermissionProtocol.Basis(UUID.randomUUID().toString(),hash(input.sourceSelection())),input.sourceSelection().mode(),input.sourceSelection().sources());
  var retrieval=new ArrayList<RetrievalProfile>();for(var r:input.retrievalProfiles())retrieval.add(new RetrievalProfile(new PermissionProtocol.Basis(UUID.randomUUID().toString(),hash(r)),r.mode(),r.maximumResults(),r.permittedFields(),r.indexConfiguration()));
  var n=input.networkPolicy();var network=new NetworkPolicy(new PermissionProtocol.Basis(UUID.randomUUID().toString(),hash(n)),n.httpsOrigins(),n.externalRequestsAllowed());
  var d=new Definition(input.schemaVersion(),input.projectId(),input.businessPurpose(),input.profileEvidencePurpose(),input.allowedClassifications(),source,retrieval,network,input.worker(),input.bindings(),input.locale(),input.timezone(),input.environment(),input.region(),input.trainingAllowed(),input.validFrom(),input.validUntil());
  return definition(canonical(d));
 }
 public DefinitionInput asInput(Definition d){return new DefinitionInput(d.schemaVersion(),d.projectId(),d.businessPurpose(),d.profileEvidencePurpose(),d.allowedClassifications(),
  new SourceSelectionInput(d.sourceSelection().mode(),d.sourceSelection().sources()),d.retrievalProfiles().stream().map(r->new RetrievalProfileInput(r.mode(),r.maximumResults(),r.permittedFields(),r.indexConfiguration())).toList(),
  new NetworkPolicyInput(d.networkPolicy().httpsOrigins(),d.networkPolicy().externalRequestsAllowed()),d.worker(),d.bindings(),d.locale(),d.timezone(),d.environment(),d.region(),d.trainingAllowed(),d.validFrom(),d.validUntil());}
 public record ComponentContent(PermissionProtocol.Basis version,String kind,JsonNode content) {public ComponentContent{content=content.deepCopy();}@Override public JsonNode content(){return content.deepCopy();}}
 public List<ComponentContent> components(Definition d)throws IOException {
  var items=new ArrayList<ComponentContent>();items.add(new ComponentContent(d.sourceSelection().version(),"SOURCE_SCOPE",tree(new SourceSelectionInput(d.sourceSelection().mode(),d.sourceSelection().sources()))));
  for(var r:d.retrievalProfiles())items.add(new ComponentContent(r.version(),"RETRIEVAL_PROFILE",tree(new RetrievalProfileInput(r.mode(),r.maximumResults(),r.permittedFields(),r.indexConfiguration()))));
  items.add(new ComponentContent(d.networkPolicy().version(),"NETWORK_POLICY",tree(new NetworkPolicyInput(d.networkPolicy().httpsOrigins(),d.networkPolicy().externalRequestsAllowed()))));
  var identities=new HashSet<String>();var roots=dependencies(d).stream().map(PermissionProtocol.Basis::version_id).collect(java.util.stream.Collectors.toSet());
  for(var c:items)if(!identities.add(c.version().version_id())||roots.contains(c.version().version_id())||!hash(c.content()).equals(c.version().content_sha256()))bad();return List.copyOf(items);
 }
 public List<PermissionProtocol.Basis> dependencies(Definition d){return dependencies(asInput(d));}
 public List<PermissionProtocol.Basis> dependencies(DefinitionInput d){
  var refs=new TreeMap<String,PermissionProtocol.Basis>();for(var source:d.sourceSelection().sources())add(refs,source);
  for(var r:d.retrievalProfiles())add(refs,r.indexConfiguration());
  for(var b:d.bindings()){add(refs,b.roleVersion());if(b.modelVersion()!=null)add(refs,b.modelVersion());if(b.tokenizerVersion()!=null)add(refs,b.tokenizerVersion());for(var a:b.actions())if(a.fixedVersion()!=null)add(refs,a.fixedVersion());}
  return List.copyOf(refs.values());
 }
 public static String entityKind(String kind){return switch(kind){case "SOURCE_SCOPE"->"AGENT_SOURCE_SCOPE_POLICY";case "RETRIEVAL_PROFILE"->"AGENT_RETRIEVAL_PROFILE";case "NETWORK_POLICY"->"AGENT_NETWORK_POLICY";default->throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);};}
 private void input(DefinitionInput d)throws IOException {
  validate("DefinitionInput",tree(d));ProjectSchema.text(d.environment(),128);ProjectSchema.text(d.region(),128);
  if(d.trainingAllowed()||d.networkPolicy().externalRequestsAllowed()||!d.networkPolicy().httpsOrigins().isEmpty())throw new TenantAccessException(ErrorCode.NETWORK_SCOPE_DENIED);
  try {new Locale.Builder().setLanguageTag(d.locale()).build();if(d.locale().equals("und"))invalid();ZoneId.of(d.timezone());var uri=URI.create(d.worker().serviceUri());if(!uri.isAbsolute()||uri.getFragment()!=null||uri.getUserInfo()!=null||uri.getQuery()!=null)invalid();}
  catch(IllegalArgumentException|DateTimeException|IllformedLocaleException e){invalid();}
  ProjectSchema.text(d.worker().audience(),256);var from=Instant.parse(d.validFrom());if(d.validUntil()!=null&&!from.isBefore(Instant.parse(d.validUntil())))invalid();
  var sources=new HashSet<String>();for(var s:d.sourceSelection().sources())if(!sources.add(s.version_id()))invalid();
  var retrieval=new HashSet<String>();for(var r:d.retrievalProfiles())if(!retrieval.add(r.indexConfiguration().version_id()))invalid();
  var nodes=new HashSet<String>();
  for(var b:d.bindings()) {
   if(!nodes.add(b.nodeKind()))invalid();
   if(b.modelVersion()==null){if(!b.nodeKind().equals("N00")||b.tokenizerVersion()!=null||b.tokenizerArtifact()!=null||!b.generationParameters().isEmpty())invalid();}
   else {
    if(b.tokenizerVersion()==null||b.tokenizerArtifact()==null)invalid();var artifact=b.tokenizerArtifact().artifact();
    if(!b.tokenizerVersion().version_id().equals(artifact.artifactId())||!b.tokenizerVersion().content_sha256().equals(artifact.sha256())||WireValues.revision(artifact.sizeBytes()).signum()<=0)invalid();
    catalogue.requirePublished(b.tokenizerArtifact().schema().schemaRef(),b.tokenizerArtifact().schema().schemaSha256());
   }
   var actionKeys=new HashSet<String>();for(var a:b.actions()){
    if(!actionKeys.add(a.targetSelector()+":"+a.resourceKind()+":"+a.action()+":"+a.purpose()+":"+Objects.toString(a.fixedVersion(),"")))invalid();
    switch(a.targetSelector()) {
     case "TASK" -> {if(!a.resourceKind().equals("TASK")||!Set.of("task.read","task.run").contains(a.action()))invalid();}
     case "PROJECT" -> {if(!a.resourceKind().equals("PROJECT")||!a.action().equals("project.read"))invalid();}
     case "SELECTED_SOURCE" -> {if(!a.resourceKind().equals("SOURCE")||!a.action().equals("source.read"))invalid();}
     case "FIXED_VERSION" -> {if(a.fixedVersion()==null||!a.action().equals(a.resourceKind().toLowerCase(Locale.ROOT)+".read"))invalid();}
     case "CURRENT_EXECUTION_POLICY" -> {if(!a.resourceKind().equals("CONFIGURATION")||!a.action().equals("configuration.read")||!a.purpose().equals("TASK_EXECUTION"))invalid();}
     case "RUN_REGISTERED_ARTIFACT" -> {if(!a.resourceKind().equals("ARTIFACT")||!a.action().equals("artifact.read")||a.artifactKinds().isEmpty())invalid();}
     default -> invalid();
    }
    if(!a.targetSelector().equals("FIXED_VERSION")&&a.fixedVersion()!=null)invalid();
    if(!a.targetSelector().equals("RUN_REGISTERED_ARTIFACT")&&!a.artifactKinds().isEmpty())invalid();
   }
  }dependencies(d);
 }
 private static void add(Map<String,PermissionProtocol.Basis> values,PermissionProtocol.Basis ref){WireValues.uuid(ref.version_id());WireValues.hash(ref.content_sha256());var previous=values.putIfAbsent(ref.version_id(),ref);if(previous!=null&&!previous.equals(ref))bad();}
 private static void positive(String value){if(PermissionSchema.revision(value).signum()<=0)invalid();}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

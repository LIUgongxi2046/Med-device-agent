package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Pure fixed-document consistency checks. A CheckedInputs value is never permission or proof of statistical independence. */
@Component
public final class EvaluationAcceptanceInputSchema {
 public static final String SCHEMA_ID="urn:medical:evaluation-acceptance-inputs:1.0.0";
 public static final String DATASET_REF=SCHEMA_ID+"#/$defs/FormalEvaluationDataset";
 public static final String SAMPLING_DESIGN_REF=SCHEMA_ID+"#/$defs/SamplingDesign";
 public static final int MAXIMUM_DOCUMENT_BYTES=2*1024*1024;
 private final PermissionSchema json;private final EvaluationSchema evaluations;private final AgentPublicationSchema publications;
 private final JsonSchema schema;private final String schemaSha256;
 private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
   DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
 public EvaluationAcceptanceInputSchema(PermissionSchema json,EvaluationSchema evaluations,AgentPublicationSchema publications)throws IOException{
  this.json=Objects.requireNonNull(json);this.evaluations=Objects.requireNonNull(evaluations);this.publications=Objects.requireNonNull(publications);
  byte[] bytes;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/evaluation-acceptance-inputs.schema.json"))){bytes=in.readAllBytes();}
  schemaSha256=DigestUtils.sha256Hex(bytes);schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());schema.initializeValidators();
 }
 public String schemaSha256(){return schemaSha256;}
 /** Use on actual downloaded bytes, before passing trees to verify. Raw SOURCE hashes must be checked by the reader separately. */
 public JsonNode parse(String definition,byte[] bytes)throws IOException{
  if(bytes==null||bytes.length==0||bytes.length>MAXIMUM_DOCUMENT_BYTES)invalid();
  try{return validate(definition,reader.readTree(bytes));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 }
 public JsonNode validate(String definition,JsonNode value){
  if(!Set.of("FormalEvaluationDataset","SamplingDesign").contains(definition))throw new IllegalArgumentException("Unknown acceptance input definition");
  if(value==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();return value;
 }
 public CheckedInputs verify(JsonNode dataset,JsonNode sampleManifest,JsonNode samplingDesign,JsonNode policyAuthor)throws IOException{
  validate("FormalEvaluationDataset",dataset);validate("SamplingDesign",samplingDesign);evaluations.validate("SampleManifest",sampleManifest);publications.validate("PolicyAuthor",policyAuthor);
  String datasetJson=canonical(dataset),manifestJson=canonical(sampleManifest),designJson=canonical(samplingDesign);
  for(String document:List.of(datasetJson,manifestJson,designJson))if(document.getBytes(StandardCharsets.UTF_8).length>MAXIMUM_DOCUMENT_BYTES)invalid();
  var datasetRef=basis(policyAuthor.required("dataset"));var manifestRef=basis(policyAuthor.required("sample_manifest_artifact"));var designRef=basis(policyAuthor.required("sampling_design_evidence"));
  if(!datasetRef.equals(basis(samplingDesign.required("dataset")))||!manifestRef.equals(basis(samplingDesign.required("sample_manifest_artifact"))))mismatch();
  if(!manifestRef.content_sha256().equals(DigestUtils.sha256Hex(manifestJson)))mismatch();
  var allRefs=new LinkedHashMap<String,PermissionProtocol.Basis>();for(var b:List.of(datasetRef,manifestRef,designRef))remember(allRefs,b);
  var requiredDimensions=new LinkedHashSet<String>();Integer repeats=null;
  for(var rule:policyAuthor.required("dimensions")){if(!requiredDimensions.add(rule.required("dimension").asText()))invalid();int next=rule.required("repeat_count").intValue();if(repeats!=null&&repeats!=next)invalid();repeats=next;}
  var candidates=new HashSet<String>();for(var c:policyAuthor.required("candidate_refs")){var b=basis(c);remember(allRefs,b);if(!candidates.add(b.version_id()))invalid();}remember(allRefs,basis(policyAuthor.required("rule_revision")));
  var samples=new ArrayList<EvaluationProtocol.Sample>();var bySample=new LinkedHashMap<String,EvaluationProtocol.Sample>();var familySamples=new LinkedHashMap<String,List<String>>();
  var inputRefs=new LinkedHashMap<String,PermissionProtocol.Basis>();var expectedRefs=new LinkedHashMap<String,PermissionProtocol.Basis>();var inputFamilies=new HashMap<String,String>();var inputDigestFamilies=new HashMap<String,String>();
  for(var node:sampleManifest){
   var s=reader.treeToValue(node,EvaluationProtocol.Sample.class);if(bySample.putIfAbsent(s.sample_id(),s)!=null)invalid();
   if(!"REAL".equals(s.evidence_class())||!"ACCEPTANCE".equals(s.split()))invalid();ProjectSchema.text(s.document_type(),64);
   if(!s.expected_ref().content_sha256().equals(evaluations.hash(evaluations.expectedItems(s))))mismatch();
   var dimensions=new HashSet<String>();for(var expected:s.expectations()){
    if(!dimensions.add(expected.dimension()))invalid();if(!"EVAL-11".equals(expected.dimension())&&!expected.control_item_ids().isEmpty())invalid();
    for(String item:expected.applicable_item_ids())ProjectSchema.text(item,256);for(String item:expected.control_item_ids())ProjectSchema.text(item,256);
   }
   if(!dimensions.containsAll(requiredDimensions))mismatch();
   requireFamily(inputFamilies,s.input_ref().version_id(),s.source_family());requireFamily(inputDigestFamilies,s.input_ref().content_sha256(),s.source_family());remember(allRefs,s.input_ref());remember(allRefs,s.expected_ref());remember(inputRefs,s.input_ref());remember(expectedRefs,s.expected_ref());
   familySamples.computeIfAbsent(s.source_family(),unused->new ArrayList<>()).add(s.sample_id());samples.add(s);
  }
  if(samples.isEmpty()||(long)samples.size()*candidates.size()*requiredDimensions.size()*repeats>10000)invalid();
  var datasetSamples=new HashSet<String>();var sourceRefs=new LinkedHashMap<String,PermissionProtocol.Basis>();var sourceFamilies=new HashMap<String,String>();
  for(var entry:dataset.required("samples")){
   String id=entry.required("sample_id").asText();if(!datasetSamples.add(id))invalid();var sample=bySample.get(id);if(sample==null)mismatch();
   if(!sample.source_family().equals(entry.required("source_family").asText())||!sample.input_ref().equals(basis(entry.required("input_ref")))||!sample.expected_ref().equals(basis(entry.required("expected_ref"))))mismatch();
   var versions=new HashSet<String>();for(var raw:entry.required("source_versions")){var source=basis(raw);if(!versions.add(source.version_id()))invalid();requireFamily(sourceFamilies,source.version_id(),sample.source_family());remember(sourceRefs,source);remember(allRefs,source);}
  }
  if(!datasetSamples.equals(bySample.keySet()))mismatch();
  var designFamilies=new HashSet<String>();var designSamples=new HashSet<String>();
  for(var group:samplingDesign.required("families")){
   String family=group.required("source_family").asText();if(!designFamilies.add(family))invalid();var actual=familySamples.get(family);if(actual==null)mismatch();var ids=new HashSet<String>();for(var id:group.required("sample_ids")){if(!ids.add(id.asText())||!designSamples.add(id.asText()))invalid();}
   if(!ids.equals(new HashSet<>(actual)))mismatch();
  }
  if(!designFamilies.equals(familySamples.keySet())||!designSamples.equals(bySample.keySet()))mismatch();
  for(var rule:policyAuthor.required("dimensions"))if(rule.required("minimum_families").intValue()>familySamples.size())mismatch();
  var evidenceRefs=new LinkedHashMap<String,PermissionProtocol.Basis>();for(var raw:samplingDesign.required("independence_evidence_sources")){var b=basis(raw);if(evidenceRefs.putIfAbsent(b.version_id(),b)!=null)invalid();remember(allRefs,b);}
  return new CheckedInputs(datasetRef,manifestRef,designRef,samples,familySamples,new ArrayList<>(sourceRefs.values()),new ArrayList<>(evidenceRefs.values()),new ArrayList<>(inputRefs.values()),new ArrayList<>(expectedRefs.values()),datasetJson,manifestJson,designJson);
 }
 /** Immutable descriptive result. The caller must still verify actual domains, original bytes, lineage, current rights and human approval. */
 public record CheckedInputs(PermissionProtocol.Basis dataset,PermissionProtocol.Basis sampleManifest,PermissionProtocol.Basis samplingDesign,
  List<EvaluationProtocol.Sample> samples,Map<String,List<String>> familySamples,List<PermissionProtocol.Basis> sourceVersions,
  List<PermissionProtocol.Basis> samplingEvidenceSources,List<PermissionProtocol.Basis> inputRefs,List<PermissionProtocol.Basis> expectedRefs,
  String canonicalDataset,String canonicalSampleManifest,String canonicalSamplingDesign){
  public CheckedInputs{samples=List.copyOf(samples);var copy=new LinkedHashMap<String,List<String>>();familySamples.forEach((key,value)->copy.put(key,List.copyOf(value)));familySamples=Collections.unmodifiableMap(copy);sourceVersions=List.copyOf(sourceVersions);samplingEvidenceSources=List.copyOf(samplingEvidenceSources);inputRefs=List.copyOf(inputRefs);expectedRefs=List.copyOf(expectedRefs);}
  public String datasetCanonicalSha256(){return DigestUtils.sha256Hex(canonicalDataset);}
  public String sampleManifestCanonicalSha256(){return DigestUtils.sha256Hex(canonicalSampleManifest);}
  public String samplingDesignCanonicalSha256(){return DigestUtils.sha256Hex(canonicalSamplingDesign);}
 }
 private String canonical(JsonNode value)throws IOException{return json.canonical(value);}
 private static PermissionProtocol.Basis basis(JsonNode value){return new PermissionProtocol.Basis(value.required("version_id").asText(),value.required("content_sha256").asText());}
 private static void requireFamily(Map<String,String> families,String version,String family){var existing=families.putIfAbsent(version,family);if(existing!=null&&!existing.equals(family))mismatch();}
 private static void remember(Map<String,PermissionProtocol.Basis> refs,PermissionProtocol.Basis basis){var existing=refs.putIfAbsent(basis.version_id(),basis);if(existing!=null&&!existing.equals(basis))mismatch();}
 private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
 private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

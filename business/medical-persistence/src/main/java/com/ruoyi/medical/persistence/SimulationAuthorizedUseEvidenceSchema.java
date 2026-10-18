package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Local, immutable evidence snapshot schema; it never grants permission. */
@Component
public final class SimulationAuthorizedUseEvidenceSchema {
    public static final String REF="urn:medical:simulation-authorized-use-evidence:1.0.0#/$defs/Document";
    public static final String PURPOSE="SIMULATION_EVALUATION";
    private static final String RESOURCE="/simulation-authorized-use-evidence.schema.json";
    private final JsonMapper reader=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(com.fasterxml.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final JsonSchema schema; private final PermissionSchema json; private final EvaluationSchema evaluations;
    public SimulationAuthorizedUseEvidenceSchema(PermissionSchema json,EvaluationSchema evaluations)throws IOException {
        this.json=json;this.evaluations=evaluations;
        schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes()),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        schema.initializeValidators();
    }
    public static String schemaRef(){return REF;}
    public static String documentSha256()throws IOException{return schemaDocumentSha256();}
    public static String schemaDocumentSha256()throws IOException{return DigestUtils.sha256Hex(bytes());}
    public String manifestSha256()throws IOException{return schemaDocumentSha256();}
    private static byte[] bytes()throws IOException{try(var in=Objects.requireNonNull(SimulationAuthorizedUseEvidenceSchema.class.getResourceAsStream(RESOURCE))){return in.readAllBytes();}}
    public JsonNode readCanonical(byte[] bytes)throws IOException {
        if(bytes==null||bytes.length==0||bytes.length>1024*1024)bad();
        var value=reader.readTree(bytes);SimulationInvocationSchema.numbers(value);validate("Document",value);
        if(!Arrays.equals(bytes,json.canonical(value).getBytes(StandardCharsets.UTF_8)))bad();return value;
    }
    public void validate(String name,JsonNode node){if(node==null||!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(node).isEmpty())bad();}
    public List<EvaluationProtocol.Sample> samples(JsonNode specification)throws IOException {
        validate("Specification",specification);var result=new ArrayList<EvaluationProtocol.Sample>();var ids=new HashSet<String>();var familySplits=new HashMap<String,String>();var inputFamilies=new HashMap<String,String>();
        for(var node:specification.required("samples")) {
            var sample=reader.treeToValue(node,EvaluationProtocol.Sample.class);
            if(!"SIMULATION".equals(sample.evidence_class())||!ids.add(sample.sample_id())
                ||!evaluations.hash(evaluations.expectedItems(sample)).equals(sample.expected_ref().content_sha256()))bad();
            var split=familySplits.putIfAbsent(sample.source_family(),sample.split());
            var family=inputFamilies.putIfAbsent(sample.input_ref().version_id(),sample.source_family());
            if(split!=null&&!split.equals(sample.split())||family!=null&&!family.equals(sample.source_family()))bad();
            if(sample.expectations().stream().map(EvaluationProtocol.Expectation::dimension).distinct().count()!=sample.expectations().size())bad();
            for(var expectation:sample.expectations())if(!"EVAL-11".equals(expectation.dimension())&&!expectation.control_item_ids().isEmpty())bad();
            result.add(sample);
        }
        if(!hash(specification.required("samples")).equals(specification.required("sample_manifest_artifact").required("content_sha256").textValue()))bad();
        return List.copyOf(result);
    }
    public String hash(JsonNode value)throws IOException{return DigestUtils.sha256Hex(json.canonical(value));}
    static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

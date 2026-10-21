package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

/** Validates a closed server-selected authoring contract and its actual published package kinds. */
@Component
public final class TaskProfessionalPackageAuthoringSchema {
    static final int MAXIMUM_BYTES=1_048_576, MAXIMUM_REQUEST_BYTES=2_200_000, MAXIMUM_CHOICES=256;
    static final String OPERATION="TASK-PROFESSIONAL-PACKAGE", PURPOSE="AGENT_PUBLICATION";
    static final Set<String> KINDS=Set.of("PROMPT","PROFILE","ROLE");
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final JsonSchema contract; private final SchemaCatalogue published; private final PermissionSchema json;
    private final String manifest, packageHash, v2PackageHash;
    private final TaskProfessionalPackageAuthoringProfile profile;
    @org.springframework.beans.factory.annotation.Autowired
    public TaskProfessionalPackageAuthoringSchema(PermissionSchema json)throws Exception {this(json,TaskProfessionalPackageAuthoringProfile.V1);}
    private TaskProfessionalPackageAuthoringSchema(PermissionSchema json,TaskProfessionalPackageAuthoringProfile profile)throws Exception {
        this.json=json;this.profile=profile;published=SchemaCatalogue.fromClasspath();byte[] bytes=resource(profile.contractResource);
        manifest=WireValues.sha256(bytes);packageHash=WireValues.sha256(resource(packageResource()));v2PackageHash=profile==TaskProfessionalPackageAuthoringProfile.V2?WireValues.sha256(resource("task-professional-package-v2.schema.json")):null;
        contract=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(reader.readTree(bytes),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        contract.initializeValidators();for(String kind:KINDS)published.requirePublished(ref(kind),packageHash(kind));
    }
    JsonNode parse(byte[] bytes)throws IOException {if(bytes.length==0||bytes.length>MAXIMUM_REQUEST_BYTES)invalid();try{return reader.readTree(bytes);}catch(com.fasterxml.jackson.core.JsonProcessingException e){invalid();return null;}}
    JsonNode read(String value)throws IOException{return parse(value.getBytes(StandardCharsets.UTF_8));}
    void validate(String definition,JsonNode value){if(value==null||!contract.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(definition)).validate(value).isEmpty())invalid();}
    JsonNode request(byte[] bytes)throws IOException {var n=parse(bytes);validateRequest(n);return n;}
    void validateRequest(JsonNode n)throws IOException {
        validate("Request",n);String kind=n.required("kind").textValue();byte[] bytes=bytes(n.required("package"));if(bytes.length>MAXIMUM_BYTES)invalid();
        published.bindPublished(ref(kind),packageHash(kind),bytes); // Also rejects invalid Unicode/noncanonical numeric values.
        var value=n.required("package");
        if(profile==TaskProfessionalPackageAuthoringProfile.SECTION_WRITER_N04_V2){switch(kind){case "PROMPT"->TaskSectionWriterPromptCompiler.validatePrompt(value,"2.0.0");case "ROLE"->TaskSectionWriterPromptCompiler.validateRole(value,"2.0.0");case "PROFILE"->TaskSectionWriterPromptCompiler.validateProfile(value,"2.0.0");default->invalid();}}
        if(profile==TaskProfessionalPackageAuthoringProfile.SOURCE_VERIFIER_N05_V2){switch(kind){case "PROMPT"->TaskSourceVerifierPromptCompiler.validatePrompt(value,"2.0.0");case "ROLE"->TaskSourceVerifierPromptCompiler.validateRole(value,"2.0.0");case "PROFILE"->TaskSourceVerifierPromptCompiler.validateProfile(value,"2.0.0");default->invalid();}}

        if("PROMPT".equals(kind)&&(value.required("system_instructions").textValue().isBlank()||utf8(value.required("system_instructions").textValue())>262144))invalid();
        if("PROFILE".equals(kind)) {if(utf8(value.required("system_prefix").textValue())>262144)invalid();var paths=new HashSet<String>();for(var file:value.required("files")){String path=file.required("relative").textValue();if(!safeProfilePath(path)||!paths.add(path))invalid();}}
        var ids=new HashSet<String>();for(var source:n.required("content_sources"))if(!ids.add(source.required("version_id").textValue()))invalid();
    }
    static boolean safeProfilePath(String path){if(path==null||!path.startsWith("profiles/")||path.indexOf('\\')>=0||path.indexOf('%')>=0||path.codePoints().anyMatch(c->c<32||c>=127&&c<=159))return false;for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;return true;}
    void key(String key){validate("Key",json.tree(key));}
    String ref(String kind){if(!KINDS.contains(kind))invalid();return packageUri()+profile.payloadVersion(kind)+"#/$defs/"+switch(kind){case "PROMPT"->"PromptPackage";case "PROFILE"->"ProfilePackage";default->"RolePackage";};}
    ObjectNode descriptor(String kind){return json.object().put("schema_ref",ref(kind)).put("schema_sha256",packageHash(kind));}
    String hash(JsonNode n)throws IOException{return org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes(n));} byte[] bytes(JsonNode n)throws IOException{return json.canonical(n).getBytes(StandardCharsets.UTF_8);}
    String canonical(JsonNode n)throws IOException{return json.canonical(n);} String manifest(){return manifest;} String packageHash(){return packageHash;}
    TaskProfessionalPackageAuthoringSchema v2()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.V2);}
    TaskProfessionalPackageAuthoringSchema planDraft()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.PLAN_DRAFT_N01);}
    TaskProfessionalPackageAuthoringSchema sectionWriter()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.SECTION_WRITER_N04);}
    TaskProfessionalPackageAuthoringSchema sourceVerifier()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.SOURCE_VERIFIER_N05);}
    TaskProfessionalPackageAuthoringSchema documentReview()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.DOCUMENT_REVIEW_N07);}
    TaskProfessionalPackageAuthoringSchema sectionWriterV2()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.SECTION_WRITER_N04_V2);}
    TaskProfessionalPackageAuthoringSchema sourceVerifierV2()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.SOURCE_VERIFIER_N05_V2);}
    TaskProfessionalPackageAuthoringSchema documentReviewV2()throws Exception{return new TaskProfessionalPackageAuthoringSchema(json,TaskProfessionalPackageAuthoringProfile.DOCUMENT_REVIEW_N07_V2);}
    private String packageResource(){return switch(profile){case SECTION_WRITER_N04_V2->"task-section-writer-package-v2.schema.json";case SOURCE_VERIFIER_N05_V2->"task-source-verifier-package-v2.schema.json";case DOCUMENT_REVIEW_N07_V2->"document-review-package-v2.schema.json";case PLAN_DRAFT_N01->"task-plan-draft-package.schema.json";case SECTION_WRITER_N04->"task-section-writer-package.schema.json";case SOURCE_VERIFIER_N05->"task-source-verifier-package.schema.json";case DOCUMENT_REVIEW_N07->"document-review-package.schema.json";default->"task-professional-package.schema.json";};}
    private String packageUri(){return "urn:medical:"+packageResource().replace("-v2.schema.json",":").replace(".schema.json",":");}
    TaskProfessionalPackageAuthoringProfile profile(){return profile;}
    String version(){return profile.version;}
    String packageHash(String kind){if(!KINDS.contains(kind))invalid();return profile==TaskProfessionalPackageAuthoringProfile.V2&&!"ROLE".equals(kind)?v2PackageHash:packageHash;}
    String packageHashForRef(String ref){for(String kind:KINDS)if(ref(kind).equals(ref))return packageHash(kind);invalid();return null;}
    void bindProfile(ObjectNode value,String project){if(profile!=TaskProfessionalPackageAuthoringProfile.V1)value.put("authoring_profile",profile.name()).put("authoring_route",profile.route(project)).put("authoring_contract_sha256",manifest).put("proof_operation",profile.proofOperation);}
    void requireProfile(JsonNode value,String project){var expected=json.object();bindProfile(expected,project);for(String field:List.of("authoring_profile","authoring_route","authoring_contract_sha256","proof_operation"))if(!Objects.equals(expected.get(field),value.get(field)))bad();}
    JsonNode envelope(JsonNode data,String revision){return json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),revision,data));}
    static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(n.required("version_id").textValue(),n.required("content_sha256").textValue());}
    static PermissionProtocol.Basis artifactBasis(JsonNode n){return new PermissionProtocol.Basis(n.required("artifact_id").textValue(),n.required("sha256").textValue());}
    static int utf8(String s){return s.getBytes(StandardCharsets.UTF_8).length;}
    private byte[] resource(String name)throws IOException{try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/"+name))){return in.readAllBytes();}}
    static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);} static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

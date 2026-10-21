package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskProfessionalPromptV2Test.id;

/** Actual compiler + actual predecessor GET orchestration, with explicit SQL/package/authority
 * doubles. Does not register real artifacts, approve packages, or invoke a model. */
class TaskProfessionalPromptV2CompilationTest {
    TaskProfessionalPredecessorEvidenceTest f;PermissionSchema json;TaskProfessionalPromptCompiler compiler;
    TaskProfessionalPredecessorEvidence.Reads evidence;TaskProfessionalRuntimeService.Envelope envelope;
    TaskProfessionalHostAccess.InputContent physicalInput;TaskInvocationPackages.Read role,prompt,profile;
    ObjectNode promptDocument,profileDocument,roleDocument;JsonNode replay;
    @BeforeEach void setup()throws Exception{initialize(true);}
    @AfterEach void cleanup(){f.cleanup();}
    void initialize(boolean v2)throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(false);f=new TaskProfessionalPredecessorEvidenceTest();f.setup();json=new PermissionSchema();var examples=new TaskProfessionalPromptCompilerTest();
        when(f.a.formalProof().identity().tenant()).thenReturn(id(5));when(f.a.formalProof().identity().actor()).thenReturn(id(10));when(f.a.formalProof().identity().generation()).thenReturn("1");when(f.a.formalProof().identity().workerService()).thenReturn("spiffe://fixture/worker");when(f.a.formalProof().identity().action()).thenReturn(id(40));
        var roleBasis=new PermissionProtocol.Basis(id(30),"3".repeat(64));var promptBasis=new PermissionProtocol.Basis(id(31),"4".repeat(64));f.manifest.set("role_version",json.tree(roleBasis));if(!v2)f.manifest.withArray("predecessors").removeAll();
        profileDocument=(ObjectNode)examples.profile();profileDocument.set("adapter_artifact",examples.artifact(53));promptDocument=(ObjectNode)examples.prompt();
        if(v2){profileDocument.put("schema_version","2.0.0").put("projection",TaskProfessionalPromptV2.PROJECTION);promptDocument.put("schema_version","2.0.0").put("input_encoding",TaskProfessionalPromptV2.ENCODING);}
        var profileArtifact=descriptor(50,profileDocument);roleDocument=(ObjectNode)examples.role();roleDocument.set("prompt_basis",json.tree(promptBasis));roleDocument.set("profile_artifact",profileArtifact);
        String v1Hash=resourceHash("task-professional-package.schema.json"),v2Hash=resourceHash("task-professional-package-v2.schema.json");
        role=read(51,roleDocument,TaskProfessionalPromptCompiler.ROLE_REF,v1Hash);prompt=read(52,promptDocument,v2?TaskProfessionalPromptV2.PROMPT_REF:TaskProfessionalPromptCompiler.PROMPT_REF,v2?v2Hash:v1Hash);profile=read(50,profileDocument,v2?TaskProfessionalPromptV2.PROFILE_REF:TaskProfessionalPromptCompiler.PROFILE_REF,v2?v2Hash:v1Hash);
        var deployment=json.object().put("role_name","Researcher");deployment.set("profile_artifact",profileArtifact);var files=deployment.putArray("profile_files");for(var file:profileDocument.required("files"))files.addObject().put("relative",file.required("relative").textValue()).put("sha256",file.required("sha256").textValue());when(f.deployment.value()).thenReturn(deployment);when(f.deployment.sha256()).thenReturn("d".repeat(64));
        var r=json.object();var release=r.putObject("agent_release");release.putArray("prompt_revisions").add(json.tree(promptBasis));var runtime=release.putObject("runtime_manifest");runtime.putObject("profile").set("artifact",profileArtifact);runtime.putArray("event_adapters").addObject().put("component","C07").set("implementation",profileDocument.required("adapter_artifact"));replay=r;
        var identities=mock(FormalCapabilityIdentityAuthority.class);var roleIdentity=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);var promptIdentity=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);var roleArtifact=role.artifact();var promptArtifact=prompt.artifact();when(roleIdentity.sourceArtifact()).thenReturn(roleArtifact);when(promptIdentity.sourceArtifact()).thenReturn(promptArtifact);when(identities.requireRegisteredIdentity(id(5),id(10),roleBasis,"ROLE",id(6))).thenReturn(roleIdentity);when(identities.requireRegisteredIdentity(id(5),id(10),promptBasis,"PROMPT",id(6))).thenReturn(promptIdentity);
        var data=mock(DataSource.class);var connection=mock(Connection.class);var statement=mock(PreparedStatement.class);when(data.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(statement);when(statement.executeQuery()).thenAnswer(c->{var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);when(rows.getString(1)).thenReturn(json.canonical(replay));return rows;});
        compiler=new TaskProfessionalPromptCompiler(data,identities,f.inputs,json,f.service);envelope=mock(TaskProfessionalRuntimeService.Envelope.class);when(envelope.admission()).thenReturn(f.a);when(envelope.input()).thenReturn(f.input);when(envelope.deployment()).thenReturn(f.deployment);when(envelope.actionId()).thenReturn(id(40));
        physicalInput=mock(TaskProfessionalHostAccess.InputContent.class);when(physicalInput.bytes(envelope)).thenAnswer(c->ContentReadAuthorization.canonicalBytes(f.manifest));
        evidence=v2?f.service.readWorker(f.hosts,f.handle):null;TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    TaskProfessionalPromptCompiler.CompiledPrompt compile()throws Exception{return evidence==null?compiler.compile(envelope,physicalInput,role,prompt,profile):compiler.compile(envelope,physicalInput,role,prompt,profile,evidence);}
    @Test void realCompilerConsumesOnlyOriginalPhysicalBundleAndEmitsClosedV2Pins()throws Exception{
        var result=compile();assertTrue(result.acceptsPredecessors());result.requireCurrent(f.a);assertEquals(TaskProfessionalPromptV2.COMPILER,result.canonicalPins().required("compiler").textValue());assertEquals(evidence.canonicalPins(),result.canonicalPins().required("predecessor_evidence"));assertEquals(3,result.contentMembers().size());assertEquals(f.body,new com.fasterxml.jackson.databind.json.JsonMapper().readTree(result.launchText()).required("predecessor_evidence").get(0).required("evidence_bundle"));assertEquals(1,f.physicalReads.get());
    }
    @Test void explicitV2WithoutPhysicalReadsCapabilityIsDenied() {assertThrows(TenantAccessException.class,()->compiler.compile(envelope,physicalInput,role,prompt,profile));}
    @Test void v2PromptWithV1ProfileNeverDowngrades() {when(profile.schemaRef()).thenReturn(TaskProfessionalPromptCompiler.PROFILE_REF);assertThrows(TenantAccessException.class,this::compile);}
    @Test void packageReadRevocationPreventsCompilationEvenWithPriorEvidence()throws Exception{doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(prompt).requireCurrent(f.a);assertThrows(TenantAccessException.class,this::compile);}
    @Test void originalCompletionChangeInvalidatesAlreadyCompiledProof()throws Exception{var compiled=compile();when(f.complete.completionReceiptSha256()).thenReturn("0".repeat(64));assertThrows(TenantAccessException.class,()->compiled.requireCurrent(f.a));}
    @Test void realV1CompilerKeepsOriginalPayloadPinsAndNoPredecessorIo()throws Exception{
        initialize(false);var result=compile();assertFalse(result.acceptsPredecessors());assertFalse(result.canonicalPins().has("predecessor_evidence"));assertEquals("N02_LITERAL_INSTRUCTIONS_AND_REGISTERED_INPUT_V1",result.canonicalPins().required("compiler").textValue());assertEquals("1.0.0",result.canonicalPins().required("schema_version").textValue());assertArrayEquals(ContentReadAuthorization.canonicalBytes(TaskProfessionalPromptCompiler.compileDocument(promptDocument,f.manifest)),result.launchText().getBytes(java.nio.charset.StandardCharsets.UTF_8));assertEquals(0,f.physicalReads.get());
    }
    TaskInvocationPackages.Read read(int n,JsonNode value,String ref,String schemaHash)throws Exception{
        var read=mock(TaskInvocationPackages.Read.class);byte[] bytes=ContentReadAuthorization.canonicalBytes(value);var artifact=new PermissionProtocol.Basis(id(n),WireValues.sha256(bytes));when(read.artifact()).thenReturn(artifact);when(read.bytes()).thenReturn(bytes);when(read.schemaRef()).thenReturn(ref);when(read.schemaSha256()).thenReturn(schemaHash);when(read.contentMembers()).thenReturn(List.of());
        var pins=json.object();pins.set("artifact",json.tree(artifact));pins.set("object_ref",new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(new InterfaceProtocol.ObjectRef("tenants/"+id(5)+"/package/"+n,"package-original",artifact.content_sha256(),Integer.toString(bytes.length),"application/json")));pins.put("schema_ref",ref).put("schema_sha256",schemaHash).put("authorization_sha256","5".repeat(64));pins.putArray("content_members");when(read.canonicalPins()).thenReturn(pins);return read;
    }
    ObjectNode descriptor(int n,JsonNode value)throws Exception{byte[] bytes=ContentReadAuthorization.canonicalBytes(value);return json.object().put("artifact_id",id(n)).put("sha256",WireValues.sha256(bytes)).put("size_bytes",Integer.toString(bytes.length)).put("media_type","application/json");}
    String resourceHash(String name)throws Exception{try(var in=getClass().getResourceAsStream("/"+name)){return WireValues.sha256(Objects.requireNonNull(in).readAllBytes());}}
}

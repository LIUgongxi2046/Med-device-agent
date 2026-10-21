package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.jgrapht.graph.DirectedPseudograph;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.TaskProfessionalPromptV2Test.id;

/** Real producer ledger + original physical GET + registration -> real TASK package GET ->
 * actual compiler. SQL/storage/current-authority boundaries are explicit local doubles;
 * no fixture creates a live approval, deployment, grant or provider request. */
class TaskProfessionalPromptV2ProducerRoundtripTest {
    TaskProfessionalPromptV2CompilationTest consumer;
    TaskProfessionalPackageAuthoringV2Fixture producer;
    TaskProfessionalPackageAuthoringRegistration.Registered prompt,profile,role;
    PackageReads reads;
    @BeforeEach void setup()throws Exception {
        consumer=new TaskProfessionalPromptV2CompilationTest();consumer.initialize(true);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        producer=new TaskProfessionalPackageAuthoringV2Fixture(id(5),id(6),id(10));
        prompt=producer.publish("PROMPT",consumer.promptDocument);
        profile=producer.publish("PROFILE",consumer.profileDocument);
        consumer.roleDocument.set("profile_artifact",profile.status().required("artifact"));
        role=producer.publish("ROLE",consumer.roleDocument);
        reads=new PackageReads(consumer.f,producer.registry,producer.bytes);
    }
    @AfterEach void cleanup(){if(consumer!=null)consumer.cleanup();}
    void prepareActualReads()throws Exception {
        consumer.role=reads.read(role);consumer.prompt=reads.read(prompt);consumer.profile=reads.read(profile);
        var deployment=(ObjectNode)consumer.f.deployment.value();deployment.set("profile_artifact",profile.status().required("artifact"));
        ((ObjectNode)consumer.replay.required("agent_release").required("runtime_manifest").required("profile")).set("artifact",profile.status().required("artifact"));
        var capabilities=(FormalCapabilityIdentityAuthority)ReflectionTestUtils.getField(consumer.compiler,"capabilities");
        var roleIdentity=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);var promptIdentity=mock(FormalCapabilityIdentityAuthority.RegisteredIdentity.class);
        var roleArtifact=consumer.role.artifact();var promptArtifact=consumer.prompt.artifact();when(roleIdentity.sourceArtifact()).thenReturn(roleArtifact);when(promptIdentity.sourceArtifact()).thenReturn(promptArtifact);
        when(capabilities.requireRegisteredIdentity(id(5),id(10),new PermissionProtocol.Basis(id(30),"3".repeat(64)),"ROLE",id(6))).thenReturn(roleIdentity);
        when(capabilities.requireRegisteredIdentity(id(5),id(10),new PermissionProtocol.Basis(id(31),"4".repeat(64)),"PROMPT",id(6))).thenReturn(promptIdentity);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    @Test void acceptedProducerOriginalsReachActualPhysicalPackageReaderAndActualCompiler()throws Exception {
        prepareActualReads();var compiled=consumer.compile();compiled.requireCurrent(consumer.f.a);
        assertTrue(compiled.acceptsPredecessors());assertEquals(3,reads.physicalReads.get());
        assertArrayEquals(prompt.original().bytes(),consumer.prompt.bytes());assertArrayEquals(profile.original().bytes(),consumer.profile.bytes());assertArrayEquals(role.original().bytes(),consumer.role.bytes());
        for(var pair:List.of(Map.entry("prompt_package",prompt),Map.entry("profile_package",profile),Map.entry("role_package",role))){
            var pin=compiled.canonicalPins().required(pair.getKey());var registered=pair.getValue();
            assertEquals(registered.original().text("artifact_id"),pin.required("artifact").required("version_id").textValue());
            assertEquals(registered.reference().sha256(),pin.required("artifact").required("content_sha256").textValue());
            assertEquals(registered.reference().objectVersion(),pin.required("object_ref").required("objectVersion").textValue());
            assertEquals(registered.status().required("schema").required("schema_ref"),pin.required("schema_ref"));
            assertFalse(registered.status().required("production_eligible").booleanValue());
        }
        assertEquals(TaskProfessionalPromptV2.COMPILER,compiled.canonicalPins().required("compiler").textValue());
        assertEquals("1.0.0",new com.fasterxml.jackson.databind.json.JsonMapper().readTree(role.original().bytes()).required("schema_version").textValue());
    }
    @Test void registeredMetadataCannotSubstituteForChangedOriginalPhysicalBytes()throws Exception {
        byte[] changed=producer.bytes.get(prompt.reference()).clone();changed[0]='!';producer.bytes.put(prompt.reference(),changed);
        assertThrows(TenantAccessException.class,()->reads.read(prompt));
        assertEquals(1,reads.physicalReads.get());
    }
    @Test void missingActualInputCannotSelectLegacyModelSendPath()throws Exception {
        prepareActualReads();assertThrows(NullPointerException.class,()->consumer.prompt.requireModelSend(consumer.f.a,"DOCUMENT_BODY","provider-test",(TaskNodeInputAuthority.Verified)null));
        assertThrows(NullPointerException.class,()->reads.packages.requireCurrentPins(consumer.f.a,id(6),consumer.prompt.canonicalPins(),"DOCUMENT_BODY","provider-test",(TaskNodeInputAuthority.Verified)null));
    }
    @Test void registrationDoesNotImplyCurrentReadPermission()throws Exception {
        doThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED)).when(reads.governance).require(eq(consumer.f.a),eq(id(6)),anyList(),eq("AGENT_PUBLICATION"));
        assertThrows(TenantAccessException.class,()->reads.read(prompt));assertEquals(0,reads.physicalReads.get());
    }

    /** The final Read is constructed only by production readWorker after physical exact-version
     * reads. Published original registry rows are used unchanged. Authority doubles are visibly
     * separate from accepted registrations and can independently revoke the read. */
    static final class PackageReads {
        final TaskProfessionalPredecessorEvidenceTest host;final ArtifactRegistryMapper registry;final Map<InterfaceProtocol.ObjectRef,byte[]> objects;
        final AtomicInteger physicalReads=new AtomicInteger();final AgentPublicationDependencies governance=mock(AgentPublicationDependencies.class);
        final TaskInvocationPackages packages;
        PackageReads(TaskProfessionalPredecessorEvidenceTest host,ArtifactRegistryMapper registry,Map<InterfaceProtocol.ObjectRef,byte[]> objects)throws Exception {
            this.host=host;this.registry=registry;this.objects=objects;
            when(host.a.formalProof().identity().executionKind()).thenReturn("TASK");
            var sources=mock(SourceReadMapper.class);var lineage=mock(DependencyLineage.class);var authority=mock(ArtifactSourceAuthority.class);var profiles=mock(EvaluationReferences.class);var transport=mock(SourceUploadTransport.class);
            var policy=new EvaluationDependencyPolicy("LOCAL","LOCAL",256,512,List.of());
            when(governance.require(eq(host.a),eq(id(6)),anyList(),eq("AGENT_PUBLICATION"))).thenAnswer(c->{List<PermissionProtocol.Basis> roots=c.getArgument(2);var proof=new PermissionSchema().object();for(var root:roots)proof.put(root.version_id(),root.content_sha256());return new AgentPublicationDependencies.Checked(roots.stream().map(PermissionProtocol.Basis::version_id).toList(),proof);});
            when(lineage.inspect(anyString(),any())).thenAnswer(c->closure(c.getArgument(0)));
            when(profiles.profile(anyString(),eq("AGENT_PUBLICATION"),eq("PROJECT"))).thenAnswer(c->{String ref=c.getArgument(0);String resource=ref.startsWith("urn:medical:task-professional-package:2.0.0#")?"task-professional-package-v2.schema.json":"task-professional-package.schema.json";try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/"+resource))){String hash=WireValues.sha256(in.readAllBytes());return new EvaluationDependencyPolicy.ArtifactProfile("PROJECT","project.read",ref,hash,hash);}});
            when(authority.require(eq(host.a),anyString(),any(),anyList(),any(),anyMap())).thenAnswer(c->{String artifact=c.getArgument(1);var row=one(registry.artifact(artifact));var binding=one(registry.binding(artifact));var members=registry.members(row.accessSetId());assertTrue(members.isEmpty(),"This fixture must not invent source rights");var manifest=new DependencyManifestSchema().verify(one(registry.manifest(row.accessSetId())),members);return new ArtifactSourceAuthority.VerifiedSources(new ArtifactRegistryProtocol.Registration(row,binding,manifest,null,null,closure(artifact)),Map.of(),Map.of());});
            when(transport.readTaskDocument(eq(id(5)),eq(id(10)),anyInt(),any(),any())).thenAnswer(c->{
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());physicalReads.incrementAndGet();S3ObjectReader.Admission current=c.getArgument(3);current.requireCurrent();var reader=mock(S3ObjectReader.class);
                when(reader.readVerified(any(),any())).thenAnswer(call->{InterfaceProtocol.ObjectRef ref=call.getArgument(0);byte[] bytes=Objects.requireNonNull(objects.get(ref),"Only producer original objects exist");var physical=mock(S3ObjectReader.VerifiedObject.class);when(physical.reference()).thenReturn(ref);when(physical.fullSha256()).thenReturn(WireValues.sha256(bytes));when(physical.chunk(anyLong(),anyInt())).thenAnswer(chunk->{int offset=Math.toIntExact(chunk.getArgument(0,Long.class));return Arrays.copyOfRange(bytes,offset,offset+chunk.getArgument(1,Integer.class));});return physical;});
                var timed=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){current.requireCurrent();}public java.time.Duration remainingTime(){return java.time.Duration.ofSeconds(5);}};
                return ((SourceUploadTransport.DocumentRead<?>)c.getArgument(4)).apply(reader,timed);
            });
            packages=new TaskInvocationPackages(registry,sources,lineage,authority,governance,profiles,policy,transport);
        }
        TaskInvocationPackages.Read read(TaskProfessionalPackageAuthoringRegistration.Registered registered)throws Exception {
            var original=registered.original();var basis=new PermissionProtocol.Basis(original.text("artifact_id"),registered.reference().sha256());
            return packages.readWorker(host.hosts,host.handle,id(6),basis);
        }
        DependencyLineage.VerifiedLineage closure(String artifact){var graph=new DirectedPseudograph<String,SourceReadProtocol.ParentRow>(null,null,false);graph.addVertex(artifact);return new DependencyLineage.VerifiedLineage(artifact,graph,Map.of(artifact,one(registry.version(artifact))));}
        static <T>T one(List<T> values){assertEquals(1,values.size());return values.get(0);}
    }
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.SimulationFixtureMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.auth.credentials.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.AgentPublicationMixedPurposeTest.*;

/** Actual loopback HTTP GET and S3 reader. DB/admission/transport factory substitutes are synthetic; no production grant is created. */
final class EvaluationAcceptanceObjectReaderTest {
 final AgentPublicationMixedPurposeTest f=new AgentPublicationMixedPurposeTest();final AgentPublicationSchema schema=new AgentPublicationSchema(f.json);
 final EnterpriseAccess access=mock(EnterpriseAccess.class);final SourceUploadTransport transport=mock(SourceUploadTransport.class);final SimulationFixtureMapper originals=mock(SimulationFixtureMapper.class);final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class);
 final AtomicInteger requests=new AtomicInteger(),stateReads=new AtomicInteger(),governance=new AtomicInteger(1);final AtomicBoolean allowed=new AtomicBoolean(true),corrupt=new AtomicBoolean(),wrongVersion=new AtomicBoolean(),wrongRange=new AtomicBoolean(),wrongMedia=new AtomicBoolean(),wrongLength=new AtomicBoolean();final List<String> ranges=Collections.synchronizedList(new ArrayList<>());
 Runnable beforeTransport=()->{},onGet=()->{},afterTransport=()->{};byte[] served;volatile SimulationInvocationSources.Original original;Path spool;HttpServer server;EvaluationAcceptanceObjectReader reader;
 EvaluationAcceptanceObjectReaderTest()throws Exception{}
 @BeforeEach void prepare()throws Exception{
  when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR,"offline","Offline fixture",null));when(subject.sessionId()).thenReturn("offline-session");when(f.admission.watermarks()).thenAnswer(i->new TenantWatermarks(BigInteger.ONE,BigInteger.valueOf(governance.get())));
  when(access.execute(eq(subject),any())).thenAnswer(i->{stateReads.incrementAndGet();Function<EnterpriseAccess.Admission,Object> operation=i.getArgument(1);return f.transaction(()->operation.apply(f.admission));});
  doAnswer(i->{assertEquals(ACTOR,i.getArgument(0));String action=i.getArgument(1);return allowed.get()?f.grants.stream().filter(g->g.action().equals(action)).toList():List.of();}).when(f.permissions).resourceGrants(anyString(),anyString());
  when(originals.original(anyMap())).thenAnswer(i->{Map<String,Object> values=i.getArgument(0);assertEquals(ACTOR,values.get("userId"));assertEquals(PRIMARY,values.get("sourceVersion"));return List.of(original);});
  sourceBytes("{\"samples\":[],\"schema_version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
  spool=Files.createTempDirectory("acceptance-reader-contract-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/fixture/",exchange->{
   requests.incrementAndGet();String range=exchange.getRequestHeaders().getFirst("Range");ranges.add(range);
   if(!"GET".equals(exchange.getRequestMethod())||!"versionId=fixed-v1".equals(exchange.getRequestURI().getRawQuery())){exchange.sendResponseHeaders(400,-1);exchange.close();return;}
   String[] positions=range.substring(6).split("-");int start=Integer.parseInt(positions[0]),end=Integer.parseInt(positions[1]);byte[] bytes=Arrays.copyOfRange(served,start,end+1);if(corrupt.get())bytes[0]^=1;
   exchange.getResponseHeaders().set("Content-Type",wrongMedia.get()?"application/octet-stream":"application/json");exchange.getResponseHeaders().set("Content-Range",wrongRange.get()?"bytes 0-0/1":"bytes "+start+"-"+end+"/"+served.length);exchange.getResponseHeaders().set("x-amz-version-id",wrongVersion.get()?"replaced-v2":"fixed-v1");onGet.run();
   if(wrongLength.get())bytes=Arrays.copyOf(bytes,bytes.length+1);exchange.sendResponseHeaders(206,bytes.length);try(var out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
  });server.start();
  when(transport.readDocument(eq(subject),eq(TENANT),anyInt(),any(),any())).thenAnswer(i->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES,(int)i.getArgument(2));var current=(S3ObjectReader.Admission)i.getArgument(3);beforeTransport.run();current.requireCurrent();var lease=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){current.requireCurrent();}public Duration remainingTime(){return Duration.ofMinutes(2);}};
   SourceUploadTransport.DocumentRead<Object> callback=i.getArgument(4);Object result;try(var actual=new S3ObjectReader(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"fixture","us-east-1",S3ObjectReader.Network.LOCAL_LOOPBACK,StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-local-id","synthetic-local-secret")),spool,EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES,16)){result=callback.apply(actual,lease);}afterTransport.run();return result;
  });
  reader=new EvaluationAcceptanceObjectReader(access,transport,f.sourceAuthority,f.sources,originals,f.artifacts,f.dependencies,f.profiles,f.limits,schema);
 }
 void sourceBytes(byte[] bytes){served=bytes.clone();String digest=DigestUtils.sha256Hex(bytes);var source=f.sourceRows.get(PRIMARY);f.sourceRows.put(PRIMARY,new SourceReadProtocol.SourceRow(source.tenantId(),source.sourceId(),source.sourceVersion(),source.ownerProjectId(),source.ownership(),source.classification(),source.rightsRevision(),digest,digest,source.registeredProjectId()));f.versions.put(PRIMARY,new ArtifactRegistryProtocol.VersionRow(PRIMARY,PROJECT,"SOURCE_VERSION",digest));f.owners.put(PRIMARY,List.of(new VersionResourceProtocol.DomainOwnerRow("SOURCE_VERSION",new VersionResourceProtocol.OwnerRow(TENANT,PRIMARY,PROJECT,PROJECT,"SOURCE",sourceOwner(PRIMARY),digest,PRIMARY,null,null,null))));original=new SimulationInvocationSources.Original(TENANT,PRIMARY,digest,"tenants/"+TENANT+"/fixed-input.json","fixed-v1",BigInteger.valueOf(bytes.length),"application/json");}
 void artifactBytes(byte[] bytes)throws Exception{served=bytes.clone();String digest=DigestUtils.sha256Hex(bytes);var old=f.artifactRows.get(ROOT);var object=new InterfaceProtocol.ObjectRef("tenants/"+TENANT+"/fixed-manifest.json","fixed-v1",digest,Integer.toString(bytes.length),"application/json");f.artifactRows.put(ROOT,new ArtifactRegistryProtocol.ArtifactRow(TENANT,PROJECT,ROOT,"SAMPLE_MANIFEST",StoredObjectReferences.encode(object),object.objectKey(),object.objectVersion(),digest,BigInteger.valueOf(bytes.length),object.mediaType(),old.accessSetId(),digest,"1.0.0",old.schemaManifest(),"INTERNAL"));f.versions.put(ROOT,new ArtifactRegistryProtocol.VersionRow(ROOT,PROJECT,"ARTIFACT",digest));f.owners.put(ROOT,List.of(new VersionResourceProtocol.DomainOwnerRow("ARTIFACT",new VersionResourceProtocol.OwnerRow(TENANT,ROOT,PROJECT,PROJECT,"ARTIFACT",ROOT,digest,null,null,null,null))));}
 EvaluationAcceptanceObjectReader.VerifiedDocument source()throws Exception{return reader.source(subject,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE);}
 EvaluationAcceptanceObjectReader.VerifiedDocument artifact()throws Exception{return reader.artifact(subject,"1",PROJECT,f.basis(ROOT),ROOT_PURPOSE,EvaluationSchema.SAMPLE_MANIFEST_REF,f.children().stream().map(c->new AgentPublicationDependencies.TypedUse(c.root(),c.purpose(),List.of(),false)).toList());}
 static Throwable rootCause(Throwable failure){while(failure.getCause()!=null)failure=failure.getCause();return failure;}
 void code(ErrorCode expected,org.junit.jupiter.api.function.Executable action){var error=rootCause(assertThrows(Exception.class,action));assertInstanceOf(TenantAccessException.class,error);assertEquals(expected,((TenantAccessException)error).code());}
 @Test void genuineSourceOriginalIsReadWithBoundedVersionedGetAndCurrentProof()throws Exception{var document=source();assertEquals(new String(served,StandardCharsets.UTF_8),document.canonical());assertEquals(f.basis(PRIMARY),document.basis());assertEquals(original.reference(),document.object());assertTrue(requests.get()>1);for(var range:ranges){var p=range.substring(6).split("-");assertTrue(Integer.parseInt(p[1])-Integer.parseInt(p[0])+1<=16);}assertTrue(stateReads.get()>requests.get());f.transaction(()->{document.requireCurrent(f.admission);return null;});}
 @Test void genuineArtifactUsesMixedPurposeGraphAndDefensiveDocumentCopy()throws Exception{artifactBytes("[]".getBytes(StandardCharsets.UTF_8));var document=artifact();assertTrue(document.document().isArray());((com.fasterxml.jackson.databind.node.ArrayNode)document.document()).add("tamper");assertEquals(0,document.document().size());f.transaction(()->{document.requireCurrent(f.admission);return null;});}
 @Test void nullSubjectAndExistingTransactionCannotStartTransport(){code(ErrorCode.ACCESS_DENIED,()->reader.source(null,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE));TransactionSynchronizationManager.setActualTransactionActive(true);try{code(ErrorCode.CONTEXT_CHANGED,this::source);}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}assertEquals(0,requests.get());}
 @Test void initialMissingCurrentGrantStopsBeforeTransport(){allowed.set(false);code(ErrorCode.ACCESS_DENIED,this::source);assertEquals(0,requests.get());}
 @Test void revocationBetweenInitialStateAndGetStopsBeforeBytes(){beforeTransport=()->allowed.set(false);code(ErrorCode.ACCESS_DENIED,this::source);assertEquals(0,requests.get());}
 @Test void revocationDuringFirstHttpGetStopsBeforeSecondSegment(){onGet=()->allowed.set(false);code(ErrorCode.ACCESS_DENIED,this::source);assertEquals(1,requests.get());}
 @Test void finalRevocationAfterTransportCannotReturnVerifiedDocument(){afterTransport=()->allowed.set(false);code(ErrorCode.ACCESS_DENIED,this::source);assertTrue(requests.get()>0);}
 @Test void returnedPrivateProofRejectsLaterRevocation()throws Exception{var document=source();allowed.set(false);code(ErrorCode.ACCESS_DENIED,()->f.transaction(()->{document.requireCurrent(f.admission);return null;}));}
 @Test void returnedProofCannotBeReusedOutsideCurrentTransaction()throws Exception{var document=source();code(ErrorCode.ACCESS_DENIED,()->document.requireCurrent(f.admission));}
 @Test void watermarksChangedDuringReadAreStale(){onGet=()->governance.set(2);code(ErrorCode.SCOPE_STALE,this::source);assertEquals(1,requests.get());}
 @Test void originalObjectVersionChangedDuringGetIsStale(){onGet=()->{var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),r.sha256(),r.objectKey(),"replacement",r.sizeBytes(),r.mediaType());};code(ErrorCode.SCOPE_STALE,this::source);}
 @Test void changedStoredBytesNeverReturnAProof(){corrupt.set(true);code(ErrorCode.CONTENT_MISMATCH,this::source);assertTrue(requests.get()>0);}
 @Test void wrongS3ObjectVersionHeaderIsRejected(){wrongVersion.set(true);code(ErrorCode.CONTENT_MISMATCH,this::source);}
 @Test void wrongRangeHeaderIsRejected(){wrongRange.set(true);code(ErrorCode.CONTENT_MISMATCH,this::source);}
 @Test void wrongMediaHeaderIsRejected(){wrongMedia.set(true);code(ErrorCode.CONTENT_MISMATCH,this::source);}
 @Test void overlongSegmentIsRejected(){wrongLength.set(true);code(ErrorCode.CONTENT_MISMATCH,this::source);}
 @Test void prettySourceRetainsRawIdentityAndIndependentCanonicalSnapshot()throws Exception{sourceBytes("{ \"schema_version\": \"1.0.0\", \"samples\": [] }".getBytes(StandardCharsets.UTF_8));var document=source();assertEquals(DigestUtils.sha256Hex(served),document.basis().content_sha256());assertNotEquals(document.basis().content_sha256(),DigestUtils.sha256Hex(document.canonical()));assertEquals("1.0.0",document.document().required("schema_version").asText());}
 @Test void invalidUtf8CannotBeNormalizedIntoAcceptedJson(){sourceBytes(new byte[]{'{','"','x','"',':','"',(byte)0xff,'"','}'});assertInstanceOf(java.nio.charset.CharacterCodingException.class,rootCause(assertThrows(Exception.class,this::source)));}
 @Test void duplicateKeysAndTrailingJsonAreRejected()throws Exception{sourceBytes("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class,rootCause(assertThrows(Exception.class,this::source)));sourceBytes("{} {}".getBytes(StandardCharsets.UTF_8));assertInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class,rootCause(assertThrows(Exception.class,this::source)));}
 @Test void scalarJsonCannotBecomeVerifiedDocument(){sourceBytes("true".getBytes(StandardCharsets.UTF_8));code(ErrorCode.CONTENT_MISMATCH,this::source);}
 @Test void sourceOriginalFromAnotherTenantIsRejectedBeforeGet(){var r=original;original=new SimulationInvocationSources.Original(id(999),r.sourceVersion(),r.sha256(),r.objectKey(),r.objectVersion(),r.sizeBytes(),r.mediaType());code(ErrorCode.CONTENT_MISMATCH,this::source);assertEquals(0,requests.get());}
 @Test void sourceOriginalVersionMismatchIsRejectedBeforeGet(){var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),id(999),r.sha256(),r.objectKey(),r.objectVersion(),r.sizeBytes(),r.mediaType());code(ErrorCode.CONTENT_MISMATCH,this::source);assertEquals(0,requests.get());}
 @Test void sourceOriginalHashMismatchIsRejectedBeforeGet(){var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),hash("wrong"),r.objectKey(),r.objectVersion(),r.sizeBytes(),r.mediaType());code(ErrorCode.CONTENT_MISMATCH,this::source);assertEquals(0,requests.get());}
 @Test void tooLargeOriginalIsRejectedBeforeGet(){var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),r.sha256(),r.objectKey(),r.objectVersion(),BigInteger.valueOf(EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES+1L),r.mediaType());code(ErrorCode.FILE_TOO_LARGE,this::source);assertEquals(0,requests.get());}
 @Test void wrongStoredMediaTypeIsRejectedBeforeGet(){var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),r.sha256(),r.objectKey(),r.objectVersion(),r.sizeBytes(),"application/octet-stream");code(ErrorCode.CONTENT_MISMATCH,this::source);assertEquals(0,requests.get());}
 @Test void crossTenantOrTraversalObjectKeysAreRejectedBeforeGet(){for(String key:List.of("tenants/"+id(999)+"/x","tenants/"+TENANT+"/../x")){var r=original;original=new SimulationInvocationSources.Original(r.tenantId(),r.sourceVersion(),r.sha256(),key,r.objectVersion(),r.sizeBytes(),r.mediaType());code(ErrorCode.CONTENT_MISMATCH,this::source);}assertEquals(0,requests.get());}
 @Test void staleContextCannotStartGet(){code(ErrorCode.CONTEXT_CHANGED,()->reader.source(subject,"2",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE));assertEquals(0,requests.get());}
 @Test void notReceivedNotApprovedOrInactiveSourceCannotRead(){when(f.sources.gates(PRIMARY)).thenReturn(List.of(new SourceReadProtocol.GateRow(id(90),"RECEIVED","APPROVED","RECYCLED",BigInteger.ONE)));code(ErrorCode.SOURCE_NOT_READY,this::source);assertEquals(0,requests.get());}
 @Test void unsupportedArtifactSchemaCannotStartGet(){code(ErrorCode.CONTENT_MISMATCH,()->reader.artifact(subject,"1",PROJECT,f.basis(ROOT),ROOT_PURPOSE,f.inputSchemaRef,List.of()));assertEquals(0,requests.get());}
 @Test void wrongActualArtifactKindCannotStartGet()throws Exception{artifactBytes("[]".getBytes(StandardCharsets.UTF_8));var r=f.artifactRows.get(ROOT);f.artifactRows.put(ROOT,new ArtifactRegistryProtocol.ArtifactRow(r.tenantId(),r.projectId(),r.artifactId(),"EXPECTED_ITEMS",r.storedObject(),r.objectKey(),r.objectVersion(),r.sha256(),r.sizeBytes(),r.mediaType(),r.accessSetId(),r.contentSha256(),r.schemaVersion(),r.schemaManifest(),r.classification()));code(ErrorCode.CONTENT_MISMATCH,this::artifact);assertEquals(0,requests.get());}
 @Test void returnedProofRejectsContextRevisionChange()throws Exception{var document=source();when(f.admission.context()).thenReturn(new BusinessSessionProtocol.Context(TENANT,"2","1","1"));code(ErrorCode.CONTEXT_CHANGED,()->f.transaction(()->{document.requireCurrent(f.admission);return null;}));}
 @Test void returnedProofRejectsActorChange()throws Exception{var document=source();when(f.admission.actorId()).thenReturn(id(909));code(ErrorCode.ACCESS_DENIED,()->f.transaction(()->{document.requireCurrent(f.admission);return null;}));}
 @Test void returnedProofRejectsChangedYetStillActiveGrantWitness()throws Exception{var document=source();for(int n=0;n<f.grants.size();n++){var g=f.grants.get(n);if(sourceOwner(PRIMARY).equals(g.resourceId()))f.grants.set(n,new PermissionProtocol.GrantRow(g.grantId(),g.subjectId(),g.projectId(),g.resourceKind(),g.resourceId(),g.action(),g.condition(),g.status(),g.validFrom(),g.validTo(),g.basisVersion(),g.schemaVersion(),g.manifest(),BigInteger.TWO));}code(ErrorCode.SCOPE_STALE,()->f.transaction(()->{document.requireCurrent(f.admission);return null;}));}
 @Test void returnedProofRejectsChangedGateRevisionEvenWhenStatusStillReady()throws Exception{var document=source();when(f.sources.gates(PRIMARY)).thenReturn(List.of(new SourceReadProtocol.GateRow(id(90),"RECEIVED","APPROVED","ACTIVE",BigInteger.TWO)));code(ErrorCode.SCOPE_STALE,()->f.transaction(()->{document.requireCurrent(f.admission);return null;}));}
 @Test void frozenManifestArtifactCannotUsePrettyJsonRawIdentity()throws Exception{artifactBytes("[ ]".getBytes(StandardCharsets.UTF_8));code(ErrorCode.CONTENT_MISMATCH,this::artifact);}
 @Test void objectRawHashCannotAuthorizeDifferentRequestedBasis(){code(ErrorCode.CONTENT_MISMATCH,()->reader.source(subject,"1",PROJECT,new PermissionProtocol.Basis(PRIMARY,hash("replacement request")),ROOT_PURPOSE));assertEquals(0,requests.get());}
 @Test void sourceRightsMustMatchExplicitRequestedPurposeBeforeGet(){code(ErrorCode.ACCESS_DENIED,()->reader.source(subject,"1",PROJECT,f.basis(PRIMARY),INPUT_PURPOSE));assertEquals(0,requests.get());}
 @Test void validUtf16JsonIsNotAcceptedAsUtf8Original(){sourceBytes("{}".getBytes(StandardCharsets.UTF_16));assertInstanceOf(java.nio.charset.CharacterCodingException.class,rootCause(assertThrows(Exception.class,this::source)));}
 /** The control issuer/DB are explicit doubles here; actual bounded loopback GET and reader checks remain real. */
 final AtomicBoolean controlActive=new AtomicBoolean(true);
 EvidenceReadAccess controlReader()throws Exception {
  var control=mock(FormalRunControlAccess.Reads.class);
  doAnswer(i->{if(!controlActive.get())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return null;}).when(control).requireActive();
  when(control.actorId()).thenReturn(ACTOR);when(control.tenantId()).thenReturn(TENANT);
  doAnswer(i->{control.requireActive();if(!PRIMARY.equals(i.getArgument(0))||!f.basis(PRIMARY).content_sha256().equals(i.getArgument(1)))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return null;}).when(control).requireVersion(anyString(),anyString());
  when(control.execute(any())).thenAnswer(i->{control.requireActive();stateReads.incrementAndGet();Function<EnterpriseAccess.Admission,Object> operation=i.getArgument(0);return f.transaction(()->operation.apply(f.admission));});
  when(transport.readControlDocument(eq(control),anyInt(),any(),any())).thenAnswer(i->{
   assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES,(int)i.getArgument(1));
   var guard=(S3ObjectReader.Admission)i.getArgument(2);beforeTransport.run();
   var lease=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){control.requireActive();guard.requireCurrent();}public Duration remainingTime(){requireCurrent();return Duration.ofMinutes(2);}};
   lease.requireCurrent();SourceUploadTransport.DocumentRead<Object> callback=i.getArgument(3);Object result;
   try(var actual=new S3ObjectReader(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"fixture","us-east-1",S3ObjectReader.Network.LOCAL_LOOPBACK,StaticCredentialsProvider.create(AwsBasicCredentials.create("synthetic-local-id","synthetic-local-secret")),spool,EvaluationAcceptanceInputSchema.MAXIMUM_DOCUMENT_BYTES,16)){result=callback.apply(actual,lease);}afterTransport.run();lease.requireCurrent();return result;
  });
  when(subject.user()).thenThrow(new AssertionError("Control path must not read browser session"));
  when(f.admission.subject()).thenThrow(new AssertionError("Control read must not synthesize session"));
  return EvidenceReadAccess.control(control);
 }
 @Test void controlOriginalReadUsesBoundedGetWithoutBrowserSession()throws Exception {
  var control=controlReader();var document=reader.sourceEvidence(control,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE);
  assertEquals(f.basis(PRIMARY),document.basis());assertTrue(requests.get()>1);assertTrue(stateReads.get()>requests.get());
  f.transaction(()->{document.requireCurrent(f.admission);return null;});verify(access,never()).execute(any(),any());verify(transport,never()).readDocument(any(),anyString(),anyInt(),any(),any());
 }
 @Test void controlReadRechecksCurrentGrantBetweenPhysicalSegments()throws Exception {
  var control=controlReader();onGet=()->allowed.set(false);code(ErrorCode.ACCESS_DENIED,()->reader.sourceEvidence(control,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE));assertEquals(1,requests.get());
 }
 @Test void controlReadRechecksCapabilityBetweenPhysicalSegments()throws Exception {
  var control=controlReader();onGet=()->controlActive.set(false);code(ErrorCode.ACCESS_DENIED,()->reader.sourceEvidence(control,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE));assertEquals(1,requests.get());
 }
 @Test void exactControlPinIsCheckedBeforeAnySourceStateOrGet()throws Exception {
  var control=controlReader();int before=stateReads.get();
  code(ErrorCode.ACCESS_DENIED,()->reader.sourceEvidence(control,"1",PROJECT,new PermissionProtocol.Basis(id(909),f.basis(PRIMARY).content_sha256()),ROOT_PURPOSE));
  code(ErrorCode.ACCESS_DENIED,()->reader.sourceEvidence(control,"1",PROJECT,new PermissionProtocol.Basis(PRIMARY,hash("different version bytes")),ROOT_PURPOSE));
  assertEquals(before,stateReads.get());assertEquals(0,requests.get());
 }
 @Test void closedControlCannotStartOriginalGet()throws Exception {
  var control=controlReader();controlActive.set(false);code(ErrorCode.ACCESS_DENIED,()->reader.sourceEvidence(control,"1",PROJECT,f.basis(PRIMARY),ROOT_PURPOSE));assertEquals(0,requests.get());
 }
 @AfterEach void cleanup()throws Exception{if(server!=null)server.stop(0);if(spool!=null){try(var paths=Files.list(spool)){assertEquals(0,paths.count(),"Actual S3 reader must release its private partial/complete files");}Files.deleteIfExists(spool);}assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertEquals(ErrorCode.ACCESS_DENIED,assertThrows(TenantAccessException.class,TenantScope::requireCurrent).code());}
}

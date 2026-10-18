package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.*;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual schemas/canonical selections/resource grants and transactional rollback shim; S3/source/SQL boundaries are doubles. */
class AnnotationThreadServiceTest {
    static final String TENANT=id(1), ACTOR=id(2), PROJECT=id(3), DOCUMENT=id(4), REVISION=id(5), ROOT=id(6), TEXT=id(7), ACCESS=id(8);
    static final String KEY="annotation-create-key-0001", HASH="a".repeat(64);
    static final LocalDateTime NOW=LocalDateTime.of(2026,10,18,0,0);
    PermissionSchema json; AnnotationThreadSchema schema; AnnotationThreadService service;
    AnnotationThreadMapper mapper; DocumentReadMapper reads; PermissionMapper permissions; DocumentReadService documents;
    SourceUploadTransport transport; ArtifactSourceAuthority artifacts; ArtifactContentService contents;
    ApplicationSessionIdentity.Verified subject; BusinessSessionProtocol.Context context; TenantWatermarks epochs;
    InterfaceProtocol.DocumentBody body; CommonProtocol.Selection selection; Store store;
    String authorizationClock; boolean canRead,canWrite,sourceDenied,failAfterPut,failOutbox,failHead,expireAtCommit;
    int puts,recovers,objectReads; Runnable onObjectRead;
    final Map<String,byte[]> objects=new HashMap<>(); final Map<String,InterfaceProtocol.ObjectRef> objectRefs=new HashMap<>();

    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        json=new PermissionSchema();schema=new AnnotationThreadSchema(json);store=new Store(); authorizationClock="NONE";
        canRead=canWrite=true;onObjectRead=()->{}; context=new BusinessSessionProtocol.Context(TENANT,"1","1","1");
        epochs=new TenantWatermarks(BigInteger.ONE,BigInteger.ONE);
        subject=mock(ApplicationSessionIdentity.Verified.class);when(subject.user()).thenReturn(new BusinessSessionProtocol.User(ACTOR,"fixture","Fixture","USER"));
        when(subject.sessionId()).thenReturn("annotation-fixture-session");
        var binding=mock(AuthenticatedIdentityBinding.class);when(subject.binding()).thenReturn(binding);
        when(binding.view()).thenReturn(new AuthenticatedIdentityBinding.View("local",ACTOR,null,null));
        body=new InterfaceProtocol.DocumentBody("1.0.0",id(9),List.of(
                new InterfaceProtocol.Node(ROOT,null,0,List.of(),List.of(),new InterfaceProtocol.ContainerContent(InterfaceProtocol.ContainerKind.DOCUMENT,"")),
                new InterfaceProtocol.Node(TEXT,ROOT,0,List.of(),List.of(),new InterfaceProtocol.TextContent("TEXT","甲😀e\u0301\n乙",List.of(InterfaceProtocol.Mark.BOLD)))),List.of(),List.of(),List.of());
        String text=((InterfaceProtocol.TextContent)body.nodes().get(1).content()).text();
        selection=new CommonProtocol.Selection(REVISION,List.of(new CommonProtocol.TextSelection("TEXT",TEXT,
                schema.hash(body.nodes().get(1)),0,text.codePointCount(0,text.length()),WireValues.sha256(text.getBytes(StandardCharsets.UTF_8)))));
        mapper=mock(AnnotationThreadMapper.class);reads=mock(DocumentReadMapper.class);permissions=mock(PermissionMapper.class);
        documents=mock(DocumentReadService.class);transport=mock(SourceUploadTransport.class);artifacts=mock(ArtifactSourceAuthority.class);contents=mock(ArtifactContentService.class);
        var access=mock(EnterpriseAccess.class);
        when(access.execute(eq(subject),any())).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"No nested tenant transaction");
            Store before=store.copy();TransactionSynchronizationManager.setActualTransactionActive(true);
            var identity=new TenantScope.Identity(context.tenant_id(),ACTOR,new BigInteger(context.context_revision()),epochs.authEpoch(),epochs.governanceEpoch());
            try(var scope=TenantScope.open(identity)) {
                scope.verifyTransaction(epochs);
                try {Function<EnterpriseAccess.Admission,Object> work=call.getArgument(1);return work.apply(new EnterpriseAccess.Admission(subject,id(10),context,epochs));}
                catch(Throwable failure){store=before;throw failure;}
                finally {scope.finishTransaction();}
            } finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        });
        when(permissions.databaseTime()).thenReturn(NOW);
        when(permissions.resourceOwner("DOCUMENT",DOCUMENT)).thenReturn(List.of(new PermissionProtocol.OwnerRow(DOCUMENT,PROJECT)));
        when(permissions.resourceGrants(eq(ACTOR),anyString())).thenAnswer(call->{String action=call.getArgument(1);
            return (action.equals("annotation.read")&&canRead||action.equals("annotation.write")&&canWrite)?List.of(grant(action)):List.of();});
        when(documents.readFixed(eq(subject),anyString(),eq(DOCUMENT),eq(REVISION))).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());if(sourceDenied)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            return proof();
        });
        doAnswer(call->{
            var admission=(EnterpriseAccess.Admission)call.getArgument(0);var fixed=(DocumentReadService.FixedRead)call.getArgument(1);
            if(sourceDenied)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            if(!"1".equals(admission.context().context_revision())||!new TenantWatermarks(BigInteger.ONE,BigInteger.ONE).equals(admission.watermarks())
                    ||!fixed.header().equals(header())||!fixed.watermarks().equals(marks()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);
            return null;
        }).when(documents).requireCurrent(any(EnterpriseAccess.Admission.class),any());
        wireMapper();
        when(reads.header(anyMap())).thenAnswer(c->List.of(header()));when(reads.watermarks(anyMap())).thenAnswer(c->List.of(marks()));
        when(permissions.saveAudit(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(c->{store.audits.put(c.getArgument(5),c.getArgument(4));return 1;});
        when(permissions.saveEvent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenAnswer(c->{
            if(failOutbox)throw new IllegalStateException("INJECTED_OUTBOX_FAILURE");store.events.put(c.getArgument(2),c.getArgument(5));
            if(expireAtCommit)authorizationClock="2026-10-18T00:00:01.000000Z";return 1;
        });
        when(transport.writeDocumentProjection(eq(subject),eq(TENANT),any(),any(),anyBoolean(),anyInt(),any())).thenAnswer(c->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var intent=(S3ObjectWriter.WriteIntent)c.getArgument(2);
            byte[] bytes=c.getArgument(3);boolean recover=c.getArgument(4);var current=(S3ObjectReader.Admission)c.getArgument(6);current.requireCurrent();
            assertEquals(intent.sha256(),WireValues.sha256(bytes));assertEquals(intent.sizeBytes(),Integer.toString(bytes.length));
            var durable=store.operationByKey.get(KEY);assertNotNull(durable);assertTrue(schema.command(durable.responseRef()).writing());
            if(recover){recovers++;assertArrayEquals(objects.get(intent.objectKey()),bytes);}else{puts++;assertFalse(objects.containsKey(intent.objectKey()));objects.put(intent.objectKey(),bytes.clone());}
            var reference=objectRefs.computeIfAbsent(intent.objectKey(),key->new InterfaceProtocol.ObjectRef(key,"object-version-1",intent.sha256(),intent.sizeBytes(),intent.mediaType()));
            current.requireCurrent();if(failAfterPut){failAfterPut=false;throw new IOException("uncertain after object persisted");}return reference;
        });
        when(transport.readDocument(eq(subject),eq(TENANT),anyInt(),any(),any())).thenAnswer(c->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());var current=(S3ObjectReader.Admission)c.getArgument(3);current.requireCurrent();
            var callback=(SourceUploadTransport.DocumentRead<Object>)c.getArgument(4);
            var admission=new S3ObjectReader.TimedAdmission(){public void requireCurrent(){current.requireCurrent();}public Duration remainingTime(){return Duration.ofMinutes(1);}};
            var result=callback.apply(mock(S3ObjectReader.class),admission);onObjectRead.run();current.requireCurrent();return result;
        });
        when(contents.readVerifiedComplete(eq(subject),any(),any(),any(),anyInt(),anyString())).thenAnswer(c->{
            objectReads++;var intent=(ContentReadProtocol.ReadIntent)c.getArgument(1);var row=store.artifacts.get(intent.artifactId());
            var complete=mock(ArtifactContentService.CompleteRead.class);byte[] bytes=objects.get(row.objectKey());
            if(!row.sha256().equals(WireValues.sha256(bytes)))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
            when(complete.jsonPayload()).thenReturn(schema.shared().bindPublished(intent.artifact().schemaRef(),intent.artifact().schemaDocumentSha256(),bytes));return complete;
        });
        when(artifacts.require(any(),anyString(),any(),anyList(),any(),anyMap())).thenAnswer(c->{
            if(sourceDenied)throw new TenantAccessException(ErrorCode.ACCESS_DENIED);String artifactId=c.getArgument(1);var artifact=store.artifacts.get(artifactId);
            assertNotNull(artifact);var requirement=(ArtifactRegistryProtocol.ReadRequirement)c.getArgument(2);
            assertEquals("DOCUMENT_BODY",requirement.purpose());assertEquals("document.read",requirement.action());assertEquals(DOCUMENT,requirement.scope().resource_id());
            assertEquals(schema.shared().schemaRef("interfaces","Selection"),requirement.schemaRef());
            var artifactBinding=store.bindings.get(artifactId);var registration=new ArtifactRegistryProtocol.Registration(artifact,artifactBinding,dependencies(),null,null,null);
            return new ArtifactSourceAuthority.VerifiedSources(registration,Map.of(),Map.of());
        });
        var storage=new DocumentUploadPolicy("TEST","test-region",100,100,100,BigInteger.valueOf(2_097_152),3600,HASH,List.of(),1000,1000,1000);
        var anchors=mock(AnnotationAnchorMapper.class);when(anchors.insertAnchor(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);var reference=(InterfaceProtocol.ObjectRef)v.get("reference");store.anchor=new AnnotationAnchorProtocol.Anchor(TENANT,PROJECT,(String)v.get("anchorId"),(String)v.get("annotationId"),REVISION,(String)v.get("anchorNodeId"),(String)v.get("selectionJson"),(String)v.get("artifactId"),(String)v.get("selectionSha256"),reference.objectVersion(),null,BigInteger.ONE,null,ACTOR,ACTOR,NOW,"INTERNAL","1.0.0",schema.manifestSha256());return 1;});when(anchors.insertPointer(anyMap())).thenReturn(1);when(anchors.anchor(anyMap())).thenAnswer(c->List.of(store.anchor));when(anchors.pointer(anyMap())).thenAnswer(c->List.of(new AnnotationAnchorProtocol.Pointer(store.anchor.anchorId(),BigInteger.ONE)));when(anchors.documentCount(anyMap())).thenAnswer(c->(long)store.annotations.size());
        service=new AnnotationThreadService(access,mapper,reads,permissions,new ResourceReadAuthority(permissions,new PermissionAuthority(json),json),documents,artifacts,contents,transport,storage,schema,json,anchors);
    }

    @Test void persistsRealCanonicalSelectionAndListsItWithOriginalTextAndInvalidation() throws Exception {
        store.reviews.add(new AnnotationThreadProtocol.Review(id(20),REVISION,"CONFIRM","DOCUMENT"));
        String note=" 原文 😀\n保持空格\t ";var saved=create(note);var receipt=saved.required("data");var item=receipt.required("annotation");
        assertEquals(1,puts);assertEquals(0,recovers);assertEquals("2",receipt.required("revision").asText());assertEquals("1",receipt.required("issue_watermark").asText());
        assertEquals("1",item.required("revision").asText());assertEquals("OPEN",item.required("status").asText());assertEquals(note,item.required("content").asText());
        assertEquals(selection,schema.selection(item.required("selection")));assertEquals(1,store.artifacts.size());assertEquals(1,store.bindings.size());assertEquals(1,store.versions.size());
        assertEquals(1,store.invalidations.size());assertEquals(item.required("selection_artifact_id").asText(),store.invalidations.get(0).triggerVersion());
        assertEquals(store.versions.iterator().next(),store.invalidations.get(0).triggerVersion());
        var view=service.list(subject,"1",DOCUMENT,REVISION,null).required("data");assertTrue(view.required("complete").asBoolean());assertEquals("1",view.required("total").asText());
        assertEquals(item,view.required("items").get(0));assertEquals(1,objectReads);
        for(String log:List.of(store.audits.toString(),store.events.toString())){assertFalse(log.contains(note));assertFalse(log.contains("content"));assertFalse(log.contains("object_key"));}
        assertEquals("DRAFT",store.status);assertEquals(BigInteger.ZERO,store.evidence);
    }
    @Test void smallLongSelectionOffsetsRoundtripThroughStoredReceiptAndReplay() throws Exception {
        selection=new CommonProtocol.Selection(REVISION,List.of(new CommonProtocol.TextSelection("TEXT",TEXT,schema.hash(body.nodes().get(1)),0L,1L,
                WireValues.sha256("甲".getBytes(StandardCharsets.UTF_8)))));
        assertTrue(json.tree(selection).required("segments").get(0).required("start_cp").isLong());
        var response=create("numeric receipt regression");var stored=schema.receipt(store.operationByKey.get(KEY).responseRef());
        assertTrue(stored.required("data").required("annotation").required("selection").required("segments").get(0).required("start_cp").isInt());
        assertEquals(response,stored);assertEquals(response,create("numeric receipt regression"));assertEquals(1,puts);assertEquals(1,objectReads);
    }
    @Test void uncertainObjectWriteResumesSameDurableObjectAndNeverDuplicatesAnnotation() throws Exception {
        failAfterPut=true;assertThrows(IOException.class,()->create("comment"));assertEquals(1,objects.size());assertTrue(store.annotations.isEmpty());
        var pending=schema.command(store.operationByKey.get(KEY).responseRef());assertTrue(pending.writing());assertNull(pending.reference());
        var result=create("comment");assertEquals(pending.annotationId(),result.required("data").required("annotation").required("annotation_id").asText());
        assertEquals(1,puts);assertEquals(1,recovers);assertEquals(1,store.annotations.size());assertEquals(1,store.artifacts.size());
    }
    @Test void replaySurvivesRepliesAndHistoricalHeadAndUsesActualStoredObject() throws Exception {
        var result=create("comment");var row=store.annotations.values().iterator().next();store.annotations.put(row.annotationId(),row(row,BigInteger.TWO,BigInteger.ONE));
        store.currentRevision=id(99);store.head=BigInteger.valueOf(3);
        assertEquals(result,create("comment"));assertEquals(1,puts);assertEquals(1,store.annotations.size());assertEquals(1,objectReads);
        var listing=service.list(subject,"1",DOCUMENT,REVISION,null).required("data").required("items").get(0);
        assertEquals("ORIGINAL_VERSION",listing.required("location_status").asText());assertEquals("2",listing.required("revision").asText());assertEquals("1",listing.required("reply_count").asText());
        denial(ErrorCode.IDEMPOTENCY_CONFLICT,()->create("changed"));
    }
    @Test void corruptedObjectOrSqlSelectionCannotBeDisplayedOrReplayed() throws Exception {
        create("comment");String key=objects.keySet().iterator().next();objects.put(key,"{}".getBytes(StandardCharsets.UTF_8));
        denial(ErrorCode.CONTENT_MISMATCH,()->service.list(subject,"1",DOCUMENT,REVISION,null));denial(ErrorCode.CONTENT_MISMATCH,()->create("comment"));
    }
    @Test void deniedOrExpiredSourceAndIndependentAnnotationPermissionsPreventWritesAndReads() throws Exception {
        canWrite=false;denial(ErrorCode.ACCESS_DENIED,()->create("comment"));assertTrue(objects.isEmpty());canWrite=true;sourceDenied=true;
        denial(ErrorCode.ACCESS_DENIED,()->create("comment"));sourceDenied=false;create("comment");canRead=false;
        denial(ErrorCode.ACCESS_DENIED,()->service.list(subject,"1",DOCUMENT,REVISION,null));canRead=true;sourceDenied=true;
        denial(ErrorCode.ACCESS_DENIED,()->create("comment"));assertEquals(1,store.annotations.size());
    }
    @Test void signedOldAndWrongExpectedDocumentsNeverStartStorage() throws Exception {
        store.status="SIGNED";denial(ErrorCode.DOCUMENT_SIGNED,()->create("comment"));store.status="DRAFT";store.currentRevision=id(99);
        denial(ErrorCode.REVISION_CONFLICT,()->create("comment"));store.currentRevision=REVISION;store.head=BigInteger.TWO;
        denial(ErrorCode.REVISION_CONFLICT,()->create("comment"));assertTrue(objects.isEmpty());assertTrue(store.operationByKey.isEmpty());
    }
    @Test void headCasAndOutboxFailureRollbackAllDomainRowsButKeepRecoverableObject() throws Exception {
        failHead=true;denial(ErrorCode.REVISION_CONFLICT,()->create("comment"));assertTrue(store.annotations.isEmpty());assertTrue(store.artifacts.isEmpty());assertEquals(BigInteger.ONE,store.head);assertEquals(1,objects.size());
        failHead=false;failOutbox=true;assertThrows(IllegalStateException.class,()->create("comment"));assertTrue(store.annotations.isEmpty());assertTrue(store.artifacts.isEmpty());assertTrue(store.audits.isEmpty());
        failOutbox=false;create("comment");assertEquals(1,puts);assertEquals(2,recovers);assertEquals(1,store.annotations.size());
    }
    @Test void crossedTimedBoundaryAtFinalCommitRollsBackDomainChanges() throws Exception {
        expireAtCommit=true;denial(ErrorCode.SCOPE_STALE,()->create("comment"));assertTrue(store.annotations.isEmpty());assertTrue(store.artifacts.isEmpty());
        assertTrue(store.events.isEmpty());assertTrue(store.audits.isEmpty());assertEquals(BigInteger.ONE,store.head);assertEquals(BigInteger.ZERO,store.issues);
    }
    @Test void listingRechecksReplyCountWatermarksAndTimeAfterActualObjectRead() throws Exception {
        create("comment");onObjectRead=()->store.issues=store.issues.add(BigInteger.ONE);
        denial(ErrorCode.SCOPE_STALE,()->service.list(subject,"1",DOCUMENT,REVISION,null));store.issues=BigInteger.ONE;
        onObjectRead=()->authorizationClock="2026-10-18T00:00:02.000000Z";
        denial(ErrorCode.SCOPE_STALE,()->service.list(subject,"1",DOCUMENT,REVISION,null));
    }
    @Test void completeListingCapacityIsReservedBeforeAnyObjectOrOperationWrite() throws Exception {
        var segments=new ArrayList<CommonProtocol.SelectionSegment>();var nodes=new ArrayList<>(body.nodes());
        // Each distinct text node is valid but the complete Selection exceeds the per-annotation response reservation.
        for(int i=30;i<400;i++) {String nid=id(i);var node=new InterfaceProtocol.Node(nid,ROOT,i,List.of(),List.of(),new InterfaceProtocol.TextContent("TEXT","x",List.of()));nodes.add(node);
            segments.add(new CommonProtocol.TextSelection("TEXT",nid,schema.hash(node),0,1,WireValues.sha256("x".getBytes(StandardCharsets.UTF_8))));}
        body=new InterfaceProtocol.DocumentBody("1.0.0",id(9),nodes,List.of(),List.of(),List.of());selection=new CommonProtocol.Selection(REVISION,segments);
        denial(ErrorCode.CONTEXT_LIMIT,()->create("comment"));assertTrue(objects.isEmpty());assertTrue(store.operationByKey.isEmpty());
        assertTrue((long)AnnotationThreadSchema.MAXIMUM_ANNOTATIONS*AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES+4096<AnnotationThreadSchema.MAXIMUM_LIST_BYTES);
    }

    @Test void exact65536ByteFutureDetailIsAdmittedButOneMoreByteFailsBeforeWrite() throws Exception {
        var nodes=new ArrayList<>(body.nodes());var segments=new ArrayList<CommonProtocol.SelectionSegment>(selection.segments());
        for(int i=30;i<35;i++) {var node=new InterfaceProtocol.Node(id(i),ROOT,i,List.of(),List.of(),new InterfaceProtocol.TextContent("TEXT","x",List.of()));
            nodes.add(node);segments.add(new CommonProtocol.TextSelection("TEXT",id(i),schema.hash(node),0,1,WireValues.sha256("x".getBytes(StandardCharsets.UTF_8))));}
        body=new InterfaceProtocol.DocumentBody("1.0.0",id(9),nodes,List.of(),List.of(),List.of());selection=new CommonProtocol.Selection(REVISION,segments);
        String maximum="18446744073709551615";
        var worst=json.object().put("annotation_id",id(0)).put("document_revision",REVISION).put("revision",maximum).put("selection_artifact_id",id(0))
                .put("selection_sha256",schema.hash(selection)).put("content","").put("actor_id",id(0)).put("created_at","9999-12-31T23:59:59.999999Z")
                .put("status","REANCHOR_REQUIRED").put("location_status","ORIGINAL_VERSION").put("reply_count",maximum);worst.set("selection",json.tree(selection));
        int remaining=AnnotationThreadSchema.MAXIMUM_ANNOTATION_BYTES-json.canonical(worst).getBytes(StandardCharsets.UTF_8).length;
        String exact="😀".repeat(remaining/4)+"x".repeat(remaining%4);assertTrue(exact.codePointCount(0,exact.length())<16000);
        assertEquals(65536,json.canonical(worst.put("content",exact)).getBytes(StandardCharsets.UTF_8).length);
        denial(ErrorCode.CONTEXT_LIMIT,()->create(exact+"x"));assertTrue(objects.isEmpty());assertTrue(store.operationByKey.isEmpty());
        var saved=create(exact);assertEquals(exact,saved.required("data").required("annotation").required("content").asText());
        assertEquals(1,puts);assertEquals("1",service.list(subject,"1",DOCUMENT,REVISION,null).required("data").required("total").asText());
    }

    private JsonNode create(String text) throws Exception {return service.create(subject,"1",KEY,DOCUMENT,input(text));}
    byte[] input(String text) throws Exception {var value=json.object().put("document_revision",REVISION).put("expected_revision","1").put("content",text);value.set("selection",json.tree(selection));return json.canonical(value).getBytes(StandardCharsets.UTF_8);}
    private DocumentReadService.FixedRead proof() throws Exception {
        var proof=mock(DocumentReadService.FixedRead.class);var header=header();var marks=marks();when(proof.header()).thenReturn(header);when(proof.watermarks()).thenReturn(marks);when(proof.body()).thenReturn(body);
        when(proof.digest()).thenReturn(schema.hash(Map.of("head",header.revision().toString(),"current",header.currentDocumentRevision(),"issues",marks.issueWatermark().toString(),"epochs",epochs.toString())));
        var required=new ArtifactRegistryProtocol.ReadRequirement(new PermissionProtocol.Scope(TENANT,PROJECT,"DOCUMENT",DOCUMENT),"document.read","DOCUMENT_BODY",
                schema.shared().schemaRef("interfaces","DocumentBody"),schema.shared().bind("interfaces",json.tree((String)null),"NullableId").schemaSha256(),HASH,new DependencyLineage.Limits(100,100));
        when(proof.bodyIntent()).thenReturn(new ContentReadProtocol.ReadIntent(id(11),"1",required,List.of(),new VersionResourceProtocol.ReadContext(REVISION.equals(store.currentRevision)?SourceReadProtocol.Use.FORMAL:SourceReadProtocol.Use.HISTORY,null),Map.of()));
        when(proof.dependencies()).thenReturn(dependencies());return proof;
    }
    private ArtifactRegistryProtocol.DependencyManifest dependencies(){return new ArtifactRegistryProtocol.DependencyManifest("1.0.0",TENANT,PROJECT,ACCESS,List.of());}
    private DocumentReadProtocol.Header header(){return new DocumentReadProtocol.Header(TENANT,PROJECT,DOCUMENT,REVISION,store.currentRevision,store.head,store.status,"ACTIVE",id(12),id(13),id(14),id(15),id(11),HASH,"body/object","body-version",id(16),id(17),id(9),ACCESS,BigInteger.ZERO,HASH,ACTOR,NOW,"INTERNAL");}
    private DocumentReadProtocol.Watermarks marks(){return new DocumentReadProtocol.Watermarks(store.issues,store.evidence);}
    private PermissionProtocol.GrantRow grant(String action) throws Exception {
        var condition=json.canonicalObject(new PermissionProtocol.Condition("1.0.0",List.of("local"),List.of("DOCUMENT_REVIEW"),
                new PermissionProtocol.Delegation(List.of(),List.of(),List.of(),null,null)));
        return new PermissionProtocol.GrantRow(id(18),ACTOR,PROJECT,"DOCUMENT",DOCUMENT,action,condition,"ACTIVE",NOW.minusDays(1),null,
                id(19),"1.0.0",json.manifestSha256(),BigInteger.ONE);
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    private void denial(ErrorCode code,org.junit.jupiter.api.function.Executable work){assertEquals(code,assertThrows(TenantAccessException.class,work).code());}
    private static AnnotationThreadProtocol.Row row(AnnotationThreadProtocol.Row r,BigInteger revision,BigInteger replies){return new AnnotationThreadProtocol.Row(r.tenantId(),r.projectId(),r.annotationId(),r.documentRevision(),r.nodeId(),r.selectionJson(),r.selectionArtifactId(),r.selectionSha256(),r.status(),r.content(),r.actorId(),r.schemaVersion(),r.schemaManifest(),r.createdBy(),r.createdAt(),r.classification(),revision,r.updatedAt(),replies);}

    @SuppressWarnings("unchecked") private void wireMapper() throws Exception {
        when(mapper.authorizationClock(anyMap())).thenAnswer(c->authorizationClock);
        when(mapper.operation(anyMap())).thenAnswer(c->{Map<String,Object> v=c.getArgument(0);var row=store.operationByKey.get(v.get("key"));return row==null?List.of():List.of(row);});
        when(mapper.startOperation(anyMap())).thenAnswer(c->{Map<String,Object> v=c.getArgument(0);String key=(String)v.get("key");
            store.operationByKey.put(key,new UploadOperationRow(TENANT,(String)v.get("operationId"),ACTOR,"RV-04-THREAD-1",key,(String)v.get("requestHash"),"PROCESSING",null,(String)v.get("commandJson"),DOCUMENT,null,"1.0.0",schema.manifestSha256(),ACTOR,NOW,"INTERNAL",BigInteger.ONE,NOW,null,null));return 1;});
        when(mapper.updateCommand(anyMap())).thenAnswer(c->{Map<String,Object> v=c.getArgument(0);String key=(String)v.get("key");var old=store.operationByKey.get(key);
            assertEquals(old.revision(),v.get("operationRevision"));store.operationByKey.put(key,operation(old,"PROCESSING",null,(String)v.get("commandJson")));return 1;});
        when(mapper.completeOperation(anyMap())).thenAnswer(c->{Map<String,Object> v=c.getArgument(0);String key=(String)v.get("key");var old=store.operationByKey.get(key);
            assertEquals(old.revision(),v.get("operationRevision"));store.operationByKey.put(key,operation(old,"COMPLETED",201,(String)v.get("response")));return 1;});
        when(mapper.annotationCount(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);return store.annotations.values().stream().filter(r->v.get("status")==null||v.get("status").equals(r.status())).count();});
        when(mapper.annotations(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);return store.annotations.values().stream().filter(r->v.get("status")==null||v.get("status").equals(r.status())).sorted(Comparator.comparing(AnnotationThreadProtocol.Row::createdAt).thenComparing(AnnotationThreadProtocol.Row::annotationId)).toList();});
        when(mapper.annotation(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);var r=store.annotations.get(v.get("annotationId"));return r==null?List.of():List.of(r);});
        when(mapper.artifactDocumentRevision(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);return store.artifacts.containsKey(v.get("artifactId"))?List.of(REVISION):List.of();});
        when(mapper.confirmations(anyMap())).thenAnswer(c->store.reviews.stream().filter(r->store.invalidations.stream().noneMatch(i->i.reviewId().equals(r.reviewId()))).sorted(Comparator.comparing(AnnotationThreadProtocol.Review::reviewId)).toList());
        when(mapper.invalidations(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);return store.invalidations.stream().filter(i->v.get("artifactId").equals(i.triggerVersion())).sorted(Comparator.comparing(AnnotationThreadProtocol.Invalidation::reviewId)).toList();});
        when(mapper.saveVersion(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);assertTrue(store.versions.add((String)v.get("artifactId")));return 1;});
        when(mapper.saveArtifact(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);var ref=(InterfaceProtocol.ObjectRef)v.get("reference");String artifactId=(String)v.get("artifactId");
            assertTrue(objects.containsKey(ref.objectKey()));assertEquals(objectRefs.get(ref.objectKey()),ref);
            store.artifacts.put(artifactId,new ArtifactRegistryProtocol.ArtifactRow(TENANT,PROJECT,artifactId,"SELECTION",(String)v.get("storedObject"),ref.objectKey(),ref.objectVersion(),ref.sha256(),new BigInteger(ref.sizeBytes()),ref.mediaType(),ACCESS,ref.sha256(),"1.0.0",HASH,"INTERNAL"));return 1;});
        when(mapper.saveBinding(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);String aid=(String)v.get("artifactId");
            assertEquals(schema.hash(dependencies()),v.get("dependencyHash"));
            store.bindings.put(aid,new ArtifactRegistryProtocol.BindingRow(TENANT,PROJECT,aid,"DOCUMENT",DOCUMENT,(String)v.get("selectionSchema"),(String)v.get("interfacesHash"),ACCESS,(String)v.get("dependencyHash")));return 1;});
        when(mapper.saveLineage(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);store.lineage.add(v.get("artifactId")+":"+v.get("upstreamId"));return 1;});
        when(mapper.insertAnnotation(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);String aid=(String)v.get("annotationId");
            store.annotations.put(aid,new AnnotationThreadProtocol.Row(TENANT,PROJECT,aid,REVISION,(String)v.get("anchorNodeId"),(String)v.get("selectionJson"),(String)v.get("artifactId"),(String)v.get("selectionSha256"),"OPEN",(String)v.get("content"),ACTOR,"1.0.0",schema.manifestSha256(),ACTOR,NOW,"INTERNAL",BigInteger.ONE,NOW,BigInteger.ZERO));return 1;});
        when(mapper.advanceHead(anyMap())).thenAnswer(c->{if(failHead)return 0;Map<String,Object>v=c.getArgument(0);assertEquals(store.head.toString(),v.get("expectedRevision"));store.head=store.head.add(BigInteger.ONE);return 1;});
        when(mapper.advanceIssues(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);assertEquals(store.issues,v.get("issueWatermark"));assertEquals(store.evidence,v.get("evidenceWatermark"));store.issues=store.issues.add(BigInteger.ONE);return 1;});
        when(mapper.invalidateReview(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);assertTrue(store.versions.contains(v.get("artifactId")));store.invalidations.add(new AnnotationThreadProtocol.Invalidation((String)v.get("invalidationId"),(String)v.get("reviewId"),(String)v.get("artifactId"),"ANNOTATION_OPENED"));return 1;});
        when(mapper.audit(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);String value=store.audits.get(v.get("requestId"));return value==null?List.of():List.of(value);});
        when(mapper.event(anyMap())).thenAnswer(c->{Map<String,Object>v=c.getArgument(0);String value=store.events.get(v.get("annotationId"));return value==null?List.of():List.of(value);});
    }
    private static UploadOperationRow operation(UploadOperationRow r,String state,Integer status,String value){return new UploadOperationRow(r.tenantId(),r.operationId(),r.subjectId(),r.operationName(),r.idempotencyKey(),r.requestSha256(),state,status,value,r.targetId(),r.errorCode(),r.schemaVersion(),r.schemaManifestSha256(),r.createdBy(),r.createdAt(),r.classification(),r.revision().add(BigInteger.ONE),r.updatedAt(),r.dedupExpiresAt(),r.dedupPolicyRevision());}
    private static class Store {
        BigInteger head=BigInteger.ONE,issues=BigInteger.ZERO,evidence=BigInteger.ZERO;String currentRevision=REVISION,status="DRAFT";
        AnnotationAnchorProtocol.Anchor anchor;Map<String,UploadOperationRow>operationByKey=new HashMap<>();Map<String,AnnotationThreadProtocol.Row>annotations=new HashMap<>();
        Map<String,ArtifactRegistryProtocol.ArtifactRow>artifacts=new HashMap<>();Map<String,ArtifactRegistryProtocol.BindingRow>bindings=new HashMap<>();
        Set<String>versions=new HashSet<>(),lineage=new HashSet<>();List<AnnotationThreadProtocol.Review>reviews=new ArrayList<>();
        List<AnnotationThreadProtocol.Invalidation>invalidations=new ArrayList<>();Map<String,String>audits=new HashMap<>(),events=new HashMap<>();
        Store copy(){var x=new Store();x.anchor=anchor;x.head=head;x.issues=issues;x.evidence=evidence;x.currentRevision=currentRevision;x.status=status;x.operationByKey.putAll(operationByKey);x.annotations.putAll(annotations);x.artifacts.putAll(artifacts);x.bindings.putAll(bindings);x.versions.addAll(versions);x.lineage.addAll(lineage);x.reviews.addAll(reviews);x.invalidations.addAll(invalidations);x.audits.putAll(audits);x.events.putAll(events);return x;}
    }
}

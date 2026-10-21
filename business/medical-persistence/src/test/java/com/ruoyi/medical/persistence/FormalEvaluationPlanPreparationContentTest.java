package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import software.amazon.awssdk.auth.credentials.*;

/** Actual shared historical verifier + immutable090 ledger/registration + reader/GET/consumer.
 * Existing upstream readers, transaction coordinator and deployed transport lease are named doubles.
 * S3ObjectReader performs actual loopback HTTP against an explicit byte fixture, not live S3. */
class FormalEvaluationPlanPreparationContentTest extends FormalEvaluationPlanPreparationReadServiceTest {
    final FormalEvaluationPlanPreparationContentSchema contentSchema=new FormalEvaluationPlanPreparationContentSchema(schema);
    final FormalEvaluationPlanPreparationContentAuthority authority=new FormalEvaluationPlanPreparationContentAuthority(service,schema,contentSchema,json);
    final SourceUploadTransport deployed=mock(SourceUploadTransport.class);
    final FormalEvaluationPlanPreparationContentService content=new FormalEvaluationPlanPreparationContentService(authority,new FormalEvaluationPlanPreparationContentObjects(deployed));
    final AtomicInteger gets=new AtomicInteger();
    HttpServer server;S3ObjectReader actualReader;Runnable duringGet=()->{};String mode="GOOD";
    FormalEvaluationPlanPreparationContentTest()throws Exception {
        when(subject.sessionId()).thenReturn("original-session");when(subject.user()).thenReturn(new BusinessSessionProtocol.User(actor,"test",null,"USER"));
        var evidence=(ObjectNode)checked.evidence();var p=(ObjectNode)evidence.required("preview");
        p.put("schema_version","1.0.0").put("preparation_kind","DETERMINISTIC_PLAN_DRAFT").put("execution_id",execution).put("evaluation_id",evaluation)
            .put("sample_id",id(6)).put("repeat_index",0).put("document_type","REGISTRATION_DIRECTORY").put("selection_state","SELECTED").put("runnable",false).put("professional_completion",false);
        p.set("candidate",json.tree(basis(10)));p.set("execution_policy",json.tree(basis(11)));p.set("context_binding",json.tree(basis(40)));p.set("source_snapshot",json.tree(basis(20)));
        p.putObject("coverage").put("scope","SELECTED_CELL").put("expected_cells",1).put("expected_nodes",1).put("selected_cells",1).put("professional_completed_cells",0).put("complete_evaluation",false);
        var choice=p.putArray("templates").addObject().put("document_type","REGISTRATION_DIRECTORY").put("applicability","APPLICABLE").put("eligible",true);choice.set("template",json.tree(basis(30)));
        p.set("plan_candidate",schema.read(new String(checked.bytes(),StandardCharsets.UTF_8)));p.putArray("conditions");p.putArray("blockers").addObject().put("code","OWNER_UNASSIGNED").putArray("target_ids").add(id(61));
        p.putArray("action_summary").addObject().put("action","artifact.register").put("resource_kind","EVALUATION").put("resource_id",evaluation).put("project_id",project).put("purpose","EVALUATION_RESULTS").put("allowed",true);
        schema.validate("Preview",p);evidence.set("source_snapshot",json.tree(basis(20)));
        var stable=(ObjectNode)FormalEvaluationPlanPreparationDependencies.stableEvidence(evidence);stable.set("roots",json.tree(checked.dependencies().roots()));stable.set("members",json.tree(checked.dependencies().members()));stable.set("dependencies",checked.dependencies().authorizations());stable.put("classification",checked.dependencies().classification());
        checked=new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),checked.original(),checked.evaluation(),checked.node(),checked.contextBinding(),checked.bytes(),evidence,schema.canonical(stable),checked.dependencies(),p,true);
        var root=only("formal_evaluation_execution");var rootDocument=(ObjectNode)schema.read((String)root.get("canonical_admission"));rootDocument.set("source_snapshot",json.tree(basis(20)));rootDocument.put("profile_revision",id(31));
        root.put("canonical_admission",schema.canonical(rootDocument));root.put("admission_sha256",schema.hash(rootDocument));
        var rootEvent=FormalEvaluationExecutionLedger.event(root,json);replaceJournal(root,rootEvent);
        var link=only("formal_evaluation_execution_context");var binding=(ObjectNode)schema.read((String)link.get("canonical_binding"));binding.put("root_admission_sha256",(String)root.get("admission_sha256"));link.put("canonical_binding",schema.canonical(binding));link.put("binding_sha256",schema.hash(binding));
        var contextEvent=json.object().put("schema_version","1.0.0").put("kind","EVALUATION_CONTEXT_BOUND").put("execution_id",execution).put("binding_sha256",schema.hash(binding));contextEvent.set("context_binding",json.tree(basis(40)));replaceJournal(link,contextEvent);
    }
    void replaceJournal(Map<String,Object> row,JsonNode event)throws Exception {
        for(var a:rows("audit_event"))if(a.get("audit_id").equals(row.get("audit_id")))a.put("resource_ref",schema.canonical(event));
        for(var e:rows("outbox_event"))if(e.get("event_id").equals(row.get("event_id")))e.put("payload",schema.canonical(event));
    }
    byte[] contentRequest()throws Exception {var n=json.object().put("schema_version","1.0.0");n.set("recovery",schema.read(new String(recovery(),StandardCharsets.UTF_8)));n.set("expected_receipt",status().required("receipt"));return schema.canonical(n).getBytes(StandardCharsets.UTF_8);}
    void transport()throws Exception {
        Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------"));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            gets.incrementAndGet();assertEquals("GET",exchange.getRequestMethod());assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(exchange.getRequestURI().getRawQuery().contains("versionId=fixture-object-version"));
            duringGet.run();byte[] bytes=checked.bytes();String range=exchange.getRequestHeaders().getFirst("Range");String[] ends=range.substring(6).split("-");int start=Integer.parseInt(ends[0]),end=Integer.parseInt(ends[1]);
            byte[] part=Arrays.copyOfRange(bytes,start,end+1);if(mode.equals("CORRUPT"))part[0]^=1;if(mode.equals("PARTIAL"))part=Arrays.copyOf(part,part.length-1);
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.getResponseHeaders().add("x-amz-version-id",mode.equals("VERSION")?"wrong-version":"fixture-object-version");exchange.getResponseHeaders().add("Content-Range","bytes "+start+"-"+end+"/"+bytes.length);
            exchange.sendResponseHeaders(206,part.length);exchange.getResponseBody().write(part);exchange.close();
        });server.start();
        actualReader=new S3ObjectReader(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"fixture-bucket","us-east-1",S3ObjectReader.Network.LOCAL_LOOPBACK,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("fixture-access","fixture-secret")),directory,1048576,1048576);
        when(deployed.readDocument(eq(subject),eq(tenant),eq(1048576),any(),any())).thenAnswer(i->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());S3ObjectReader.Admission current=i.getArgument(3);current.requireCurrent();
            S3ObjectReader.TimedAdmission lease=new S3ObjectReader.TimedAdmission(){public Duration remainingTime(){return Duration.ofMinutes(1);}public void requireCurrent(){}};
            return ((SourceUploadTransport.DocumentRead<?>)i.getArgument(4)).apply(actualReader,lease);
        });
    }
    @AfterEach void closeTransport(){if(actualReader!=null)actualReader.close();if(server!=null)server.stop(0);}
    @Test void completeVerifiedOriginalGetAndSafeProjectionDoNotInvokeWriteAuthority()throws Exception {
        complete();byte[] request=contentRequest();transport();when(proof.current(admission)).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));clearInvocations(proof);int beforeSql=executedSql.size();
        var result=content.read(subject,"1",request);assertEquals(schema.read(new String(checked.bytes(),StandardCharsets.UTF_8)),result.required("plan_candidate"));assertEquals(1,gets.get());assertTrue(executedSql.subList(beforeSql,executedSql.size()).stream().allMatch(q->q.startsWith("SELECT ")));
        assertEquals(0,result.required("original_preview").required("action_summary").size());assertFalse(result.toString().contains("artifact_registration"));assertFalse(result.required("runnable").asBoolean());
        verifyNoInteractions(proof);verify(policies,never()).requireByVersion(any(),anyString(),anyString(),anyString(),anyString(),anyString());verify(evaluations,never()).requireExecutionDefinition(any(),anyString());
        try(var files=Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void forgedReceiptOrArtifactHashCannotReachGet()throws Exception {
        complete();var request=(ObjectNode)schema.read(new String(contentRequest(),StandardCharsets.UTF_8));
        for(String field:List.of("receipt_sha256","artifact_sha256")){var copy=request.deepCopy();((ObjectNode)copy.required("expected_receipt")).put(field,hash('f'));
            assertThrows(TenantAccessException.class,()->content.read(subject,"1",schema.canonical(copy).getBytes(StandardCharsets.UTF_8)));}verifyNoInteractions(deployed);
    }
    @Test void fullGetRejectsWrongVersion()throws Exception {complete();byte[] request=contentRequest();transport();mode="VERSION";assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);}
    @Test void fullGetRejectsPartialBytes()throws Exception {complete();byte[] request=contentRequest();transport();mode="PARTIAL";assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);}
    @Test void fullGetRejectsCorruptBytes()throws Exception {complete();byte[] request=contentRequest();transport();mode="CORRUPT";assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);}
    @Test void currentReadRevocationDuringGetBlocksContent()throws Exception {
        complete();byte[] request=contentRequest();transport();duringGet=()->{try{when(resources.require(any(),any(),eq("artifact.read"),anyString())).thenThrow(new TenantAccessException(ErrorCode.ACCESS_DENIED));}catch(Exception e){throw new RuntimeException(e);}};
        assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);
    }
    @Test void exactAncestryDriftDuringGetBlocksContent()throws Exception {
        complete();byte[] request=contentRequest();transport();var deps=checked.dependencies();var changed=new ArrayList<>(deps.members());changed.add(new ArtifactRegistryProtocol.MemberRow(id(997),id(50)));
        duringGet=()->{try{when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(deps.roots(),changed,deps.classification(),deps.authorizations()));}catch(Exception e){throw new RuntimeException(e);}};
        assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);
    }
    @Test void duplicateAndMissingOriginalJournalsBlockBeforeGet()throws Exception {
        complete();byte[] request=contentRequest();var original=new ArrayList<>(rows("audit_event"));rows("audit_event").add(new LinkedHashMap<>(rows("audit_event").get(0)));
        assertThrows(Exception.class,()->content.read(subject,"1",request));rows("audit_event").clear();rows("audit_event").addAll(original);rows("outbox_event").remove(rows("outbox_event").size()-1);
        assertThrows(Exception.class,()->content.read(subject,"1",request));verifyNoInteractions(deployed);
    }
    @Test void missingGetLeavesIndependentMetadataAvailable()throws Exception {
        complete();byte[] request=contentRequest();when(deployed.readDocument(any(),anyString(),anyInt(),any(),any())).thenThrow(new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE));
        assertThrows(TenantAccessException.class,()->content.read(subject,"1",request));assertEquals("PREPARED",status().required("state").asText());assertFalse(status().required("content_available").asBoolean());
    }
    @Test void crossActorContextExecutionSessionAndForgedContentCannotConsumeProof()throws Exception {
        complete();byte[] request=contentRequest();var read=authority.require(subject,"1",request);var other=mock(ApplicationSessionIdentity.Verified.class);
        assertThrows(TenantAccessException.class,()->read.current(other,"1"));assertThrows(TenantAccessException.class,()->read.current(subject,"2"));
        var wrong=checked.bytes();wrong[0]^=1;assertThrows(TenantAccessException.class,()->read.consume(wrong));
        when(subject.sessionId()).thenReturn("replaced-session");assertThrows(TenantAccessException.class,read::requireCurrent);when(subject.sessionId()).thenReturn("original-session");
        when(admission.actorId()).thenReturn(id(999));assertThrows(TenantAccessException.class,read::requireCurrent);when(admission.actorId()).thenReturn(actor);
        var changed=(ObjectNode)schema.read(new String(request,StandardCharsets.UTF_8));((ObjectNode)changed.required("recovery").required("original_request")).put("execution_id",id(999));
        assertThrows(TenantAccessException.class,()->authority.require(subject,"1",schema.canonical(changed).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void currentContextEpochChangeDuringGetBlocksContent()throws Exception {
        complete();byte[] request=contentRequest();transport();duringGet=()->when(admission.context()).thenReturn(new BusinessSessionProtocol.Context(tenant,"1","2","1"));assertThrows(Exception.class,()->content.read(subject,"1",request));
    }
    @Test void sourceRightsChangeAtConsumptionBlocksEvenAfterSuccessfulGet()throws Exception {
        complete();var read=authority.require(subject,"1",contentRequest());var deps=checked.dependencies();var changed=new ArrayList<>(deps.members());changed.set(0,new ArtifactRegistryProtocol.MemberRow(changed.get(0).upstreamVersionId(),id(998)));
        when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(deps.roots(),changed,deps.classification(),deps.authorizations()));assertThrows(Exception.class,()->read.consume(checked.bytes()));
    }
    @Test void contentCannotBeReadInsideTransaction()throws Exception {complete();byte[] request=contentRequest();assertThrows(TenantAccessException.class,()->tx(()->content.read(subject,"1",request)));verifyNoInteractions(deployed);}
    @Test void closedRequestRejectsUnknownFieldsAndDuplicateFields()throws Exception {
        complete();String original=new String(contentRequest(),StandardCharsets.UTF_8);assertThrows(Exception.class,()->contentSchema.request(original.replaceFirst("\\{","{\"authority\":true,").getBytes(StandardCharsets.UTF_8)));
        assertThrows(Exception.class,()->contentSchema.request(original.replaceFirst("\\{","{\"schema_version\":\"1.0.0\",").getBytes(StandardCharsets.UTF_8)));
    }
    @Test void sameMembersWithRewrittenTopologyDuringGetCannotDiscloseContent()throws Exception {
        complete();byte[] request=contentRequest();transport();var deps=checked.dependencies();
        duringGet=()->{try{when(provenance.require(any(),anyString(),anyString(),any())).thenReturn(new FormalEvaluationPlanPreparationDependencies.Checked(deps.roots(),deps.members(),deps.classification(),deps.authorizations(),json.object().put("changed_edge",true)));}catch(Exception e){throw new RuntimeException(e);}};
        assertThrows(Exception.class,()->content.read(subject,"1",request));assertTrue(gets.get()>0);
    }
    @Test void historicalReadCapabilityHasOnlyPrivateConstructors() {for(var constructor:FormalEvaluationPlanPreparationContentAuthority.HistoricalRead.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));}

    @Test void mismatchedOriginalProfileProjectionBlocksBeforeGet()throws Exception {
        var evidence=(ObjectNode)checked.evidence();((ObjectNode)evidence.required("preview")).put("profile_revision",id(999));
        var stable=(ObjectNode)FormalEvaluationPlanPreparationDependencies.stableEvidence(evidence);stable.set("roots",json.tree(checked.dependencies().roots()));stable.set("members",json.tree(checked.dependencies().members()));stable.set("dependencies",checked.dependencies().authorizations());stable.put("classification",checked.dependencies().classification());
        checked=new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),checked.original(),checked.evaluation(),checked.node(),checked.contextBinding(),checked.bytes(),evidence,schema.canonical(stable),checked.dependencies(),evidence.required("preview"),true);
        // Explicit malformed original fixture cannot be treated as a source for historical content.
        complete();byte[] request=contentRequest();assertThrows(Exception.class,()->authority.require(subject,"1",request));verifyNoInteractions(deployed);
    }

    @Test void mismatchedOriginalSourceSnapshotBlocksBeforeGet()throws Exception {
        var evidence=(ObjectNode)checked.evidence();evidence.set("source_snapshot",json.tree(basis(999)));
        var stable=(ObjectNode)FormalEvaluationPlanPreparationDependencies.stableEvidence(evidence);stable.set("roots",json.tree(checked.dependencies().roots()));stable.set("members",json.tree(checked.dependencies().members()));stable.set("dependencies",checked.dependencies().authorizations());stable.put("classification",checked.dependencies().classification());
        checked=new FormalEvaluationPlanPreparationAuthority.Checked(checked.tenant(),checked.actor(),checked.context(),checked.project(),checked.original(),checked.evaluation(),checked.node(),checked.contextBinding(),checked.bytes(),evidence,schema.canonical(stable),checked.dependencies(),evidence.required("preview"),true);
        complete();byte[] request=contentRequest();assertThrows(Exception.class,()->authority.require(subject,"1",request));verifyNoInteractions(deployed);
    }


    /** Construct the caller selector from the actual registered fixture without using the reader under test. */
    byte[] registeredFixtureContentRequest()throws Exception {
        var r=tx(()->registrations.readStored(admission,ledger.findOriginal(admission,key,checked.original()).orElseThrow()).orElseThrow());
        var receipt=json.object().put("schema_version","1.0.0").put("operation_id",r.operationId()).put("write_id",r.writeId()).put("attempt_id",r.attemptId()).put("execution_id",r.executionId()).put("artifact_id",r.artifact().artifactId()).put("artifact_sha256",r.artifact().sha256()).put("object_version",r.objectVersion()).put("receipt_sha256",r.receiptSha256()).put("state","PREPARED").put("runnable",false).put("professional_completion",false);
        var request=json.object().put("schema_version","1.0.0");request.set("recovery",schema.read(new String(recovery(),StandardCharsets.UTF_8)));request.set("expected_receipt",receipt);return schema.canonical(request).getBytes(StandardCharsets.UTF_8);
    }
    void originalBindingDeniedBeforeGet()throws Exception {
        complete();byte[] request=registeredFixtureContentRequest();clearInvocations(provenance,contexts,deployed);
        assertEquals(ErrorCode.CONTENT_MISMATCH,assertThrows(TenantAccessException.class,()->content.read(subject,"1",request)).code());verifyNoInteractions(provenance,contexts,deployed);assertEquals(0,gets.get());
    }
    @Test void alteredSourcePurposeCannotReachContentTransport()throws Exception {originalFixture(basis(30),basis(30),"EVALUATION_RESULTS");originalBindingDeniedBeforeGet();}
    @Test void mismatchedExplicitTemplateCannotReachContentTransport()throws Exception {originalFixture(basis(32),basis(30),"DOCUMENT_BODY");originalBindingDeniedBeforeGet();}
    @Test void outsideManifestTemplateCannotReachContentTransport()throws Exception {originalFixture(basis(99),basis(99),"DOCUMENT_BODY");originalBindingDeniedBeforeGet();}
    @Test void automaticOriginalSelectionCanReadFullHistoricalContent()throws Exception {
        originalFixture(null,basis(30),"DOCUMENT_BODY");complete();byte[] request=contentRequest();transport();clearInvocations(proof);
        assertEquals(id(30),content.read(subject,"1",request).required("plan_candidate").required("template_version").asText());assertEquals(1,gets.get());verifyNoInteractions(proof);
    }
}

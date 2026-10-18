package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.VerificationInputFixture.*;
/** Explicit mapper doubles, real old save entry/schema/authorities. Not a MySQL or complete TaskCreateService test. */
class ScopeSnapshotLegacyEntryTest {
 VerificationInputFixture f;TaskInputSchema inputs;
 @BeforeEach void before()throws Exception{f=new VerificationInputFixture();inputs=new TaskInputSchema(f.json);f.grant("task.create");}
 @AfterEach void after()throws Exception{f.close();}
 TaskProtocol.ValidatedCreate request()throws Exception{var r=f.json.object().put("schema_version","1.0.0").put("project_id",PROJECT).put("profile_revision",PROFILE).put("document_type","PRODUCT_TECHNICAL_REQUIREMENTS").put("template_version",id(201)).put("agent_release_id",id(202)).put("owner_id",ACTOR).put("goal","Explicit old task snapshot regression").putNull("based_on_document_revision");r.set("scope_selection",f.request().required("scope_selection"));return inputs.create(f.json.canonical(r).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 ScopeSnapshotStore.Stored save(TaskProtocol.ValidatedCreate r)throws Exception{return f.transaction(()->f.snapshotStore.save(f.admission,r,PURPOSE,"OFFLINE_TEST","LOCAL",ModelVerificationInputService.LIMITS));}
 @Test void actualOldSaveRevalidatesCompleteRequestAndStoresSameSources()throws Exception{var request=request();var result=save(request);assertEquals(PROFILE,result.manifest().profile_revision());assertEquals(request.request().scope_selection(),result.manifest().scope_selection());assertEquals(SOURCE,result.manifest().members().get(0).source_version());assertEquals(PARSE,result.manifest().members().get(0).parse_version());assertEquals(1,f.snapshots.size());verify(f.sources,atLeast(2)).scopeCandidates(anyString(),eq(PROJECT),anyString(),anyList(),anyList(),anyInt());}
 @Test void oldEntryStillRequiresTaskCreateRatherThanModelVerify(){f.grants.removeIf(g->g.action().equals("task.create"));assertThrows(Exception.class,()->save(request()));assertTrue(f.snapshots.isEmpty());}
 @Test void suppliedValidatedCreateCannotOverrideCanonicalOriginal()throws Exception{var r=request();var changed=new TaskProtocol.ValidatedCreate(r.request(),r.canonical_request(),"0".repeat(64),r.scope_sha256(),r.schema_manifest_sha256());assertThrows(Exception.class,()->save(changed));assertTrue(f.snapshots.isEmpty());}
 @Test void oldEntryRechecksTaskCreateAfterPersistedReadback()throws Exception{when(f.snapshotRows.parents(anyMap())).thenAnswer(i->{Map<String,Object> v=i.getArgument(0);f.grants.removeIf(g->g.action().equals("task.create"));return f.snapshotParents.getOrDefault(v.get("snapshotId"),List.of());});assertThrows(Exception.class,()->save(request()));}
 @Test void oldEntryRechecksFullSelectionAfterPersistedReadback()throws Exception{when(f.sources.scopeCandidates(anyString(),eq(PROJECT),anyString(),anyList(),anyList(),anyInt())).thenReturn(List.of(f.selection()),List.of());assertThrows(Exception.class,()->save(request()));}
}

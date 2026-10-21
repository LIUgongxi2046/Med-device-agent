package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.BusinessSessionProtocol;
import com.ruoyi.medical.persistence.mapper.SourceExactMapper;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Projection and early-boundary tests, not physical read or registration acceptance. */
class TaskFieldTargetPreparationServiceTest {
    final PermissionSchema json=new PermissionSchema();
    final TaskFieldTargetSchema schema=new TaskFieldTargetSchema();
    TaskFieldTargetPreparationServiceTest()throws Exception{}
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    ObjectNode manifest(){
        var value=json.object().put("source_version",id(1)).put("field_revision",id(2));
        var fields=value.putObject("request").putArray("fields");
        fields.addObject().put("field_kind","MODEL").put("raw_value","型号－甲𠀀").put("alias_value","normalized alias");
        fields.addObject().put("field_kind","STANDARD_YEAR").put("raw_value","２０２６");return value;
    }
    @Test void choicesPreserveRawLexicalValueAndOrdinalNeverAlias()throws Exception{
        var choices=json.object().arrayNode();var source=manifest();TaskFieldTargetPreparationService.appendChoices(choices,source,schema,2);
        assertEquals(2,choices.size());assertEquals("型号－甲𠀀",choices.get(0).required("raw_value").asText());
        assertFalse(choices.toString().contains("normalized alias"));assertEquals("２０２６",choices.get(1).required("raw_value").asText());
        assertEquals(1,choices.get(1).at("/source_exact/ordinal").asInt());
        assertEquals(schema.hash(source),choices.get(0).at("/source_exact/manifest_sha256").asText());
        assertEquals(id(2),choices.get(1).at("/source_exact/field_revision").asText());
    }
    @Test void choiceLimitIsCheckedBeforeAddingAnExcessField()throws Exception{
        var choices=json.object().arrayNode();var failure=assertThrows(TenantAccessException.class,()->TaskFieldTargetPreparationService.appendChoices(choices,manifest(),schema,1));
        assertEquals(ErrorCode.CONTEXT_LIMIT,failure.code());assertEquals(1,choices.size());
    }
    @Test void nonTextExactKindNeverBecomesAProposedTextChoice(){
        var source=manifest();((ObjectNode)source.at("/request/fields/0")).put("field_kind","DECIMAL");
        var choices=json.object().arrayNode();assertThrows(TenantAccessException.class,()->TaskFieldTargetPreparationService.appendChoices(choices,source,schema,2));assertTrue(choices.isEmpty());
    }
    @Test void unresolvedOptionalAndConditionalInventoryIsNotDropped(){
        var inventory=json.object().arrayNode();assertFalse(TaskFieldTargetPreparationService.hasUnresolved(inventory));
        inventory.addObject().put("state","NOT_APPLICABLE").put("required",false);assertFalse(TaskFieldTargetPreparationService.hasUnresolved(inventory));
        inventory.addObject().put("state","MAPPED");assertFalse(TaskFieldTargetPreparationService.hasUnresolved(inventory));
        for(String state:List.of("UNKNOWN_CONDITION","MISSING_MAPPING","MISSING_SOURCE","UNRESOLVED_CONTEXT","UNSUPPORTED_TYPE","UNASSIGNED_SECTION")){
            ((ObjectNode)inventory.get(1)).put("state",state);assertTrue(TaskFieldTargetPreparationService.hasUnresolved(inventory),state);
        }
    }
    @Test void sectionProjectionUsesPersistedQuestionIdsNotQuestionKeys(){
        var plan=new TaskPlanProjection.Projection(id(1),id(2),List.of(id(3)),List.of(),
            List.of(new TaskPlanProjection.Section(id(4),"goal",id(5),null,0)),
            List.of(new TaskPlanProjection.SectionNode(id(4),id(3),0)),List.of(),List.of(),
            List.of(new TaskPlanProjection.Question(id(6),"q-display-key",id(4),"question",0)),List.of(),List.of(),List.of());
        var sections=TaskFieldTargetPreparationService.sections(plan,json);assertEquals(id(6),sections.get(0).at("/question_ids/0").asText());
        assertEquals(id(3),sections.get(0).at("/requirement_node_ids/0").asText());assertFalse(sections.toString().contains("q-display-key"));
    }
    @Test void changedContextRejectsReadBeforeAuthorityOrSourceCalls()throws Exception{
        var access=mock(EnterpriseAccess.class);var basis=mock(TaskFieldTargetBasis.class);var exact=mock(SourceExactService.class);
        var subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
        var admission=new EnterpriseAccess.Admission(subject,id(8),new BusinessSessionProtocol.Context(id(9),"2","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        doAnswer(invocation->{Function<EnterpriseAccess.Admission,Object> callback=invocation.getArgument(1);return callback.apply(admission);}).when(access).execute(eq(subject),any());
        var service=new TaskFieldTargetPreparationService(access,basis,mock(TaskFieldMappingService.class),mock(TaskPlanRelationStore.class),schema,json,
            new TaskFieldTargetPolicy(0,0,0,0,0,null,null),mock(ResourceReadAuthority.class),exact,mock(SourceExactMapper.class),
            mock(TaskFieldTargetLedger.class),mock(TaskFieldTargetRegistration.class),mock(DataSource.class));
        var failure=assertThrows(TenantAccessException.class,()->service.read(subject,"1",id(7)));assertEquals(ErrorCode.CONTEXT_CHANGED,failure.code());
        verifyNoInteractions(basis,exact);
    }
    /** Executes both real snapshot() passes and the real read orchestration. Only existing
     * authority, SQL mapper and published-source boundaries are doubles; no physical receipt is minted. */
    final class ReadFixture {
        final EnterpriseAccess access=mock(EnterpriseAccess.class);
        final TaskFieldTargetBasis basis=mock(TaskFieldTargetBasis.class);
        final TaskFieldTargetBasis.Verified proof=mock(TaskFieldTargetBasis.Verified.class,RETURNS_DEEP_STUBS);
        final TaskFieldMappingService mappings=mock(TaskFieldMappingService.class);
        final TaskPlanRelationStore relations=mock(TaskPlanRelationStore.class);
        final ResourceReadAuthority resources=mock(ResourceReadAuthority.class);
        final SourceExactService exact=mock(SourceExactService.class);
        final SourceExactMapper rows=mock(SourceExactMapper.class);
        final TaskFieldTargetLedger ledger=mock(TaskFieldTargetLedger.class);
        final TaskFieldTargetRegistration registrations=mock(TaskFieldTargetRegistration.class);
        final ApplicationSessionIdentity.Verified subject=mock(ApplicationSessionIdentity.Verified.class,RETURNS_DEEP_STUBS);
        final EnterpriseAccess.Admission admission=new EnterpriseAccess.Admission(subject,id(8),
            new BusinessSessionProtocol.Context(id(9),"1","1","1"),new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));
        final java.util.concurrent.atomic.AtomicBoolean inSnapshot=new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicInteger snapshotCount=new java.util.concurrent.atomic.AtomicInteger();
        final ObjectNode published=manifest();
        final TaskFieldTargetPreparationService service;
        ReadFixture(boolean configured,boolean transport)throws Exception {
            doAnswer(invocation->{
                assertTrue(inSnapshot.compareAndSet(false,true),"Nested admission transaction");snapshotCount.incrementAndGet();
                try{Function<EnterpriseAccess.Admission,Object> callback=invocation.getArgument(1);return callback.apply(admission);}
                finally{inSnapshot.set(false);}
            }).when(access).execute(eq(subject),any());
            when(basis.require(admission,id(7),"task.read")).thenReturn(proof);
            when(proof.task().head().tenantId()).thenReturn(id(9));when(proof.task().head().projectId()).thenReturn(id(10));
            when(proof.plan().planRevision()).thenReturn(id(12));
            var pins=json.object().put("project_id",id(10)).put("task_revision",id(11)).put("task_input_sha256","a".repeat(64))
                .put("plan_revision",id(12)).put("plan_sha256","b".repeat(64)).put("template_version",id(13))
                .put("template_sha256","c".repeat(64)).put("profile_revision",id(14)).put("scope_snapshot",id(15));
            when(proof.pins()).thenAnswer(ignored->pins.deepCopy());when(proof.authorizationsSha256()).thenReturn("d".repeat(64));
            var content=new TemplateProtocol.Content("1.0.0","TEST","TEST",false,"TEST",List.of(),List.of(),List.of(),List.of(),List.of(),null,List.of(),null);
            when(proof.execution().template().view().template().content()).thenReturn(content);
            when(proof.execution().template().view().conditions()).thenReturn(List.of());
            var projection=new TaskPlanProjection.Projection(id(13),id(14),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
            when(relations.read(eq(admission),eq(id(10)),eq(id(7)),eq(id(12)),any())).thenReturn(projection);
            var source=mock(SourceReadProtocol.VerifiedSource.class,RETURNS_DEEP_STUBS);
            when(source.selection().sourceVersion()).thenReturn(id(1));when(source.selection().parseVersion()).thenReturn(id(20));
            when(source.selection().approvalRevision()).thenReturn(id(21));
            when(proof.execution().sources()).thenReturn(List.of(source));
            when(rows.head(any())).thenAnswer(invocation->{
                assertTrue(inSnapshot.get());java.util.Map<String,Object> query=invocation.getArgument(0);
                assertEquals(id(9),query.get("tenantId"));assertEquals(id(1),query.get("sourceVersion"));
                assertEquals(id(20),query.get("parseVersion"));assertEquals(id(21),query.get("approvalRevision"));
                return java.util.List.of(java.util.Map.<String,Object>of("field_revision",id(2),"revision",BigInteger.ONE));
            });
            published.put("revision","1");((ObjectNode)published.required("request")).put("parse_version",id(20)).put("approval_revision",id(21));
            when(exact.currentFields(subject,"1",id(1),id(10),id(20),id(21))).thenAnswer(invocation->{
                assertFalse(inSnapshot.get(),"SourceExact admission must not nest in snapshot");
                return json.object().set("data",published.deepCopy());
            });
            var policy=configured?new TaskFieldTargetPolicy(20,20,65536,65536,65536,"test","test",32,64)
                :new TaskFieldTargetPolicy(0,0,0,0,0,null,null);
            service=new TaskFieldTargetPreparationService(access,basis,mappings,relations,schema,json,policy,resources,
                exact,rows,ledger,registrations,mock(DataSource.class),transport);
        }
        com.fasterxml.jackson.databind.JsonNode read()throws Exception{return service.read(subject,"1",id(7));}
    }
    @Test void readRunsTwoActualSnapshotsAndReturnsOnlyVerifiedPublicationChoices()throws Exception {
        var f=new ReadFixture(true,true);var result=f.read();assertEquals(2,f.snapshotCount.get());
        verify(f.basis,times(2)).require(f.admission,id(7),"task.read");verify(f.proof,times(2)).requireUnchanged();
        verify(f.exact).currentFields(f.subject,"1",id(1),id(10),id(20),id(21));verify(f.rows,times(2)).head(any());
        assertEquals("型号－甲𠀀",result.at("/source_choices/0/raw_value").asText());assertFalse(result.toString().contains("normalized alias"));
        assertEquals(schema.hash(f.published),result.at("/source_choices/0/source_exact/manifest_sha256").asText());
        assertEquals("NOT_STARTED",result.at("/production/state").asText());assertTrue(result.required("inventory").isEmpty());
        assertTrue(result.required("allowed_actions").toString().contains("WRITE_MAPPING"));
        assertFalse(result.required("prerequisites").toString().contains("CONFIGURATION_REQUIRED"));
        verifyNoInteractions(f.ledger,f.registrations);
    }
    @Test void changedExactHeadBetweenSnapshotsRejectsTheReadResult()throws Exception {
        var f=new ReadFixture(true,true);
        doReturn(List.of(java.util.Map.of("field_revision",id(2),"revision",BigInteger.ONE)),
            List.of(java.util.Map.of("field_revision",id(22),"revision",BigInteger.TWO))).when(f.rows).head(any());
        var error=assertThrows(TenantAccessException.class,f::read);assertEquals(ErrorCode.SCOPE_STALE,error.code());assertEquals(2,f.snapshotCount.get());
    }
    @Test void changedAuthorityBetweenSnapshotsRejectsEvenUnchangedPublication()throws Exception {
        var f=new ReadFixture(true,true);when(f.proof.authorizationsSha256()).thenReturn("a".repeat(64),"b".repeat(64));
        var error=assertThrows(TenantAccessException.class,f::read);assertEquals(ErrorCode.SCOPE_STALE,error.code());assertEquals(2,f.snapshotCount.get());
    }
    @Test void missingTransportLeavesReadableChoicesAndAnExplicitConfigurationPrerequisite()throws Exception {
        var f=new ReadFixture(true,false);var result=f.read();assertEquals(2,result.required("source_choices").size());
        assertTrue(result.required("prerequisites").toString().contains("CONFIGURATION_REQUIRED"));
        assertFalse(result.required("allowed_actions").toString().contains("WRITE_MAPPING"));assertFalse(result.required("allowed_actions").toString().contains("PRODUCE"));
        verifyNoInteractions(f.ledger,f.registrations);
    }
    @Test void missingOperationalConfigurationDoesNotInventDefaultsOrWriteAnything()throws Exception {
        var f=new ReadFixture(false,true);var result=f.read();assertEquals(2,f.snapshotCount.get());
        assertTrue(result.required("prerequisites").toString().contains("CONFIGURATION_REQUIRED"));
        assertEquals("NOT_STARTED",result.at("/production/state").asText());verifyNoInteractions(f.ledger,f.registrations);
    }
    @Test void sourceEnvelopeForAnotherOriginalPublicationIsRejectedBeforeSecondSnapshot()throws Exception {
        var f=new ReadFixture(true,true);f.published.put("field_revision",id(25));
        var error=assertThrows(TenantAccessException.class,f::read);assertEquals(ErrorCode.CONTENT_MISMATCH,error.code());assertEquals(1,f.snapshotCount.get());
    }

}

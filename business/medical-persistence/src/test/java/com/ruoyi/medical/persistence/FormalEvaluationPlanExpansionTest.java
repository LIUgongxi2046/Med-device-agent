package com.ruoyi.medical.persistence;
import com.ruoyi.medical.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ruoyi.medical.persistence.PlanPreparationFixture.*;

/** Actual PlanCandidate binding and TaskPlanPolicy; template records are explicit fixture inputs. */
class FormalEvaluationPlanExpansionTest {
    final PermissionSchema json=new PermissionSchema();
    final FormalEvaluationPlanExpansion expansion=new FormalEvaluationPlanExpansion(new TaskPlanPolicy(),json);
    FormalEvaluationPlanExpansionTest()throws Exception{}
    static TemplateProtocol.Node node(int n,String parent){return new TemplateProtocol.Node(id(n),parent,"1."+n,"Fixture section "+n,n,
            new TemplateProtocol.Condition("ALWAYS",null,null,List.of()),true,"HUMAN_PROVIDED",null,List.of(),"TEXT",List.of());}
    static TemplateProtocol.Stored template(int n){return new TemplateProtocol.Stored(id(1),id(n),"APPROVED","2",
            new TemplateProtocol.Content("1.0.0","REGISTRATION_DIRECTORY","FIRST_REGISTRATION",false,"DIRECTORY",List.of(node(60,null),node(62,id(60))),List.of(),List.of(),List.of(),List.of(),null,List.of(),null),hash('a'),null,"Fixture",id(2),"1",id(2),"2026-10-18T00:00:00Z",List.of());}
    TemplateApplicabilityProtocol.TemplateView view(){return new TemplateApplicabilityProtocol.TemplateView(template(30),"UNKNOWN",List.of(
            new TemplateApplicabilityProtocol.ConditionResult("NODE",id(60),null,null,List.of(),"UNKNOWN",List.of("unknown_fixture")),
            new TemplateApplicabilityProtocol.ConditionResult("NODE",id(62),null,null,List.of(),"NOT_APPLICABLE",List.of())),List.of(),hash('b'));}
    FormalEvaluationExecutionProtocol.Node actual(){var c=new FormalEvaluationExecutionProtocol.Cell(id(5),hash('a'),basis(10),basis(11),id(6),id(7),"ACCEPTANCE",0,basis(20),basis(21),hash('b'));return new FormalEvaluationExecutionProtocol.Node(c,"N01",basis(22),basis(23),basis(24),hash('c'));}
    @Test void everyFixedNodeSurvivesIncludingUnknownAndInapplicableAndMissingWorkStaysBlocked()throws Exception{
        var x=expansion.expand(id(1),id(4),actual(),id(31),view());var p=x.validation().candidate();
        assertEquals(List.of(id(60),id(62)),p.requirementNodeIds());assertEquals(2,p.sections().size());
        assertEquals(List.of(id(60)),p.pendingConditionIds());assertTrue(p.sections().stream().allMatch(s->s.ownerId()==null&&s.completionRuleIds().isEmpty()));
        assertEquals(Set.of("APPLICABILITY_UNKNOWN","OWNER_UNASSIGNED","COMPLETION_RULE_MISSING","METHOD_REVIEW_REQUIRED"),x.blockers().stream().map(b->b.required("code").asText()).collect(java.util.stream.Collectors.toSet()));
        assertThrows(TenantAccessException.class,()->new TaskPlanPolicy().requireConfirmable(x.validation()));
    }
    @Test void serverSectionMappingIsStableForSameOriginalAndChangesAcrossOriginalExecutions()throws Exception{
        var first=expansion.expand(id(1),id(4),actual(),id(31),view());var again=expansion.expand(id(1),id(4),actual(),id(31),view());
        assertEquals(first.validation().canonicalContent(),again.validation().canonicalContent());
        assertNotEquals(first.validation().candidate().sections().get(0).sectionId(),expansion.expand(id(1),id(99),actual(),id(31),view()).validation().candidate().sections().get(0).sectionId());
        assertEquals(2,new HashSet<>(first.validation().candidate().sections().stream().map(ArtifactProtocol.PlanSection::sectionId).toList()).size());
    }
    @Test void duplicateFixedNodeCannotProduceAPlan()throws Exception{
        var t=template(30);var c=t.content();var invalid=new TemplateProtocol.Stored(t.tenant_id(),t.template_version(),t.status(),t.revision(),
                new TemplateProtocol.Content(c.schema_version(),c.document_type(),c.application_path(),c.ivd(),c.directory_profile(),List.of(node(60,null),node(60,null)),c.field_definitions(),c.repeat_definitions(),c.attachment_requirements(),c.cross_document_rules(),c.condition_schema(),c.basis_manifest(),c.format_profile()),t.content_sha256(),null,t.reason(),t.authority_grant_id(),t.authority_revision(),t.created_by(),t.created_at(),List.of());
        assertThrows(TenantAccessException.class,()->expansion.expand(id(1),id(4),actual(),id(31),new TemplateApplicabilityProtocol.TemplateView(invalid,"APPLICABLE",List.of(),List.of(),hash('b'))));
    }
}

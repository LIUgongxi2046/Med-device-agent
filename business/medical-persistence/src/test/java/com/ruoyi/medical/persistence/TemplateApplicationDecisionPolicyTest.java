package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.contracts.ResourceProtocol;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred unit checks. Synthetic records are not professional approvals or DB acceptance. */
class TemplateApplicationDecisionPolicyTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private static final String TEMPLATE="00000000-0000-4000-8000-000000000001",NODE="00000000-0000-4000-8000-000000000002",HASH="a".repeat(64);
    @Test void unknownWithNoInputKeysNeverBecomesApplicable() {
        assertEquals("UNKNOWN",TemplateApplicabilityService.combineResults(List.of(new TemplateApplicabilityProtocol.Result("UNKNOWN",List.of()))).status());
        assertEquals("NOT_APPLICABLE",TemplateApplicabilityService.combineResults(List.of(new TemplateApplicabilityProtocol.Result("UNKNOWN",List.of()),new TemplateApplicabilityProtocol.Result("NOT_APPLICABLE",List.of()))).status());
    }
    @Test void kleeneBooleanTruthTablesPreserveOnlyRelevantUnknowns() {
        var engine=new RuleExpression();
        for(var pair:List.of(new String[]{"false and #attributes['missing']","NOT_APPLICABLE"},new String[]{"true or #attributes['missing']","APPLICABLE"},
                new String[]{"true and #attributes['missing']","UNKNOWN"},new String[]{"false or #attributes['missing']","UNKNOWN"},
                new String[]{"!#attributes['missing']","UNKNOWN"},new String[]{"(false and #attributes['missing']) == false","APPLICABLE"})) {
            var compiled=engine.compile(new TemplateApplicabilityProtocol.Expression("1.0.0",RuleExpression.ENGINE,pair[0],List.of("missing"),List.of()));
            var result=engine.evaluate(compiled,List.of());assertEquals(pair[1],result.status(),pair[0]);
            assertEquals(pair[1].equals("UNKNOWN")?List.of("missing"):List.of(),result.unknown_input_keys());
        }
    }
    @Test void humanCannotOverrideMachineFalseOrRelaxRequiredNode() {
        assertThrows(SchemaRejection.class,()->TemplateApplicationService.validateTarget(TEMPLATE,request("APPLICABLE",false),view(false,"NOT_APPLICABLE")));
        assertThrows(SchemaRejection.class,()->TemplateApplicationService.validateTarget(TEMPLATE,request("APPLICABLE",false),view(true,"UNKNOWN")));
        assertThrows(SchemaRejection.class,()->TemplateApplicationService.validateTarget(TEMPLATE,request("NOT_APPLICABLE",true),view(true,"UNKNOWN")));
        assertDoesNotThrow(()->TemplateApplicationService.validateTarget(TEMPLATE,request("APPLICABLE",true),view(true,"UNKNOWN")));
        assertDoesNotThrow(()->TemplateApplicationService.validateTarget(TEMPLATE,request("NOT_APPLICABLE",false),view(false,"UNKNOWN")));
    }
    @Test void machineViewRetainsUnknownDespiteAnEffectiveHumanChoice() {
        var machine=view(true,"UNKNOWN");var effective=machine.conditions().stream().map(c->new TemplateApplicabilityProtocol.ConditionResult(c.scope(),c.target_id(),null,null,List.of(),"APPLICABLE",List.of())).toList();
        var resolved=new TemplateApplicabilityProtocol.TemplateView(machine.template(),"APPLICABLE",effective,List.of(),HASH,machine.conditions(),List.of(request("APPLICABLE",true)));
        var raw=TemplateApplicabilityService.machineView(resolved);assertEquals("UNKNOWN",raw.conditions().get(2).status());assertTrue(raw.human_decisions().isEmpty());
    }
    @Test void normalConsumerProjectionUsesPinnedLatestDecisionAndKeepsMachineUnknown()throws Exception {
        var machine=view(true,"UNKNOWN");var permissions=new PermissionSchema();
        var req=(com.fasterxml.jackson.databind.node.ObjectNode)request("APPLICABLE",true);
        req.put("baseline_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(permissions.canonical(permissions.tree(machine))));
        req.put("reason","Human reviewed the original fixed condition");
        var d=json.createObjectNode();d.set("request",req);
        var result=TemplateApplicabilityService.resolveHumanDecisions(machine,List.of(d),permissions);
        assertEquals("APPLICABLE",result.conditions().get(2).status());assertEquals("UNKNOWN",result.machine_conditions().get(2).status());
        assertEquals(List.of(d),result.human_decisions());assertEquals(machine.template(),result.template());
        var stale=d.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)stale.required("request")).put("baseline_sha256","b".repeat(64));
        var invalidated=TemplateApplicabilityService.resolveHumanDecisions(machine,List.of(d,stale),permissions);
        assertEquals("UNKNOWN",invalidated.conditions().get(2).status());assertTrue(invalidated.human_decisions().isEmpty());
        var changed=new TemplateApplicabilityProtocol.TemplateView(machine.template(),machine.applicability(),machine.conditions(),machine.missing_requirements(),"c".repeat(64));
        assertTrue(TemplateApplicabilityService.resolveHumanDecisions(changed,List.of(d),permissions).human_decisions().isEmpty());
    }
    @Test void humanInputIsClosedAndRequiresExplicitReasonAndNodeRequirement()throws Exception {
        var schema=new TemplateApplicabilitySchema(new PermissionSchema());var request=(com.fasterxml.jackson.databind.node.ObjectNode)request("APPLICABLE",true);
        request.put("schema_version","1.0.0").put("project_id",TEMPLATE).put("profile_revision",TEMPLATE).put("document_type","REGISTRATION_DIRECTORY")
            .put("baseline_sha256",HASH).put("expected_revision","0").put("reason","Explicit human determination against fixed source");
        assertDoesNotThrow(()->schema.verifyApplication("HumanDecisionInput",request));
        var unknown=request.deepCopy().put("applicability","UNKNOWN");assertThrows(SchemaRejection.class,()->schema.verifyApplication("HumanDecisionInput",unknown));
        var empty=request.deepCopy().put("reason","");assertThrows(SchemaRejection.class,()->schema.verifyApplication("HumanDecisionInput",empty));
        var approval=request.deepCopy().put("approved",true);assertThrows(SchemaRejection.class,()->schema.verifyApplication("HumanDecisionInput",approval));
        var absent=request.deepCopy();absent.remove("required");assertThrows(SchemaRejection.class,()->schema.verifyApplication("HumanDecisionInput",absent));
    }
    @Test void legacyWireProjectionDoesNotDiscardInternalDecisionProof()throws Exception {
        var machine=view(true,"UNKNOWN");var permissions=new PermissionSchema();
        var req=(com.fasterxml.jackson.databind.node.ObjectNode)request("APPLICABLE",true);
        req.put("baseline_sha256",org.apache.commons.codec.digest.DigestUtils.sha256Hex(permissions.canonical(permissions.tree(machine))));
        var decision=json.createObjectNode();decision.set("request",req);
        var effective=TemplateApplicabilityService.resolveHumanDecisions(machine,List.of(decision),permissions);
        var legacy=json.valueToTree(TemplateApplicabilityService.legacyView(effective));
        assertEquals(5,legacy.size());assertFalse(legacy.has("machine_conditions"));assertFalse(legacy.has("human_decisions"));
        assertEquals("APPLICABLE",legacy.required("conditions").get(2).required("status").asText());
        assertEquals("UNKNOWN",effective.machine_conditions().get(2).status());assertEquals(List.of(decision),effective.human_decisions());
        assertEquals(List.of(unknownNodeRequirement()),machine.missing_requirements());
        assertTrue(effective.missing_requirements().isEmpty());assertTrue(legacy.required("missing_requirements").isEmpty());
        var restored=TemplateApplicabilityService.machineView(effective);
        assertEquals(List.of(unknownNodeRequirement()),restored.missing_requirements());assertTrue(restored.human_decisions().isEmpty());
        assertEquals(json.valueToTree(machine),json.valueToTree(restored));
    }
    @Test void historicalManifestCompatibilityIsExactAndDoesNotAcceptArbitraryHash() {
        String current="3a6235b4d98d13f7156389cf1538ff8cd23e898d1db8aa3fec18c04d72234525";
        assertTrue(TemplateApplicabilitySchema.applicationManifestMatches(current,current));
        assertTrue(TemplateApplicabilitySchema.applicationManifestMatches("06f3f94b5108b33d43b412a86417364d1529ba5d62405a0fa2112e2e68102624",current));
        assertFalse(TemplateApplicabilitySchema.applicationManifestMatches("a".repeat(64),current));
    }
    private ResourceProtocol.Requirement unknownNodeRequirement() {
        return new ResourceProtocol.Requirement("template:"+TEMPLATE+":node:"+NODE,NODE,null,"APPLICABILITY_UNKNOWN",List.of());
    }
    private JsonNode request(String status,boolean required){return json.createObjectNode().put("template_sha256",HASH).put("scope","NODE").put("target_id",NODE).put("applicability",status).put("required",required);}
    private TemplateApplicabilityProtocol.TemplateView view(boolean required,String status) {
        var condition=new TemplateProtocol.Condition("ALWAYS",null,null,List.of());
        var node=new TemplateProtocol.Node(NODE,null,"1","Synthetic node",1,condition,required,"TEXT",null,List.of(),"SYNTHETIC",List.of());
        var content=new TemplateProtocol.Content("1.0.0","REGISTRATION_DIRECTORY","FIRST_REGISTRATION",false,"SYNTHETIC",List.of(node),List.of(),List.of(),List.of(),List.of(),null,List.of(),null);
        var stored=new TemplateProtocol.Stored(TEMPLATE,TEMPLATE,"APPROVED","1",content,HASH,null,"Synthetic",TEMPLATE,"1",TEMPLATE,"2026-10-20T00:00:00Z",List.of());
        var conditions=List.of(new TemplateApplicabilityProtocol.ConditionResult("PROFILE",TEMPLATE,null,null,List.of(),"APPLICABLE",List.of()),new TemplateApplicabilityProtocol.ConditionResult("TEMPLATE",TEMPLATE,null,null,List.of(),"APPLICABLE",List.of()),new TemplateApplicabilityProtocol.ConditionResult("NODE",NODE,null,null,List.of(),status,List.of()));
        // UNKNOWN remains unresolved even when no missing input key can be named.
        var missing="UNKNOWN".equals(status)?List.of(unknownNodeRequirement()):List.<ResourceProtocol.Requirement>of();
        return new TemplateApplicabilityProtocol.TemplateView(stored,"APPLICABLE",conditions,missing,HASH);
    }
}

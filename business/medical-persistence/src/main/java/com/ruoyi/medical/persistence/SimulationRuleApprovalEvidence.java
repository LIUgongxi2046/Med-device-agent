package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.persistence.mapper.SimulationRuleVersionMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;

/** Reconstructs the complete current approval evidence; an EXISTS/status/id match is not evidence integrity. */
final class SimulationRuleApprovalEvidence {
    private SimulationRuleApprovalEvidence() { }
    static void require(SimulationRuleVersionMapper mapper, SimulationRuleVersionSchema schema, PermissionSchema json,
            Map<String,Object> values, SimulationRuleVersionProtocol.Stored actual) throws IOException {
        var rows=mapper.approvedEvidence(values);if(rows.size()!=1)bad();verify(schema,json,actual,rows.get(0));
    }
    static void verify(SimulationRuleVersionSchema schema,PermissionSchema json,SimulationRuleVersionProtocol.Stored actual,
            SimulationRuleVersionProtocol.ApprovalEvidenceRow row) throws IOException {
        schema.readView(actual,actual.rule_version());
        if(!"APPROVED".equals(actual.status()))bad();
        var d=schema.read(row.decisionJson());var receipt=schema.receipt(row.operationResponse());
        var data=receipt.required("data");var request=schema.decide(json.canonical(d.required("request")).getBytes(StandardCharsets.UTF_8));
        var baseline=request.baseline_manifest();
        if(!"APPROVE".equals(request.decision())||!request.target_revision().equals(actual.rule_version())
                ||!"APPROVE".equals(text(d,"decision"))||!"APPROVED".equals(text(d,"to_status"))
                ||!List.of("DRAFT","RETURNED").contains(text(d,"from_status"))
                ||!actual.rule_version().equals(text(d,"rule_version"))||!actual.content_sha256().equals(text(d,"rule_sha256"))
                ||!actual.revision().equals(text(d,"resulting_revision"))
                ||!PermissionSchema.next(request.expected_revision()).equals(actual.revision())
                ||!request.expected_revision().equals(text(d,"expected_revision"))||!request.reason().equals(text(d,"reason")))bad();
        if(!baseline.rule_version().equals(actual.rule_version())||!baseline.content_sha256().equals(actual.content_sha256())
                ||!baseline.definition_manifest_sha256().equals(schema.contentManifestSha256())
                ||!baseline.dependency_refs().equals(actual.dependency_refs())||!baseline.binding_sha256().equals(actual.binding_sha256())
                ||!json.tree(baseline).equals(d.required("baseline_manifest"))
                ||!hash(json,d.required("baseline_manifest")).equals(text(d,"baseline_sha256"))
                ||!hash(json,d.required("dependency_authorizations")).equals(baseline.dependency_authorizations_sha256()))bad();
        if(!d.required("receipt").equals(receipt)||!hash(json,receipt).equals(text(d,"receipt_sha256"))
                ||!actual.rule_version().equals(text(data,"rule_version"))||!actual.content_sha256().equals(text(data,"content_sha256"))
                ||!actual.binding_sha256().equals(text(data,"binding_sha256"))||!actual.revision().equals(text(data,"revision"))
                ||!"APPROVED".equals(text(data,"status"))||!"APPROVE".equals(text(data,"decision"))
                ||!text(d,"operation_id").equals(text(data,"operation_id"))||!text(d,"decision_id").equals(text(data,"decision_id"))
                ||!text(d,"created_by").equals(text(data,"actor_id"))||!actual.tenant_id().equals(text(data.required("current_context"),"tenant_id")))bad();
        if(!"1.0.0".equals(text(d,"schema_version"))||!schema.manifestSha256().equals(text(d,"schema_manifest_sha256"))
                ||!row.bindingHash().equals(actual.binding_sha256())||!row.bindingCreator().equals(text(d,"created_by"))
                ||!row.bindingClassification().equals(text(d,"classification"))
                ||!time(text(d,"created_at")).equals(row.bindingCreatedAt())||!time(text(data,"decided_at")).equals(row.bindingCreatedAt()))bad();
        for(String revision:List.of("authority_revision","auth_epoch","governance_epoch"))
            if(PermissionSchema.revision(text(d,revision)).signum()<=0)bad();
        com.ruoyi.medical.contracts.WireValues.uuid(text(d,"authority_grant"));
        verifyAudit(json,actual,d,schema.read(row.auditJson()),schema.read(row.eventJson()));
    }
    /** Audit snapshots describe this historical transition, not necessarily the latest current state. */
    static void verifyAudit(PermissionSchema json,SimulationRuleVersionProtocol.Stored actual,JsonNode d,JsonNode audit,JsonNode event) {
        var expectedAfter=(ObjectNode)json.tree(actual);expectedAfter.put("status",text(d,"to_status"));expectedAfter.put("revision",text(d,"resulting_revision"));
        var before=expectedAfter.deepCopy();before.put("status",text(d,"from_status"));before.put("revision",text(d,"expected_revision"));
        var expectedAudit=json.object().put("schema_version","1.0.0").put("operation_id",text(d,"operation_id"));
        expectedAudit.set("request",d.required("request"));expectedAudit.set("before",before);expectedAudit.set("after",expectedAfter);expectedAudit.set("decision",d);
        var expectedEvent=json.object().put("schema_version","1.0.0");expectedEvent.set("decision",d);
        if(!expectedAudit.equals(audit)||!expectedEvent.equals(event))bad();
    }
    private static LocalDateTime time(String value){return java.time.OffsetDateTime.parse(value).toLocalDateTime();}
    private static String hash(PermissionSchema json,JsonNode value)throws IOException{return DigestUtils.sha256Hex(json.canonical(value));}
    private static String text(JsonNode value,String key){var field=value.required(key);if(!field.isTextual())bad();return field.textValue();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

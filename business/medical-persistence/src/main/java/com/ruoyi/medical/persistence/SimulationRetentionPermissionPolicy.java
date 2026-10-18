package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Explicit simulation-record retention approval action. Registration grants no permission. */
@Component
public final class SimulationRetentionPermissionPolicy {
    public static final String ACTION="model.simulation.retention.configure";
    public static final String PURPOSE=SimulationExecutionPermissionPolicy.PURPOSE;
    public static final String RESOURCE="simulation-retention-permission-policy.json";
    private final ResourceReadAuthority authority;
    private final JsonNode definition;
    private final String manifest;
    public SimulationRetentionPermissionPolicy(PermissionSchema json,ResourceReadAuthority authority)throws IOException {
        this.authority=java.util.Objects.requireNonNull(authority);
        byte[] bytes;
        try(var stream=SimulationRetentionPermissionPolicy.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if(stream==null)throw new IOException("SIMULATION_RETENTION_POLICY_MISSING");bytes=stream.readAllBytes();
        }
        definition=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().readTree(bytes);
        var expected=json.tree(Map.of("schema_version","1.0.0","registration_id","SIMULATION_RETENTION_CONFIGURATION_V1",
            "action",ACTION,"purpose",PURPOSE,"resource_kinds",List.of("EVALUATION"),
            "bounded_fields",List.of("retain_until","maximum_record_bytes"),"grants_created",false,"authorizes_execution",false,"financial_budget_approval",false));
        if(!expected.equals(definition))throw new IOException("SIMULATION_RETENTION_POLICY_MISMATCH");
        manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(json.tree(Map.of("schema_version","1.0.0",RESOURCE,org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))));
    }
    public PermissionProtocol.GrantRow requireEvaluation(EnterpriseAccess.Admission admission,String projectId,String evaluationId)throws IOException {
        WireValues.uuid(evaluationId);if(projectId!=null)WireValues.uuid(projectId);
        return authority.require(admission,new PermissionProtocol.Scope(admission.context().tenant_id(),projectId,"EVALUATION",evaluationId),ACTION,PURPOSE);
    }
    public JsonNode definition(){return definition.deepCopy();}
    public String manifestSha256(){return manifest;}
}

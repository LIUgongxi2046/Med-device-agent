package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.WireValues;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registration of a narrow action vocabulary, never creation of a permission grant. */
@Component
public final class SimulationExecutionPermissionPolicy {
    public static final String ACTION="model.simulation.execute";
    public static final String PURPOSE="SIMULATION_EVALUATION";
    public static final String RESOURCE_KIND="EVALUATION";
    public static final String RESOURCE="simulation-execution-permission-policy.json";
    private final ResourceReadAuthority authority;
    private final JsonNode definition;
    private final String manifest;

    public SimulationExecutionPermissionPolicy(PermissionSchema json,ResourceReadAuthority authority)throws IOException {
        this.authority=java.util.Objects.requireNonNull(authority);
        byte[] bytes;
        try(var stream=SimulationExecutionPermissionPolicy.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if(stream==null)throw new IOException("SIMULATION_PERMISSION_POLICY_MISSING");
            bytes=stream.readAllBytes();
        }
        definition=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build().readTree(bytes);
        var expected=json.tree(Map.of("schema_version","1.0.0","registration_id","SIMULATION_EVALUATION_EXECUTION_V1",
            "action",ACTION,"purpose",PURPOSE,"resource_kind",RESOURCE_KIND,"scope_id_semantics","evaluation_batch.evaluation_id",
            "execution_kind","SYSTEM_JOB","production_eligible",false,"grants_created",false,
            "does_not_authorize",List.of("TASK_EXECUTION","PRODUCTION_SYSTEM_JOB","EXTERNAL_PROVIDER_CALL","ROLE_PUBLICATION")));
        if(!expected.equals(definition))throw new IOException("SIMULATION_PERMISSION_POLICY_MISMATCH");
        manifest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(json.tree(Map.of("schema_version","1.0.0",RESOURCE,org.apache.commons.codec.digest.DigestUtils.sha256Hex(bytes)))));
    }

    public PermissionProtocol.GrantRow require(EnterpriseAccess.Admission admission,String projectId,String evaluationId)throws IOException {
        WireValues.uuid(evaluationId);
        if(projectId!=null)WireValues.uuid(projectId);
        return authority.require(admission,new PermissionProtocol.Scope(admission.context().tenant_id(),projectId,RESOURCE_KIND,evaluationId),ACTION,PURPOSE);
    }
    public JsonNode definition(){return definition.deepCopy();}
    public String manifestSha256(){return manifest;}
}

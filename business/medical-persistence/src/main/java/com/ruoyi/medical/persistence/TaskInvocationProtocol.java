package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.CommonProtocol;
import com.ruoyi.medical.contracts.WireValues;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/** Closed TASK request identity. Constructing data never authorizes a model call. */
public final class TaskInvocationProtocol {
    private TaskInvocationProtocol() { }
    public record Node(String tenant_id,String project_id,String task_id,String run_id,String principal_id,
            String step_id,String host_action_id,String session_id,String lease_generation,String worker_service_uri,
            String node_input_artifact_id,String node_input_sha256,String node_input_receipt_sha256,
            String supervisor_receipt_id,String supervisor_receipt_sha256,String classification) {
        public Node {
            for(String id:List.of(tenant_id,project_id,task_id,run_id,principal_id,step_id,host_action_id,session_id,
                    node_input_artifact_id,supervisor_receipt_id))WireValues.uuid(id);
            for(String hash:List.of(node_input_sha256,node_input_receipt_sha256,supervisor_receipt_sha256))WireValues.hash(hash);
            if(lease_generation==null||!lease_generation.matches("[1-9][0-9]{0,19}")||new BigInteger(lease_generation).bitLength()>64
                    ||worker_service_uri==null||!java.net.URI.create(worker_service_uri).isAbsolute()
                    ||!List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(classification))throw new IllegalArgumentException("TASK_NODE_IDENTITY_REQUIRED");
        }
    }
    public record Request(String schema_version,String execution_kind,String node_kind,Node node,
            String model_config_revision,String business_purpose,CommonProtocol.ArtifactRef exact_input,
            PermissionProtocol.Basis scope_snapshot_basis,PermissionProtocol.Basis tokenizer_basis,
            PermissionProtocol.Basis role_basis,PermissionProtocol.Basis prompt_basis,List<PermissionProtocol.Basis> rule_bases,
            PermissionProtocol.Basis enterprise_budget_basis,PermissionProtocol.Basis parent_budget_basis,
            String maximum_input_tokens,JsonNode request_parameters) {
        public Request {
            if(!"1.0.0".equals(schema_version)||!"TASK".equals(execution_kind)||!"N02".equals(node_kind)
                    ||business_purpose==null||!business_purpose.matches("[A-Z][A-Z0-9_]{0,63}"))throw new IllegalArgumentException("TASK_REQUEST_REQUIRED");
            Objects.requireNonNull(node);WireValues.uuid(model_config_revision);Objects.requireNonNull(exact_input);
            Objects.requireNonNull(scope_snapshot_basis);Objects.requireNonNull(tokenizer_basis);Objects.requireNonNull(role_basis);
            Objects.requireNonNull(prompt_basis);Objects.requireNonNull(enterprise_budget_basis);Objects.requireNonNull(parent_budget_basis);
            rule_bases=List.copyOf(rule_bases);request_parameters=Objects.requireNonNull(request_parameters).deepCopy();
            if(rule_bases.size()>256||rule_bases.stream().distinct().count()!=rule_bases.size()
                    ||enterprise_budget_basis.version_id().equals(parent_budget_basis.version_id())
                    ||maximum_input_tokens==null||!maximum_input_tokens.matches("[1-9][0-9]{0,19}")
                    ||new BigInteger(maximum_input_tokens).bitLength()>64)throw new IllegalArgumentException("TASK_REQUEST_BOUNDS");
            if(node.node_input_artifact_id().equals(exact_input.artifactId()))throw new IllegalArgumentException("TASK_INPUT_IS_NOT_EXACT_INPUT");
        }
        @Override public JsonNode request_parameters(){return request_parameters.deepCopy();}
    }
}

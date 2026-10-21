package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Pure exact-model narrowing. Data passed here grants nothing: callers must independently
 * verify the publication, policy/model owner, original 093 approval and current grants. */
final class TaskModelNodePermissions {
    private TaskModelNodePermissions() { }
    static List<RunTokenAuthoritySchema.Permission> declared(PermissionProtocol.Basis model,
            PermissionProtocol.Scope configuration,JsonNode authorizations) {
        if(model==null||configuration==null||!"CONFIGURATION".equals(configuration.resource_kind())
                ||authorizations==null||!authorizations.path("rule").path("request").path("operations").isArray())deny();
        var result=new ArrayList<RunTokenAuthoritySchema.Permission>();
        for(var operation:authorizations.path("rule").path("request").path("operations")){
            if(!"MODEL_CONFIG".equals(operation.path("selector").asText())||!"model.send".equals(operation.path("action").asText()))continue;
            var version=operation.path("model_version");
            if(!model.version_id().equals(version.path("version_id").asText())||!model.content_sha256().equals(version.path("content_sha256").asText()))continue;
            var purpose=operation.path("purpose");if(!purpose.isTextual()||!purpose.textValue().matches("[A-Z][A-Z0-9_]{0,63}"))deny();
            result.add(new RunTokenAuthoritySchema.Permission(configuration,"model.send",purpose.textValue()));
        }
        if(result.isEmpty()||new HashSet<>(result).size()!=result.size())deny();
        return List.copyOf(result);
    }
    private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

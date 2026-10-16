package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.PathType;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SourceUploadStatusSchema {
    private final SchemaCatalogue catalogue;
    private final PermissionSchema json;
    private final JsonSchema compiled;

    public SourceUploadStatusSchema(PermissionSchema json) throws IOException,NoSuchAlgorithmException {
        this.json=json; catalogue=SchemaCatalogue.fromClasspath(); var reader=JsonMapper.builder().build();
        ObjectNode document;
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-upload-status.schema.json"))) {
            document=(ObjectNode)reader.readTree(input);
        }
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/source-governance.schema.json"))) {
            var governance=reader.readTree(input);
            if(!document.required("$defs").required("SourceReview").equals(governance.required("$defs").required("Review")))
                throw new IllegalArgumentException("上传状态与来源审核定义不一致");
        }
        compiled=catalogue.compileApiSchema(document); compiled.initializeValidators();
        for(String name:java.util.List.of("Completion","Source","View")) definition(name).initializeValidators();
    }

    JsonNode envelope(JsonNode value,String revision) {
        if(!definition("View").validate(value).isEmpty()) throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);
        var response=json.tree(new InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),revision,value));
        catalogue.validate("interfaces",response,"Envelope"); return response;
    }

    private JsonSchema definition(String name) { return compiled.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)); }
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class SimulationFixtureController {
    private static final int MAXIMUM_REQUEST_BYTES=65536;
    private final ApplicationSessionIdentity identities;
    private final SimulationInvocationSources sources;
    private final PermissionSchema json;
    private final com.ruoyi.medical.contracts.SchemaCatalogue schemas;
    private final JsonMapper parser=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public SimulationFixtureController(ApplicationSessionIdentity identities,SimulationInvocationSources sources,PermissionSchema json) throws Exception {
        this.identities=identities; this.sources=sources; this.json=json; this.schemas=com.ruoyi.medical.contracts.SchemaCatalogue.fromClasspath();
    }
    @PostMapping(value="/api/simulation-fixtures",consumes="application/json")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public JsonNode register(HttpServletRequest request,HttpServletResponse response,Authentication authentication,
            @RequestHeader(value="X-Schema-Version",required=false) String version,
            @RequestHeader(value="X-Context-Revision",required=false) String context) throws Exception {
        header(request,"X-Schema-Version",version); header(request,"X-Context-Revision",context);
        if(!"1.0.0".equals(version)) throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);
        PermissionSchema.revision(context); if(request.getQueryString()!=null) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        var subject=identities.requireCurrent(request,authentication);
        if(request.getContentLengthLong()>MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        byte[] bytes=request.getInputStream().readNBytes(MAXIMUM_REQUEST_BYTES+1);
        if(bytes.length>MAXIMUM_REQUEST_BYTES) throw new TenantAccessException(ErrorCode.FILE_TOO_LARGE);
        var registered=sources.register(subject,context,parser.readTree(bytes));
        // No private object addresses, source bytes or authorization proof are exposed.
        var receipt=json.object(); receipt.put("schema_version","1.0.0"); receipt.put("case_id",registered.caseId());
        receipt.put("case_receipt_id",registered.caseReceiptId()); receipt.put("model_config_revision",registered.configRevision());
        receipt.put("case_sha256",registered.caseSha256()); receipt.put("input_sha256",registered.inputSha256());
        receipt.put("input_data_kind","SIMULATION"); receipt.put("production_eligible",false);
        schemas.validate("simulation-execution",receipt,"SimulationFixtureRegistrationReceipt");
        var envelope=json.tree(new com.ruoyi.medical.contracts.InterfaceProtocol.Envelope("1.0.0",java.util.UUID.randomUUID().toString(),"1",receipt));
        schemas.validate("interfaces",envelope,"Envelope");
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Schema-Version","1.0.0");
        response.setHeader("X-Resource-Revision","1"); return envelope;
    }
    private static void header(HttpServletRequest request,String name,String value) {
        var values=request.getHeaders(name);
        if(values==null || !values.hasMoreElements() || !java.util.Objects.equals(values.nextElement(),value) || values.hasMoreElements())
            throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
    }
    @ExceptionHandler(TenantAccessException.class)
    void denied(TenantAccessException failure,HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response,failure.code()==ErrorCode.ACCESS_DENIED?403:
            failure.code()==ErrorCode.FILE_TOO_LARGE?413:409,failure.code());
    }
    @ExceptionHandler(SchemaRejection.class)
    void invalid(SchemaRejection failure,HttpServletResponse response) throws java.io.IOException {
        new IdentityErrorResponseWriter().write(response,422,failure.code());
    }
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class EnterpriseMemberLabelsController {
    private final EnterpriseAccess access;
    private final ApplicationSessionIdentity identities;
    private final ResourceReadAuthority resources;
    private final PermissionSchema json;
    private final JdbcTemplate db;
    private final JsonSchema schema;
    public EnterpriseMemberLabelsController(EnterpriseAccess access,ApplicationSessionIdentity identities,ResourceReadAuthority resources,PermissionSchema json,@Qualifier("masterDataSource")DataSource data)throws IOException{
        this.access=access;this.identities=identities;this.resources=resources;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/enterprise-member-labels.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(input),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}schema.initializeValidators();
    }
    @GetMapping("/api/enterprise-member-labels")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);for(var parameter:request.getParameterMap().entrySet())if(!parameter.getKey().equals("after")||parameter.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String after=request.getParameter("after");if(after!=null)WireValues.uuid(after);
        return access.execute(identities.requireCurrent(request,authentication),a->org.springframework.util.function.ThrowingSupplier.of(()->{
            if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var scope=new PermissionProtocol.Scope(a.context().tenant_id(),null,"ENTERPRISE",a.context().tenant_id());if(resources.find(a,scope,"member.read","MEMBER_DIRECTORY").isEmpty()&&resources.find(a,scope,"member.manage","MEMBER_DIRECTORY").isEmpty())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
            var rows=db.queryForList("SELECT m.user_id,m.status,m.valid_from,m.valid_to,a.display_name,a.username FROM tenant_member m LEFT JOIN platform_local_account a ON a.user_id=m.user_id WHERE m.tenant_id=? AND (? IS NULL OR m.user_id>?) ORDER BY m.user_id LIMIT 101",a.context().tenant_id(),after,after);
            var result=json.object().put("schema_version","1.0.0").put("tenant_id",a.context().tenant_id()).put("complete",rows.size()<=100);var items=result.putArray("items");
            for(var row:rows.subList(0,Math.min(rows.size(),100))){var item=json.object().put("user_id",row.get("user_id").toString()).put("member_status",row.get("status").toString());for(String field:List.of("display_name","username"))item.set(field,json.tree(row.get(field)));for(String field:List.of("valid_from","valid_to"))item.set(field,row.get(field)==null?json.tree(null):json.tree(DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(row.get(field)))));items.add(item);}
            if(rows.size()>100)result.put("next_after",items.get(99).required("user_id").asText());else result.putNull("next_after");if(!schema.validate(result).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,result));
        }).get());
    }
    @ExceptionHandler(IllegalArgumentException.class)void invalid(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}

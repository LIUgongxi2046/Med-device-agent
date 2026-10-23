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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class CustomerConnectorWorkspaceController {
    private static final String PURPOSE="CUSTOMER_CONNECTOR";
    private final EnterpriseAccess access;
    private final ApplicationSessionIdentity identities;
    private final PermissionSchema json;
    private final ResourceReadAuthority resources;
    private final CustomerConnectorService connectors;
    private final ObjectProvider<CustomerConnectorAdapter> adapters;
    private final ObjectProvider<CustomerConnectorCredentials> credentials;
    private final JdbcTemplate db;
    private final JsonSchema schema;
    public CustomerConnectorWorkspaceController(EnterpriseAccess access,ApplicationSessionIdentity identities,PermissionSchema json,ResourceReadAuthority resources,CustomerConnectorService connectors,ObjectProvider<CustomerConnectorAdapter> adapters,ObjectProvider<CustomerConnectorCredentials> credentials,@Qualifier("masterDataSource")DataSource data)throws IOException{
        this.access=access;this.identities=identities;this.json=json;this.resources=resources;this.connectors=connectors;this.adapters=adapters;this.credentials=credentials;db=new JdbcTemplate(data);db.setQueryTimeout(5);
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/connector-workspace.schema.json"))){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(JsonMapper.builder().build().readTree(input),SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}schema.initializeValidators();
    }
    @GetMapping("/api/connector-workspace")
    public JsonNode workspace(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);if(request.getQueryString()!=null||request.getInputStream().read()!=-1)invalid();
        var subject=identities.requireCurrent(request,authentication);
        return access.execute(subject,a->org.springframework.util.function.ThrowingSupplier.of(()->{
            current(a,context);var value=json.object().put("schema_version","1.0.0").put("identity_mode",a.bindingView().mode());value.set("current_context",json.tree(a.context()));
            var actions=value.putArray("allowed_actions");var scope=new PermissionProtocol.Scope(a.context().tenant_id(),null,"ENTERPRISE",a.context().tenant_id());
            for(String action:List.of("connector.read","connector.configure","connector.approve","connector.sync"))if(resources.find(a,scope,action,PURPOSE).isPresent())actions.add(action);
            value.put("approved_credential_directory_configured",credentials.getIfAvailable()!=null).put("customer_protocol_adapter_configured",adapters.orderedStream().findAny().isPresent());validate("Workspace",value);return envelope(context,value,response);
        }).get());
    }
    @GetMapping("/api/connector-directory")
    public JsonNode directory(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);if(request.getInputStream().read()!=-1)invalid();
        for(var parameter:request.getParameterMap().entrySet())if(!Set.of("project_id","after").contains(parameter.getKey())||parameter.getValue().length!=1)invalid();
        String project=request.getParameter("project_id"),after=request.getParameter("after");WireValues.uuid(project);if(after!=null)WireValues.uuid(after);
        return access.execute(identities.requireCurrent(request,authentication),a->org.springframework.util.function.ThrowingSupplier.of(()->{
            current(a,context);resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),null,"ENTERPRISE",a.context().tenant_id()),"connector.read",PURPOSE);resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project),"project.read",PURPOSE);
            var rows=db.queryForList("SELECT connector_id,current_revision,revision,created_at,updated_at FROM customer_connector_head WHERE tenant_id=? AND project_id=? AND current_revision IS NOT NULL AND (? IS NULL OR connector_id>?) ORDER BY connector_id LIMIT 26",a.context().tenant_id(),project,after,after);
            var value=json.object().put("schema_version","1.0.0").put("project_id",project).put("complete",rows.size()<=25).put("pagination_mode","LIVE_DIRECTORY");var items=value.putArray("items");
            for(var row:rows.subList(0,Math.min(25,rows.size()))){var configuration=connectors.view(a,Objects.requireNonNull(row.get("current_revision")).toString());if(!project.equals(configuration.required("definition").required("project_id").asText())||!row.get("connector_id").toString().equals(configuration.required("definition").required("connector_id").asText()))throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);var item=json.object().put("connector_id",row.get("connector_id").toString()).put("head_revision",row.get("revision").toString()).put("created_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(row.get("created_at")))).put("updated_at",DatabaseTimestamp.format(KnowledgeFeedbackProofs.time(row.get("updated_at"))));item.set("configuration",configuration);items.add(item);}
            if(rows.size()>25)value.put("next_after",items.get(24).required("connector_id").asText());else value.putNull("next_after");validate("Directory",value);return envelope(context,value,response);
        }).get());
    }
    private JsonNode envelope(String context,JsonNode value,HttpServletResponse response){String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,value));}
    private void validate(String name,JsonNode value){if(!schema.getSubSchema(new JsonNodePath(PathType.JSON_POINTER).append("$defs").append(name)).validate(value).isEmpty())invalid();}
    private static void current(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
    private static void invalid(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(IllegalArgumentException.class)void invalidValue(HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,ErrorCode.SCHEMA_INVALID);}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,SimulationRuleVersionController.errorStatus(failure.code()),failure.code());}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public final class BusinessOperationsController {
    private final BusinessOperationsService service;
    private final ApplicationSessionIdentity identities;
    private final PermissionSchema json;
    public BusinessOperationsController(BusinessOperationsService service,ApplicationSessionIdentity identities,PermissionSchema json){this.service=service;this.identities=identities;this.json=json;}
    @GetMapping("/api/business-operations/metrics")
    public JsonNode metrics(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);
        var query=query(request);
        var result=service.read(identities.requireCurrent(request,authentication),context,query);
        String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);
        return json.tree(new InterfaceProtocol.Envelope("1.0.0",id,context,result));
    }
    @GetMapping(value="/api/business-operations/metrics.csv",produces="text/csv")
    public void export(HttpServletRequest request,HttpServletResponse response,Authentication authentication)throws Exception{
        String context=BusinessAuditController.headers(request,response);var value=service.read(identities.requireCurrent(request,authentication),context,query(request),true);
        String id=UUID.randomUUID().toString();response.setHeader("X-Request-Id",id);response.setHeader("Content-Disposition","attachment; filename=business-operations.csv");response.setContentType("text/csv;charset=UTF-8");
        var csv=new StringBuilder("section,name,project_id,currency,numerator,denominator,known_amount,total_amount,unknown_count,detail\r\n");
        var fields=value.fieldNames();while(fields.hasNext()){String name=fields.next();JsonNode item=value.get(name);
            if(name.equals("cost_by_currency")){for(var cost:item)cost(csv,name,"",null,cost,null);}
            else if(name.equals("adoption")||name.equals("return"))csv.append(line("metric",name,null,null,number(item,"numerator"),number(item,"denominator"),null,null,null,item.toString()));
            else if(name.equals("dimension_groups")){int index=0;for(var group:item){String label=Integer.toString(index++);csv.append(line(name,label,null,null,null,null,null,null,null,group.toString()));for(var c:group.required("cost_by_currency"))cost(csv,"dimension_cost",label,null,c,group.required("dimensions").toString());}}
            else if(name.equals("records")){int index=0;for(var row:item)csv.append(line(name,Integer.toString(index++),row.path("project_id").asText(),row.path("currency").isMissingNode()?null:row.path("currency").asText(),null,null,number(row,"known_amount"),null,null,row.toString()));}
            else csv.append(line("summary",name,null,null,null,null,null,null,null,item.toString()));
        }
        byte[] bytes=csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);response.setContentLength(bytes.length);response.getOutputStream().write(bytes);
    }
    private static void cost(StringBuilder out,String section,String name,String project,JsonNode cost,String detail){out.append(line(section,name,project,cost.required("currency").asText(),null,null,number(cost,"known_amount"),number(cost,"total_amount"),number(cost,"unknown_count"),detail==null?cost.toString():"{\"dimensions\":"+detail+",\"cost\":"+cost.toString()+"}"));}
    private static String number(JsonNode node,String key){var value=node.path(key);if(value.isNull()||value.isMissingNode())return "";String text=value.asText();if(!text.matches("[0-9]+(?:\\.[0-9]+)?"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return text;}
    private static String line(String section,String name,String project,String currency,String numerator,String denominator,String known,String total,String unknown,String detail){
        return cell(section)+","+cell(name)+","+cell(project)+","+cell(currency)+","+numeric(numerator)+","+numeric(denominator)+","+numeric(known)+","+numeric(total)+","+numeric(unknown)+","+cell(detail)+"\r\n";
    }
    private static String numeric(String text){if(text==null||text.isEmpty())return "";if(!text.matches("[0-9]+(?:\\.[0-9]+)?"))throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return text;}
    static String cell(String value){
        String safe=value==null?"":value;int first=0;while(first<safe.length()&&(Character.isWhitespace(safe.charAt(first))||Character.isSpaceChar(safe.charAt(first))||Character.isISOControl(safe.charAt(first))||Character.getType(safe.charAt(first))==Character.FORMAT||safe.charAt(first)=='\uFEFF'))first++;
        if(first<safe.length()&&"=+-@".indexOf(safe.charAt(first))>=0)safe="'"+safe;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
    private static BusinessOperationsService.Query query(HttpServletRequest request){
        for(var entry:request.getParameterMap().entrySet())if(!Set.of("project_id","from","to","actor_id","device_category_key","device_category","product_type_key","product_type","stage_key","stage","document_type","role_version","include_records").contains(entry.getKey())||entry.getValue().length!=1)BusinessOperationsSchema.invalid();
        BusinessOperationsService.Query query;
        try{
            String include=request.getParameter("include_records");if(include!=null&&!Set.of("true","false").contains(include))BusinessOperationsSchema.invalid();
            var filters=new BusinessOperationsService.Filters(
                new BusinessOperationsService.AttributeFilter(request.getParameter("device_category_key"),request.getParameter("device_category")),
                new BusinessOperationsService.AttributeFilter(request.getParameter("product_type_key"),request.getParameter("product_type")),
                new BusinessOperationsService.AttributeFilter(request.getParameter("stage_key"),request.getParameter("stage")),
                request.getParameter("document_type"),request.getParameter("role_version"),"true".equals(include));
            query=new BusinessOperationsService.Query(request.getParameter("project_id"),Instant.parse(request.getParameter("from")),Instant.parse(request.getParameter("to")),request.getParameter("actor_id"),filters);
        }
        catch(RuntimeException invalid){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
        return query;
    }
    @ExceptionHandler(Exception.class)
    void failed(Exception failure,HttpServletResponse response)throws java.io.IOException{
        ErrorCode code=ErrorCode.INTERNAL_ERROR;int status=500;
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()){
            if(cause instanceof TenantAccessException e){code=e.code();status=SimulationRuleVersionController.errorStatus(code);break;}
            if(cause instanceof SchemaRejection e){code=e.code();status=422;break;}
            if(cause instanceof org.springframework.security.core.AuthenticationException){code=ErrorCode.AUTHENTICATION_REQUIRED;status=401;break;}
            if(cause instanceof org.springframework.security.access.AccessDeniedException){code=ErrorCode.ACCESS_DENIED;status=403;break;}
        }
        new IdentityErrorResponseWriter().write(response,status,code);
    }
}

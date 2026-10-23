package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.SchemaRejection;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.ConfigurationDiscoveryMapper;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.bind.annotation.*;

@RestController
public final class ProjectWorkspaceDirectoryController {
    private final ApplicationSessionIdentity identities;
    private final EnterpriseAccess access;
    private final ProjectService projects;
    private final ResourceReadAuthority authority;
    private final SourceGovernanceMapper sources;
    private final ConfigurationDiscoveryMapper discovery;
    private final ConfigurationDiscoveryCursor cursors;
    private final PermissionMapper permissions;
    private final ProjectWorkspaceDirectorySchema schema;
    private final PermissionSchema json;
    private final JdbcTemplate db;
    public ProjectWorkspaceDirectoryController(ApplicationSessionIdentity identities,EnterpriseAccess access,ProjectService projects,
            ResourceReadAuthority authority,SourceGovernanceMapper sources,ConfigurationDiscoveryMapper discovery,ConfigurationDiscoveryCursor cursors,PermissionMapper permissions,
            ProjectWorkspaceDirectorySchema schema,PermissionSchema json,@Qualifier("masterDataSource")DataSource data) {
        this.identities=identities;this.access=access;this.projects=projects;this.authority=authority;this.sources=sources;
        this.discovery=discovery;this.cursors=cursors;this.permissions=permissions;this.schema=schema;this.json=json;db=new JdbcTemplate(data);db.setQueryTimeout(5);
    }
    @GetMapping("/api/project-workspace-directory")
    public JsonNode read(HttpServletRequest request,HttpServletResponse response,Authentication authentication) throws IOException {
        String context=BusinessAuditController.headers(request,response);var query=schema.query(request.getParameterMap());
        if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        return access.execute(identities.requireCurrent(request,authentication),admission->ThrowingSupplier.of(()->{
            var values=new HashMap<String,Object>();values.put("userId",admission.subject().user().user_id());String clock=Objects.requireNonNull(discovery.authorizationClock(values));
            String binding=binding(admission,query,clock),boundary=cursors.after(query.after(),binding);
            var listing=projects.listAuthorized(admission,context,query.projects(boundary),true).required("data");var result=json.object().put("schema_version","1.0.0").put("tenant_id",admission.context().tenant_id()).put("total",listing.required("total").asText());
            result.set("current_context",json.tree(admission.context()));result.set("query",schema.filters(query));
            result.set("next_after",listing.required("next_after").isNull()?json.tree(null):json.tree(cursors.next(listing.required("next_after").asText(),binding)));var items=result.putArray("items");
            var ids=new HashSet<String>();for(var project:listing.required("projects"))ids.add(project.required("project_id").asText());
            var totals=new HashMap<String,Totals>();for(String id:ids)totals.put(id,new Totals());var common=new Totals();var seen=new HashSet<String>();
            if(!ids.isEmpty())for(var source:sources.catalogue(values)) {
                if(!source.tenantId().equals(admission.context().tenant_id())||!seen.add(source.sourceVersion()))mismatch();
                if(source.ownership().equals("SESSION")||source.ownerProjectId()!=null&&!ids.contains(source.ownerProjectId())||source.lifecycle()!=null&&!source.lifecycle().equals("ACTIVE"))continue;
                if(authority.find(admission,new PermissionProtocol.Scope(source.tenantId(),source.ownerProjectId(),"SOURCE",source.sourceId()),"source.read","SOURCE_READING").isEmpty())continue;
                var counter=source.ownerProjectId()==null?common:Objects.requireNonNull(totals.get(source.ownerProjectId()));counter.registered=counter.registered.add(BigInteger.ONE);if("APPROVED".equals(source.approvalStatus()))counter.approved=counter.approved.add(BigInteger.ONE);
            }
            var enterprise=new PermissionProtocol.Scope(admission.context().tenant_id(),null,"ENTERPRISE",admission.context().tenant_id());
            boolean directory=authority.find(admission,enterprise,"member.read","MEMBER_DIRECTORY").isPresent()||authority.find(admission,enterprise,"member.manage","MEMBER_DIRECTORY").isPresent();
            var members=new HashMap<String,Map<String,Object>>();
            if(directory&&!ids.isEmpty())for(var row:db.queryForList("SELECT m.user_id,a.display_name,a.username FROM tenant_member m LEFT JOIN platform_local_account a ON a.user_id=m.user_id WHERE m.tenant_id=?",admission.context().tenant_id()))if(members.put(row.get("user_id").toString(),row)!=null)mismatch();
            String checked=DatabaseTimestamp.format(Objects.requireNonNull(permissions.databaseTime()));
            for(var project:listing.required("projects")) {
                String id=project.required("project_id").asText(),responsible=project.required("responsible_user_id").asText();var item=json.object();item.set("project",project);
                var owner=item.putObject("responsible").put("user_id",responsible);boolean self=responsible.equals(admission.subject().user().user_id());owner.put("identity_available",self||directory);
                if(self){owner.set("display_name",json.tree(admission.subject().user().display_name()));owner.set("username",json.tree(admission.subject().user().username()));}
                else if(directory){var member=members.get(responsible);if(member==null)mismatch();owner.set("display_name",json.tree(member.get("display_name")));owner.set("username",json.tree(member.get("username")));}
                else {owner.putNull("display_name");owner.putNull("username");}
                var material=Objects.requireNonNull(totals.get(id));BigInteger registered=common.registered.add(material.registered),approved=common.approved.add(material.approved);
                item.putObject("materials").put("scope","AUTHORIZED_ENTERPRISE_AND_PROJECT").put("registered",registered.toString()).put("approved",approved.toString()).put("not_approved",registered.subtract(approved).toString()).put("checked_at",checked);items.add(item);
            }
            if(!clock.equals(discovery.authorizationClock(values)))throw new TenantAccessException(ErrorCode.CURSOR_INVALID);
            var output=schema.envelope(result);response.setHeader("X-Request-Id",output.required("request_id").asText());return output;
        }).get());
    }
    private static final class Totals {private BigInteger registered=BigInteger.ZERO;private BigInteger approved=BigInteger.ZERO;}
    private String binding(EnterpriseAccess.Admission admission,ProjectWorkspaceDirectorySchema.Query query,String clock)throws IOException{
        var value=json.object().put("kind","PROJECT_WORKSPACE").put("actor",admission.subject().user().user_id()).put("session",admission.subject().sessionId()).put("identity_mode",admission.subject().binding().view().mode()).put("context_id",admission.contextId()).put("authorization_clock",clock);
        value.set("context",json.tree(admission.context()));value.set("filters",schema.filters(query));return org.apache.commons.codec.digest.DigestUtils.sha256Hex(json.canonical(value));
    }
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,422,failure.code());}
    @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException failure,HttpServletResponse response)throws IOException{new IdentityErrorResponseWriter().write(response,RuleVersionController.errorStatus(failure.code()),failure.code());}
}

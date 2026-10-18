package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Existing approved BUDGET validation plus exact atomic account reservation. Caller owns tenant transaction. */
public final class FormalInvocationBudget {
    private final RunCreateBudget authority;private final BudgetSchema budgets;private final PermissionSchema json;private final JdbcTemplate jdbc;
    public FormalInvocationBudget(RunCreateBudget authority,BudgetSchema budgets,PermissionSchema json,DataSource data){this.authority=authority;this.budgets=budgets;this.json=json;this.jdbc=new JdbcTemplate(data);jdbc.setQueryTimeout(5);}
    public static final class Basis {
        private final String tenant,project,id,version,currency;private final BigDecimal upper;private final BigInteger tokens;
        private Basis(String tenant,String project,String id,String version,String currency,BigDecimal upper,BigInteger tokens){this.tenant=tenant;this.project=project;this.id=id;this.version=version;this.currency=currency;this.upper=upper;this.tokens=tokens;}
        public String budgetId(){return id;}public String budgetRevision(){return version;}public String currency(){return currency;}public BigDecimal upperAmount(){return upper;}public BigInteger upperTokens(){return tokens;}
    }
    /** Discover actual account IDs first, lock by ID globally, then validate exact approved revisions. */
    public List<Basis> reserve(EnterpriseAccess.Admission a,String project,List<PermissionProtocol.Basis> revisions,
            String currency,BigDecimal upper,BigInteger tokens,LocalDateTime now)throws Exception {
        if(revisions==null||revisions.size()!=2||upper==null||upper.signum()<0||tokens==null||tokens.signum()<0)fail(ErrorCode.SCHEMA_INVALID);
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())fail(ErrorCode.ACCESS_DENIED);
        String tenant=a.context().tenant_id();if(!tenant.equals(TenantScope.requireCurrent().tenantId())||!a.actorId().equals(TenantScope.requireCurrent().subjectId()))fail(ErrorCode.ACCESS_DENIED);
        Map<String,PermissionProtocol.Basis> ordered=new TreeMap<>();
        for(var revision:revisions){var ids=jdbc.query("SELECT budget_id FROM budget_revision WHERE tenant_id=? AND budget_revision=? LIMIT 2",(r,n)->r.getString(1),tenant,revision.version_id());if(ids.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);if(ordered.putIfAbsent(ids.get(0),revision)!=null)fail(ErrorCode.BUDGET_EXCEEDED);}
        List<Basis> result=new ArrayList<>();boolean enterprise=false;
        for(var entry:ordered.entrySet()) {
            var lock=jdbc.query("SELECT revision FROM budget_account WHERE tenant_id=? AND budget_id=? LIMIT 2 FOR UPDATE",(r,n)->r.getBigDecimal(1).toBigIntegerExact(),tenant,entry.getKey());if(lock.size()!=1)fail(ErrorCode.CONTENT_MISMATCH);
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",project);values.put("budgetRevision",entry.getValue().version_id());
            JsonNode row=authority.require(a,values,now,currency);
            if(!entry.getKey().equals(row.required("budget_id").textValue())||!entry.getValue().content_sha256().equals(row.required("content_sha256").textValue()))fail(ErrorCode.CONTENT_MISMATCH);
            var approval=budgets.approval(json.canonical(row.required("approval")));if(approval.project_id()==null)enterprise=true;
            if(entry.getValue().version_id().equals(revisions.get(0).version_id()) && approval.project_id()!=null)fail(ErrorCode.ACCESS_DENIED);
            BigDecimal available=BudgetSchema.money(approval.limits().amount()).subtract(BudgetSchema.money(row.required("spent").textValue())).subtract(BudgetSchema.money(row.required("reserved").textValue()));
            BigInteger availableTokens=new BigInteger(approval.limits().tokens()).subtract(new BigInteger(row.required("spent_tokens").textValue())).subtract(new BigInteger(row.required("reserved_tokens").textValue()));
            BigInteger availableCalls=new BigInteger(approval.limits().calls()).subtract(new BigInteger(row.required("spent_calls").textValue())).subtract(new BigInteger(row.required("reserved_calls").textValue()));
            if(upper.compareTo(available)>0||tokens.compareTo(availableTokens)>0||availableCalls.signum()<=0)fail(ErrorCode.BUDGET_EXCEEDED);
            int changed=jdbc.update("UPDATE budget_account SET reserved=reserved+?,reserved_tokens=reserved_tokens+?,reserved_calls=reserved_calls+1,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND budget_id=? AND revision=? AND current_budget_revision=?",upper,tokens,tenant,entry.getKey(),lock.get(0),entry.getValue().version_id());
            if(changed!=1)fail(ErrorCode.REVISION_CONFLICT);
            result.add(new Basis(tenant,approval.project_id(),entry.getKey(),entry.getValue().version_id(),currency,upper,tokens));
        }
        if(!enterprise)fail(ErrorCode.ACCESS_DENIED);
        return List.copyOf(result);
    }
    private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

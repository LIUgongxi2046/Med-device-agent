package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Source purge integration. Revocation is immediate at every read/send; physical purge also removes
 * vectors, all copied source text/citations, and durable gateway originals before confirmation. */
@Component
public final class DenseRetrievalRetention {
    private final JdbcTemplate db;private final ObjectProvider<DenseEmbeddingGateway> gateways;
    public DenseRetrievalRetention(DataSource data,ObjectProvider<DenseEmbeddingGateway> gateways){db=new JdbcTemplate(data);this.gateways=gateways;}
    void erase(String tenant,String source)throws Exception {
        var scope=TenantScope.requireCurrent();if(!scope.tenantId().equals(tenant))deny();
        var operations=db.query("""
            SELECT o.operation_id,o.status,(o.updated_at>UTC_TIMESTAMP(6)-INTERVAL 10 MINUTE) AS recent
            FROM dense_operation o JOIN dense_operation_source s ON s.tenant_id=o.tenant_id AND s.operation_id=o.operation_id
            WHERE s.tenant_id=? AND s.source_version=? ORDER BY o.operation_id FOR UPDATE
            """,(r,n)->new Entry(r.getString(1),r.getString(2),r.getBoolean(3)),tenant,source);
        for(var op:operations) {
            // An in-flight sender may still persist its response. Never attest absence while it can do so.
            if(List.of("DISPATCHING","UNKNOWN").contains(op.status())&&op.recent())throw new TenantAccessException(ErrorCode.ACTION_STALE);
            if(op.status().equals("WITHDRAWN"))continue;
            if(!op.status().equals("PREPARED")) {
                var gateway=gateways.getIfAvailable();if(gateway==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
                gateway.eraseOriginal(tenant,op.id());
            } else {
                // Proven never-dispatched work is refundable. UNKNOWN remains reserved, never invented as zero cost.
                var reservations=db.query("SELECT budget_id,upper_amount,upper_tokens FROM dense_budget_reservation WHERE tenant_id=? AND operation_id=? AND settled=FALSE ORDER BY budget_id FOR UPDATE",
                    (r,n)->new Reservation(r.getString(1),r.getBigDecimal(2),r.getLong(3)),tenant,op.id());
                for(var r:reservations){
                    if(db.update("UPDATE budget_account SET reserved=reserved-?,reserved_tokens=reserved_tokens-?,reserved_calls=reserved_calls-1,revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND budget_id=? AND reserved>=? AND reserved_tokens>=? AND reserved_calls>=1",r.amount(),r.tokens(),tenant,r.id(),r.amount(),r.tokens())!=1)deny();
                    db.update("UPDATE dense_budget_reservation SET revision=revision+1,updated_at=UTC_TIMESTAMP(6),settled=TRUE,actual_amount=0,actual_tokens=0 WHERE tenant_id=? AND operation_id=? AND budget_id=?",tenant,op.id(),r.id());
                }
            }
            db.update("UPDATE dense_operation SET status='WITHDRAWN',revision=revision+1,request=JSON_OBJECT(),exact_input=JSON_OBJECT(),result=NULL,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=?",tenant,op.id());
        }
        db.update("DELETE FROM dense_index_vector WHERE tenant_id=? AND source_version=?",tenant,source);
        db.update("UPDATE index_manifest SET status='WITHDRAWN' WHERE tenant_id=? AND source_version=? AND mode IN ('DENSE','HYBRID')",tenant,source);
        long remaining=db.queryForObject("SELECT COUNT(*) FROM dense_index_vector WHERE tenant_id=? AND source_version=?",Long.class,tenant,source);
        if(remaining!=0)deny();
        long copied=db.queryForObject("SELECT COUNT(*) FROM dense_operation o JOIN dense_operation_source s ON s.tenant_id=o.tenant_id AND s.operation_id=o.operation_id WHERE s.tenant_id=? AND s.source_version=? AND (o.status<>'WITHDRAWN' OR JSON_LENGTH(o.exact_input)<>0 OR o.result IS NOT NULL)",Long.class,tenant,source);
        if(copied!=0)deny();
    }
    private static void deny(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private record Entry(String id,String status,boolean recent){}
    private record Reservation(String id,java.math.BigDecimal amount,long tokens){}
}

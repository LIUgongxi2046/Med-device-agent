package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import static org.mockito.Mockito.*;

/** Real 097 ledger and registration against an explicit JDBC row double.
 * Current sealed production/098/content capabilities are named authority doubles, not live grants. */
class TaskNodeInputFixture {
    final PermissionSchema json=new PermissionSchema();
    final TaskNodeInputSchema schema=new TaskNodeInputSchema();
    final JdbcTemplate db=mock(JdbcTemplate.class);
    final EnterpriseAccess.Admission admission=mock(EnterpriseAccess.Admission.class);
    final TaskNodeInputAuthority.Prepared proof=mock(TaskNodeInputAuthority.Prepared.class);
    final TaskNodeProductionBasis bootstrap=mock(TaskRunBootstrapAuthority.Verified.class);
    final TaskNodeProducerAuthority.Verified producer=mock(TaskNodeProducerAuthority.Verified.class);
    final TaskRunReplayContentAuthority.Verified content=mock(TaskRunReplayContentAuthority.Verified.class);
    final TaskNodeInputLedger ledger=new TaskNodeInputLedger(db,schema,json);
    final TaskNodeInputRegistration registrations=new TaskNodeInputRegistration(db,ledger,schema,json,new DependencyManifestSchema());
    final Map<String,List<Map<String,Object>>> tables=new HashMap<>();
    final List<String> executedSql=new ArrayList<>();
    final String tenant=id(1),actor=id(2),project=id(3),run=id(4),task=id(5),step=id(6);
    final String key="task-input-original-key-0001";
    LocalDateTime now=LocalDateTime.of(2026,10,20,1,0);
    TaskNodeInputProtocol.Input input;
    TenantScope scope;
    TaskNodeInputFixture()throws Exception {
        when(admission.context()).thenReturn(new com.ruoyi.medical.identity.BusinessSessionProtocol.Context(tenant,"1","1","1"));
        when(admission.actorId()).thenReturn(actor);when(admission.controlWorker()).thenReturn(true);
        when(admission.controlProof()).thenReturn(mock(FormalRunControlAccess.Proof.class));
        when(bootstrap.admission()).thenReturn(admission);when(bootstrap.tenantId()).thenReturn(tenant);when(bootstrap.actorId()).thenReturn(actor);
        when(bootstrap.projectId()).thenReturn(project);when(bootstrap.runId()).thenReturn(run);when(bootstrap.taskId()).thenReturn(task);
        when(bootstrap.classification()).thenReturn("RESTRICTED");when(bootstrap.authorizationsSha256()).thenReturn(hash('a'));
        when(bootstrap.command()).thenReturn(json.object().put("explicit_test_authority_boundary","original-command"));
        when(producer.ruleId()).thenReturn(id(40));when(producer.ruleSha256()).thenReturn(hash('b'));when(producer.decisionId()).thenReturn(id(41));when(producer.decisionSha256()).thenReturn(hash('c'));
        when(producer.authorizations()).thenReturn(json.object().put("explicit_test_authority_boundary","current-approved-producer"));
        input=new TaskNodeInputProtocol.Input("1.0.0",tenant,project,task,run,step,id(7),hash('a'),"N02",id(8),"question-1","Fixed confirmed question",id(9),id(10),hash('b'),id(11),hash('c'),hash('d'),new TaskNodeInputProtocol.ReplayArtifact(id(12),hash('d'),"replay-version"),hash('e'),basis(13),basis(14),List.of(new TaskNodeInputProtocol.SourceSelection(id(20),id(21),id(22),id(23),id(24),id(25),"TASK_EXECUTION")),List.of());
        when(proof.input()).thenAnswer(i->input);when(proof.bootstrap()).thenReturn(bootstrap);when(proof.producer()).thenReturn(producer);when(proof.content()).thenReturn(content);
        when(content.roots()).thenReturn(List.of(id(20),id(21),id(22)));
        when(content.members()).thenReturn(List.of(new ArtifactRegistryProtocol.MemberRow(id(20),id(25)),new ArtifactRegistryProtocol.MemberRow(id(21),id(25)),new ArtifactRegistryProtocol.MemberRow(id(22),id(25))));
        when(db.query(eq("SELECT UTC_TIMESTAMP(6)"),any(RowMapper.class))).thenAnswer(i->List.of(now));
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(i->select(i.getArgument(0),arguments(i.getArguments())));
        when(db.update(anyString(),any(Object[].class))).thenAnswer(i->update(i.getArgument(0),arguments(i.getArguments())));
    }
    static String id(int n){return String.format("00000000-0000-4000-8000-%012d",n);}
    static String hash(char c){return String.valueOf(c).repeat(64);}
    static PermissionProtocol.Basis basis(int n){return new PermissionProtocol.Basis(id(n),hash('a'));}
    <T>T tx(CheckedSupplier<T> work)throws Exception {
        scope=TenantScope.open(new TenantScope.Identity(tenant,actor,BigInteger.ONE,BigInteger.ONE,BigInteger.ONE));
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.initSynchronization();
        scope.verifyTransaction(new TenantWatermarks(BigInteger.ONE,BigInteger.ONE));boolean committed=false;
        try{var result=work.get();committed=true;return result;}
        finally{
            for(var synchronization:TransactionSynchronizationManager.getSynchronizations()){
                if(committed)synchronization.afterCommit();synchronization.afterCompletion(committed?TransactionSynchronization.STATUS_COMMITTED:TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            scope.finishTransaction();scope.close();scope=null;TransactionSynchronizationManager.clear();
        }
    }
    interface CheckedSupplier<T>{T get()throws Exception;}
    List<Map<String,Object>> rows(String table){return tables.computeIfAbsent(table,x->new ArrayList<>());}
    Map<String,Object> only(String table){if(rows(table).size()!=1)throw new AssertionError(table+" rows="+rows(table).size());return rows(table).get(0);}
    TaskNodeInputLedger.Attempt prepare()throws Exception{return tx(()->ledger.prepare(proof,key));}
    TaskNodeInputLedger.Attempt recover()throws Exception{return tx(()->ledger.recover(proof,key));}
    private List<Map<String, Object>> select(String sql, Object[] args) {
        executedSql.add(sql);
        if (sql.startsWith("SELECT user_id FROM tenant_member")) return List.of(Map.of("user_id", actor));
        var name = Pattern.compile("FROM ([a-z_]+)").matcher(sql);
        if (!name.find()) throw new AssertionError(sql);
        String table = name.group(1);
        List<Map<String, Object>> result = new ArrayList<>();
        for (var row : rows(table)) {
            var condition = Pattern.compile("([a-z_]+)=\\?").matcher(sql);
            int index = 0;
            boolean match = true;
            while (condition.find()) {
                Object expected = args[index++], actual = row.get(condition.group(1));
                if (!Objects.equals(expected, actual)) { match = false; break; }
            }
            if (match) result.add(new LinkedHashMap<>(row));
        }
        if (sql.contains("ORDER BY upstream_version_id")) result.sort(Comparator.comparing(row -> (String) row.get("upstream_version_id")));
        return result;
    }

    private int update(String sql, Object[] args) {
        executedSql.add(sql);
        if (sql.startsWith("INSERT INTO ")) {
            var match = Pattern.compile("INSERT INTO ([a-z_]+)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)").matcher(sql);
            if (!match.find()) throw new AssertionError(sql);
            String[] columns = match.group(2).split(","), values = match.group(3).split(",");
            if (columns.length != values.length) throw new AssertionError(sql);
            var row = new LinkedHashMap<String, Object>();
            int index = 0;
            for (int i = 0; i < columns.length; i++) {
                String value = values[i].trim();
                Object parsed = value.equals("?") ? args[index++] : value.equals("NULL") ? null
                        : value.startsWith("'") ? value.substring(1, value.length() - 1) : Integer.parseInt(value);
                row.put(columns[i].trim(), parsed);
            }
            if (index != args.length) throw new AssertionError("Bind count: " + sql);
            if (match.group(1).equals("operation_record")) row.put("error_code", null);
            if (match.group(1).equals("audit_event")) row.put("reason_code", null);
            if (match.group(1).equals("lineage_edge")) row.put("decision_ref", null);
            rows(match.group(1)).add(row);
            return 1;
        }
        if (sql.startsWith("UPDATE operation_record SET revision=2")) {
            var row = only("operation_record");
            if (!Integer.valueOf(1).equals(row.get("revision"))) return 0;
            row.put("revision", 2); row.put("updated_at", args[0]); return 1;
        }
        if (sql.startsWith("UPDATE operation_record SET state='COMPLETED'")) {
            var row = only("operation_record");
            if (!Integer.valueOf(2).equals(row.get("revision"))) return 0;
            row.put("state", "COMPLETED"); row.put("http_status", 201); row.put("response_ref", args[0]);
            row.put("revision", 3); row.put("updated_at", args[1]); return 1;
        }
        throw new AssertionError(sql);
    }
    private static Object[] arguments(Object[] all) {
        var result = Arrays.copyOfRange(all, 1, all.length);
        return result.length == 1 && result[0] instanceof Object[] array ? array : result;
    }
}

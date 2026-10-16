package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.SourceGovernanceMapper;
import com.ruoyi.medical.persistence.mapper.SourceIndexMapper;
import com.ruoyi.medical.persistence.mapper.SourceProcessingMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

final class SourceIndexAccess {
    private final JdbcTemplate jdbc;
    private final SourceProcessingAccess processing;
    private final SourceProcessingPolicy parserPolicy;
    private final SourceIndexPolicy policy;
    private final SourceIndexMapper mapper;
    private final SourceProcessingMapper parses;
    private final SourceGovernanceMapper governance;
    private final SourceIndexManifest manifests;

    SourceIndexAccess(DataSource data,SourceProcessingAccess processing,SourceProcessingPolicy parserPolicy,SourceIndexPolicy policy,
            SourceIndexMapper mapper,SourceProcessingMapper parses,SourceGovernanceMapper governance) throws Exception {
        jdbc=new JdbcTemplate(data); this.processing=processing; this.parserPolicy=parserPolicy; this.policy=policy;
        this.mapper=mapper; this.parses=parses; this.governance=governance; manifests=new SourceIndexManifest();
        if(!parserPolicy.environment().equals(policy.environment()) || !parserPolicy.region().equals(policy.region()))
            throw new IllegalArgumentException("全文索引与来源处理的环境范围不一致");
        requireEngine();
    }

    List<SourceIndexProtocol.Job> pending() {
        var jobs=new java.util.ArrayList<SourceIndexProtocol.Job>();
        for(String tenant:parserPolicy.tenantIds()) {
            jobs.addAll(jdbc.query("""
                SELECT e.event_id,a.attempt_id FROM outbox_event e
                JOIN processing_attempt a ON a.tenant_id=e.tenant_id AND a.parse_version=e.aggregate_id AND a.job_type='PARSE'
                JOIN source_version v ON v.tenant_id=a.tenant_id AND v.source_version=a.source_version
                JOIN rights_revision r ON r.tenant_id=v.tenant_id AND r.source_version=v.source_version AND r.rights_revision=v.rights_revision
                LEFT JOIN outbox_delivery d ON d.tenant_id=e.tenant_id AND d.event_id=e.event_id AND d.consumer_id='SOURCE_INDEX_FULLTEXT'
                WHERE e.tenant_id=? AND e.event_type='source.index.requested' AND a.status='SUCCEEDED'
                  AND r.status='APPROVED' AND (r.valid_from IS NULL OR r.valid_from<=UTC_TIMESTAMP(6))
                  AND (r.valid_to IS NULL OR r.valid_to>UTC_TIMESTAMP(6))
                  AND (d.event_id IS NULL OR d.next_attempt_at<=UTC_TIMESTAMP(6)
                    AND (d.status='PENDING' OR d.status='DELIVERING' AND d.lease_until<=UTC_TIMESTAMP(6)))
                ORDER BY e.occurred_at,e.event_id LIMIT 10
                """,(row,n)->new SourceIndexProtocol.Job(tenant,row.getString(1),row.getString(2)),tenant));
            if(jobs.size()>=100) return List.copyOf(jobs.subList(0,100));
        }
        return List.copyOf(jobs);
    }

    <T> T execute(SourceIndexProtocol.Job job,Operation<T> action) {
        WireValues.uuid(job.tenant()); WireValues.uuid(job.event()); WireValues.uuid(job.attempt());
        // 原解析作业提供真实人员、当前许可及固定发布配置，索引动作继续消费同一来源。
        return processing.execute(job.tenant(),job.attempt(),admission -> {
            var row=admission.row(); var values=admission.values(); values.put("eventId",job.event());
            var command=manifests.read(one(mapper.command(values))); manifests.require("Command",command);
            if(!"SUCCEEDED".equals(row.status()) || !"COMPLETE".equals(row.parseStatus()) || row.commandRef()==null || row.resultRef()==null
                    || !row.sourceVersion().equals(command.required("source_version").textValue())
                    || !row.parseVersion().equals(command.required("parse_version").textValue())
                    || !row.configRevision().equals(command.required("parse_config_revision").textValue())
                    || !row.projectId().equals(command.required("project_id").textValue())
                    || !row.createdBy().equals(command.required("requested_by").textValue())
                    || !"PARSE_AND_INDEX".equals(manifests.read(row.requestManifest()).required("stage").textValue())
                    || !Objects.equals(admission.binding().indexConfigRevision(),command.required("config_revision").textValue())) mismatch();
            var parseCommand=manifests.read(row.commandRef());
            if(!admission.binding().configSha256().equals(parseCommand.required("config_sha256").textValue())
                    || !row.configRevision().equals(parseCommand.required("config_revision").textValue())
                    || !row.originalSha256().equals(parseCommand.required("original_sha256").textValue())) mismatch();
            var materialized=one(parses.materialized(values));
            String content=command.required("content_sha256").textValue();
            if(!content.equals(materialized.get("contentSha256")) || !content.equals(materialized.get("registeredSha256"))
                    || !"COMPLETE".equals(materialized.get("parseStatus"))) mismatch();
            values.put("configRevision",command.required("config_revision").textValue());
            var config=one(governance.processingConfig(values)); manifests.requireCapability(config,policy);
            if(!job.tenant().equals(config.tenantId()) || !command.required("config_revision").textValue().equals(config.versionId())
                    || config.projectId()!=null && !row.projectId().equals(config.projectId())) mismatch();
            requireEngine(); values.put("inputSha256",manifests.hash(command)); values.put("schemaManifest",manifests.sha256());
            values.put("indexConfigRevision",command.required("config_revision").textValue());
            values.put("configSha256",config.contentSha256()); values.put("rightsRevision",row.rightsRevision());
            values.put("fetchLimit",policy.maximumChunks()+1);
            return action.apply(new Admission(row,command,materialized,admission.now(),values));
        });
    }

    private void requireEngine() {
        var tokenSize=jdbc.queryForObject("SELECT @@ngram_token_size",Integer.class);
        var indexes=jdbc.query("""
            SELECT INDEX_TYPE,COLUMN_NAME FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='source_chunk' AND INDEX_NAME='ft_chunk'
            """,(row,n)->List.of(row.getString(1),row.getString(2)));
        if(!Integer.valueOf(policy.ngramTokenSize()).equals(tokenSize)
                || !indexes.equals(List.of(List.of("FULLTEXT","search_text"))))
            throw new TenantAccessException(ErrorCode.OBJECT_UNAVAILABLE);
    }

    record Admission(SourceProcessingRow row,JsonNode command,Map<String,Object> materialized,LocalDateTime now,Map<String,Object> values) { }
    @FunctionalInterface interface Operation<T> { T apply(Admission admission) throws Exception; }
    private static <T> T one(List<T> rows) { if(rows.size()!=1) mismatch(); return rows.get(0); }
    private static void mismatch() { throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH); }
}

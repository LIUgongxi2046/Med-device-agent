package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.math.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Read-only business cohorts. Never uses operation-record counts as business outcomes. */
@Service
public final class BusinessOperationsService {
    static final int MAX_ROWS = 2000;
    private final EnterpriseAccess access;
    private final ResourceReadAuthority authority;
    private final DocumentReadService documents;
    private final PermissionSchema json;
    private final ProjectSchema projects;
    private final String categoryKey,productKey,stageKey;
    private final JdbcTemplate db;
    private final BusinessOperationsSchema codec=new BusinessOperationsSchema();
    private final FormalInvocationSchema accountingSchema;
    private final com.networknt.schema.JsonSchema responseSchema;
    public BusinessOperationsService(EnterpriseAccess access, ResourceReadAuthority authority,
            DocumentReadService documents, PermissionSchema json, ProjectSchema projects,DataSource data,
            @org.springframework.beans.factory.annotation.Value("${medical.business-operations.device-category-key:}") String categoryKey,
            @org.springframework.beans.factory.annotation.Value("${medical.business-operations.product-type-key:}") String productKey,
            @org.springframework.beans.factory.annotation.Value("${medical.business-operations.stage-key:}") String stageKey)throws Exception {
        this.access=access; this.authority=authority; this.documents=documents; this.json=json;this.projects=projects;this.categoryKey=configured(categoryKey);this.productKey=configured(productKey);this.stageKey=configured(stageKey);
        db=new JdbcTemplate(data); db.setQueryTimeout(10);accountingSchema=new FormalInvocationSchema();
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/business-operations-metrics.schema.json"))){
            responseSchema=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(new com.fasterxml.jackson.databind.ObjectMapper().readTree(input),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        }
    }
    record AttributeFilter(String key,String value){
        AttributeFilter{if(key!=null&&(key.isBlank()||key.length()>128)||value!=null&&(value.isBlank()||value.length()>256))BusinessOperationsSchema.invalid();}
    }
    record Filters(AttributeFilter deviceCategory,AttributeFilter productType,AttributeFilter stage,String documentType,String roleVersion,boolean includeRecords){
        Filters{if(documentType!=null&&(documentType.isBlank()||documentType.length()>64))BusinessOperationsSchema.invalid();if(roleVersion!=null)WireValues.uuid(roleVersion);}
        static Filters all(){return new Filters(new AttributeFilter(null,null),new AttributeFilter(null,null),new AttributeFilter(null,null),null,null,false);}
    }
    record Query(String project, Instant from, Instant to, String actor,Filters filters) {
        Query(String project,Instant from,Instant to,String actor){this(project,from,to,actor,Filters.all());}
        Query {
            if(project!=null)WireValues.uuid(project); if(actor!=null) WireValues.uuid(actor);Objects.requireNonNull(filters);
            if(from==null || to==null || !from.isBefore(to) || Duration.between(from,to).compareTo(Duration.ofDays(93))>0)
                throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        }
    }
    JsonNode read(ApplicationSessionIdentity.Verified subject,String context,Query query){return read(subject,context,query,false);}
    JsonNode read(ApplicationSessionIdentity.Verified subject,String context,Query query,boolean exporting){
        var f=query.filters();var resolved=new Filters(resolve(f.deviceCategory(),categoryKey),resolve(f.productType(),productKey),resolve(f.stage(),stageKey),f.documentType(),f.roleVersion(),f.includeRecords());
        return readResolved(subject,context,new Query(query.project(),query.from(),query.to(),query.actor(),resolved),exporting);
    }
    private static String configured(String key){if(key==null||key.isBlank())return null;if(key.length()>128)throw new IllegalArgumentException("REPORTING_DIMENSION_KEY_TOO_LONG");return key;}
    private static AttributeFilter resolve(AttributeFilter filter,String configured){
        if(filter.key()!=null&&!filter.key().equals(configured))throw new TenantAccessException(ErrorCode.ACCESS_DENIED);
        if(filter.value()!=null&&configured==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        return new AttributeFilter(configured,filter.value());
    }
    private JsonNode readResolved(ApplicationSessionIdentity.Verified subject,String context,Query query,boolean exporting) {
        return access.execute(subject,a->ThrowingSupplier.of(()->{
            if(!a.context().context_revision().equals(context)) throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
            var scope=new PermissionProtocol.Scope(a.context().tenant_id(),query.project(),query.project()==null?"ENTERPRISE":"PROJECT",query.project()==null?a.context().tenant_id():query.project());
            authority.require(a,scope,"report.read","BUSINESS_OPERATIONS");
            if(exporting)authority.require(a,scope,"report.export","BUSINESS_OPERATIONS");
            var observed=Objects.requireNonNull(db.queryForObject("SELECT UTC_TIMESTAMP(6)",LocalDateTime.class));
            if(query.to().isAfter(observed.toInstant(ZoneOffset.UTC))) throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            Object[] args={a.context().tenant_id(),query.project(),query.project(),LocalDateTime.ofInstant(query.from(),ZoneOffset.UTC),LocalDateTime.ofInstant(query.to(),ZoneOffset.UTC),query.actor(),query.actor()};
            var invocations=rows("""
                SELECT i.project_id,i.invocation_id,i.run_id,t.task_id,t.task_revision,i.currency,i.status,s.role_version,
                  CASE WHEN OCTET_LENGTH(f.receipt_json)<=65536 THEN f.receipt_json ELSE NULL END AS receipt_json,
                  f.receipt_sha256,f.receipt_id,f.created_at AS accounted_at,f.schema_version AS accounting_version,f.schema_manifest_sha256 AS accounting_manifest,
                  i.settlement_status,i.settled_amount,i.total_tokens,i.usage_receipt_id,
                  r.kind AS capture_kind,r.receipt_sha256 AS capture_sha256
                FROM invocation i JOIN task_run t ON t.tenant_id=i.tenant_id AND t.project_id=i.project_id AND t.run_id=i.run_id
                JOIN run_step s ON s.tenant_id=i.tenant_id AND s.project_id=i.project_id AND s.run_id=i.run_id AND s.step_id=i.step_id
                LEFT JOIN formal_invocation_accounting f ON f.tenant_id=i.tenant_id AND f.project_id=i.project_id AND f.invocation_id=i.invocation_id
                LEFT JOIN invocation_receipt r ON r.tenant_id=f.tenant_id AND r.invocation_id=f.invocation_id AND r.receipt_id=f.receipt_id
                WHERE i.tenant_id=? AND (? IS NULL OR i.project_id=?) AND i.created_at>=? AND i.created_at<? AND (? IS NULL OR i.created_by=?)
                ORDER BY i.created_at,i.invocation_id LIMIT 2001
                """,args);
            var candidates=rows("""
                SELECT c.project_id,c.candidate_id,c.task_id,c.task_revision,c.candidate_kind,s.role_version,
                  (SELECT MIN(d.adoption_id) FROM candidate_adoption d JOIN operation_record o ON o.tenant_id=d.tenant_id AND o.operation_id=d.operation_id
                    WHERE d.tenant_id=c.tenant_id AND d.project_id=c.project_id AND d.candidate_id=c.candidate_id AND o.state='COMPLETED') AS adoption_id
                FROM candidate c JOIN run_step s ON s.tenant_id=c.tenant_id AND s.project_id=c.project_id AND s.run_id=c.run_id AND s.step_id=c.step_id WHERE c.tenant_id=? AND (? IS NULL OR c.project_id=?) AND c.created_at>=? AND c.created_at<? AND (? IS NULL OR c.created_by=?)
                ORDER BY c.created_at,c.candidate_id LIMIT 2001
                """,args);
            var reviews=rows("""
                SELECT r.project_id,r.review_id,r.document_revision,m.document_id,m.task_id,m.task_revision,r.decision,
                  (SELECT s.role_version FROM candidate_adoption d JOIN candidate c ON c.tenant_id=d.tenant_id AND c.project_id=d.project_id AND c.candidate_id=d.candidate_id
                   JOIN run_step s ON s.tenant_id=c.tenant_id AND s.project_id=c.project_id AND s.run_id=c.run_id AND s.step_id=c.step_id
                   WHERE d.tenant_id=r.tenant_id AND d.project_id=r.project_id AND d.result_document_revision=r.document_revision LIMIT 1) AS role_version
                FROM review_decision r JOIN document_revision_meta m ON m.tenant_id=r.tenant_id AND m.project_id=r.project_id AND m.document_revision=r.document_revision
                WHERE r.tenant_id=? AND (? IS NULL OR r.project_id=?) AND r.decided_at>=? AND r.decided_at<? AND (? IS NULL OR r.actor_id=?)
                ORDER BY r.decided_at,r.review_id LIMIT 2001
                """,args);
            var standaloneReturns=rows("""
                SELECT r.project_id,r.return_id,r.document_id,r.document_revision,r.task_id,r.task_revision,
                  (SELECT s.role_version FROM candidate_adoption d JOIN candidate c ON c.tenant_id=d.tenant_id AND c.project_id=d.project_id AND c.candidate_id=d.candidate_id
                   JOIN run_step s ON s.tenant_id=c.tenant_id AND s.project_id=c.project_id AND s.run_id=c.run_id AND s.step_id=c.step_id
                   WHERE d.tenant_id=r.tenant_id AND d.project_id=r.project_id AND d.result_document_revision=r.document_revision LIMIT 1) AS role_version
                FROM document_revision_return r WHERE r.tenant_id=? AND (? IS NULL OR r.project_id=?) AND r.created_at>=? AND r.created_at<? AND (? IS NULL OR r.actor_id=?)
                ORDER BY r.created_at,r.return_id LIMIT 2001
                """,args);
            var dimensions=new HashMap<String,Dimension>();var excluded=new TreeMap<String,Long>();
            invocations=filter(a,query,invocations,dimensions,excluded);candidates=filter(a,query,candidates,dimensions,excluded);
            reviews=filter(a,query,reviews,dimensions,excluded);standaloneReturns=filter(a,query,standaloneReturns,dimensions,excluded);
            var raw=json.object().arrayNode();var costs=new TreeMap<String,Cost>();var tasks=new HashSet<String>();
            for(var row:invocations){
                if(query.filters().includeRecords())task(a,str(row,"project_id"),str(row,"task_id"),tasks);
                var cost=costs.computeIfAbsent(str(row,"currency"),ignored->new Cost());cost.calls++;
                var item=json.object().put("kind","TASK_INVOCATION").put("project_id",str(row,"project_id")).put("role_version",str(row,"role_version")).put("invocation_id",str(row,"invocation_id"))
                    .put("run_id",str(row,"run_id")).put("task_id",str(row,"task_id")).put("task_revision",str(row,"task_revision"))
                    .put("currency",str(row,"currency")).put("status",str(row,"status"));
                if(row.get("receipt_json")==null){if(row.get("receipt_id")!=null)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);cost.unknown++;item.putNull("known_amount");item.putNull("known_tokens");item.putNull("known_calls");item.putNull("accounting_receipt_id");item.put("accounting_state","MISSING");}
                else {
                    var receipt=codec.read(str(row,"receipt_json"));
                    if(!codec.hash(receipt).equals(str(row,"receipt_sha256")) || !a.context().tenant_id().equals(receipt.path("tenant_id").asText())
                      || !str(row,"project_id").equals(receipt.path("project_id").asText()) || !str(row,"invocation_id").equals(receipt.path("invocation_id").asText())
                      || !str(row,"currency").equals(receipt.path("currency").asText()) || !str(row,"receipt_id").equals(receipt.path("usage_receipt_id").asText())
                      || !"TASK".equals(receipt.path("execution_kind").asText())
                      || !"1.0.0".equals(str(row,"accounting_version")) || !"1.0.0".equals(receipt.path("schema_version").asText())
                      || !accountingSchema.manifestSha256().equals(str(row,"accounting_manifest"))
                      || !"PROVIDER_CAPTURE".equals(str(row,"capture_kind"))
                      || !Objects.equals(str(row,"capture_sha256"),receipt.at("/capture_artifact/sha256").asText())
                      || !Objects.equals(str(row,"receipt_id"),str(row,"usage_receipt_id"))
                      || !Objects.equals(str(row,"settlement_status"),receipt.path("settlement_status").asText())) mismatch();
                    var at=time(row.get("accounted_at"));if(at.isAfter(observed)||!at.toInstant(ZoneOffset.UTC).equals(Instant.parse(receipt.required("recorded_at").asText())))mismatch();
                    item.put("accounting_receipt_id",str(row,"receipt_id"));item.put("accounting_state",receipt.path("settlement_status").asText());
                    if("SETTLED".equals(receipt.path("settlement_status").asText()) && receipt.path("known_amount").isTextual() && receipt.path("known_tokens").isTextual() && receipt.path("known_calls").isTextual()) {
                        var amount=new BigDecimal(receipt.required("known_amount").asText());var tokens=new BigInteger(receipt.required("known_tokens").asText());var calls=new BigInteger(receipt.required("known_calls").asText());
                        if(amount.signum()<0 || tokens.signum()<0 || calls.signum()<0 || row.get("settled_amount")==null || row.get("total_tokens")==null || amount.compareTo(new BigDecimal(str(row,"settled_amount")))!=0 || !tokens.equals(new BigInteger(str(row,"total_tokens")))) mismatch();cost.amount=cost.amount.add(amount);cost.tokens=cost.tokens.add(tokens);cost.providerCalls=cost.providerCalls.add(calls);cost.settled++;item.put("known_amount",amount.toPlainString()).put("known_tokens",tokens.toString()).put("known_calls",calls.toString());
                    } else {cost.unknown++;item.putNull("known_amount");item.putNull("known_tokens");item.putNull("known_calls");}
                }
                raw.add(item);
            }
            var byKind=new TreeMap<String,long[]>();int adopted=0;for(var row:candidates){if(query.filters().includeRecords())task(a,str(row,"project_id"),str(row,"task_id"),tasks);
                boolean yes=row.get("adoption_id")!=null;if(yes)adopted++;var kind=byKind.computeIfAbsent(str(row,"candidate_kind"),ignored->new long[2]);kind[1]++;if(yes)kind[0]++;
                raw.add(json.object().put("kind","CANDIDATE").put("project_id",str(row,"project_id")).put("role_version",str(row,"role_version")).put("candidate_id",str(row,"candidate_id")).put("candidate_kind",str(row,"candidate_kind"))
                    .put("task_id",str(row,"task_id")).put("task_revision",str(row,"task_revision")).put("adoption_id",str(row,"adoption_id")).put("adopted",yes));}
            int returned=0;for(var row:reviews){if(query.filters().includeRecords()){task(a,str(row,"project_id"),str(row,"task_id"),tasks);documents.authorizeMetadata(a,context,str(row,"document_id"),str(row,"document_revision"));}
                if("RETURN".equals(str(row,"decision")))returned++;
                raw.add(json.object().put("kind","REVIEW_DECISION").put("project_id",str(row,"project_id")).put("role_version",str(row,"role_version")).put("review_id",str(row,"review_id")).put("document_id",str(row,"document_id"))
                    .put("document_revision",str(row,"document_revision")).put("task_id",str(row,"task_id")).put("task_revision",str(row,"task_revision")).put("decision",str(row,"decision")));}
            for(var row:standaloneReturns){if(query.filters().includeRecords())task(a,str(row,"project_id"),str(row,"task_id"),tasks);if(query.filters().includeRecords())documents.authorizeMetadata(a,context,str(row,"document_id"),str(row,"document_revision"));
                raw.add(json.object().put("kind","INDEPENDENT_REVISION_RETURN").put("project_id",str(row,"project_id")).put("role_version",str(row,"role_version")).put("return_id",str(row,"return_id")).put("document_id",str(row,"document_id"))
                    .put("document_revision",str(row,"document_revision")).put("task_id",str(row,"task_id")).put("task_revision",str(row,"task_revision")));}
            var result=json.object().put("schema_version","1.0.0").put("metric_revision","BUSINESS-OPERATIONS-1")
                .put("tenant_id",a.context().tenant_id()).put("project_id",query.project()).put("from",query.from().toString()).put("to",query.to().toString())
                .put("observed_at",observed.toInstant(ZoneOffset.UTC).toString()).put("watermark_mode","LOCKED_TENANT_LIVE_READ")
                .put("auth_epoch",a.context().auth_epoch()).put("governance_epoch",a.context().governance_epoch())
                .put("complete",true).put("quality_improvement_status","INSUFFICIENT_EVIDENCE");
            if(query.actor()==null)result.putNull("actor_id");else result.put("actor_id",query.actor());
            result.set("adoption",adoption(adopted,candidates.size()));
            result.set("return",ratio(returned,reviews.size(),"FORMAL_REVIEW_DECISIONS_IN_INTERVAL_INCLUDING_HISTORIC_INVALIDATED"));
            var amounts=json.object().arrayNode();costs.forEach((currency,c)->{var amount=json.object().put("currency",currency).put("invocations",c.calls)
                .put("settled_count",c.settled).put("unknown_count",c.unknown).put("known_amount",c.amount.toPlainString()).put("known_tokens",c.tokens.toString()).put("known_provider_calls",c.providerCalls.toString());
                if(c.unknown==0)amount.put("total_amount",c.amount.toPlainString());else amount.putNull("total_amount");amounts.add(amount);});
            result.put("registered_task_invocations",invocations.size()).put("independent_revision_return_count",standaloneReturns.size());
            var grouped=json.object();byKind.forEach((kind,count)->grouped.set(kind,adoption(count[0],count[1])));result.set("adoption_by_candidate_kind",grouped);
            result.set("cost_by_currency",amounts);
            var groupedProjects=new TreeMap<String,long[]>();for(var item:raw){var group=groupedProjects.computeIfAbsent(item.path("project_id").asText(),ignored->new long[4]);switch(item.path("kind").asText()){case "TASK_INVOCATION"->group[0]++;case "CANDIDATE"->group[1]++;case "REVIEW_DECISION"->group[2]++;case "INDEPENDENT_REVISION_RETURN"->group[3]++;}}
            var groups=json.object().arrayNode();groupedProjects.forEach((project,count)->groups.add(json.object().put("project_id",project).put("invocations",count[0]).put("candidates",count[1]).put("formal_review_decisions",count[2]).put("independent_revision_returns",count[3])));result.set("project_groups",groups);
            for(var item:raw){var dimension=dimensions.get(item.path("task_revision").asText());((ObjectNode)item).put("profile_revision",dimension.profile()).put("document_type",dimension.documentType());}
            result.put("records_included",query.filters().includeRecords());result.set("records",query.filters().includeRecords()?raw:json.object().arrayNode());
            result.set("applied_filters",json.tree(query.filters()));result.set("filter_exclusion_counts",json.tree(excluded));
            result.put("role_filter_semantics","PRODUCING_EXECUTION_ROLE_VERSION_NOT_CURRENT_HUMAN_ROLE");
            result.set("dimension_groups",groups(raw,dimensions,query.filters()));
            result.set("exclusions",json.tree(List.of("SYSTEM_JOBS_AND_SIMULATIONS","TASKS_WITHOUT_MODEL_INVOCATIONS_FROM_USAGE",
                "UNSETTLED_AND_DISPUTED_COST_FROM_KNOWN_SUM","CANDIDATES_OUTSIDE_CREATION_COHORT","INDEPENDENT_DOCUMENT_REVISION_RETURNS_FROM_FORMAL_REVIEW_RATE",
                "QUALITY_AND_TIME_SAVING_WITHOUT_EXPERT_BASELINE")));
            authority.require(a,scope,"report.read","BUSINESS_OPERATIONS");
            if(exporting)authority.require(a,scope,"report.export","BUSINESS_OPERATIONS");
            if(ContentReadAuthorization.canonicalBytes(result).length>8_000_000)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
            if(!responseSchema.validate(result).isEmpty())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
            return result;
        }).get());
    }
    private com.fasterxml.jackson.databind.node.ArrayNode groups(com.fasterxml.jackson.databind.node.ArrayNode records,Map<String,Dimension> dimensions,Filters filters)throws Exception{
        var groups=new TreeMap<String,Group>();for(var item:records){var source=dimensions.get(item.path("task_revision").asText());
            var d=json.object().put("device_category",attribute(source,filters.deviceCategory().key())).put("product_type",attribute(source,filters.productType().key()))
                .put("stage",attribute(source,filters.stage().key())).put("document_type",source.documentType()).put("role_version",item.path("role_version").isNull()?null:item.path("role_version").asText());
            String key=codec.canonical(d);var group=groups.computeIfAbsent(key,ignored->new Group(d));
            switch(item.path("kind").asText()){
                case "CANDIDATE"->{group.candidates++;if(item.path("adopted").asBoolean())group.adopted++;}
                case "REVIEW_DECISION"->{group.reviews++;if("RETURN".equals(item.path("decision").asText()))group.returned++;}
                case "INDEPENDENT_REVISION_RETURN"->group.independent++;
                case "TASK_INVOCATION"->{group.invocations++;var cost=group.costs.computeIfAbsent(item.path("currency").asText(),ignored->new Cost());cost.calls++;
                    if(item.path("known_amount").isNull())cost.unknown++;else{cost.settled++;cost.amount=cost.amount.add(new BigDecimal(item.path("known_amount").asText()));cost.tokens=cost.tokens.add(new BigInteger(item.path("known_tokens").asText()));cost.providerCalls=cost.providerCalls.add(new BigInteger(item.path("known_calls").asText()));}}
            }
        }
        var result=json.object().arrayNode();for(var group:groups.values()){
            var row=json.object().put("registered_task_invocations",group.invocations).put("independent_revision_return_count",group.independent);
            row.set("dimensions",group.dimensions);row.set("adoption",adoption(group.adopted,group.candidates));row.set("return",ratio(group.returned,group.reviews,"FORMAL_REVIEW_DECISIONS_IN_INTERVAL_INCLUDING_HISTORIC_INVALIDATED"));
            var costs=json.object().arrayNode();group.costs.forEach((currency,c)->{var cost=json.object().put("currency",currency).put("invocations",c.calls).put("settled_count",c.settled).put("unknown_count",c.unknown).put("known_amount",c.amount.toPlainString()).put("known_tokens",c.tokens.toString()).put("known_provider_calls",c.providerCalls.toString());if(c.unknown>0)cost.putNull("total_amount");else cost.put("total_amount",c.amount.toPlainString());costs.add(cost);});row.set("cost_by_currency",costs);result.add(row);
        }return result;
    }
    private static String attribute(Dimension dimension,String key){if(key==null)return null;for(var attr:dimension.attributes())if(key.equals(attr.path("key").asText())&&"KNOWN".equals(attr.path("status").asText())&&attr.path("value").isValueNode()&&!attr.path("value").isNull())return attr.path("value").asText();return null;}
    private static final class Group{final ObjectNode dimensions;final Map<String,Cost> costs=new TreeMap<>();long invocations,candidates,adopted,reviews,returned,independent;Group(ObjectNode dimensions){this.dimensions=dimensions;}}
    private record Dimension(String profile,String documentType,JsonNode attributes){}
    private List<Map<String,Object>> filter(EnterpriseAccess.Admission a,Query query,List<Map<String,Object>> input,Map<String,Dimension> cache,Map<String,Long> excluded)throws Exception{
        var result=new ArrayList<Map<String,Object>>();for(var row:input){String revision=str(row,"task_revision");var dimension=cache.get(revision);
            if(dimension==null){
                var rows=rows("""
                    SELECT t.profile_revision,v.document_type,CASE WHEN OCTET_LENGTH(p.attributes)<=65536 THEN p.attributes ELSE NULL END AS attributes,
                      CASE WHEN OCTET_LENGTH(p.authority_refs)<=65536 THEN p.authority_refs ELSE NULL END AS authority_refs,
                      p.content_sha256,p.schema_version,p.schema_manifest_sha256,vr.content_sha256 AS registered_hash
                    FROM task_revision t JOIN project_profile_revision p ON p.tenant_id=t.tenant_id AND p.project_id=t.project_id AND p.profile_revision=t.profile_revision
                    JOIN template_version v ON v.tenant_id=t.tenant_id AND v.template_version=t.template_version
                    JOIN version_registry vr ON vr.tenant_id=p.tenant_id AND vr.version_id=p.profile_revision
                    WHERE t.tenant_id=? AND t.project_id=? AND t.task_id=? AND t.task_revision=? LIMIT 2
                    """,a.context().tenant_id(),str(row,"project_id"),str(row,"task_id"),revision);
                if(rows.size()!=1)mismatch();var source=rows.get(0);if(source.get("attributes")==null||source.get("authority_refs")==null)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);
                var attrs=codec.read(str(source,"attributes"));projects.attributes(attrs);var content=json.object();content.set("attributes",attrs);content.set("authority_refs",codec.read(str(source,"authority_refs")));
                if(!"1.0.0".equals(str(source,"schema_version"))||!projects.manifestSha256().equals(str(source,"schema_manifest_sha256"))||!codec.hash(content).equals(str(source,"content_sha256"))||!str(source,"content_sha256").equals(str(source,"registered_hash")))mismatch();
                dimension=new Dimension(str(source,"profile_revision"),str(source,"document_type"),attrs);cache.put(revision,dimension);
            }
            String exclusion=null;var f=query.filters();
            if(f.documentType()!=null&&!f.documentType().equals(dimension.documentType()))exclusion="DOCUMENT_TYPE_NOT_MATCHED";
            if(exclusion==null&&f.roleVersion()!=null&&!f.roleVersion().equals(str(row,"role_version")))exclusion=row.get("role_version")==null?"UNKNOWN_PRODUCING_ROLE":"ROLE_NOT_MATCHED";
            for(var pair:List.of(Map.entry("DEVICE_CATEGORY",f.deviceCategory()),Map.entry("PRODUCT_TYPE",f.productType()),Map.entry("STAGE",f.stage())))if(exclusion==null&&pair.getValue().value()!=null){
                JsonNode actual=null;for(var attr:dimension.attributes())if(pair.getValue().key().equals(attr.path("key").asText())){if(actual!=null)mismatch();actual=attr;}
                if(actual==null||!"KNOWN".equals(actual.path("status").asText())||!actual.path("value").isValueNode()||actual.path("value").isNull())exclusion="UNKNOWN_"+pair.getKey();
                else if(!pair.getValue().value().equals(actual.path("value").asText()))exclusion=pair.getKey()+"_NOT_MATCHED";
            }
            if(exclusion==null)result.add(row);else excluded.merge(exclusion,1L,Long::sum);
        }return result;
    }
    static ObjectNode adoption(long numerator,long denominator){
        var value=ratio(numerator,denominator,"CANDIDATES_CREATED_IN_INTERVAL_ADOPTED_BY_OBSERVATION");
        // EVAL-08: absence of an actual adoption is not a reviewed negative business outcome.
        if(numerator==0)value.putNull("rate").put("evidence_status","INSUFFICIENT_EVIDENCE");return value;
    }
    static ObjectNode ratio(long numerator,long denominator,String definition){
        var value=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("numerator",numerator).put("denominator",denominator).put("definition",definition)
            .put("evidence_status",denominator==0?"INSUFFICIENT_EVIDENCE":"OBSERVED");
        if(denominator==0)value.putNull("rate");else value.put("rate",BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),8,RoundingMode.HALF_UP).toPlainString());return value;
    }
    private void task(EnterpriseAccess.Admission a,String project,String id,Set<String> checked)throws Exception{
        if(checked.add(id))authority.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),project,"TASK",id),"task.read","TASK_READING");
    }
    private List<Map<String,Object>> rows(String sql,Object... args){var rows=db.queryForList(sql,args);if(rows.size()>MAX_ROWS)throw new TenantAccessException(ErrorCode.CONTEXT_LIMIT);return rows;}
    private static LocalDateTime time(Object value){if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),null);}
    private static void mismatch(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static final class Cost {long calls,settled,unknown;BigDecimal amount=BigDecimal.ZERO;BigInteger tokens=BigInteger.ZERO,providerCalls=BigInteger.ZERO;}
}

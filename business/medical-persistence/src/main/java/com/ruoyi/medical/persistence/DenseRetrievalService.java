package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.identity.IdentityDirectoryPolicy;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingSupplier;

/** Durable producer/consumer for real dense indices and deterministic dense/hybrid retrieval.
 * Preparation reserves both approved budgets. Dispatch commits before any network operation.
 * UNKNOWN recovery consumes the same durable original response; it never submits a replacement call.
 */
@Component
public final class DenseRetrievalService {
    private static final String PROTOCOL="EMBEDDINGS-1";
    private final EnterpriseAccess access;
    private final ModelConfigurationService models;
    private final ResourceReadAuthority resources;
    private final SourceReadAuthority sources;
    private final PermissionMapper permissions;
    private final PermissionSchema json;
    private final RetrievalPolicy policy;
    private final IdentityDirectoryPolicy identities;
    private final ObjectProvider<DenseEmbeddingGateway> gateways;
    private final FormalInvocationBudget budgets;
    private final RunCreateBudget budgetAuthority;
    private final FormalInvocationSourceOriginals originals;
    private final JdbcTemplate db;
    private final String schemaManifest;
    private final com.networknt.schema.JsonSchema contract;
    private final JsonMapper reader=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public DenseRetrievalService(EnterpriseAccess access,ModelConfigurationService models,ResourceReadAuthority resources,
            SourceReadAuthority sources,PermissionMapper permissions,PermissionSchema json,RetrievalPolicy policy,
            IdentityDirectoryPolicy identities,ObjectProvider<DenseEmbeddingGateway> gateways,RunCreateBudget budgetAuthority,
            BudgetSchema budgetSchema,DataSource data,com.ruoyi.medical.persistence.mapper.SourceReadMapper sourceRows,
            VersionResourceAuthority versions,DependencyLineage lineage,SourceUploadTransport transport) {
        this.access=access;this.models=models;this.resources=resources;this.sources=sources;this.permissions=permissions;
        this.json=json;this.policy=policy;this.identities=identities;this.gateways=gateways;this.budgetAuthority=budgetAuthority;
        this.originals=new FormalInvocationSourceOriginals(access,sources,sourceRows,versions,lineage,transport,data,
            policy.environment(),policy.region(),65536,new DependencyLineage.Limits(128,512));
        this.budgets=new FormalInvocationBudget(budgetAuthority,budgetSchema,json,data);db=new JdbcTemplate(data);db.setQueryTimeout(10);
        try(var dense=Objects.requireNonNull(getClass().getResourceAsStream("/dense-retrieval.schema.json"));
            var embedding=Objects.requireNonNull(getClass().getResourceAsStream("/embedding-request.schema.json"))) {
            byte[] document=dense.readAllBytes();
            schemaManifest=DigestUtils.sha256Hex(json.canonical(json.tree(Map.of("dense-retrieval.schema.json",DigestUtils.sha256Hex(document),"embedding-request.schema.json",DigestUtils.sha256Hex(embedding.readAllBytes())))));
            contract=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012)
                .getSchema(reader.readTree(document),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        } catch(java.io.IOException failure){throw new IllegalStateException("DENSE_SCHEMA_UNAVAILABLE",failure);}
    }
    public <T>T decode(byte[] bytes,Class<T> type)throws Exception {
        if(bytes.length>131072)deny(ErrorCode.CONTEXT_LIMIT);var node=reader.readTree(bytes);
        var schema=contract.getSubSchema(new com.networknt.schema.JsonNodePath(com.networknt.schema.PathType.JSON_POINTER).append("$defs").append(type.getSimpleName()));
        if(node==null||!schema.validate(node).isEmpty())deny(ErrorCode.SCHEMA_INVALID);
        return reader.treeToValue(node,type);
    }
    public JsonNode configure(ApplicationSessionIdentity.Verified user,String context,String key,DenseRetrievalProtocol.Configuration config) {
        valid(config);
        // Physical approved price source, not an arbitrary caller-supplied cheap tariff or registry UUID.
        var original=checked(()->originals.read(user,context,config.price_basis(),"MODEL_CONFIGURATION",config.project_id()));
        return access.execute(user,a->checked(()->{
            current(a,context);original.requireCurrent(a);valid(config);var model=model(a,config.model_revision(),config.project_id(),false);
            resources.require(a,scope(a,model),"model.configure","MODEL_CONFIGURATION");
            var definition=(ModelConfigurationProtocol.Definition)model.definition();
            if(!config.price_basis().equals(definition.price_basis()))deny(ErrorCode.MODEL_POLICY_INCOMPLETE);
            var tariff=reader.readTree(original.bytes());
            if(!"EMBEDDING_PRICE_1".equals(tariff.required("profile").textValue())
                ||!definition.provider().equals(tariff.required("provider").textValue())
                ||!definition.model_version().equals(tariff.required("model_version").textValue())
                ||!definition.currency().equals(tariff.required("currency").textValue())
                ||!"UTF8_BYTES_UPPER_BOUND".equals(tariff.required("tokenizer_contract").textValue())
                ||!"CEILING_8_DECIMAL_PLACES".equals(tariff.required("rounding").textValue())
                ||new BigDecimal(config.input_price_per_million()).compareTo(new BigDecimal(tariff.required("input_price_per_million").textValue()))!=0)
                deny(ErrorCode.MODEL_POLICY_INCOMPLETE);
            var registered=permissions.basis(config.price_basis().version_id());
            if(registered.size()!=1||!registered.get(0).sha256().equals(config.price_basis().content_sha256()))mismatch();
            var fixed=json.tree(config);String hash=hash(fixed);
            // Deterministic version identity makes authoring retry-safe without rewriting immutable configuration.
            String id=UUID.nameUUIDFromBytes((a.context().tenant_id()+":"+a.actorId()+":"+key).getBytes(StandardCharsets.UTF_8)).toString();
            var existing=db.query("SELECT content_sha256 FROM dense_index_configuration WHERE tenant_id=? AND config_revision=?",
                (r,n)->r.getString(1),tenant(a),id);
            if(!existing.isEmpty()){if(!one(existing).equals(hash))deny(ErrorCode.IDEMPOTENCY_CONFLICT);return envelope(Map.of("config_revision",id,"content_sha256",hash));}
            change(db.update("INSERT INTO version_registry(tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES(?,?,?,'INDEX_CONFIGURATION',?)",tenant(a),id,config.project_id(),hash));
            change(db.update("INSERT INTO dense_index_configuration(tenant_id,config_revision,project_id,model_revision,definition,content_sha256,created_by,created_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6),'1.0.0',?,?)",
                tenant(a),id,config.project_id(),config.model_revision(),canonical(fixed),hash,a.actorId(),schemaManifest,identities.classification()));
            lineage(a,id,config.model_revision(),"INDEX_MODEL");lineage(a,id,config.price_basis().version_id(),"INDEX_PRICE");
            audit(a,config.project_id(),"dense.configuration.created",Map.of("config_revision",id,"content_sha256",hash));
            return envelope(Map.of("config_revision",id,"content_sha256",hash));
        }));
    }
    public JsonNode configuration(ApplicationSessionIdentity.Verified user,String context,String id) {
        return access.execute(user,a->checked(()->{current(a,context);var c=config(a,id);model(a,c.model_revision(),c.project_id(),false);return envelope(Map.of("config_revision",id,"definition",c));}));
    }
    public JsonNode build(ApplicationSessionIdentity.Verified user,String context,String key,DenseRetrievalProtocol.Build request) {
        return access.execute(user,a->checked(()->{
            current(a,context);version(request.schema_version());WireValues.uuid(request.config_revision());if(request.source()==null)deny(ErrorCode.SCHEMA_INVALID);
            var c=config(a,request.config_revision());var prior=prior(a,key,hash(json.tree(request)));if(prior!=null)return view(a,prior,false);
            var m=model(a,c.model_revision(),c.project_id(),true);var proof=source(a,c,request.source(),m,true);
            var chunks=chunks(a,request.source());if(chunks.isEmpty()||chunks.size()>c.maximum_chunks())deny(ErrorCode.CONTEXT_LIMIT);
            if(!proof.selection().expectedCount().equals(BigInteger.valueOf(chunks.size())))mismatch();
            var exact=json.object();exact.put("index_version",UUID.randomUUID().toString());exact.set("source",json.tree(request.source()));
            exact.set("chunks",json.tree(chunks));exact.set("inputs",json.tree(chunks.stream().map(v->v.required("raw_text").textValue()).toList()));
            exact.put("owner_project_id",proof.source().ownerProjectId());
            return prepare(a,key,"INDEX",request.config_revision(),json.tree(request),exact,c,m,request.budget_bases());
        }));
    }
    public JsonNode search(ApplicationSessionIdentity.Verified user,String context,String key,DenseRetrievalProtocol.Search request) {
        return access.execute(user,a->checked(()->{
            current(a,context);version(request.schema_version());WireValues.uuid(request.config_revision());ProjectSchema.text(request.query(),500);
            if(request.query().length()<2||request.limit()<1||request.limit()>policy.maximumResults()||request.index_versions()==null
                ||request.index_versions().isEmpty()||request.index_versions().size()>50||new HashSet<>(request.index_versions()).size()!=request.index_versions().size())deny(ErrorCode.SCHEMA_INVALID);
            var c=config(a,request.config_revision());var prior=prior(a,key,hash(json.tree(request)));if(prior!=null)return view(a,prior,false);
            var m=model(a,c.model_revision(),c.project_id(),true);var indices=json.object().arrayNode();int count=0;
            for(String id:request.index_versions().stream().sorted().toList()) {
                WireValues.uuid(id);var index=index(a,id,request.config_revision());var source=reader.treeToValue(index.required("source"),DenseRetrievalProtocol.Source.class);
                source(a,c,source,m,true);count=Math.addExact(count,index.required("chunks").size());indices.add(index);
            }
            if(count>c.maximum_chunks()||(long)count*c.dimension()>1000000L)deny(ErrorCode.CONTEXT_LIMIT);
            var exact=json.object();exact.set("indices",indices);exact.set("inputs",json.tree(List.of(request.query())));
            return prepare(a,key,"QUERY",request.config_revision(),json.tree(request),exact,c,m,request.budget_bases());
        }));
    }
    private JsonNode prepare(EnterpriseAccess.Admission a,String key,String kind,String revision,JsonNode request,ObjectNode exact,
            DenseRetrievalProtocol.Configuration c,ModelConfigurationProtocol.Fixed model,List<PermissionProtocol.Basis> budgetBases)throws Exception {
        String op=UUID.randomUUID().toString();var inputs=new ArrayList<String>();long bytes=0;
        for(var input:exact.required("inputs")){String text=input.textValue();inputs.add(text);bytes=Math.addExact(bytes,text.getBytes(StandardCharsets.UTF_8).length);}
        if(bytes<=0||bytes>c.maximum_input_bytes()||(long)inputs.size()*c.dimension()>1000000L
            ||canonical(exact).getBytes(StandardCharsets.UTF_8).length>16*1024*1024)deny(ErrorCode.CONTEXT_LIMIT);
        var definition=(ModelConfigurationProtocol.Definition)model.definition();
        // Byte upper-bound is intentionally conservative for UTF-8 byte-level embedding tokenizers.
        // Explicit deployment protocol only supports providers with that upper-bound contract.
        BigInteger upperTokens=BigInteger.valueOf(bytes);
        if(upperTokens.compareTo(new BigInteger(definition.limits().context_window()))>0)deny(ErrorCode.CONTEXT_LIMIT);
        BigDecimal upper=price(c,upperTokens);
        var reservations=budgets.reserve(a,c.project_id(),budgetBases,definition.currency(),upper,upperTokens,permissions.databaseTime());
        exact.put("model_sha256",hash(json.tree(model)));exact.put("upper_tokens",upperTokens.toString());
        String auth=authorization(a,c,model,exact,true),inputHash=hash(json.tree(inputs));
        String classification=classification(a,c,model,exact);
        change(db.update("""
            INSERT INTO dense_operation(tenant_id,operation_id,project_id,created_by,idempotency_key,kind,config_revision,
                request,request_sha256,exact_input,exact_sha256,input_sha256,authorization_sha256,status,created_at,updated_at,schema_version,schema_manifest_sha256,classification)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'PREPARED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'1.0.0',?,?)
            """,tenant(a),op,c.project_id(),a.actorId(),key,kind,revision,canonical(request),hash(request),canonical(exact),hash(exact),inputHash,auth,schemaManifest,classification));
        var sourceIds=new TreeSet<String>();
        if(exact.has("source"))sourceIds.add(exact.required("source").required("source_version").textValue());
        else exact.required("indices").forEach(i->sourceIds.add(i.required("source").required("source_version").textValue()));
        for(String sourceId:sourceIds)change(db.update("INSERT INTO dense_operation_source(tenant_id,operation_id,source_version,project_id,created_by,created_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),'1.0.0',?,?)",tenant(a),op,sourceId,c.project_id(),a.actorId(),schemaManifest,classification));
        for(var r:reservations)change(db.update("INSERT INTO dense_budget_reservation(tenant_id,operation_id,budget_id,budget_revision,upper_amount,upper_tokens,project_id,created_by,created_at,updated_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'1.0.0',?,?)",
            tenant(a),op,r.budgetId(),r.budgetRevision(),r.upperAmount(),r.upperTokens(),c.project_id(),a.actorId(),schemaManifest,classification));
        audit(a,c.project_id(),"dense.operation.prepared",Map.of("operation_id",op,"kind",kind,"input_sha256",inputHash));
        return view(a,operation(a,op),false);
    }
    public JsonNode execute(ApplicationSessionIdentity.Verified user,String context,String id) {
        var gateway=gateways.getIfAvailable();if(gateway==null)deny(ErrorCode.DEPENDENCY_UNAVAILABLE);
        Dispatch dispatch=access.execute(user,a->checked(()->{
            current(a,context);var op=operation(a,id);var c=config(a,op.config());var model=model(a,c.model_revision(),c.project_id(),true);
            if(op.status().equals("COMPLETE"))return new Dispatch(op,c,model,null,true);
            if(op.status().equals("WITHDRAWN"))deny(ErrorCode.ACCESS_DENIED);
            boolean recovery=!op.status().equals("PREPARED");
            var exact=parse(op.exact());if(!op.authorization().equals(authorization(a,c,model,exact,true)))deny(ErrorCode.SCOPE_STALE);
            currentBudgets(a,op,c,(ModelConfigurationProtocol.Definition)model.definition());
            if(!recovery) {
                gateway.requireConfigured(tenant(a),(ModelConfigurationProtocol.Definition)model.definition());
                requireRate(a,model,exact);
                change(db.update("UPDATE dense_operation SET status='DISPATCHING',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=? AND status='PREPARED'",tenant(a),id));
                audit(a,c.project_id(),"dense.embedding.dispatched",Map.of("operation_id",id,"input_sha256",op.inputHash()));
            }
            var d=(ModelConfigurationProtocol.Definition)model.definition();var texts=new ArrayList<String>();exact.required("inputs").forEach(v->texts.add(v.textValue()));
            return new Dispatch(op,c,model,new DenseEmbeddingGateway.Request(tenant(a),id,op.inputHash(),d.model_id(),d.model_version(),c.dimension(),texts,
                d.credential_reference_id(),d.credential_revision(),d.provider(),d.base_url(),d.limits().deadline_seconds()),recovery);
        }));
        if(dispatch.request()==null)return read(user,context,id);
        DenseEmbeddingGateway.Receipt receipt;
        try { receipt=gateway.execute(dispatch.request(),dispatch.recovery(),()->access.execute(user,a->checked(()->{
                current(a,context);var op=operation(a,id);if(!"DISPATCHING".equals(op.status()))deny(ErrorCode.SCOPE_STALE);
                var c=config(a,op.config());var m=model(a,c.model_revision(),c.project_id(),true);
                if(!op.authorization().equals(authorization(a,c,m,parse(op.exact()),true)))deny(ErrorCode.SCOPE_STALE);
                currentBudgets(a,op,c,(ModelConfigurationProtocol.Definition)m.definition());
                change(db.update("UPDATE dense_operation SET revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=? AND status='DISPATCHING'",tenant(a),id));return null;
            }))); }
        catch(Exception failure) {
            access.execute(user,a->checked(()->{var op=operation(a,id);if(!op.status().equals("COMPLETE")&&!op.status().equals("WITHDRAWN")&&(!dispatch.recovery()||op.status().equals("UNKNOWN")))
                db.update("UPDATE dense_operation SET status='UNKNOWN',revision=revision+1,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=?",tenant(a),id);return null;}));
            return read(user,context,id);
        }
        return access.execute(user,a->checked(()->{
            current(a,context);var op=operation(a,id);if(op.status().equals("COMPLETE"))return view(a,op,true);
            if(!List.of("DISPATCHING","UNKNOWN").contains(op.status()))deny(ErrorCode.ACCESS_DENIED);
            var c=config(a,op.config());var model=model(a,c.model_revision(),c.project_id(),true);var exact=parse(op.exact());
            if(!op.authorization().equals(authorization(a,c,model,exact,true)))deny(ErrorCode.SCOPE_STALE);
            currentBudgets(a,op,c,(ModelConfigurationProtocol.Definition)model.definition());
            if(receipt.inputTokens()>new BigInteger(exact.required("upper_tokens").textValue()).longValueExact())deny(ErrorCode.BUDGET_EXCEEDED);
            JsonNode result=op.kind().equals("INDEX")?publish(a,op,c,exact,receipt):rank(a,op,c,exact,receipt);
            settle(a,op,c,receipt);
            change(db.update("UPDATE dense_operation SET status='COMPLETE',revision=revision+1,result=?,result_sha256=?,response_sha256=?,updated_at=UTC_TIMESTAMP(6) WHERE tenant_id=? AND operation_id=? AND status IN ('DISPATCHING','UNKNOWN')",
                canonical(result),hash(result),receipt.responseSha256(),tenant(a),id));
            audit(a,c.project_id(),"dense.operation.completed",Map.of("operation_id",id,"response_sha256",receipt.responseSha256(),"result_sha256",hash(result)));
            return view(a,operation(a,id),true);
        }));
    }
    public JsonNode read(ApplicationSessionIdentity.Verified user,String context,String id) {
        return access.execute(user,a->checked(()->{current(a,context);return view(a,operation(a,id),true);}));
    }
    private JsonNode publish(EnterpriseAccess.Admission a,Operation op,DenseRetrievalProtocol.Configuration c,JsonNode exact,DenseEmbeddingGateway.Receipt receipt)throws Exception {
        var source=reader.treeToValue(exact.required("source"),DenseRetrievalProtocol.Source.class);
        String id=exact.required("index_version").textValue();var chunks=exact.required("chunks");
        if(chunks.size()!=receipt.vectors().size()||!json.tree(chunks(a,source)).equals(chunks))mismatch();
        var manifest=json.object();manifest.put("schema_version","1.0.0");manifest.put("index_version",id);
        manifest.put("mode",c.mode());manifest.put("config_revision",op.config());manifest.put("operation_id",op.id());
        manifest.put("model_revision",c.model_revision());manifest.put("dimension",c.dimension());
        manifest.put("input_sha256",op.inputHash());manifest.put("response_sha256",receipt.responseSha256());
        manifest.set("source",exact.required("source"));var items=manifest.putArray("items");
        for(int n=0;n<chunks.size();n++) {
            var item=items.addObject();item.put("chunk_id",chunks.get(n).required("chunk_id").textValue());
            item.put("quote_hash",chunks.get(n).required("quote_hash").textValue());
            item.put("locator_sha256",hash(chunks.get(n).required("locator")));
            item.put("vector_sha256",hash(json.tree(receipt.vectors().get(n))));
        }
        String digest=hash(manifest),owner=exact.required("owner_project_id").textValue();
        change(db.update("INSERT INTO version_registry(tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES(?,?,?,'INDEX_VERSION',?)",tenant(a),id,owner,digest));
        change(db.update("""
            INSERT INTO index_manifest(tenant_id,source_version,parse_version,index_version,mode,model_config_revision,dimension,
                config_sha256,manifest_sha256,expected_count,verified_count,status) VALUES(?,?,?,?,?,?,?,?,?,?,0,'BUILDING')
            """,tenant(a),source.source_version(),source.parse_version(),id,c.mode(),c.model_revision(),c.dimension(),hash(json.tree(c)),digest,chunks.size()));
        for(int n=0;n<chunks.size();n++) {
            String chunk=chunks.get(n).required("chunk_id").textValue();String vectorHash=items.get(n).required("vector_sha256").textValue();
            String point=UUID.nameUUIDFromBytes((tenant(a)+":"+id+":"+chunk).getBytes(StandardCharsets.UTF_8)).toString();
            change(db.update("""
                INSERT INTO index_item(tenant_id,source_version,parse_version,index_version,chunk_id,point_id,vector_sha256,
                    embedding_operation_id,verified_at,status,created_by,created_at,classification,revision,updated_at)
                VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),'READY',?,UTC_TIMESTAMP(6),?,1,UTC_TIMESTAMP(6))
                """,tenant(a),source.source_version(),source.parse_version(),id,chunk,point,vectorHash,op.id(),a.actorId(),op.classification()));
            change(db.update("INSERT INTO dense_index_vector(tenant_id,index_version,source_version,parse_version,chunk_id,operation_id,config_revision,vector_json,vector_sha256,project_id,created_by,created_at,schema_version,schema_manifest_sha256,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),'1.0.0',?,?)",
                tenant(a),id,source.source_version(),source.parse_version(),chunk,op.id(),op.config(),canonical(json.tree(receipt.vectors().get(n))),vectorHash,c.project_id(),a.actorId(),schemaManifest,op.classification()));
        }
        int verified=one(db.query("SELECT COUNT(*) FROM dense_index_vector WHERE tenant_id=? AND index_version=? AND operation_id=?",(r,n)->r.getInt(1),tenant(a),id,op.id()));
        if(verified!=chunks.size())mismatch();
        change(db.update("UPDATE index_manifest SET status='READY',verified_count=? WHERE tenant_id=? AND source_version=? AND parse_version=? AND index_version=? AND status='BUILDING' AND expected_count=?",
            verified,tenant(a),source.source_version(),source.parse_version(),id,verified));
        lineage(a,id,source.parse_version(),"INDEX_INPUT");lineage(a,id,op.config(),"INDEX_CONFIGURATION");lineage(a,id,c.model_revision(),"INDEX_MODEL");
        return manifest;
    }
    private JsonNode rank(EnterpriseAccess.Admission a,Operation op,DenseRetrievalProtocol.Configuration c,JsonNode exact,DenseEmbeddingGateway.Receipt receipt)throws Exception {
        if(receipt.vectors().size()!=1)mismatch();var query=receipt.vectors().get(0);var request=parse(op.request());
        var candidates=new ArrayList<Ranked>();var keys=new HashSet<String>();
        for(var fixed:exact.required("indices")) {
            String id=fixed.required("index_version").textValue();var current=index(a,id,op.config());
            if(!current.equals(fixed))deny(ErrorCode.SCOPE_STALE);
            var source=reader.treeToValue(fixed.required("source"),DenseRetrievalProtocol.Source.class);
            var vectors=db.query("SELECT chunk_id,vector_json,vector_sha256 FROM dense_index_vector WHERE tenant_id=? AND index_version=? ORDER BY chunk_id",
                (r,n)->List.of(r.getString(1),r.getString(2),r.getString(3)),tenant(a),id);
            if(vectors.size()!=fixed.required("chunks").size())mismatch();
            Map<String,JsonNode> chunks=new HashMap<>();fixed.required("chunks").forEach(v->chunks.put(v.required("chunk_id").textValue(),v));
            for(var row:vectors) {
                var vector=parse(row.get(1));if(!hash(vector).equals(row.get(2)))mismatch();var chunk=chunks.get(row.get(0));if(chunk==null)mismatch();
                String identity=source.source_version()+":"+source.parse_version()+":"+row.get(0);
                // Multiple versions of the same source in one search are ambiguous, never silently duplicated.
                if(!keys.add(identity))deny(ErrorCode.SCHEMA_INVALID);
                double score=cosine(query,DenseEmbeddingGateway.vector(vector,c.dimension()));
                double lexical=0;
                if(c.mode().equals("HYBRID"))lexical=one(db.query("""
                    SELECT GREATEST(MATCH(search_text) AGAINST(? IN NATURAL LANGUAGE MODE),0)
                    FROM source_chunk WHERE tenant_id=? AND source_version=? AND parse_version=? AND chunk_id=?
                    """,(r,n)->r.getDouble(1),request.required("query").textValue(),tenant(a),source.source_version(),source.parse_version(),row.get(0)));
                candidates.add(new Ranked(identity,id,source,chunk,score,lexical));
            }
        }
        Comparator<Ranked> tie=Comparator.comparing(Ranked::key);
        var dense=candidates.stream().sorted(Comparator.comparingDouble(Ranked::dense).reversed().thenComparing(tie)).toList();
        Map<String,BigDecimal> scores=new HashMap<>();
        for(int n=0;n<dense.size();n++)scores.put(dense.get(n).key(),rrf(n));
        if(c.mode().equals("HYBRID")) {
            var lexical=candidates.stream().filter(v->v.lexical()>0).sorted(Comparator.comparingDouble(Ranked::lexical).reversed().thenComparing(tie)).toList();
            for(int n=0;n<lexical.size();n++)scores.merge(lexical.get(n).key(),rrf(n),BigDecimal::add);
        }
        var selected=candidates.stream().sorted(Comparator.<Ranked,BigDecimal>comparing(v->scores.get(v.key())).reversed().thenComparing(tie))
            .limit(request.required("limit").intValue()).toList();
        var result=json.object();result.put("schema_version","1.0.0");result.put("mode",c.mode());result.put("fusion","RRF_K60_V1");
        result.put("query",request.required("query").textValue());result.put("config_revision",op.config());result.put("status",selected.isEmpty()?"EMPTY":"FOUND");
        var hits=result.putArray("items");int rank=1;
        for(var selectedHit:selected) {
            var hit=hits.addObject();hit.put("rank",rank++);hit.put("source_version",selectedHit.source().source_version());
            hit.put("parse_version",selectedHit.source().parse_version());hit.put("index_version",selectedHit.index());
            hit.put("approval_revision",selectedHit.source().approval_revision());hit.put("rights_revision",selectedHit.source().rights_revision());
            for(String field:List.of("source_id","chunk_id","quote_hash","locator","title"))hit.set(field,selectedHit.chunk().required(field));
            String raw=selectedHit.chunk().required("raw_text").textValue();int end=raw.offsetByCodePoints(0,Math.min(1200,raw.codePointCount(0,raw.length())));
            hit.put("excerpt",raw.substring(0,end));hit.put("excerpt_truncated",end<raw.length());hit.put("relevance",scores.get(selectedHit.key()).stripTrailingZeros().toPlainString());
        }
        result.put("selection_sha256",hash(hits));return result;
    }
    private JsonNode index(EnterpriseAccess.Admission a,String id,String config)throws Exception {
        var rows=db.query("""
            SELECT o.exact_input,o.result,i.manifest_sha256,v.content_sha256,i.expected_count,i.verified_count
            FROM index_manifest i JOIN dense_index_vector d ON d.tenant_id=i.tenant_id AND d.index_version=i.index_version
            JOIN dense_operation o ON o.tenant_id=d.tenant_id AND o.operation_id=d.operation_id AND o.kind='INDEX' AND o.status='COMPLETE'
            JOIN version_registry v ON v.tenant_id=i.tenant_id AND v.version_id=i.index_version
            WHERE i.tenant_id=? AND i.index_version=? AND d.config_revision=? AND i.status='READY'
            GROUP BY o.operation_id,o.exact_input,o.result,i.manifest_sha256,v.content_sha256,i.expected_count,i.verified_count
            """,(r,n)->new IndexRow(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6)),tenant(a),id,config);
        var row=one(rows);var manifest=parse(row.result());
        if(!hash(manifest).equals(row.hash())||!row.hash().equals(row.registered())||row.count()<1||row.count()!=row.verified())mismatch();
        var persisted=db.query("SELECT d.chunk_id,d.vector_sha256,i.vector_sha256,i.embedding_operation_id,d.operation_id,d.vector_json FROM dense_index_vector d JOIN index_item i ON i.tenant_id=d.tenant_id AND i.index_version=d.index_version AND i.chunk_id=d.chunk_id WHERE d.tenant_id=? AND d.index_version=? AND i.status='READY' ORDER BY d.chunk_id",
            (r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),tenant(a),id);
        if(persisted.size()!=row.count())mismatch();var expected=new HashMap<String,String>();
        manifest.required("items").forEach(item->expected.put(item.required("chunk_id").textValue(),item.required("vector_sha256").textValue()));
        for(var item:persisted)if(!item.get(1).equals(expected.get(item.get(0)))||!item.get(1).equals(item.get(2))
            ||!item.get(3).equals(item.get(4))||!item.get(4).equals(manifest.required("operation_id").textValue())||!hash(parse(item.get(5))).equals(item.get(1)))mismatch();
        var exact=parse(row.exact());if(exact.required("chunks").size()!=row.count()
            ||!manifest.required("source").equals(exact.required("source"))||!id.equals(exact.required("index_version").textValue())
            ||!config.equals(manifest.required("config_revision").textValue())||!hash(exact.required("inputs")).equals(manifest.required("input_sha256").textValue()))mismatch();
        var source=reader.treeToValue(exact.required("source"),DenseRetrievalProtocol.Source.class);
        if(!json.tree(chunks(a,source)).equals(exact.required("chunks")))deny(ErrorCode.SOURCE_NOT_READY);
        var result=json.object();result.put("index_version",id);result.set("source",exact.required("source"));result.set("chunks",exact.required("chunks"));
        result.put("manifest_sha256",row.hash());return result;
    }
    private List<JsonNode> chunks(EnterpriseAccess.Admission a,DenseRetrievalProtocol.Source source)throws Exception {
        var rows=db.query("""
            SELECT c.chunk_id,c.raw_text,c.quote_hash,c.locator,s.source_id,s.title,c.ordinal
            FROM source_chunk c JOIN source_version v ON v.tenant_id=c.tenant_id AND v.source_version=c.source_version
            JOIN source s ON s.tenant_id=v.tenant_id AND s.source_id=v.source_id
            JOIN source_use_gate g ON g.tenant_id=c.tenant_id AND g.source_version=c.source_version AND g.parse_version=c.parse_version
            WHERE c.tenant_id=? AND c.source_version=? AND c.parse_version=? AND g.approval_revision=?
              AND g.upload_status='RECEIVED' AND g.approval_status='APPROVED' AND g.lifecycle='ACTIVE'
            ORDER BY c.ordinal,c.chunk_id LIMIT 10001
            """,(r,n)->List.of(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)),
            tenant(a),source.source_version(),source.parse_version(),source.approval_revision());
        var result=new ArrayList<JsonNode>();
        for(int n=0;n<rows.size();n++) {
            var row=rows.get(n);if(!row.get(2).equals(DigestUtils.sha256Hex(row.get(1)))||!Integer.toString(n).equals(row.get(6)))mismatch();
            var chunk=json.object();chunk.put("chunk_id",row.get(0));chunk.put("raw_text",row.get(1));chunk.put("quote_hash",row.get(2));
            chunk.set("locator",parse(row.get(3)));chunk.put("source_id",row.get(4));chunk.put("title",row.get(5));result.add(chunk);
        }
        return List.copyOf(result);
    }
    private SourceReadProtocol.VerifiedSource source(EnterpriseAccess.Admission a,DenseRetrievalProtocol.Configuration c,
            DenseRetrievalProtocol.Source source,ModelConfigurationProtocol.Fixed model,boolean send)throws Exception {
        var d=(ModelConfigurationProtocol.Definition)model.definition();
        var verified=sources.require(a,new SourceReadProtocol.ReadRequirement(source.source_version(),source.rights_revision(),c.project_id(),
            source.snapshot_id(),source.parse_version(),null,send?SourceReadProtocol.Use.MODEL_SEND:SourceReadProtocol.Use.FORMAL,
            source.purpose(),policy.environment(),policy.region(),send?d.provider():null));
        if(verified.selection()==null||!verified.selection().approvalRevision().equals(source.approval_revision()))deny(ErrorCode.SCOPE_STALE);
        if(!d.data_policy().allowed_classifications().contains(verified.source().classification())
            ||!d.data_policy().allowed_regions().contains(policy.region())||!d.region().equals(policy.region()))deny(ErrorCode.MODEL_POLICY_INCOMPLETE);
        verifyBootstrap(a,source,verified.selection());
        return verified;
    }
    private void verifyBootstrap(EnterpriseAccess.Admission a,DenseRetrievalProtocol.Source source,SourceReadProtocol.SelectionRow selected)throws Exception {
        var identity=one(db.query("SELECT i.manifest_sha256,v.content_sha256 FROM index_manifest i JOIN version_registry v ON v.tenant_id=i.tenant_id AND v.version_id=i.index_version WHERE i.tenant_id=? AND i.source_version=? AND i.parse_version=? AND i.index_version=? AND i.status='READY' AND i.verified_count=i.expected_count",
            (r,n)->List.of(r.getString(1),r.getString(2)),tenant(a),source.source_version(),source.parse_version(),selected.indexVersion()));
        var originals=db.query("SELECT manifest FROM source_index_build WHERE tenant_id=? AND source_version=? AND parse_version=? AND index_version=? AND status='READY'",(r,n)->r.getString(1),tenant(a),source.source_version(),source.parse_version(),selected.indexVersion());
        if(originals.isEmpty())originals=db.query("SELECT result FROM dense_operation WHERE tenant_id=? AND kind='INDEX' AND status='COMPLETE' AND JSON_UNQUOTE(JSON_EXTRACT(result,'$.index_version'))=?",(r,n)->r.getString(1),tenant(a),selected.indexVersion());
        var manifest=parse(one(originals));if(!hash(manifest).equals(identity.get(0))||!identity.get(0).equals(identity.get(1)))mismatch();
        var chunks=chunks(a,source);var items=manifest.required("items");if(items.size()!=chunks.size()||!selected.expectedCount().equals(BigInteger.valueOf(chunks.size())))mismatch();
        for(int n=0;n<chunks.size();n++) {
            var item=items.get(n);if(item.has("chunk"))item=item.required("chunk");var chunk=chunks.get(n);
            if(!item.required("chunk_id").equals(chunk.required("chunk_id"))||!item.required("quote_hash").equals(chunk.required("quote_hash"))
                ||!item.required("locator_sha256").textValue().equals(hash(chunk.required("locator"))))mismatch();
        }
    }
    private String classification(EnterpriseAccess.Admission a,DenseRetrievalProtocol.Configuration c,ModelConfigurationProtocol.Fixed model,JsonNode exact)throws Exception {
        var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int level=levels.indexOf(identities.classification());if(level<0)mismatch();
        var nodes=new ArrayList<JsonNode>();if(exact.has("source"))nodes.add(exact.required("source"));else exact.required("indices").forEach(i->nodes.add(i.required("source")));
        for(var node:nodes){var source=reader.treeToValue(node,DenseRetrievalProtocol.Source.class);int actual=levels.indexOf(source(a,c,source,model,true).source().classification());if(actual<0)mismatch();level=Math.max(level,actual);}
        return levels.get(level);
    }
    private String authorization(EnterpriseAccess.Admission a,DenseRetrievalProtocol.Configuration c,ModelConfigurationProtocol.Fixed m,JsonNode exact,boolean send)throws Exception {
        if(!hash(json.tree(m)).equals(exact.required("model_sha256").textValue()))deny(ErrorCode.SCOPE_STALE);
        var proof=json.object();proof.put("price_authorization",originals.requireCurrentBasis(a,c.price_basis(),"MODEL_CONFIGURATION",c.project_id()));proof.put("model",hash(json.tree(m)));proof.put("actor",a.actorId());var selections=proof.putArray("sources");
        var nodes=new ArrayList<JsonNode>();if(exact.has("source"))nodes.add(exact.required("source"));else exact.required("indices").forEach(i->nodes.add(i.required("source")));
        for(var node:nodes) {
            var s=reader.treeToValue(node,DenseRetrievalProtocol.Source.class);var p=source(a,c,s,m,send);
            var v=selections.addObject();v.set("source",json.tree(p.source()));v.set("rights",json.tree(p.rights()));v.set("selection",json.tree(p.selection()));
            if(exact.has("source")&&!json.tree(chunks(a,s)).equals(exact.required("chunks")))deny(ErrorCode.SCOPE_STALE);
        }
        return hash(proof);
    }
    private ModelConfigurationProtocol.Fixed model(EnterpriseAccess.Admission a,String revision,String project,boolean send)throws Exception {
        var m=models.requireCurrentExternal(a,revision,"MODEL_CONFIGURATION",project);var d=(ModelConfigurationProtocol.Definition)m.definition();
        if(!PROTOCOL.equals(d.model_protocol_revision())||!"urn:medical:embedding-request:1.0.0".equals(d.request_parameter_schema_ref()))deny(ErrorCode.MODEL_POLICY_INCOMPLETE);
        try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/embedding-request.schema.json"))) {
            if(!DigestUtils.sha256Hex(stream.readAllBytes()).equals(d.request_parameter_schema_sha256()))deny(ErrorCode.MODEL_POLICY_INCOMPLETE);
        }
        if(send)resources.require(a,scope(a,m),"model.send","MODEL_CONFIGURATION");
        return m;
    }
    private void requireRate(EnterpriseAccess.Admission a,ModelConfigurationProtocol.Fixed model,JsonNode exact) {
        // Serializes embedding admissions for the same fixed model. Unknown calls still occupy concurrency.
        var lock=db.query("SELECT model_config_revision FROM model_config_revision WHERE tenant_id=? AND model_config_revision=? FOR UPDATE",(r,n)->r.getString(1),tenant(a),model.model_config_revision());one(lock);
        var limits=model.definition().limits();
        long active=db.queryForObject("SELECT COUNT(*) FROM dense_operation o JOIN dense_index_configuration c ON c.tenant_id=o.tenant_id AND c.config_revision=o.config_revision WHERE o.tenant_id=? AND c.model_revision=? AND o.status IN ('DISPATCHING','UNKNOWN')",Long.class,tenant(a),model.model_config_revision());
        var usage=one(db.query("SELECT COUNT(*),COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(o.exact_input,'$.upper_tokens')) AS UNSIGNED)),0) FROM dense_operation o JOIN dense_index_configuration c ON c.tenant_id=o.tenant_id AND c.config_revision=o.config_revision WHERE o.tenant_id=? AND c.model_revision=? AND o.status<>'PREPARED' AND o.updated_at>UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE",(r,n)->List.of(r.getBigDecimal(1).toBigIntegerExact(),r.getBigDecimal(2).toBigIntegerExact()),tenant(a),model.model_config_revision()));
        if(BigInteger.valueOf(active).compareTo(new BigInteger(limits.max_concurrency()))>=0
            ||usage.get(0).compareTo(new BigInteger(limits.requests_per_minute()))>=0
            ||usage.get(1).add(new BigInteger(exact.required("upper_tokens").textValue())).compareTo(new BigInteger(limits.tokens_per_minute()))>0)
            deny(ErrorCode.CONTEXT_LIMIT);
    }
    private void currentBudgets(EnterpriseAccess.Admission a,Operation op,DenseRetrievalProtocol.Configuration c,ModelConfigurationProtocol.Definition d)throws Exception {
        var request=parse(op.request());var bases=request.required("budget_bases");if(bases.size()!=2)mismatch();
        for(var basis:bases) {
            var values=new HashMap<String,Object>();values.put("userId",a.actorId());values.put("projectId",c.project_id());values.put("budgetRevision",basis.required("version_id").textValue());
            var budget=budgetAuthority.requireWithinBounds(a,values,permissions.databaseTime(),d.currency());
            if(!budget.required("content_sha256").equals(basis.required("content_sha256")))mismatch();
        }
    }
    private void settle(EnterpriseAccess.Admission a,Operation op,DenseRetrievalProtocol.Configuration c,DenseEmbeddingGateway.Receipt receipt)throws Exception {
        BigDecimal amount=price(c,BigInteger.valueOf(receipt.inputTokens()));
        var rows=db.query("SELECT budget_id,upper_amount,upper_tokens,settled FROM dense_budget_reservation WHERE tenant_id=? AND operation_id=? ORDER BY budget_id FOR UPDATE",
            (r,n)->new Reservation(r.getString(1),r.getBigDecimal(2),r.getLong(3),r.getBoolean(4)),tenant(a),op.id());
        if(rows.size()!=2)mismatch();
        for(var r:rows) {
            if(r.settled()||amount.compareTo(r.amount())>0||receipt.inputTokens()>r.tokens())mismatch();
            change(db.update("""
                UPDATE budget_account SET reserved=reserved-?,reserved_tokens=reserved_tokens-?,reserved_calls=reserved_calls-1,
                  spent=spent+?,spent_tokens=spent_tokens+?,spent_calls=spent_calls+1,revision=revision+1,updated_at=UTC_TIMESTAMP(6)
                WHERE tenant_id=? AND budget_id=? AND reserved>=? AND reserved_tokens>=? AND reserved_calls>=1
                """,r.amount(),r.tokens(),amount,receipt.inputTokens(),tenant(a),r.id(),r.amount(),r.tokens()));
            change(db.update("UPDATE dense_budget_reservation SET revision=revision+1,updated_at=UTC_TIMESTAMP(6),settled=TRUE,actual_amount=?,actual_tokens=? WHERE tenant_id=? AND operation_id=? AND budget_id=? AND settled=FALSE",
                amount,receipt.inputTokens(),tenant(a),op.id(),r.id()));
        }
    }
    private DenseRetrievalProtocol.Configuration config(EnterpriseAccess.Admission a,String id)throws Exception {
        WireValues.uuid(id);var row=one(db.query("SELECT d.definition,d.content_sha256,v.content_sha256 FROM dense_index_configuration d JOIN version_registry v ON v.tenant_id=d.tenant_id AND v.version_id=d.config_revision WHERE d.tenant_id=? AND d.config_revision=? AND d.schema_version='1.0.0' AND d.schema_manifest_sha256=?",
            (r,n)->List.of(r.getString(1),r.getString(2),r.getString(3)),tenant(a),id,schemaManifest));
        var node=parse(row.get(0));if(!hash(node).equals(row.get(1))||!row.get(1).equals(row.get(2)))mismatch();
        var c=reader.treeToValue(node,DenseRetrievalProtocol.Configuration.class);valid(c);return c;
    }
    private Operation prior(EnterpriseAccess.Admission a,String key,String requestHash) throws Exception {
        var ids=db.query("SELECT operation_id,request_sha256 FROM dense_operation WHERE tenant_id=? AND created_by=? AND idempotency_key=?",
            (r,n)->List.of(r.getString(1),r.getString(2)),tenant(a),a.actorId(),key);
        if(ids.isEmpty())return null;var row=one(ids);if(!row.get(1).equals(requestHash))deny(ErrorCode.IDEMPOTENCY_CONFLICT);return operation(a,row.get(0));
    }
    private Operation operation(EnterpriseAccess.Admission a,String id) throws Exception {
        WireValues.uuid(id);var result=one(db.query("SELECT operation_id,project_id,config_revision,kind,request,exact_input,input_sha256,authorization_sha256,status,result,request_sha256,exact_sha256,result_sha256,classification FROM dense_operation WHERE tenant_id=? AND operation_id=? AND created_by=? AND schema_version='1.0.0' AND schema_manifest_sha256=? FOR UPDATE",
            (r,n)->new Operation(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),r.getString(13),r.getString(14)),tenant(a),id,a.actorId(),schemaManifest));
        if(!result.status().equals("WITHDRAWN")&&(!hash(parse(result.request())).equals(result.requestHash())||!hash(parse(result.exact())).equals(result.exactHash())
            ||!hash(parse(result.exact()).required("inputs")).equals(result.inputHash())))mismatch();
        if(result.status().equals("COMPLETE")&&!hash(parse(result.result())).equals(result.resultHash()))mismatch();
        return result;
    }
    private JsonNode view(EnterpriseAccess.Admission a,Operation op,boolean result)throws Exception {
        var c=config(a,op.config());var m=model(a,c.model_revision(),c.project_id(),false);
        if(op.status().equals("COMPLETE")&&result) {
            authorization(a,c,m,parse(op.exact()),false);
            if(op.kind().equals("QUERY"))for(var index:parse(op.exact()).required("indices"))index(a,index.required("index_version").textValue(),op.config());
            else index(a,parse(op.exact()).required("index_version").textValue(),op.config());
        }
        var out=json.object();out.put("operation_id",op.id());out.put("kind",op.kind());out.put("status",op.status());
        out.put("status_url","/api/dense-operations/"+op.id());out.put("execute_url","/api/dense-operations/"+op.id()+"/execute");
        out.put("recovery",op.status().equals("UNKNOWN")||op.status().equals("DISPATCHING")?"ORIGINAL_RESPONSE_ONLY":"NONE");
        if(result&&op.status().equals("COMPLETE"))out.set("result",parse(op.result()));else out.putNull("result");return envelope(out);
    }
    private void lineage(EnterpriseAccess.Admission a,String id,String parent,String relation) {
        change(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES(?,?,?,?,?,NULL,UTC_TIMESTAMP(6))",tenant(a),id,parent,relation,a.actorId()));
    }
    private void audit(EnterpriseAccess.Admission a,String project,String type,Object data)throws Exception {
        change(permissions.saveAudit(a.actorId(),project,UUID.randomUUID().toString(),type,canonical(json.tree(data)),UUID.randomUUID().toString(),schemaManifest,identities.classification()));
    }
    private JsonNode envelope(Object result){var out=json.object();out.put("schema_version","1.0.0");out.put("request_id",UUID.randomUUID().toString());out.putNull("resource_revision");out.set("data",json.tree(result));return out;}
    private JsonNode parse(String value)throws Exception{return reader.readTree(value);}
    private String canonical(JsonNode value)throws Exception{return json.canonical(value);}
    private String hash(JsonNode value)throws Exception{return DigestUtils.sha256Hex(canonical(value));}
    private static PermissionProtocol.Scope scope(EnterpriseAccess.Admission a,ModelConfigurationProtocol.Fixed m){return new PermissionProtocol.Scope(tenant(a),m.project_id(),"CONFIGURATION",m.config_id());}
    private static String tenant(EnterpriseAccess.Admission a){return a.context().tenant_id();}
    private static void current(EnterpriseAccess.Admission a,String context){if(!a.context().context_revision().equals(context))deny(ErrorCode.CONTEXT_CHANGED);}
    private static void version(String value){if(!"1.0.0".equals(value))deny(ErrorCode.SCHEMA_UNSUPPORTED);}
    private static void valid(DenseRetrievalProtocol.Configuration c) {
        version(c.schema_version());WireValues.uuid(c.project_id());WireValues.uuid(c.model_revision());ProjectSchema.text(c.reason(),2000);
        if(!List.of("DENSE","HYBRID").contains(c.mode())||c.dimension()<1||c.dimension()>4096||c.maximum_chunks()<1||c.maximum_chunks()>10000
            ||c.maximum_input_bytes()<1||c.maximum_input_bytes()>4*1024*1024||c.price_basis()==null)deny(ErrorCode.SCHEMA_INVALID);
        WireValues.uuid(c.price_basis().version_id());WireValues.hash(c.price_basis().content_sha256());
        BigDecimal price=new BigDecimal(c.input_price_per_million());if(price.signum()<0||price.scale()>8||price.precision()>24)deny(ErrorCode.SCHEMA_INVALID);
    }
    private static BigDecimal price(DenseRetrievalProtocol.Configuration c,BigInteger tokens){return new BigDecimal(c.input_price_per_million()).multiply(new BigDecimal(tokens)).divide(BigDecimal.valueOf(1000000),8,RoundingMode.CEILING);}
    static BigDecimal rrf(int index){return BigDecimal.ONE.divide(BigDecimal.valueOf(61L+index),16,RoundingMode.HALF_EVEN);}
    static double cosine(List<Double> a,List<Double> b){double dot=0,x=0,y=0;for(int i=0;i<a.size();i++){dot+=a.get(i)*b.get(i);x+=a.get(i)*a.get(i);y+=b.get(i)*b.get(i);}double result=dot/Math.sqrt(x*y);if(!Double.isFinite(result))mismatch();return result;}
    private static <T>T checked(ThrowingSupplier<T> action){return ThrowingSupplier.of(action).get();}
    private static <T>T one(List<T> values){if(values.size()!=1)deny(ErrorCode.OBJECT_UNAVAILABLE);return values.get(0);}
    private static void change(int count){if(count!=1)deny(ErrorCode.REVISION_CONFLICT);}
    private static void mismatch(){deny(ErrorCode.CONTENT_MISMATCH);}
    private static void deny(ErrorCode code){throw new TenantAccessException(code);}
    private record Operation(String id,String project,String config,String kind,String request,String exact,String inputHash,String authorization,String status,String result,String requestHash,String exactHash,String resultHash,String classification) { }
    private record Dispatch(Operation operation,DenseRetrievalProtocol.Configuration config,ModelConfigurationProtocol.Fixed model,DenseEmbeddingGateway.Request request,boolean recovery) { }
    private record Reservation(String id,BigDecimal amount,long tokens,boolean settled) { }
    private record IndexRow(String exact,String result,String hash,String registered,int count,int verified) { }
    private record Ranked(String key,String index,DenseRetrievalProtocol.Source source,JsonNode chunk,double dense,double lexical) { }
}

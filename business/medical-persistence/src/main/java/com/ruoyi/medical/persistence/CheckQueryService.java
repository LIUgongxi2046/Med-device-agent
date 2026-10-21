package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;
import static com.ruoyi.medical.persistence.ProfessionalCheckService.*;
/** Versioned BX-06 dispatch. Technical checks cannot be mistaken for professional results. */
public final class CheckQueryService {
 private final EnterpriseAccess access;private final JdbcTemplate db;private final ProfessionalCheckService documents;private final CandidateProfessionalCheckReadService candidates;private final ObjectProvider<CandidateSetCheckReadService> assemblies;private final ObjectProvider<DocumentTechnicalCheckService> technical;private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();private final SchemaCatalogue schemas;private final com.networknt.schema.JsonSchema extended;
 public CheckQueryService(DataSource data,EnterpriseAccess access,ProfessionalCheckService documents,CandidateProfessionalCheckReadService candidates,ObjectProvider<CandidateSetCheckReadService> assemblies,ObjectProvider<DocumentTechnicalCheckService> technical)throws Exception{this.access=access;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.documents=documents;this.candidates=candidates;this.assemblies=assemblies;this.technical=technical;schemas=SchemaCatalogue.fromClasspath();try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/check-detail-v1.1.schema.json"))){extended=com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V202012).getSchema(json.read(in.readAllBytes()),com.networknt.schema.SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());}}
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String check,String version)throws Exception{
  WireValues.uuid(check);if(!Set.of("1.0.0","1.1.0").contains(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);var row=access.execute(subject,a->{context(a,context);return one(db.queryForList("SELECT * FROM check_run WHERE tenant_id=? AND check_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),check));});String target=str(row,"target_kind");JsonNode detail;String kind;boolean complete=false;
  if("CANDIDATE_SET".equals(target)){if(!"1.1.0".equals(version))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);var service=assemblies.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);detail=service.read(subject,context,check);kind="CROSS_FILE_STRUCTURE";}
  else if("CANDIDATE".equals(target)){detail=candidates.read(subject,context,check);kind="CANDIDATE_PROFESSIONAL";complete=json.read(str(row,"result_ref")).path("coverage_complete").asBoolean(false);}
  else if("DOCUMENT".equals(target)&&row.get("result_ref")!=null&&"N07".equals(json.read(str(row,"result_ref")).path("node_kind").asText())){var proof=documents.verified(subject,context,check);detail=proof.detail();kind="DOCUMENT_PROFESSIONAL";complete=proof.findings().coverageComplete();}
  else if("DOCUMENT".equals(target)){var service=technical.getIfAvailable();if(service==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var actual=service.read(subject,context,check);same(check,text(actual.required("receipt"),"check_id"));detail=technicalDetail(subject,context,row,actual);kind="STRUCTURE_FIXED_REFERENCES";}
  else throw new TenantAccessException(ErrorCode.STEP_NOT_READY);
  if("1.0.0".equals(version))return detail;var out=(ObjectNode)detail.deepCopy();out.put("check_kind",kind).put("professional_coverage_complete",complete).put("professional_approval",false);if(!extended.validate(out).isEmpty())bad();return out;
 }
 private JsonNode technicalDetail(ApplicationSessionIdentity.Verified subject,String context,Map<String,Object> row,JsonNode actual)throws Exception{
  return access.execute(subject,a->ThrowingSupplier.of(()->{context(a,context);var current=one(db.queryForList("SELECT * FROM check_run WHERE tenant_id=? AND check_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(row,"check_id")));same(json.hash(row),json.hash(current));var ar=one(db.queryForList("SELECT a.*,b.schema_ref FROM artifact a JOIN artifact_content_binding b ON b.tenant_id=a.tenant_id AND b.artifact_id=a.artifact_id WHERE a.tenant_id=? AND a.project_id=? AND a.artifact_id=? LIMIT 2 FOR UPDATE",a.context().tenant_id(),str(row,"project_id"),str(row,"findings_artifact_id")));var ref=new InterfaceProtocol.ObjectRef(str(ar,"object_key"),str(ar,"object_version"),str(ar,"sha256"),str(ar,"size_bytes"),str(ar,"media_type"));String range=ObjectContentPaging.rangeDigest(ObjectContentPaging.objectDigest(ref),0,Long.parseLong(ref.sizeBytes()));var coverage=new CommonProtocol.Coverage(range,range,true,null);
   var result=new ResourceProtocol.CheckDetail(str(row,"check_id"),ResourceProtocol.TargetKind.DOCUMENT,str(row,"target_id"),str(row,"target_revision"),str(row,"input_sha256"),str(row,"rule_manifest_id"),ResourceProtocol.CheckStatus.valueOf(str(row,"status")),coverage,new CommonProtocol.ArtifactRef(str(row,"findings_artifact_id"),ref.sha256(),str(ar,"schema_ref"),coverage),List.of(),str(row,"evidence_watermark"),str(row,"issue_watermark"),"TECHNICAL_STRUCTURE_ONLY");return json.tree(TypedSchemaBindings.bind(schemas,ResourceBindings.CHECK_DETAIL,json.tree(result)).read());}).get());
 }
}

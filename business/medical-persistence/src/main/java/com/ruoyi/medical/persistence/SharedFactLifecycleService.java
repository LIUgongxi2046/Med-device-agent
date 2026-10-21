package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.function.ThrowingSupplier;

/** DC-04: proposals are never formal facts; explicit policy, human support and independent adoption. */
public final class SharedFactLifecycleService {
 private final SharedFactScopeAuthority scopes;
 private static final String PURPOSE="SHARED_FACT_GOVERNANCE";
 private final EnterpriseAccess access; private final ResourceReadAuthority resources; private final PermissionMapper permissions;
 private final SharedFactReplacementImpact impacts; private final DocumentReadService documents; private final HumanEvidenceCitationReads citations; private final JdbcTemplate db;
 private final SharedFactLifecycleCodec codec=new SharedFactLifecycleCodec(); private final TaskSourceVerifierSchema json=codec.json;
 public SharedFactLifecycleService(DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,PermissionMapper permissions,DocumentReadService documents,HumanEvidenceCitationReads citations,SharedFactReplacementImpact impacts,SharedFactScopeAuthority scopes)throws Exception{
  this.scopes=Objects.requireNonNull(scopes);this.impacts=impacts;this.access=access;this.resources=resources;this.permissions=permissions;this.documents=documents;this.citations=citations;db=new JdbcTemplate(data);db.setQueryTimeout(5);
 }
 public JsonNode rule(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes)throws Exception{
  var request=codec.request("Rule",bytes);keys(context,project,key);String hash=json.hash(request);
  if(!request.required("unit").isNull()||("TEXT_EXACT".equals(t(request,"operator"))!=request.required("scale").isNull()))fail(ErrorCode.SCHEMA_INVALID);
  return tx(subject,a->{base(a,context,project);var old=replay(a,project,"RULE",key,hash);if(old!=null)return old;var witness=grant(a,project,"fact.rule.approve");String id=id();
   write(db.update("INSERT INTO shared_fact_check_rule(tenant_id,project_id,rule_id,fact_class,definition_json,definition_sha256,authority_json,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,t(request,"fact_class"),json.canonical(request),hash,json.canonical(AgentPublicationWitness.tree(witness)),codec.manifest,a.actorId(),permissions.databaseTime(),classification(a,project)));return finish(a,project,"RULE",key,hash,id,"APPROVED","0");
  });
 }
 public JsonNode check(ApplicationSessionIdentity.Verified subject,String context,String project,String proposal,String key,byte[] bytes)throws Exception{
  var request=codec.request("Check",bytes);keys(context,project,key);WireValues.uuid(proposal);String hash=json.hash(Map.of("proposal_id",proposal,"request",request));
  var replay=tx(subject,a->{base(a,context,project);return replay(a,project,"CHECK",key,hash);});if(replay!=null)return replay;
  var row=tx(subject,a->{base(a,context,project);return proposal(a,project,proposal);});var content=content(row);var fixed=documents.readFixed(subject,context,t(content,"document_id"),t(content,"document_revision"));var physical=citations.read(subject,fixed);var baseline=evidence(content,fixed,physical);
  return tx(subject,a->{base(a,context,project);var old=replay(a,project,"CHECK",key,hash);if(old!=null)return old;var witness=grant(a,project,"fact.check");currentProposal(a,project,row);documents.requireCurrent(a,fixed);citations.requireCurrent(a,fixed,physical);var factScope=scopes.require(a,project,content);baseline.set("fact_scope",factScope.witness());
   var policy=policy(a,project,t(content,"fact_class"));if(!s(policy,"policy_id").equals(t(request,"policy_id")))fail(ErrorCode.REVIEW_STALE);var definition=json.read(s(policy,"content_json"));if(!"EXACT_CHECKS".equals(t(definition,"mode"))||definition.required("required_rule_versions").isEmpty())fail(ErrorCode.SCHEMA_INVALID);
   boolean pass=true;var checks=baseline.putArray("checks");for(var rule:definition.required("required_rule_versions")){var actual=rule(a,project,rule.asText(),t(content,"fact_class"));var result=SharedFactExactCheck.evaluate(content,json.read(s(actual,"definition_json")),physical);checks.addObject().put("rule_id",rule.asText()).put("rule_sha256",s(actual,"definition_sha256")).set("evaluation",result);pass&="PASS".equals(t(result,"result"));}
   baseline.put("proposal_sha256",s(row,"content_sha256")).put("policy_id",s(policy,"policy_id"));baseline.set("authority",AgentPublicationWitness.tree(witness));String id=id();write(db.update("INSERT INTO shared_fact_check(tenant_id,project_id,check_id,proposal_id,policy_id,baseline_json,baseline_sha256,result,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,proposal,s(policy,"policy_id"),json.canonical(baseline),json.hash(baseline),pass?"PASS":"FAIL",codec.manifest,a.actorId(),permissions.databaseTime(),s(row,"classification")));return finish(a,project,"CHECK",key,hash,id,pass?"PASS":"FAIL","0");
  });
 }
 private Map<String,Object> rule(EnterpriseAccess.Admission a,String project,String id,String factClass)throws Exception{
  var row=one(db.queryForList("SELECT * FROM shared_fact_check_rule WHERE tenant_id=? AND project_id=? AND rule_id=? AND fact_class=?",tenant(a),project,id,factClass));if(!json.hash(json.read(s(row,"definition_json"))).equals(s(row,"definition_sha256")))fail(ErrorCode.CONTENT_MISMATCH);return row;
 }
 public JsonNode policy(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes)throws Exception{
  var request=codec.request("Policy",bytes);String hash=json.hash(request);keys(context,project,key);
  if(("HUMAN_ONLY".equals(t(request,"mode")))!=request.required("required_rule_versions").isEmpty())fail(ErrorCode.SCHEMA_INVALID);
  return tx(subject,a->{base(a,context,project);var replay=replay(a,project,"POLICY",key,hash);if(replay!=null)return replay;
   var grant=grant(a,project,"fact.policy.approve");String id=id(),factClass=t(request,"fact_class");for(var required:request.required("required_rule_versions"))rule(a,project,required.asText(),factClass);var now=permissions.databaseTime();
   checkHead(a,project,"policy","fact_class",factClass,t(request,"expected_revision"));
   write(db.update("INSERT INTO shared_fact_policy(tenant_id,project_id,policy_id,fact_class,content_json,content_sha256,authority_json,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,factClass,json.canonical(request),hash,json.canonical(AgentPublicationWitness.tree(grant)),codec.manifest,a.actorId(),now,classification(a,project)));
   head(a,project,"policy","fact_class",factClass,"policy_id",id,t(request,"expected_revision"));return finish(a,project,"POLICY",key,hash,id,"APPROVED",t(request,"expected_revision"));
  });
 }
 public JsonNode propose(ApplicationSessionIdentity.Verified subject,String context,String project,String key,byte[] bytes)throws Exception{
  var request=codec.request("Proposal",bytes);String hash=json.hash(request);keys(context,project,key);
  var replay=tx(subject,a->{base(a,context,project);return replay(a,project,"PROPOSAL",key,hash);});if(replay!=null)return replay;
  var content=request.required("content");var fixed=documents.readFixed(subject,context,t(content,"document_id"),t(content,"document_revision"));validateContent(project,content,fixed);
  return tx(subject,a->{base(a,context,project);var old=replay(a,project,"PROPOSAL",key,hash);if(old!=null)return old;grant(a,project,"fact.propose");documents.requireCurrent(a,fixed);scopes.require(a,project,content);
   checkHead(a,project,"proposal","fact_key",t(content,"fact_key"),t(request,"expected_revision"));String id=id();
   var prior=db.queryForList("SELECT proposal_id FROM shared_fact_proposal_head WHERE tenant_id=? AND project_id=? AND fact_key=?",tenant(a),project,t(content,"fact_key"));String baseProposal=prior.isEmpty()?null:s(one(prior),"proposal_id");
   write(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'SHARED_FACT_PROPOSAL',?)",tenant(a),project,id,json.hash(content)));
   write(db.update("INSERT INTO shared_fact_proposal(tenant_id,project_id,proposal_id,fact_key,base_proposal_id,document_id,document_revision,content_json,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,t(content,"fact_key"),baseProposal,t(content,"document_id"),t(content,"document_revision"),json.canonical(content),json.hash(content),codec.manifest,a.actorId(),permissions.databaseTime(),fixed.header().classification()));
   head(a,project,"proposal","fact_key",t(content,"fact_key"),"proposal_id",id,t(request,"expected_revision"));return finish(a,project,"PROPOSAL",key,hash,id,"PROPOSED",t(request,"expected_revision"));
  });
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String project,String proposal)throws Exception{
  WireValues.uuid(proposal);var row=tx(subject,a->{base(a,context,project);return proposal(a,project,proposal);});
  var content=content(row);var fixed=documents.readFixed(subject,context,t(content,"document_id"),t(content,"document_revision"));
  return tx(subject,a->{base(a,context,project);documents.requireCurrent(a,fixed);var result=JsonNodeFactory.instance.objectNode().put("proposal_id",proposal).put("content_sha256",s(row,"content_sha256")).put("status","PROPOSED").put("requires_current_revalidation",true);result.set("content",content);
   var decisions=db.queryForList("SELECT verification_id,policy_id,decision,reason,baseline_sha256,created_by,created_at FROM shared_fact_verification WHERE tenant_id=? AND project_id=? AND proposal_id=? ORDER BY created_at,verification_id LIMIT 1001",tenant(a),project,proposal);
   var adoptions=db.queryForList("SELECT fact_version,verification_id,policy_id,baseline_sha256,created_by,created_at FROM shared_fact_adoption WHERE tenant_id=? AND project_id=? AND proposal_id=? ORDER BY created_at,fact_version LIMIT 1001",tenant(a),project,proposal);
   if(decisions.size()>1000||adoptions.size()>1000)fail(ErrorCode.CONTEXT_LIMIT);
   result.set("verification_history",json.tree(decisions));result.set("adoption_history",json.tree(adoptions));return result;});
 }
 public JsonNode verify(ApplicationSessionIdentity.Verified subject,String context,String project,String proposal,String key,byte[] bytes)throws Exception{
  var request=codec.request("Verification",bytes);keys(context,project,key);WireValues.uuid(proposal);String hash=json.hash(Map.of("proposal_id",proposal,"request",request));
  var replay=tx(subject,a->{base(a,context,project);return replay(a,project,"VERIFY",key,hash);});if(replay!=null)return replay;
  var row=tx(subject,a->{base(a,context,project);return proposal(a,project,proposal);});var content=content(row);var fixed=documents.readFixed(subject,context,t(content,"document_id"),t(content,"document_revision"));validateContent(project,content,fixed);var physical=citations.read(subject,fixed);var baseline=evidence(content,fixed,physical);
  return tx(subject,a->{base(a,context,project);var old=replay(a,project,"VERIFY",key,hash);if(old!=null)return old;var witness=grant(a,project,"fact.verify");currentProposal(a,project,row);documents.requireCurrent(a,fixed);citations.requireCurrent(a,fixed,physical);var factScope=scopes.require(a,project,content);baseline.set("fact_scope",factScope.witness());
   if(a.actorId().equals(s(row,"created_by")))fail(ErrorCode.ACCESS_DENIED);var policy=policy(a,project,t(content,"fact_class"));if(!s(policy,"policy_id").equals(t(request,"policy_id")))fail(ErrorCode.REVIEW_STALE);
   String id=id();baseline.put("proposal_sha256",s(row,"content_sha256")).put("policy_id",s(policy,"policy_id")).put("auth_epoch",a.context().auth_epoch());baseline.set("authority",AgentPublicationWitness.tree(witness));
   write(db.update("INSERT INTO shared_fact_verification(tenant_id,project_id,verification_id,proposal_id,policy_id,decision,reason,baseline_json,baseline_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,proposal,s(policy,"policy_id"),t(request,"decision"),t(request,"reason"),json.canonical(baseline),json.hash(baseline),codec.manifest,a.actorId(),permissions.databaseTime(),s(row,"classification")));
   return finish(a,project,"VERIFY",key,hash,id,t(request,"decision"),"0");
  });
 }
 public JsonNode adopt(ApplicationSessionIdentity.Verified subject,String context,String project,String proposal,String key,byte[] bytes)throws Exception{
  var request=codec.request("Adoption",bytes);keys(context,project,key);WireValues.uuid(proposal);String hash=json.hash(Map.of("proposal_id",proposal,"request",request));
  var replay=tx(subject,a->{base(a,context,project);return replay(a,project,"ADOPT",key,hash);});if(replay!=null)return replay;
  var row=tx(subject,a->{base(a,context,project);return proposal(a,project,proposal);});var content=content(row);var fixed=documents.readFixed(subject,context,t(content,"document_id"),t(content,"document_revision"));validateContent(project,content,fixed);var physical=citations.read(subject,fixed);var baseline=evidence(content,fixed,physical);
  return tx(subject,a->{base(a,context,project);var old=replay(a,project,"ADOPT",key,hash);if(old!=null)return old;var witness=grant(a,project,"fact.adopt");currentProposal(a,project,row);documents.requireCurrent(a,fixed);citations.requireCurrent(a,fixed,physical);var factScope=scopes.require(a,project,content);baseline.set("fact_scope",factScope.witness());
   var policy=policy(a,project,t(content,"fact_class"));var definition=json.read(s(policy,"content_json"));if("EXACT_CHECKS".equals(t(definition,"mode"))){
    var checks=db.queryForList("SELECT * FROM shared_fact_check WHERE tenant_id=? AND project_id=? AND proposal_id=? AND policy_id=? AND result='PASS' ORDER BY created_at DESC LIMIT 1",tenant(a),project,proposal,s(policy,"policy_id"));var checked=one(checks);var check=json.read(s(checked,"baseline_json"));
    if(!json.hash(check).equals(s(checked,"baseline_sha256"))||!s(row,"content_sha256").equals(t(check,"proposal_sha256"))||!baseline.required("fact_scope").equals(check.required("fact_scope"))||!baseline.required("evidence").equals(check.required("evidence"))||!baseline.required("document_digest").equals(check.required("document_digest")))fail(ErrorCode.REVIEW_STALE);
    var actualRules=new HashSet<String>();for(var entry:check.required("checks")){var actual=rule(a,project,t(entry,"rule_id"),t(content,"fact_class"));if(!s(actual,"definition_sha256").equals(t(entry,"rule_sha256"))||!SharedFactExactCheck.evaluate(content,json.read(s(actual,"definition_json")),physical).equals(entry.required("evaluation"))||!"PASS".equals(t(entry.required("evaluation"),"result"))||!actualRules.add(t(entry,"rule_id")))fail(ErrorCode.REVIEW_STALE);}
    var requiredRules=new HashSet<String>();definition.required("required_rule_versions").forEach(r->requiredRules.add(r.asText()));if(requiredRules.isEmpty()||!requiredRules.equals(actualRules))fail(ErrorCode.REVIEW_STALE);baseline.put("check_id",s(checked,"check_id")).put("check_sha256",s(checked,"baseline_sha256"));
   }else if(!"HUMAN_ONLY".equals(t(definition,"mode"))||!definition.required("required_rule_versions").isEmpty())fail(ErrorCode.REVIEW_STALE);
   var decision=one(db.queryForList("SELECT * FROM shared_fact_verification WHERE tenant_id=? AND project_id=? AND verification_id=? AND proposal_id=?",tenant(a),project,t(request,"verification_id"),proposal));
   if(!db.queryForList("SELECT verification_id FROM shared_fact_verification WHERE tenant_id=? AND project_id=? AND proposal_id=? AND policy_id=? AND verification_id<>? AND created_at>=? LIMIT 1",tenant(a),project,proposal,s(policy,"policy_id"),t(request,"verification_id"),decision.get("created_at")).isEmpty())fail(ErrorCode.REVIEW_STALE);
   if(!"SUPPORTS".equals(s(decision,"decision"))||!s(policy,"policy_id").equals(s(decision,"policy_id"))||a.actorId().equals(s(decision,"created_by"))||a.actorId().equals(s(row,"created_by"))||s(row,"created_by").equals(s(decision,"created_by")))fail(ErrorCode.ACCESS_DENIED);
   var verified=json.read(s(decision,"baseline_json"));
   var authority=verified.required("authority");var validTo=authority.path("valid_to");var validFrom=authority.path("valid_from");var checkTime=permissions.databaseTime();
   if(!validTo.isNull()&&!validTo.isMissingNode()&&!checkTime.isBefore(java.time.LocalDateTime.parse(validTo.asText())))fail(ErrorCode.REVIEW_STALE);
   if(!validFrom.isNull()&&!validFrom.isMissingNode()&&checkTime.isBefore(java.time.LocalDateTime.parse(validFrom.asText())))fail(ErrorCode.REVIEW_STALE);if(!a.context().auth_epoch().equals(t(verified,"auth_epoch")))fail(ErrorCode.REVIEW_STALE);if(!json.hash(verified).equals(s(decision,"baseline_sha256"))||!s(row,"content_sha256").equals(t(verified,"proposal_sha256"))||!baseline.required("fact_scope").equals(verified.required("fact_scope"))||!baseline.required("evidence").equals(verified.required("evidence"))||!baseline.required("document_digest").equals(verified.required("document_digest")))fail(ErrorCode.REVIEW_STALE);
   // A replacement needs B06's complete impact and atomic invalidation proof. Never infer it from zero visible uses.
   String previousFact=null;checkHead(a,project,"adopted","fact_key",t(content,"fact_key"),t(request,"expected_revision"));
   if("0".equals(t(request,"expected_revision"))&&!db.queryForList("SELECT fact_version FROM shared_fact_version WHERE tenant_id=? AND project_id=? AND fact_key=? LIMIT 1",tenant(a),project,t(content,"fact_key")).isEmpty())fail(ErrorCode.REVIEW_STALE);
   if(!"0".equals(t(request,"expected_revision"))){
    var previous=one(db.queryForList("SELECT fact_version FROM shared_fact_adopted_head WHERE tenant_id=? AND project_id=? AND fact_key=? FOR UPDATE",tenant(a),project,t(content,"fact_key")));
    previousFact=s(previous,"fact_version");var impact=impacts.require(a,project,s(previous,"fact_version"),proposal,s(row,"content_sha256"));impact.require(a,project,s(previous,"fact_version"),proposal,s(row,"content_sha256"));baseline.set("replacement_impact",impact.snapshot());
   }
   String id=id();var now=permissions.databaseTime();String digest=s(row,"content_sha256");baseline.put("proposal_sha256",digest).put("policy_id",s(policy,"policy_id")).put("verification_id",t(request,"verification_id")).put("reason",t(request,"reason"));baseline.set("authority",AgentPublicationWitness.tree(witness));
   write(db.update("INSERT INTO version_registry(tenant_id,owner_project_id,version_id,entity_kind,content_sha256) VALUES(?,?,?,'SHARED_FACT',?)",tenant(a),project,id,digest));
   write(db.update("INSERT INTO shared_fact_version(tenant_id,project_id,fact_version,fact_key,raw_value,value,unit,model_ids,condition_id,evidence_manifest,verifier_id,status,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,'ADOPTED',?,'1.0.0',?,?,?,?)",tenant(a),project,id,t(content,"fact_key"),t(content,"raw_value"),content.required("decimal_value").isNull()?null:new java.math.BigDecimal(t(content,"decimal_value")),content.required("unit").isNull()?null:t(content,"unit"),json.canonical(factScope.modelIds()),factScope.conditionId(),json.canonical(baseline.required("evidence")),s(decision,"created_by"),digest,codec.manifest,a.actorId(),now,s(row,"classification")));
   write(db.update("INSERT INTO shared_fact_adoption(tenant_id,project_id,fact_version,proposal_id,verification_id,policy_id,baseline_json,baseline_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,id,proposal,t(request,"verification_id"),s(policy,"policy_id"),json.canonical(baseline),json.hash(baseline),codec.manifest,a.actorId(),now,s(row,"classification")));
   var upstream=new TreeSet<String>();if(previousFact!=null)upstream.add(previousFact);for(var link:fixed.body().evidenceLinks())if(contains(content.required("evidence_link_ids"),link.evidenceLinkId())){upstream.add(link.evidence().sourceVersion());upstream.add(link.evidence().parseVersion());upstream.add(link.evidence().approvalRevision());}
   for(String parent:upstream)write(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES(?,?,?,'SHARED_FACT_EVIDENCE',?,NULL,?)",tenant(a),id,parent,a.actorId(),now));
   if(content.hasNonNull("scope_context"))for(String scopeVersion:List.of(content.required("scope_context").required("version_id").asText(),factScope.witness().required("context_decision").required("version_id").asText()))
    write(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES(?,?,?,'SHARED_FACT_SCOPE',?,NULL,?)",tenant(a),id,scopeVersion,a.actorId(),now));
   head(a,project,"adopted","fact_key",t(content,"fact_key"),"fact_version",id,t(request,"expected_revision"));return finish(a,project,"ADOPT",key,hash,id,"ADOPTED",t(request,"expected_revision"));
  });
 }
 private ObjectNode evidence(JsonNode content,DocumentReadService.FixedRead fixed,Map<String,HumanEvidenceCitationReads.Citation> physical){
  var result=JsonNodeFactory.instance.objectNode().put("document_digest",fixed.header().bodySha256());var selected=result.putObject("evidence");
  for(var id:content.required("evidence_link_ids")){var proof=physical.get(id.asText());if(proof==null||!proof.located())fail(ErrorCode.CONTENT_MISMATCH);selected.set(id.asText(),json.tree(proof));}return result;
 }
 private void validateContent(String project,JsonNode content,DocumentReadService.FixedRead fixed){
  if(!project.equals(fixed.header().projectId()))fail(ErrorCode.ACCESS_DENIED);
  var declared=new HashSet<String>();content.required("evidence_link_ids").forEach(x->declared.add(x.asText()));if(content.has("value_evidence_link_ids"))for(var value:content.required("value_evidence_link_ids"))if(!declared.contains(value.asText()))fail(ErrorCode.CONTENT_MISMATCH);
  var links=new HashSet<String>();fixed.body().evidenceLinks().forEach(l->links.add(l.evidenceLinkId()));for(var id:content.required("evidence_link_ids"))if(!links.contains(id.asText()))fail(ErrorCode.CONTENT_MISMATCH);
 }
 private Map<String,Object> proposal(EnterpriseAccess.Admission a,String project,String id)throws Exception{
  var row=one(db.queryForList("SELECT * FROM shared_fact_proposal WHERE tenant_id=? AND project_id=? AND proposal_id=?",tenant(a),project,id));content(row);return row;
 }
 private JsonNode content(Map<String,Object> row)throws Exception{var result=json.read(s(row,"content_json"));if(!json.hash(result).equals(s(row,"content_sha256")))fail(ErrorCode.CONTENT_MISMATCH);return result;}
 private void currentProposal(EnterpriseAccess.Admission a,String project,Map<String,Object> row)throws Exception{
  if(!row.equals(proposal(a,project,s(row,"proposal_id"))))fail(ErrorCode.REVIEW_STALE);
  var h=one(db.queryForList("SELECT proposal_id FROM shared_fact_proposal_head WHERE tenant_id=? AND project_id=? AND fact_key=? FOR UPDATE",tenant(a),project,s(row,"fact_key")));if(!s(row,"proposal_id").equals(s(h,"proposal_id")))fail(ErrorCode.REVIEW_STALE);
 }
 private Map<String,Object> policy(EnterpriseAccess.Admission a,String project,String factClass)throws Exception{
  var row=one(db.queryForList("SELECT p.* FROM shared_fact_policy p JOIN shared_fact_policy_head h ON h.tenant_id=p.tenant_id AND h.project_id=p.project_id AND h.policy_id=p.policy_id WHERE p.tenant_id=? AND p.project_id=? AND h.fact_class=? FOR UPDATE",tenant(a),project,factClass));
  if(!json.hash(json.read(s(row,"content_json"))).equals(s(row,"content_sha256")))fail(ErrorCode.CONTENT_MISMATCH);return row;
 }
 private void checkHead(EnterpriseAccess.Admission a,String project,String kind,String keyColumn,String key,String expected){
  var rows=db.queryForList("SELECT revision FROM shared_fact_"+kind+"_head WHERE tenant_id=? AND project_id=? AND "+keyColumn+"=? FOR UPDATE",tenant(a),project,key);
  String revision=rows.isEmpty()?"0":s(one(rows),"revision");if(!revision.equals(expected))fail(ErrorCode.REVISION_CONFLICT);
 }
 private void head(EnterpriseAccess.Admission a,String project,String kind,String keyColumn,String key,String idColumn,String id,String expected){
  var now=permissions.databaseTime();if("0".equals(expected))write(db.update("INSERT INTO shared_fact_"+kind+"_head(tenant_id,project_id,"+keyColumn+","+idColumn+",revision,updated_at,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,1,?,'1.0.0',?,?,?,?)",tenant(a),project,key,id,now,codec.manifest,a.actorId(),now,classification(a,project)));
  else write(db.update("UPDATE shared_fact_"+kind+"_head SET "+idColumn+"=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND "+keyColumn+"=? AND revision=?",id,now,tenant(a),project,key,expected));
 }
 private JsonNode finish(EnterpriseAccess.Admission a,String project,String operation,String key,String hash,String id,String state,String previous)throws Exception{
  var result=JsonNodeFactory.instance.objectNode().put("request_id",id()).put("schema_version","1.0.0");result.putObject("data").put("id",id).put("status",state).put("revision",PermissionSchema.next(previous));
  write(db.update("INSERT INTO shared_fact_lifecycle_operation(tenant_id,project_id,actor_id,operation_name,idempotency_key,request_sha256,receipt_json,receipt_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,'1.0.0',?,?,?,?)",tenant(a),project,a.actorId(),operation,key,hash,json.canonical(result),json.hash(result),codec.manifest,a.actorId(),permissions.databaseTime(),classification(a,project)));
  var event=JsonNodeFactory.instance.objectNode().put("id",id).put("operation",operation).put("request_sha256",hash);
  write(permissions.saveAudit(a.actorId(),project,id(),"shared_fact.lifecycle.changed",json.canonical(event),t(result,"request_id"),codec.manifest,classification(a,project)));
  write(permissions.saveEvent(a.actorId(),id(),project,"1","shared_fact.lifecycle.changed",json.canonical(event)));
  write(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",tenant(a),a.watermarks().authEpoch(),a.watermarks().governanceEpoch()));return result;
 }
 private JsonNode replay(EnterpriseAccess.Admission a,String project,String operation,String key,String hash)throws Exception{
  var rows=db.queryForList("SELECT request_sha256,receipt_json,receipt_sha256 FROM shared_fact_lifecycle_operation WHERE tenant_id=? AND project_id=? AND actor_id=? AND operation_name=? AND idempotency_key=?",tenant(a),project,a.actorId(),operation,key);
  if(rows.isEmpty())return null;var row=one(rows);if(!hash.equals(s(row,"request_sha256")))fail(ErrorCode.IDEMPOTENCY_CONFLICT);var result=json.read(s(row,"receipt_json"));if(!json.hash(result).equals(s(row,"receipt_sha256")))fail(ErrorCode.CONTENT_MISMATCH);return result;
 }
 private String classification(EnterpriseAccess.Admission a,String project){
  var row=one(db.queryForList("SELECT classification FROM project WHERE tenant_id=? AND project_id=? FOR UPDATE",tenant(a),project));
  String value=s(row,"classification");if(!Set.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(value))fail(ErrorCode.CONTENT_MISMATCH);return value;
 }
 private void base(EnterpriseAccess.Admission a,String context,String project)throws Exception{if(!a.context().context_revision().equals(context))fail(ErrorCode.CONTEXT_CHANGED);grant(a,project,"fact.read");}
 private PermissionProtocol.GrantRow grant(EnterpriseAccess.Admission a,String project,String action)throws Exception{return resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,PURPOSE);}
 private <T>T tx(ApplicationSessionIdentity.Verified subject,ThrowingWork<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get());}
 @FunctionalInterface private interface ThrowingWork<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
 private static void keys(String context,String project,String key){PermissionSchema.revision(context);WireValues.uuid(project);if(key==null||key.isBlank()||key.length()>128)fail(ErrorCode.SCHEMA_INVALID);}
 private static boolean contains(JsonNode ids,String id){for(var item:ids)if(id.equals(item.asText()))return true;return false;}
 private static String tenant(EnterpriseAccess.Admission a){return a.context().tenant_id();}private static String id(){return UUID.randomUUID().toString();}
 private static String t(JsonNode n,String k){return n.required(k).asText();}private static String s(Map<String,Object> r,String k){return Objects.requireNonNull(r.get(k)).toString();}
 private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)fail(ErrorCode.REVIEW_STALE);return rows.get(0);}private static void write(int n){if(n!=1)fail(ErrorCode.REVISION_CONFLICT);}
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

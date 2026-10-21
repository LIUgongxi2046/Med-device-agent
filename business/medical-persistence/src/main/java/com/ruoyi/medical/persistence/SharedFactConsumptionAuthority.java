package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.ErrorCode;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** New-use gate only. VersionResourceAuthority remains the historical read authority. */
@Component
public final class SharedFactConsumptionAuthority {
 private final JdbcTemplate db;
 private final SharedFactScopeAuthority scopes;
 public SharedFactConsumptionAuthority(@org.springframework.beans.factory.annotation.Qualifier("masterDataSource") DataSource data,SharedFactScopeAuthority scopes){this.scopes=scopes;db=new JdbcTemplate(data);db.setQueryTimeout(5);}
 void requireConsumable(EnterpriseAccess.Admission a,String project,String version){
  var rows=db.queryForList("""
   SELECT f.model_ids,f.condition_id,f.content_sha256,p.content_sha256 AS proposal_sha256,v.content_sha256 AS registry_sha256,
          a.created_by AS adopter,p.created_by AS author,d.created_by AS verifier,f.verifier_id,
          a.proposal_id,a.policy_id,a.baseline_json,a.baseline_sha256,d.baseline_json AS verification_json,d.baseline_sha256 AS verification_sha256,
          p.content_json,pol.content_json AS policy_json,pol.content_sha256 AS policy_sha256
   FROM shared_fact_version f
   JOIN version_registry v ON v.tenant_id=f.tenant_id AND v.version_id=f.fact_version AND v.owner_project_id=f.project_id
   JOIN shared_fact_adoption a ON a.tenant_id=f.tenant_id AND a.project_id=f.project_id AND a.fact_version=f.fact_version
   JOIN shared_fact_adopted_head h ON h.tenant_id=f.tenant_id AND h.project_id=f.project_id AND h.fact_version=f.fact_version AND h.fact_key=f.fact_key
   JOIN shared_fact_proposal p ON p.tenant_id=a.tenant_id AND p.project_id=a.project_id AND p.proposal_id=a.proposal_id
   JOIN shared_fact_verification d ON d.tenant_id=a.tenant_id AND d.project_id=a.project_id AND d.verification_id=a.verification_id AND d.proposal_id=p.proposal_id AND d.policy_id=a.policy_id AND d.decision='SUPPORTS'
   JOIN shared_fact_policy pol ON pol.tenant_id=a.tenant_id AND pol.project_id=a.project_id AND pol.policy_id=a.policy_id
   JOIN shared_fact_policy_head ph ON ph.tenant_id=pol.tenant_id AND ph.project_id=pol.project_id AND ph.policy_id=pol.policy_id AND ph.fact_class=pol.fact_class
   WHERE f.tenant_id=? AND f.project_id=? AND f.fact_version=? AND f.status='ADOPTED' AND v.entity_kind='SHARED_FACT'
     AND NOT EXISTS(SELECT 1 FROM shared_fact_verification newer WHERE newer.tenant_id=d.tenant_id AND newer.project_id=d.project_id AND newer.proposal_id=d.proposal_id AND newer.policy_id=d.policy_id AND newer.verification_id<>d.verification_id AND newer.created_at>=d.created_at)
   """,a.context().tenant_id(),project,version);
  if(rows.size()!=1)fail();var r=rows.get(0);var json=new TaskSourceVerifierSchema();try{
   for(String[] pair:new String[][]{{"content_json","proposal_sha256"},{"baseline_json","baseline_sha256"},{"verification_json","verification_sha256"},{"policy_json","policy_sha256"}})if(!json.hash(json.read(r.get(pair[0]).toString())).equals(r.get(pair[1]).toString()))fail();
   if(!r.get("content_sha256").equals(r.get("proposal_sha256"))||!r.get("content_sha256").equals(r.get("registry_sha256"))||!r.get("verifier").equals(r.get("verifier_id"))||r.get("adopter").equals(r.get("author"))||r.get("adopter").equals(r.get("verifier"))||r.get("author").equals(r.get("verifier")))fail();
   var content=json.read(r.get("content_json").toString());var factScope=scopes.require(a,project,content);
   var adoptionScope=json.read(r.get("baseline_json").toString()).required("fact_scope");
   if(!factScope.witness().equals(adoptionScope)||!json.tree(factScope.modelIds()).equals(json.read(r.get("model_ids").toString()))||!java.util.Objects.equals(factScope.conditionId(),r.get("condition_id")))fail();
   var policy=json.read(r.get("policy_json").toString());if("EXACT_CHECKS".equals(policy.required("mode").asText())){
    var adoption=json.read(r.get("baseline_json").toString());var checkRows=db.queryForList("SELECT baseline_json,baseline_sha256 FROM shared_fact_check WHERE tenant_id=? AND project_id=? AND check_id=? AND proposal_id=? AND policy_id=? AND result='PASS'",a.context().tenant_id(),project,adoption.required("check_id").asText(),r.get("proposal_id"),r.get("policy_id"));
    if(checkRows.size()!=1)fail();var checkRow=checkRows.get(0);var check=json.read(checkRow.get("baseline_json").toString());if(!json.hash(check).equals(checkRow.get("baseline_sha256"))||!checkRow.get("baseline_sha256").equals(adoption.required("check_sha256").asText())||!check.required("fact_scope").equals(adoption.required("fact_scope"))||!check.required("evidence").equals(adoption.required("evidence"))||!check.required("proposal_sha256").asText().equals(r.get("proposal_sha256")))fail();
    var required=new java.util.HashSet<String>();policy.required("required_rule_versions").forEach(x->required.add(x.asText()));var actual=new java.util.HashSet<String>();for(var rule:check.required("checks")){if(!"PASS".equals(rule.required("evaluation").required("result").asText())||!actual.add(rule.required("rule_id").asText()))fail();var rules=db.queryForList("SELECT definition_sha256 FROM shared_fact_check_rule WHERE tenant_id=? AND project_id=? AND rule_id=?",a.context().tenant_id(),project,rule.required("rule_id").asText());if(rules.size()!=1||!rules.get(0).get("definition_sha256").equals(rule.required("rule_sha256").asText()))fail();}if(required.isEmpty()||!required.equals(actual))fail();
   }else if(!"HUMAN_ONLY".equals(policy.required("mode").asText())||!policy.required("required_rule_versions").isEmpty())fail();
  }catch(TenantAccessException e){throw e;}catch(Exception e){fail();}
 }
 private static void fail(){throw new TenantAccessException(ErrorCode.REVIEW_STALE);}
}

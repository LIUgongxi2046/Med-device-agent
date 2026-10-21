package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.mapper.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** TP06. Exhaustive tenant reverse graph, never a permission-filtered usages page. */
@Service
public final class VersionChangeImpactService {
 private static final int LIMIT=10000;
 private static final String PURPOSE="VERSION_CHANGE_IMPACT";
 private final JdbcTemplate db;private final EnterpriseAccess access;private final ResourceReadAuthority resources;
 private final DocumentReadService documents;private final PermissionMapper permissions;private final VersionResourceMapper owners;
 private final PermissionAuthority grants;private final com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities;
 private final SourceReadMapper sourceRows;private final SourceReadAuthority sources;private final DocumentUploadPolicy storage;
 private final TaskSourceVerifierSchema json=new TaskSourceVerifierSchema();private final String manifest;
 public VersionChangeImpactService(DataSource data,EnterpriseAccess access,ResourceReadAuthority resources,DocumentReadService documents,PermissionMapper permissions,VersionResourceMapper owners,SourceReadMapper sourceRows,SourceReadAuthority sources,DocumentUploadPolicy storage,PermissionAuthority grants,com.ruoyi.medical.identity.PlatformMemberIdentityStatus identities)throws Exception{
  db=new JdbcTemplate(data);db.setQueryTimeout(5);this.access=access;this.resources=resources;this.documents=documents;this.permissions=permissions;this.owners=owners;this.sourceRows=sourceRows;this.sources=sources;this.storage=storage;this.grants=grants;this.identities=identities;
  try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/version-change-impact.schema.json"))){manifest=WireValues.sha256(in.readAllBytes());}
 }
 public JsonNode propose(ApplicationSessionIdentity.Verified subject,String context,String key,byte[] bytes)throws Exception{
  var n=request(bytes,"project_id","kind","old_version","new_version","reviewer_id","reason");String project=t(n,"project_id");for(String f:List.of("project_id","old_version","new_version","reviewer_id"))WireValues.uuid(t(n,f));if(!Set.of("SOURCE","RULE","FACT").contains(t(n,"kind"))||t(n,"old_version").equals(t(n,"new_version")))bad();String hash=json.hash(n);
  return tx(subject,a->{base(a,context,project,"impact.propose");var prior=replay(a,key,hash,"PROPOSE");if(prior!=null)return prior;
   versionPair(a,project,t(n,"kind"),t(n,"old_version"),t(n,"new_version"));var member=permissions.targetMember(t(n,"reviewer_id"));if(member.size()!=1||!"ACTIVE".equals(member.get(0).status()))denied();String classification=pairClassification(a,project,t(n,"kind"),t(n,"old_version"),t(n,"new_version"));String id=id();var at=permissions.databaseTime();
   write(db.update("INSERT INTO change_proposal(tenant_id,proposal_id,old_version,new_version,reason,reviewer_id,status,created_by,created_at,classification,revision,updated_at) VALUES(?,?,?,?,?,?,'PROPOSED',?,?,?,1,?)",tenant(a),id,t(n,"old_version"),t(n,"new_version"),t(n,"reason"),t(n,"reviewer_id"),a.actorId(),at,classification,at));
   var row=meta(a,at,classification);row.putAll(Map.of("project_id",project,"proposal_id",id,"kind",t(n,"kind"),"proposal_json",json.canonical(n),"proposal_sha256",hash));insert("change_proposal_scope",row);
   return finish(a,key,hash,"PROPOSE",project,id,classification,json.tree(Map.of("proposal_id",id,"revision","1","status","PROPOSED")));
  });
 }
 public JsonNode scan(ApplicationSessionIdentity.Verified subject,String context,String proposal,String key,byte[] bytes)throws Exception{
  var n=request(bytes,"expected_revision");PermissionSchema.revision(t(n,"expected_revision"));String hash=json.hash(Map.of("proposal_id",proposal,"request",n));
  return tx(subject,a->{var p=proposal(a,proposal);base(a,context,s(p,"project_id"),"impact.scan");var prior=replay(a,key,hash,"SCAN");if(prior!=null)return prior;expected(p,n);if(!"PROPOSED".equals(s(p,"status")))stale();
   var snapshot=graph(a,p);String id=id(),digest=json.hash(snapshot);var at=permissions.databaseTime();
   insert("version_registry",Map.of("tenant_id",tenant(a),"owner_project_id",s(p,"project_id"),"version_id",id,"entity_kind","IMPACT_MANIFEST","content_sha256",digest));
   write(db.update("INSERT INTO impact_manifest(tenant_id,impact_id,proposal_id,governance_epoch,result_ref,complete,content_sha256,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,TRUE,?,'1.0.0',?,?,?,?)",tenant(a),id,proposal,a.watermarks().governanceEpoch(),json.canonical(snapshot),digest,manifest,a.actorId(),at,t(snapshot,"classification")));
   return finish(a,key,hash,"SCAN",s(p,"project_id"),id,t(snapshot,"classification"),json.tree(Map.of("proposal_id",proposal,"impact_id",id,"impact_sha256",digest,"complete",true,"snapshot",snapshot,"revision",s(p,"revision"))));
  });
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String proposal)throws Exception{
  WireValues.uuid(proposal);return tx(subject,a->{var p=proposal(a,proposal);base(a,context,s(p,"project_id"),"impact.read");var current=graph(a,p);var scans=db.queryForList("SELECT impact_id,content_sha256,governance_epoch,result_ref FROM impact_manifest WHERE tenant_id=? AND proposal_id=? ORDER BY impact_id LIMIT 1001",tenant(a),proposal);if(scans.size()>1000)incomplete();for(var scan:scans){var saved=json.read(s(scan,"result_ref"));if(!s(scan,"content_sha256").equals(json.hash(saved)))stale();authorizeSnapshot(a,saved);}var entries=bounded("SELECT e.entry_id,e.project_id,e.document_id,e.document_revision,e.requested_action,CASE WHEN EXISTS(SELECT 1 FROM change_impact_resolution r JOIN review_decision rv ON rv.tenant_id=r.tenant_id AND rv.project_id=r.project_id AND rv.batch_id=r.batch_id WHERE r.tenant_id=e.tenant_id AND r.entry_id=e.entry_id AND rv.target_key='DOCUMENT' AND rv.decision='CONFIRM' AND NOT EXISTS(SELECT 1 FROM review_invalidation i WHERE i.tenant_id=rv.tenant_id AND i.project_id=rv.project_id AND i.review_id=rv.review_id)) THEN 'REVALIDATED' ELSE e.state END AS state,e.reason FROM change_revision_entry e JOIN change_impact_decision d ON d.tenant_id=e.tenant_id AND d.decision_id=e.decision_id WHERE e.tenant_id=? AND d.proposal_id=? ORDER BY e.entry_id LIMIT 10001",tenant(a),proposal);return json.tree(Map.of("proposal",json.read(s(p,"proposal_json")),"proposal_id",proposal,"status",s(p,"status"),"revision",s(p,"revision"),"current_snapshot",current,"scans",scans,"revision_entries",entries));});
 }
 public JsonNode decide(ApplicationSessionIdentity.Verified subject,String context,String proposal,String key,byte[] bytes)throws Exception{
  var n=request(bytes,"expected_revision","impact_id","impact_sha256","decision","reason","targets");WireValues.uuid(t(n,"impact_id"));WireValues.hash(t(n,"impact_sha256"));PermissionSchema.revision(t(n,"expected_revision"));if(!Set.of("ACCEPT","REJECT").contains(t(n,"decision"))||!n.required("targets").isArray())bad();String hash=json.hash(Map.of("proposal_id",proposal,"request",n));
  return tx(subject,a->{var p=proposal(a,proposal);var grant=base(a,context,s(p,"project_id"),"impact.decide");var prior=replay(a,key,hash,"DECIDE");if(prior!=null)return prior;expected(p,n);if(!"PROPOSED".equals(s(p,"status"))||!a.actorId().equals(s(p,"reviewer_id")))denied();
   var impact=impact(a,p,t(n,"impact_id"),t(n,"impact_sha256"));var snapshot=json.read(s(impact,"result_ref"));same(snapshot,graph(a,p));if(!s(impact,"governance_epoch").equals(a.watermarks().governanceEpoch().toString()))stale();
   validateTargets(a,snapshot,n.required("targets"),"ACCEPT".equals(t(n,"decision")));String id=id();var at=permissions.databaseTime();var row=meta(a,at,t(snapshot,"classification"));row.putAll(Map.of("decision_id",id,"project_id",s(p,"project_id"),"proposal_id",proposal,"impact_id",t(n,"impact_id"),"decision",t(n,"decision"),"decision_json",json.canonical(n),"decision_sha256",json.hash(n),"auth_epoch",a.watermarks().authEpoch(),"governance_epoch",a.watermarks().governanceEpoch()));row.put("authority_json",json.canonical(AgentPublicationWitness.tree(grant)));row.put("identity_mode",a.bindingView().mode());insert("change_impact_decision",row);
   write(db.update("UPDATE change_proposal SET status=?,revision=revision+1,updated_at=? WHERE tenant_id=? AND proposal_id=? AND revision=? AND status='PROPOSED'",t(n,"decision").equals("ACCEPT")?"ACCEPTED":"REJECTED",at,tenant(a),proposal,t(n,"expected_revision")));
   // Fact invalidation is delayed until the same transaction that advances its adopted head.
   if("ACCEPT".equals(t(n,"decision"))&&!"FACT".equals(s(p,"kind"))){apply(a,p,id,snapshot,n);write(db.update("UPDATE tenant SET governance_epoch=governance_epoch+1 WHERE tenant_id=? AND auth_epoch=? AND governance_epoch=?",tenant(a),a.watermarks().authEpoch(),a.watermarks().governanceEpoch()));}
   return finish(a,key,hash,"DECIDE",s(p,"project_id"),id,t(snapshot,"classification"),json.tree(Map.of("proposal_id",proposal,"decision_id",id,"decision",t(n,"decision"),"revision",PermissionSchema.next(t(n,"expected_revision")),"state","ACCEPT".equals(t(n,"decision"))?"PENDING_CONFIRMATION":"REJECTED","version_transition_performed",false,"targets",n.required("targets"))));
  });
 }
 /** Called only under B05's already locked tenant transaction, before its fact head CAS. */
 JsonNode applyFactReplacement(EnterpriseAccess.Admission a,String project,String oldVersion,String newProposal,String newHash)throws Exception{
  StrictComparisonAuthority.human(a,a.context().context_revision());base(a,a.context().context_revision(),project,"impact.apply");
  // The latest explicit acceptance wins; never fall back to an older decision when it is stale.
  var rows=db.queryForList("SELECT p.proposal_id FROM change_proposal p JOIN change_proposal_scope s ON s.tenant_id=p.tenant_id AND s.proposal_id=p.proposal_id JOIN change_impact_decision d ON d.tenant_id=p.tenant_id AND d.proposal_id=p.proposal_id AND d.decision='ACCEPT' WHERE p.tenant_id=? AND s.project_id=? AND s.kind='FACT' AND p.old_version=? AND p.new_version=? AND p.status='ACCEPTED' ORDER BY d.created_at DESC,d.decision_id DESC LIMIT 1 FOR UPDATE",tenant(a),project,oldVersion,newProposal);var p=proposal(a,s(one(rows),"proposal_id"));
  var proposed=one(db.queryForList("SELECT content_sha256 FROM shared_fact_proposal WHERE tenant_id=? AND project_id=? AND proposal_id=?",tenant(a),project,newProposal));if(!newHash.equals(s(proposed,"content_sha256")))stale();
  var d=one(db.queryForList("SELECT * FROM change_impact_decision WHERE tenant_id=? AND proposal_id=? AND decision='ACCEPT' FOR UPDATE",tenant(a),s(p,"proposal_id")));if(!a.watermarks().authEpoch().toString().equals(s(d,"auth_epoch"))||!a.watermarks().governanceEpoch().toString().equals(s(d,"governance_epoch")))stale();
  var request=json.read(s(d,"decision_json"));if(!json.hash(request).equals(s(d,"decision_sha256")))stale();var i=impact(a,p,s(d,"impact_id"),t(request,"impact_sha256"));var snapshot=json.read(s(i,"result_ref"));same(snapshot,graph(a,p));
  // Reviewer remains a current active member; auth epoch binds its decision to the same grants.
  var member=permissions.targetMember(s(d,"created_by"));if(member.size()!=1||!"ACTIVE".equals(member.get(0).status())||!s(p,"reviewer_id").equals(s(d,"created_by")))denied();
  reviewerCurrent(a,p,d);validateTargets(a,snapshot,request.required("targets"),true);apply(a,p,s(d,"decision_id"),snapshot,request);
  return json.tree(Map.of("change_proposal_id",s(p,"proposal_id"),"impact_id",s(d,"impact_id"),"impact_sha256",t(request,"impact_sha256"),"decision_id",s(d,"decision_id"),"snapshot",snapshot,"state","PENDING_CONFIRMATION"));
 }
 private JsonNode graph(EnterpriseAccess.Admission a,Map<String,Object> p)throws Exception{
  String tenant=tenant(a),root=s(p,"old_version"),classification=s(p,"classification");versionPair(a,s(p,"project_id"),s(p,"kind"),root,s(p,"new_version"));
  // No user/project visibility predicate. Capacity overflow aborts, never returns a partial complete=true.
  var edges=bounded("SELECT consumer_version_id,upstream_version_id,relation_kind FROM lineage_edge WHERE tenant_id=? ORDER BY consumer_version_id,upstream_version_id,relation_kind LIMIT 10001 FOR UPDATE",tenant);
  var uses=bounded("SELECT project_id,usage_id,fact_version,document_revision,node_id,field_id,row_id FROM fact_usage WHERE tenant_id=? ORDER BY project_id,usage_id LIMIT 10001 FOR UPDATE",tenant);
  var evidence=bounded("SELECT e.project_id,e.evidence_id,e.source_version,e.parse_version,e.approval_revision,e.document_revision,c.node_id,c.field_id,c.row_id FROM evidence_link e JOIN claim c ON c.tenant_id=e.tenant_id AND c.project_id=e.project_id AND c.document_revision=e.document_revision AND c.claim_id=e.claim_id WHERE e.tenant_id=? ORDER BY e.project_id,e.evidence_id LIMIT 10001 FOR UPDATE",tenant);
  var fields=bounded("SELECT project_id,document_revision,node_id,field_id,row_id,fact_version FROM field_value WHERE tenant_id=? AND fact_version IS NOT NULL ORDER BY project_id,document_revision,node_id,field_id,row_key LIMIT 10001 FOR UPDATE",tenant);
  var reverse=new TreeMap<String,Set<String>>();for(var e:edges)edge(reverse,s(e,"upstream_version_id"),s(e,"consumer_version_id"));for(var u:uses)edge(reverse,s(u,"fact_version"),s(u,"document_revision"));for(var e:evidence)for(String k:List.of("source_version","parse_version","approval_revision"))edge(reverse,s(e,k),s(e,"document_revision"));for(var f:fields)edge(reverse,s(f,"fact_version"),s(f,"document_revision"));
  var reached=new TreeSet<String>();var queue=new ArrayDeque<String>();reached.add(root);queue.add(root);while(!queue.isEmpty()){for(String child:reverse.getOrDefault(queue.removeFirst(),Set.of()))if(reached.add(child)){if(reached.size()>LIMIT)incomplete();queue.add(child);}}
  // Cyclic or orphaned provenance is not a complete graph.
  var degrees=new TreeMap<String,Integer>();reached.forEach(v->degrees.put(v,0));for(String parent:reached)for(String child:reverse.getOrDefault(parent,Set.of()))if(reached.contains(child))degrees.merge(child,1,Integer::sum);var ready=new ArrayDeque<String>();degrees.forEach((v,d)->{if(d==0)ready.add(v);});int visited=0;while(!ready.isEmpty()){String v=ready.remove();visited++;for(String child:reverse.getOrDefault(v,Set.of()))if(degrees.computeIfPresent(child,(k,d)->d-1)==0)ready.add(child);}if(visited!=reached.size())incomplete();
  var versions=new ArrayList<Map<String,Object>>();var docIds=new TreeSet<String>();var legalSources=new TreeSet<String>();
  for(String v:reached){var registered=one(db.queryForList("SELECT version_id,owner_project_id,entity_kind,content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=?",tenant,v));versions.add(registered);var resolved=owners.ownersAcrossDomains(List.of(v),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());if(resolved.size()!=1)incomplete();var o=resolved.get(0).owner();if(!tenant.equals(o.tenantId())||!v.equals(o.versionId())||!Objects.equals(registered.get("owner_project_id"),o.versionProjectId())||o.contentSha256()!=null&&!s(registered,"content_sha256").equals(o.contentSha256()))incomplete();
   if(o.conversationId()!=null)incomplete();String action=VersionResourceAuthority.action(VersionResourceProtocol.Domain.valueOf(resolved.get(0).domain()),o.resourceKind());classification=maxClassification(classification,versionClassification(a,resolved.get(0)));resources.require(a,new PermissionProtocol.Scope(tenant,o.resourceProjectId(),o.resourceKind(),o.resourceId()),action,PURPOSE);
   if(o.sourceVersion()!=null)legalSources.add(o.sourceVersion());if("DOCUMENT_REVISION".equals(resolved.get(0).domain()))docIds.add(v);
  }
  var prerequisites=new TreeSet<String>(reached);var ancestors=new ArrayDeque<String>(reached);while(!ancestors.isEmpty()){String consumer=ancestors.removeFirst();for(var relation:edges)if(consumer.equals(s(relation,"consumer_version_id"))&&prerequisites.add(s(relation,"upstream_version_id"))){if(prerequisites.size()>LIMIT)incomplete();ancestors.add(s(relation,"upstream_version_id"));}}
  for(String ancestor:prerequisites){var matches=sourceRows.source(ancestor);if(matches.size()>1)incomplete();if(matches.size()==1)legalSources.add(ancestor);}
  var sourceProofs=new ArrayList<JsonNode>();for(String source:legalSources){var legal=legalSource(a,source);sourceProofs.add(legal);classification=maxClassification(classification,t(legal,"classification"));}
  var locations=new ArrayList<Map<String,Object>>();for(var u:uses)if(reached.contains(s(u,"fact_version"))){position(tenant,u);var location=new LinkedHashMap<>(u);location.put("relation_kind","FACT_USAGE");location.put("direct",root.equals(s(u,"fact_version")));locations.add(location);docIds.add(s(u,"document_revision"));}
  for(var f:fields)if(reached.contains(s(f,"fact_version"))){boolean found=uses.stream().anyMatch(u->List.of("project_id","document_revision","node_id","field_id","row_id","fact_version").stream().allMatch(k->Objects.equals(f.get(k),u.get(k))));if(!found)incomplete();}
  for(var e:evidence)if(List.of("source_version","parse_version","approval_revision").stream().anyMatch(k->reached.contains(s(e,k)))){position(tenant,e);var location=new LinkedHashMap<>(e);location.put("relation_kind","EVIDENCE_LINK");location.put("direct",List.of("source_version","parse_version","approval_revision").stream().anyMatch(k->root.equals(s(e,k))));locations.add(location);docIds.add(s(e,"document_revision"));}
  var docs=new ArrayList<JsonNode>();for(String revision:docIds){var d=one(db.queryForList("SELECT d.project_id,d.document_id,d.document_revision,d.body_sha256,h.current_document_revision,h.task_id,h.revision AS head_revision,h.status,h.classification,s.issue_watermark,s.evidence_watermark,s.revision AS state_revision FROM document_revision d JOIN document_head h ON h.tenant_id=d.tenant_id AND h.project_id=d.project_id AND h.document_id=d.document_id JOIN document_revision_state s ON s.tenant_id=d.tenant_id AND s.project_id=d.project_id AND s.document_revision=d.document_revision WHERE d.tenant_id=? AND d.document_revision=? FOR UPDATE",tenant,revision));
   // Current legal access validates the entire fixed document dependency set, not just the changed source.
   documents.authorizeMetadata(a,a.context().context_revision(),s(d,"document_id"),revision);classification=maxClassification(classification,s(d,"classification"));var doc=(ObjectNode)json.tree(d);doc.set("review_decisions",json.tree(bounded("SELECT review_id,decision FROM review_decision WHERE tenant_id=? AND project_id=? AND document_revision=? ORDER BY review_id LIMIT 10001 FOR UPDATE",tenant,s(d,"project_id"),revision)));docs.add(doc);
   if(locations.stream().noneMatch(l->revision.equals(s(l,"document_revision")))){var l=new LinkedHashMap<String,Object>();l.put("project_id",s(d,"project_id"));l.put("document_revision",revision);l.put("relation_kind","DOCUMENT_DEPENDENCY");l.put("node_id",null);l.put("field_id",null);l.put("row_id",null);l.put("direct",reverse.getOrDefault(root,Set.of()).contains(revision));locations.add(l);}
  }
  // Hash the actual complete backing relation set, but disclose only affected authorized targets.
  var result=(ObjectNode)json.tree(Map.of("schema_version","1.0.0","complete",true,"proposal_sha256",s(p,"proposal_sha256"),"old_version",root,"new_version",s(p,"new_version"),"graph_watermark",json.hash(Map.of("lineage",edges,"fact_usage",uses,"evidence",evidence,"fact_fields",fields)),"versions",versions,"documents",docs,"locations",locations,"source_legality",sourceProofs));
  result.put("classification",classification);result.put("auth_epoch",a.watermarks().authEpoch().toString());return result;
 }
 private void position(String tenant,Map<String,Object> p){
  var node=db.queryForList("SELECT node_id FROM document_node WHERE tenant_id=? AND project_id=? AND document_revision=? AND node_id=?",tenant,s(p,"project_id"),s(p,"document_revision"),s(p,"node_id"));if(node.size()!=1)incomplete();
  if(p.get("field_id")!=null){var fields=db.queryForList("SELECT field_id FROM field_value WHERE tenant_id=? AND project_id=? AND document_revision=? AND node_id=? AND field_id=? AND row_key=?",tenant,s(p,"project_id"),s(p,"document_revision"),s(p,"node_id"),p.get("field_id"),p.get("row_id")==null?"SCALAR":p.get("row_id"));if(fields.size()!=1)incomplete();}
 }
 private JsonNode legalSource(EnterpriseAccess.Admission a,String version)throws Exception{
  storage.requireConfigured();var rows=sourceRows.source(version);if(rows.size()!=1)incomplete();var source=rows.get(0);var verified=sources.require(a,new SourceReadProtocol.ReadRequirement(version,source.rightsRevision(),source.ownerProjectId(),null,null,null,SourceReadProtocol.Use.REVIEW,PURPOSE,storage.environment(),storage.region(),null));
  return json.tree(Map.of("classification",maxClassification(source.classification(),verified.rights().classification()),"source_version",version,"rights_revision",verified.rights().rightsRevision(),"rights_sha256",verified.rights().contentSha256(),"gates",sourceRows.gates(version)));
 }
 private void versionPair(EnterpriseAccess.Admission a,String project,String kind,String old,String next)throws Exception{
  if("FACT".equals(kind)){
   var fact=one(db.queryForList("SELECT f.fact_key,f.content_sha256 FROM shared_fact_version f JOIN version_registry v ON v.tenant_id=f.tenant_id AND v.version_id=f.fact_version AND v.content_sha256=f.content_sha256 AND v.entity_kind='SHARED_FACT' WHERE f.tenant_id=? AND f.project_id=? AND f.fact_version=?",tenant(a),project,old));
   var proposal=one(db.queryForList("SELECT p.fact_key,p.content_sha256 FROM shared_fact_proposal p JOIN version_registry v ON v.tenant_id=p.tenant_id AND v.version_id=p.proposal_id AND v.content_sha256=p.content_sha256 AND v.entity_kind='SHARED_FACT_PROPOSAL' WHERE p.tenant_id=? AND p.project_id=? AND p.proposal_id=?",tenant(a),project,next));if(!s(fact,"fact_key").equals(s(proposal,"fact_key")))bad();resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),"fact.read",PURPOSE);
  }else if("SOURCE".equals(kind)){legalSource(a,old);legalSource(a,next);var x=sourceRows.source(old).get(0);var y=sourceRows.source(next).get(0);if(!x.sourceId().equals(y.sourceId()))bad();}
  else if("RULE".equals(kind)){for(String v:List.of(old,next)){var r=one(db.queryForList("SELECT r.content_sha256,r.status FROM rule_version r JOIN version_registry v ON v.tenant_id=r.tenant_id AND v.version_id=r.rule_version AND v.content_sha256=r.content_sha256 WHERE r.tenant_id=? AND r.rule_version=?",tenant(a),v));if(!"APPROVED".equals(s(r,"status")))stale();resources.require(a,new PermissionProtocol.Scope(tenant(a),null,"RULE",v),"rule.read",PURPOSE);}}
  else bad();
 }
 private void validateTargets(EnterpriseAccess.Admission a,JsonNode snapshot,JsonNode decisions,boolean accept)throws Exception{
  if(!decisions.isArray()||decisions.size()>LIMIT)bad();if(!accept){if(!decisions.isEmpty())bad();return;}var expected=new TreeMap<String,JsonNode>();for(var d:snapshot.required("documents"))expected.put(t(d,"document_revision"),d);var actual=new TreeSet<String>();for(var d:decisions){fields(d,"document_revision","action","reason");String revision=t(d,"document_revision");WireValues.uuid(revision);if(!actual.add(revision)||!expected.containsKey(revision)||!Set.of("NEW_REVISION","REVALIDATE").contains(t(d,"action")))bad();var target=expected.get(revision);if("SIGNED".equals(t(target,"status"))&&!"NEW_REVISION".equals(t(d,"action")))bad();resources.require(a,new PermissionProtocol.Scope(tenant(a),t(target,"project_id"),"DOCUMENT",t(target,"document_id")),"document.write",PURPOSE);}if(!actual.equals(expected.keySet()))incomplete();
 }
 private void apply(EnterpriseAccess.Admission a,Map<String,Object> p,String decision,JsonNode snapshot,JsonNode request)throws Exception{
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())stale();if(!db.queryForList("SELECT decision_id FROM change_impact_application WHERE tenant_id=? AND decision_id=? FOR UPDATE",tenant(a),decision).isEmpty())stale();
  var at=permissions.databaseTime();var invalidations=new ArrayList<String>();for(var doc:snapshot.required("documents")){String project=t(doc,"project_id"),revision=t(doc,"document_revision");var target=java.util.stream.StreamSupport.stream(request.required("targets").spliterator(),false).filter(n->revision.equals(t(n,"document_revision"))).findFirst().orElseThrow();
   var reviews=db.queryForList("SELECT review_id FROM review_decision WHERE tenant_id=? AND project_id=? AND document_revision=? AND decision='CONFIRM' ORDER BY review_id FOR UPDATE",tenant(a),project,revision);
   for(var review:reviews){String reviewId=s(review,"review_id");write(db.update("INSERT INTO review_invalidation(tenant_id,project_id,invalidation_id,review_id,trigger_version,reason_code,created_by,created_at,classification) VALUES(?,?,?,?,?,'VERSION_CHANGE_IMPACT',?,?,?)",tenant(a),project,id(),reviewId,t(request,"impact_id"),a.actorId(),at,t(doc,"classification")));invalidations.add(reviewId);}
   var entry=meta(a,at,t(doc,"classification"));entry.putAll(Map.of("entry_id",id(),"decision_id",decision,"project_id",project,"document_id",t(doc,"document_id"),"document_revision",revision,"requested_action",t(target,"action"),"state","PENDING_CONFIRMATION","reason",t(target,"reason")));insert("change_revision_entry",entry);
   // Reopen only the current unsigned task's confirmation stage. Never rewrite signed history,
   // returned work, a newer revision, or any source/rule legal authority.
   if(revision.equals(t(doc,"current_document_revision"))&&!"SIGNED".equals(t(doc,"status"))){var tasks=db.queryForList("SELECT status,revision FROM task_head WHERE tenant_id=? AND project_id=? AND task_id=? FOR UPDATE",tenant(a),project,t(doc,"task_id"));var task=one(tasks);if("SIGN_PENDING".equals(s(task,"status")))write(db.update("UPDATE task_head SET status='REVIEW_PENDING',revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND task_id=? AND revision=? AND status='SIGN_PENDING'",at,tenant(a),project,t(doc,"task_id"),s(task,"revision")));}

  }
  var row=meta(a,at,t(snapshot,"classification"));row.putAll(Map.of("decision_id",decision,"proposal_id",s(p,"proposal_id"),"impact_id",t(request,"impact_id"),"invalidations_json",json.canonical(invalidations),"snapshot_sha256",json.hash(snapshot)));insert("change_impact_application",row);
  var event=json.tree(Map.of("proposal_id",s(p,"proposal_id"),"decision_id",decision,"impact_id",t(request,"impact_id"),"invalidated_review_ids",invalidations,"state","PENDING_CONFIRMATION"));write(permissions.saveAudit(a.actorId(),s(p,"project_id"),id(),"version_change.applied",json.canonical(event),id(),manifest,t(snapshot,"classification")));write(permissions.saveEvent(a.actorId(),id(),decision,"1","version_change.applied",json.canonical(event)));
  // No document bytes, signed revision, source rights, approval policy or source/rule heads are changed.
 }
 private String pairClassification(EnterpriseAccess.Admission a,String project,String kind,String old,String next){
  String classification=s(one(db.queryForList("SELECT classification FROM project WHERE tenant_id=? AND project_id=?",tenant(a),project)),"classification");
  if("SOURCE".equals(kind)){for(String id:List.of(old,next)){var rows=sourceRows.source(id);if(rows.size()!=1)incomplete();classification=maxClassification(classification,rows.get(0).classification());}}
  else {String table="FACT".equals(kind)?"shared_fact_version":"rule_version",column="FACT".equals(kind)?"fact_version":"rule_version";classification=maxClassification(classification,s(one(db.queryForList("SELECT classification FROM "+table+" WHERE tenant_id=? AND "+column+"=?",tenant(a),old)),"classification"));if("FACT".equals(kind)){table="shared_fact_proposal";column="proposal_id";}classification=maxClassification(classification,s(one(db.queryForList("SELECT classification FROM "+table+" WHERE tenant_id=? AND "+column+"=?",tenant(a),next)),"classification"));}
  return classification;
 }
 private String versionClassification(EnterpriseAccess.Admission a,VersionResourceProtocol.DomainOwnerRow domain){
  var o=domain.owner();if(o.sourceVersion()!=null){var source=sourceRows.source(o.sourceVersion());if(source.size()!=1)incomplete();return maxClassification(source.get(0).classification());}
  String[] table=switch(domain.domain()){
   case "STRICT_COMPARISON_POLICY" -> new String[]{"strict_comparison_policy_revision","revision_id"};
   case "STRICT_COMPARISON_DECISION" -> new String[]{"strict_comparison_policy_decision","revision_id"};
   case "STRICT_DECIMAL_EVIDENCE" -> new String[]{"strict_decimal_evidence_revision","revision_id"};
   case "STRICT_FIELD_CONTEXT" -> new String[]{"task_strict_field_context_revision","revision_id"};
   case "STRICT_FIELD_CONTEXT_DECISION" -> new String[]{"task_strict_field_context_decision","revision_id"};
   case "STRICT_FIELD_TYPED_TARGET" -> new String[]{"task_strict_field_typed_revision","revision_id"};
   case "STRICT_FIELD_TYPED_TARGET_DECISION" -> new String[]{"task_strict_field_typed_decision","revision_id"};
   case "STRICT_FIELD_RULE_SET" -> new String[]{"task_strict_field_rule_set","revision_id"};
   case "AGENT_EXECUTION_POLICY" -> new String[]{"agent_execution_policy_version","version_id"};
   case "SOURCE_EXACT_REVISION" -> new String[]{"source_exact_revision","field_revision"};
   case "EXACT_RETRIEVAL" -> new String[]{"source_exact_query","retrieval_id"};
   case "SOURCE_VERSION" -> new String[]{"source_version","source_version"};
   case "PARSE_VERSION" -> new String[]{"parse_version","parse_version"};
   case "INDEX_MANIFEST" -> new String[]{"index_manifest","index_version"};
   case "APPROVAL_DECISION" -> new String[]{"approval_decision","approval_revision"};
   case "RIGHTS_REVISION" -> new String[]{"rights_revision","rights_revision"};
   case "SOURCE_BINDING" -> new String[]{"project_source_binding","binding_revision"};
   case "COLLECTION_VERSION" -> new String[]{"collection_version","collection_version"};
   case "PROJECT_PROFILE" -> new String[]{"project_profile_revision","profile_revision"};
   case "SCOPE_SNAPSHOT" -> new String[]{"scope_snapshot","snapshot_id"};
   case "TEMPLATE_VERSION" -> new String[]{"template_version","template_version"};
   case "RULE_VERSION" -> new String[]{"rule_version","rule_version"};
   case "TASK_REVISION" -> new String[]{"task_revision","task_revision"};
   case "PLAN_REVISION" -> new String[]{"plan_revision","plan_revision"};
   case "DOCUMENT_REVISION" -> new String[]{"document_revision_meta","document_revision"};
   case "SECTION_REVISION" -> new String[]{"section_revision","section_revision"};
   case "SHARED_FACT" -> new String[]{"shared_fact_version","fact_version"};
   case "ARTIFACT" -> new String[]{"artifact","artifact_id"};
   case "DEPENDENCY_ACCESS_SET" -> new String[]{"dependency_access_set","access_set_id"};
   case "CONTEXT_MANIFEST" -> new String[]{"context_manifest","context_manifest_id"};
   case "CANDIDATE" -> new String[]{"candidate","candidate_id"};
   case "CAPABILITY_VERSION" -> new String[]{"capability_version","capability_version"};
   case "MODEL_CONFIG" -> new String[]{"model_config_revision","model_config_revision"};
   case "AGENT_VERSION" -> new String[]{"agent_version","agent_version_id"};
   case "RELEASE_RECORD" -> new String[]{"release_record","release_id"};
   case "RELEASE_SCOPE" -> new String[]{"release_scope","scope_revision"};
   case "CONNECTOR_CONFIG" -> new String[]{"connector_config","config_revision"};
   case "EVALUATION" -> new String[]{"evaluation_batch","evaluation_id"};
   case "TASK_SUMMARY" -> new String[]{"task_summary","summary_id"};
   case "BUDGET_REVISION" -> new String[]{"budget_revision","budget_revision"};
   case "CONVERSATION_MESSAGE" -> new String[]{"conversation_message","message_id"};
   case "ROLE_DEFINITION" -> new String[]{"enterprise_role_version","version_id"};
   case "SIGNATURE" -> new String[]{"signature_record","signature_id"};
   case "EXPORT_MANIFEST" -> new String[]{"export_manifest","export_id"};
   case "MANAGEMENT_RELEASE" -> new String[]{"management_binding_release","release_id"};
   case "IMPACT_MANIFEST" -> new String[]{"impact_manifest","impact_id"};
   case "APPLICABILITY_DECISION" -> new String[]{"applicability_decision","decision_id"};
   default -> throw new TenantAccessException(ErrorCode.IMPACT_INCOMPLETE);
  };
  return maxClassification(s(one(db.queryForList("SELECT classification FROM "+table[0]+" WHERE tenant_id=? AND "+table[1]+"=?",tenant(a),o.versionId())),"classification"));
 }
 private static String maxClassification(String... values){var levels=List.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH");int max=0;for(String v:values){int index=levels.indexOf(v);if(index<0)incomplete();max=Math.max(max,index);}return levels.get(max);}
 private void reviewerCurrent(EnterpriseAccess.Admission a,Map<String,Object> p,Map<String,Object> decision)throws Exception{
  String actor=s(decision,"created_by"),mode=s(decision,"identity_mode");var members=permissions.targetMember(actor);var bindings=permissions.targetBinding(actor);var now=permissions.databaseTime();if(members.size()!=1||bindings.size()!=1)denied();var member=members.get(0);var binding=bindings.get(0);
  if(!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now)||!"ACTIVE".equals(binding.status())||!mode.equals(binding.mode())||!identities.active(new com.ruoyi.medical.identity.AuthenticatedIdentityBinding.View(mode,actor,binding.issuer(),binding.subject())))denied();
  var original=json.read(s(decision,"authority_json"));var current=permissions.currentGrant(t(original,"grant_id"));if(current.size()!=1||!AgentPublicationWitness.tree(current.get(0)).equals(original))stale();grants.requireResource(new PermissionProtocol.Scope(tenant(a),s(p,"project_id"),"PROJECT",s(p,"project_id")),actor,mode,"impact.decide",PURPOSE,current,now);
 }
 private Map<String,Object> proposal(EnterpriseAccess.Admission a,String id)throws Exception{WireValues.uuid(id);var p=one(db.queryForList("SELECT p.*,s.project_id,s.kind,s.proposal_json,s.proposal_sha256 FROM change_proposal p JOIN change_proposal_scope s ON s.tenant_id=p.tenant_id AND s.proposal_id=p.proposal_id WHERE p.tenant_id=? AND p.proposal_id=? FOR UPDATE",tenant(a),id));var n=json.read(s(p,"proposal_json"));if(!json.hash(n).equals(s(p,"proposal_sha256"))||!t(n,"old_version").equals(s(p,"old_version"))||!t(n,"new_version").equals(s(p,"new_version"))||!t(n,"reviewer_id").equals(s(p,"reviewer_id"))||!t(n,"reason").equals(s(p,"reason"))||!t(n,"kind").equals(s(p,"kind"))||!t(n,"project_id").equals(s(p,"project_id")))stale();return p;}
 private Map<String,Object> impact(EnterpriseAccess.Admission a,Map<String,Object> p,String id,String hash)throws Exception{var r=one(db.queryForList("SELECT i.* FROM impact_manifest i JOIN version_registry v ON v.tenant_id=i.tenant_id AND v.version_id=i.impact_id AND v.content_sha256=i.content_sha256 WHERE i.tenant_id=? AND i.proposal_id=? AND i.impact_id=? AND i.complete=TRUE FOR UPDATE",tenant(a),s(p,"proposal_id"),id));if(!manifest.equals(s(r,"schema_manifest_sha256"))||!hash.equals(s(r,"content_sha256"))||!hash.equals(json.hash(json.read(s(r,"result_ref")))))stale();return r;}
 private PermissionProtocol.GrantRow base(EnterpriseAccess.Admission a,String context,String project,String action)throws Exception{StrictComparisonAuthority.human(a,context);WireValues.uuid(project);return resources.require(a,new PermissionProtocol.Scope(tenant(a),project,"PROJECT",project),action,PURPOSE);}
 private JsonNode finish(EnterpriseAccess.Admission a,String key,String hash,String operation,String project,String id,String classification,JsonNode receipt)throws Exception{var at=permissions.databaseTime();var row=meta(a,at,classification);row.putAll(Map.of("actor_id",a.actorId(),"operation_name",operation,"idempotency_key",key,"request_sha256",hash,"receipt_json",json.canonical(receipt),"receipt_sha256",json.hash(receipt)));insert("change_impact_operation",row);write(permissions.saveAudit(a.actorId(),project,id(),"version_change."+operation.toLowerCase(Locale.ROOT),json.canonical(receipt),id(),manifest,classification));write(permissions.saveEvent(a.actorId(),id(),id,"1","version_change."+operation.toLowerCase(Locale.ROOT),json.canonical(receipt)));return receipt;}
 private JsonNode replay(EnterpriseAccess.Admission a,String key,String hash,String operation)throws Exception{if(key==null||key.isBlank()||key.length()>128)bad();var rows=db.queryForList("SELECT request_sha256,receipt_json,receipt_sha256 FROM change_impact_operation WHERE tenant_id=? AND actor_id=? AND operation_name=? AND idempotency_key=? FOR UPDATE",tenant(a),a.actorId(),operation,key);if(rows.isEmpty())return null;var r=one(rows);if(!hash.equals(s(r,"request_sha256")))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);var n=json.read(s(r,"receipt_json"));if(!json.hash(n).equals(s(r,"receipt_sha256")))stale();if(n.has("snapshot"))authorizeSnapshot(a,n.required("snapshot"));if(n.has("targets"))for(var target:n.required("targets")){var d=one(db.queryForList("SELECT document_id FROM document_revision WHERE tenant_id=? AND document_revision=?",tenant(a),t(target,"document_revision")));documents.authorizeMetadata(a,a.context().context_revision(),s(d,"document_id"),t(target,"document_revision"));}return n;}
 private void authorizeSnapshot(EnterpriseAccess.Admission a,JsonNode snapshot)throws Exception{
  for(var d:snapshot.required("documents"))documents.authorizeMetadata(a,a.context().context_revision(),t(d,"document_id"),t(d,"document_revision"));
  for(var v:snapshot.required("versions")){var matches=owners.ownersAcrossDomains(List.of(t(v,"version_id")),Arrays.stream(VersionResourceProtocol.Domain.values()).map(Enum::name).toList());if(matches.size()!=1)incomplete();var resolved=matches.get(0);var o=resolved.owner();if(o.conversationId()!=null)denied();resources.require(a,new PermissionProtocol.Scope(tenant(a),o.resourceProjectId(),o.resourceKind(),o.resourceId()),VersionResourceAuthority.action(VersionResourceProtocol.Domain.valueOf(resolved.domain()),o.resourceKind()),PURPOSE);if(o.sourceVersion()!=null)legalSource(a,o.sourceVersion());}
  for(var source:snapshot.required("source_legality"))legalSource(a,t(source,"source_version"));
 }
 private JsonNode request(byte[] bytes,String... fields)throws Exception{var n=json.read(bytes);fields(n,fields);return n;}
 private static void fields(JsonNode n,String... keys){if(!n.isObject())bad();var actual=new TreeSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(new TreeSet<>(List.of(keys))))bad();for(String k:keys)if(!"targets".equals(k)&&(!n.required(k).isTextual()||n.required(k).textValue().isBlank()||n.required(k).textValue().length()>8192))bad();}
 private static void expected(Map<String,Object> p,JsonNode n){if(!s(p,"revision").equals(t(n,"expected_revision")))throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
 private Map<String,Object> meta(EnterpriseAccess.Admission a,Object at,String classification){return new LinkedHashMap<>(Map.of("tenant_id",tenant(a),"created_by",a.actorId(),"created_at",at,"classification",classification,"schema_version","1.0.0","schema_manifest_sha256",manifest));}
 private List<Map<String,Object>> bounded(String sql,Object... args){var rows=db.queryForList(sql,args);if(rows.size()>LIMIT)incomplete();return rows;}
 private void insert(String table,Map<String,Object> values){var keys=new ArrayList<>(values.keySet());write(db.update("INSERT INTO "+table+" ("+String.join(",",keys)+") VALUES ("+String.join(",",Collections.nCopies(keys.size(),"?"))+")",keys.stream().map(values::get).toArray()));}
 private void same(JsonNode x,JsonNode y)throws Exception{if(!json.hash(x).equals(json.hash(y)))stale();}
 private static void edge(Map<String,Set<String>> graph,String parent,String child){graph.computeIfAbsent(parent,k->new TreeSet<>()).add(child);}
 private static Map<String,Object> one(List<Map<String,Object>> rows){if(rows.size()!=1)incomplete();return rows.get(0);}
 private static void write(int n){if(n!=1)throw new TenantAccessException(ErrorCode.REVISION_CONFLICT);}
 private static String tenant(EnterpriseAccess.Admission a){return a.context().tenant_id();}private static String id(){return UUID.randomUUID().toString();}private static String t(JsonNode n,String k){return n.required(k).asText();}private static String s(Map<String,Object> n,String k){return Objects.requireNonNull(n.get(k)).toString();}
 private static void bad(){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}private static void denied(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void stale(){throw new TenantAccessException(ErrorCode.REVIEW_STALE);}private static void incomplete(){throw new TenantAccessException(ErrorCode.IMPACT_INCOMPLETE);}
 private <T>T tx(ApplicationSessionIdentity.Verified subject,Work<T> work){return access.execute(subject,a->ThrowingSupplier.of(()->work.run(a)).get());}@FunctionalInterface private interface Work<T>{T run(EnterpriseAccess.Admission a)throws Exception;}
}

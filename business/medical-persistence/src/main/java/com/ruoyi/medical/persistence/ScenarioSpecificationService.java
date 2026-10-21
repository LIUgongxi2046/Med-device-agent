package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import com.ruoyi.medical.persistence.mapper.PermissionMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;

/** Scenario prose is never execution authority. Only current genuine publication evidence can mint Proof. */
@Service public final class ScenarioSpecificationService {
 static final List<String> FIELDS=List.of("ROLES","INPUTS","APPLICABLE_KNOWLEDGE","TASK_RULES","OUTPUTS","HUMAN_REVIEW","FAILURE_STATES","ACCEPTANCE_SAMPLES");
 private static final String PURPOSE="AGENT_PUBLICATION";
 private final EnterpriseAccess access; private final JdbcTemplate db; private final ScenarioSpecificationSchema schema;
 private final PermissionSchema json; private final ResourceReadAuthority resources; private final PermissionMapper permissions;
 private final FormalCapabilityIdentityAuthority capabilities; private final AgentPublicationDependencies dependencies;
 private final ObjectProvider<AgentPublicationAuthority> publications; private final IdentityDirectoryPolicy settings;
 public ScenarioSpecificationService(EnterpriseAccess access,DataSource data,ScenarioSpecificationSchema schema,PermissionSchema json,
   ResourceReadAuthority resources,PermissionMapper permissions,FormalCapabilityIdentityAuthority capabilities,
   AgentPublicationDependencies dependencies,ObjectProvider<AgentPublicationAuthority> publications,IdentityDirectoryPolicy settings){
  this.access=access;db=new JdbcTemplate(data);db.setQueryTimeout(5);this.schema=schema;this.json=json;this.resources=resources;
  this.permissions=permissions;this.capabilities=capabilities;this.dependencies=dependencies;this.publications=publications;this.settings=settings;
 }
 record Row(String id,String project,String config,String kind,String creator,LocalDateTime created,String hash,String content,String requestHash,String key,String classification){}
 private record Spec(Row row,JsonNode request,List<PermissionProtocol.Basis> roots,AgentPublicationDependencies.Checked checked){}
 private record Head(String publication,String revision,String status){}
 public JsonNode write(ApplicationSessionIdentity.Verified subject,String context,String key,String kind,byte[] bytes)throws Exception {
  json.headers(context,key);var request=schema.request(kind,bytes);if("Author".equals(kind)){new RegulatoryComparisonSchema().author(request);new ReportScenarioSchema().author(request);new TranslationScenarioSchema().author(request);new MarketAnalysisScenarioSchema().author(request);new ProductTechnicalRequirementSchema().author(request);new RegistrationDossierSchema().author(request);new NonclinicalPrinciplesRiskSchema().author(request);new ClinicalEvaluationSchema().author(request);new RegistrationLifecycleSchema().author(request);new InstructionsLabelSchema().author(request);new MultimodalQaSchema().author(request);}String requestHash=hash(request);
  // Exact completed operation replay needs no new physical publication preparation.
  var prior=access.execute(subject,a->checked(()->{current(a,context);return prior(a,key,kind,requestHash);}));if(prior!=null)return receipt(prior);
  AgentPublicationAuthority authority=null;AgentPublicationAuthority.PreparedPublication prepared=null;
  if("Publish".equals(kind)){authority=publications.getIfAvailable();if(authority==null)unavailable();prepared=authority.prepare(subject,context,request.required("release_id").asText(),PURPOSE,false);}
  final var actualAuthority=authority;final var actualPrepared=prepared;
  return access.execute(subject,a->checked(()->{
   current(a,context);if(a.simulationWorker())deny();var old=prior(a,key,kind,requestHash);if(old!=null)return receipt(old);
   Row parent=null;String project,config;var roots=new ArrayList<PermissionProtocol.Basis>();AgentPublicationAuthority.VerifiedPublication publication=null;
   if("Author".equals(kind)){
    project=request.required("project_id").asText();var capability=capabilities.require(a,basis(request.required("capability")),"SKILL",project,PURPOSE);
    if(!project.equals(capability.canonicalDefinition().required("request").required("project_id").asText()))deny();
    config=capability.canonicalDefinition().required("config_id").asText();roots.addAll(roots(request));
    verifyRoles(a,project,request);capability.requireUnchanged();
   }else{
    parent=load(a,basis(request.required("Disable".equals(kind)?"publication":"specification")),PURPOSE);
    if("Disable".equals(kind)){if(!"Publish".equals(parent.kind()))bad();}
    else if(!"Author".equals(parent.kind()))bad();
    project=parent.project();config=parent.config();roots.add(new PermissionProtocol.Basis(parent.id(),parent.hash()));
    if(!"Disable".equals(kind)){
     var spec=spec(a,parent,PURPOSE);roots.addAll(spec.roots());
     if("Review".equals(kind)&&parent.creator().equals(a.actorId()))deny();
     if("Publish".equals(kind)){
      var review=load(a,basis(request.required("review")),PURPOSE);requireReview(a,spec,review);roots.add(new PermissionProtocol.Basis(review.id(),review.hash()));
      publication=actualAuthority.require(a,request.required("release_id").asText(),PURPOSE,actualPrepared);
      requirePublication(a,spec,request.required("release_id").asText(),request.required("document_type").asText(),publication,PURPOSE);
     }
    }
   }
   if(!List.of("ACTIVE").equals(permissions.projectStatus(project)))deny();
   var grant=resources.require(a,projectScope(a,project),action(kind),PURPOSE);
   var closure=dependencies.require(a,project,roots,PURPOSE);
   var content=json.object().put("schema_version",request.required("schema_version").asText()).put("schema_manifest_sha256",schema.manifest(request.required("schema_version").asText())).put("kind",kind)
      .put("tenant_id",a.context().tenant_id()).put("project_id",project).put("config_id",config).put("created_by",a.actorId())
      .put("created_at",Objects.requireNonNull(permissions.databaseTime()).toString()).put("classification",settings.classification())
      .put("identity_mode",a.bindingView().mode());
   content.set("request",request);content.set("governance",AgentPublicationWitness.tree(grant));content.set("dependencies",closure.authorizations());
   if(publication!=null){content.put("approval_sha256",publication.approvalSha256());content.set("publication",publication.authorizations());var publicationVersions=new TreeSet<>(publication.dependencyVersions());publicationVersions.add(request.required("release_id").asText());content.set("publication_dependency_versions",json.tree(publicationVersions));publication.requireUnchanged();}
   var saved=save(a,key,kind,requestHash,content,roots,parent);
   if("Publish".equals(kind)){
    updateHead(a,saved,request.required("release_id").asText(),request.required("document_type").asText(),request.required("expected_revision").asText());
   }else if("Disable".equals(kind)){
    var published=schema.read(parent.content()).required("request");String release=published.required("release_id").asText(),type=published.required("document_type").asText();
    int n=db.update("UPDATE scenario_task_entry SET status='DISABLED',revision=revision+1,updated_at=?,last_record_id=? WHERE tenant_id=? AND project_id=? AND release_id=? AND document_type=? AND publication_id=? AND status='ACTIVE' AND revision=?",permissions.databaseTime(),saved.id(),a.context().tenant_id(),project,release,type,parent.id(),request.required("expected_revision").asText());one(n);
   }
   if(publication!=null)publication.requireUnchanged();
   if(!grant.equals(resources.require(a,projectScope(a,project),action(kind),PURPOSE)))stale();
   return receipt(saved);
  }));
 }
 public JsonNode read(ApplicationSessionIdentity.Verified subject,String context,String id)throws Exception {
  WireValues.uuid(id);return access.execute(subject,a->checked(()->{current(a,context);var row=loadId(a,id,PURPOSE);var result=receipt(row);result.set("content",schema.read(row.content()));result.put("execution_eligible",false);return result;}));
 }
 /** Current binding discovery only; callers still need actual TaskCreate admission. */
 public JsonNode readEntry(ApplicationSessionIdentity.Verified subject,String context,String project,String release,String type)throws Exception {
  WireValues.uuid(project);WireValues.uuid(release);ProjectSchema.text(type,128);
  return access.execute(subject,a->checked(()->{current(a,context);resources.require(a,projectScope(a,project),"project.read","PROJECT_READING");
   var head=head(a,project,release,type);var row=loadId(a,head.publication(),PURPOSE);var content=schema.read(row.content());
   var request=schema.validate("Publish",content.required("request"));if(!project.equals(row.project())||!release.equals(request.required("release_id").asText())||!type.equals(request.required("document_type").asText()))bad();
   var result=receipt(row).put("project_id",project).put("release_id",release).put("document_type",type).put("revision",head.revision()).put("status",head.status()).put("admission_required",true);
   result.set("specification",request.required("specification"));result.set("review",request.required("review"));return result;
  }));
 }
 private Spec spec(EnterpriseAccess.Admission a,Row row,String purpose)throws Exception {
  if(!"Author".equals(row.kind()))bad();var request=schema.validate("Author",schema.read(row.content()).required("request"));new RegulatoryComparisonSchema().author(request);new ReportScenarioSchema().author(request);new TranslationScenarioSchema().author(request);new MarketAnalysisScenarioSchema().author(request);new ProductTechnicalRequirementSchema().author(request);new RegistrationDossierSchema().author(request);new NonclinicalPrinciplesRiskSchema().author(request);new ClinicalEvaluationSchema().author(request);new RegistrationLifecycleSchema().author(request);new InstructionsLabelSchema().author(request);new MultimodalQaSchema().author(request);
  if(!row.project().equals(request.required("project_id").asText()))bad();
  var capability=capabilities.require(a,basis(request.required("capability")),"SKILL",row.project(),purpose);
  if(!row.config().equals(capability.canonicalDefinition().required("config_id").asText()))bad();verifyRoles(a,row.project(),request);
  var roots=roots(request);var closure=dependencies.require(a,row.project(),roots,PURPOSE);return new Spec(row,request,List.copyOf(roots),closure);
 }
 private void verifyRoles(EnterpriseAccess.Admission a,String project,JsonNode request)throws IOException {
  for(var ref:request.required("specification").required("ROLES").required("evidence"))capabilities.require(a,basis(ref),"ROLE",project,PURPOSE);
 }
 private List<PermissionProtocol.Basis> roots(JsonNode request){
  var refs=new TreeMap<String,PermissionProtocol.Basis>();add(refs,basis(request.required("capability")));
  for(String field:FIELDS)for(var ref:request.required("specification").required(field).required("evidence"))add(refs,basis(ref));
  for(var ref:request.required("applicability").required("templates"))add(refs,basis(ref));return List.copyOf(refs.values());
 }
 private void requireReview(EnterpriseAccess.Admission a,Spec spec,Row review)throws IOException {
  if(!"Review".equals(review.kind())||!review.project().equals(spec.row().project())||!review.config().equals(spec.row().config())||review.creator().equals(spec.row().creator()))deny();
  var content=schema.read(review.content());var request=schema.validate("Review",content.required("request"));
  if(!basis(request.required("specification")).equals(new PermissionProtocol.Basis(spec.row().id(),spec.row().hash())))bad();
  requireCurrentGovernance(a,review);
 }
 private void requireCurrentGovernance(EnterpriseAccess.Admission a,Row row)throws IOException {
  var content=schema.read(row.content());var now=Objects.requireNonNull(permissions.databaseTime());
  var members=permissions.targetMember(row.creator());if(members.size()!=1)deny();var member=members.get(0);
  if(!a.context().tenant_id().equals(member.tenantId())||!"ACTIVE".equals(member.status())||member.validFrom().isAfter(now)||member.validTo()!=null&&!member.validTo().isAfter(now))deny();
  if(permissions.currentBinding(row.creator()).stream().noneMatch(binding->binding.mode().equals(content.required("identity_mode").asText())&&"ACTIVE".equals(binding.status())))deny();
  var grants=permissions.currentGrant(content.required("governance").required("grant_id").asText());
  if(grants.size()!=1||!content.required("governance").equals(AgentPublicationWitness.tree(grants.get(0)))
    ||new PermissionAuthority(json).findResource(projectScope(a,row.project()),row.creator(),content.required("identity_mode").asText(),action(row.kind()),PURPOSE,grants,now).isEmpty())deny();
 }
 private void requirePublication(EnterpriseAccess.Admission a,Spec spec,String release,String type,AgentPublicationAuthority.VerifiedPublication publication,String purpose)throws Exception {
  publication.requireConsumer(a,release,purpose);
  var releaseScope=publication.manifest().manifest().releaseScope();if(!a.context().tenant_id().equals(releaseScope.tenantId())||!releaseScope.allowedClassifications().contains(settings.classification())||"PROJECTS".equals(releaseScope.projectMode())&&!releaseScope.projectIds().contains(spec.row().project()))deny();
  boolean applicable=false;for(var value:spec.request().required("applicability").required("document_types"))if(type.equals(value.asText()))applicable=true;
  if(!applicable)deny();var ids=new HashSet<>(publication.dependencyVersions());publication.manifest().dependencies().forEach(v->ids.add(v.versionId()));publication.manifest().artifacts().forEach(v->ids.add(v.artifactId()));
  for(var ref:spec.roots())if(!ids.contains(ref.version_id()))unavailable();
  for(var ref:spec.request().required("applicability").required("templates")){
   var b=basis(ref);if(!publication.manifest().manifest().templateVersions().contains(new AgentReleaseProtocol.VersionRef(b.version_id(),b.content_sha256())))bad();
  }
  // This private proof revalidates actual evaluation, independent acceptance, release state and physical bytes.
  publication.requireUnchanged();
 }
 /** Called only inside the actual prepared task-create admission, never by catalogue metadata. */
 public Proof requireTask(EnterpriseAccess.Admission a,TaskProtocol.ValidatedCreate input,AgentPublicationAuthority.VerifiedPublication publication,List<PermissionProtocol.Basis> selectedSources)throws Exception {
  return requireScenario(a,input,publication,selectedSources,"TASK_CREATION");
 }
 private Proof requireScenario(EnterpriseAccess.Admission a,TaskProtocol.ValidatedCreate input,AgentPublicationAuthority.VerifiedPublication publication,List<PermissionProtocol.Basis> selectedSources,String purpose)throws Exception {
  var request=input.request();var head=head(a,request.project_id(),request.agent_release_id(),request.document_type());
  if(!"ACTIVE".equals(head.status()))unavailable();var published=loadId(a,head.publication(),purpose);if(!"Publish".equals(published.kind()))bad();
  requireCurrentGovernance(a,published);
  var publishedContent=schema.read(published.content());if(!publication.approvalSha256().equals(publishedContent.required("approval_sha256").asText()))bad();
  var publish=schema.validate("Publish",publishedContent.required("request"));
  if(!request.project_id().equals(published.project())||!request.agent_release_id().equals(publish.required("release_id").asText())||!request.document_type().equals(publish.required("document_type").asText()))bad();
  var specification=spec(a,load(a,basis(publish.required("specification")),purpose),purpose);
  var review=load(a,basis(publish.required("review")),purpose);requireReview(a,specification,review);
  requirePublication(a,specification,request.agent_release_id(),request.document_type(),publication,purpose);
  boolean template=false;for(var ref:specification.request().required("applicability").required("templates"))if(request.template_version().equals(ref.required("version_id").asText()))template=true;if(!template)deny();
  var knowledgeRoots=new ArrayList<PermissionProtocol.Basis>();for(var ref:specification.request().required("specification").required("APPLICABLE_KNOWLEDGE").required("evidence"))knowledgeRoots.add(basis(ref));
  var knowledge=dependencies.require(a,request.project_id(),knowledgeRoots,PURPOSE);for(var selected:selectedSources)if(!knowledge.versionIds().contains(selected.version_id()))deny();
  if(specification.request().required("specification").required("INPUTS").required("definition").asText().startsWith(RegulatoryComparisonSchema.PREFIX)){
   var selection=new RegulatoryComparisonSchema().definition(specification.request().required("specification").required("INPUTS").required("definition").asText());
   if(!selectedSources.contains(selection.baseline().source())||!selectedSources.contains(selection.target().source()))deny();
  }
  String typedDefinition=specification.request().required("specification").required("INPUTS").required("definition").asText();
  if(typedDefinition.startsWith(ReportScenarioSchema.PREFIX))for(var item:new ReportScenarioSchema().definition(typedDefinition).inputs())if(!selectedSources.contains(item.source()))deny();
  if(typedDefinition.startsWith(TranslationScenarioSchema.PREFIX))for(var item:new TranslationScenarioSchema().definition(typedDefinition).segments())if(!selectedSources.contains(item.source()))deny();
  if(typedDefinition.startsWith(MarketAnalysisScenarioSchema.PREFIX)){var market=new MarketAnalysisScenarioSchema().definition(typedDefinition);for(var item:market.inputs())if(!selectedSources.contains(item.source()))deny();for(var item:market.context())if(!selectedSources.contains(item.source()))deny();}
  if(typedDefinition.startsWith(ProductTechnicalRequirementSchema.PREFIX))for(var item:new ProductTechnicalRequirementSchema().definition(typedDefinition).sources())if(!selectedSources.contains(item.source()))deny();
  if(typedDefinition.startsWith(RegistrationDossierSchema.PREFIX)){var dossier=new RegistrationDossierSchema().definition(typedDefinition);if(!request.template_version().equals(dossier.path().templateVersion().version_id()))deny();for(var item:dossier.sources())if(!selectedSources.contains(item.source()))deny();}
  if(typedDefinition.startsWith(NonclinicalPrinciplesRiskSchema.PREFIX))for(var item:new NonclinicalPrinciplesRiskSchema().definition(typedDefinition).sources())if(!selectedSources.contains(item.source()))deny();
  if(RegistrationLifecycleSchema.supportsDefinition(typedDefinition)){var lifecycle=new RegistrationLifecycleSchema().definition(typedDefinition);if(!request.template_version().equals(lifecycle.templateVersion().version_id()))deny();for(var item:lifecycle.sources())if(!selectedSources.contains(item.source()))deny();}
  if(typedDefinition.startsWith(ClinicalEvaluationSchema.PREFIX))for(var item:new ClinicalEvaluationSchema().definition(typedDefinition).sources())if(!selectedSources.contains(item.source()))deny();
  if(typedDefinition.startsWith(InstructionsLabelSchema.PREFIX))for(var item:new InstructionsLabelSchema().definition(typedDefinition).sources())if(!selectedSources.contains(item.source()))deny();
  if(typedDefinition.startsWith(MultimodalQaSchema.PREFIX))for(var item:new MultimodalQaSchema().definition(typedDefinition).inputs())if(!selectedSources.contains(item.source()))deny();
  var versions=new TreeSet<>(specification.checked().requiredVersionIds());versions.addAll(List.of(published.id(),review.id(),specification.row().id()));
  var fingerprint=json.object().put("publication_id",published.id()).put("head_revision",head.revision()).put("content_sha256",published.hash());fingerprint.set("dependencies",specification.checked().authorizations());
  return new Proof(this,a,input,publication,List.copyOf(versions),fingerprint,selectedSources,purpose);
 }
 /** Reuses the task admission gate and demands that the exact current B14 proof was frozen in the run. */
 JsonNode requireRegulatory(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication)throws Exception {
  var replay=production.replay().content();
  var input=new TaskInputSchema(json).create(ContentReadAuthorization.canonicalBytes(replay.required("task_input")));
  var selected=new ArrayList<PermissionProtocol.Basis>();var pinned=new HashMap<String,String>();
  for(var v:replay.required("dependency_versions"))pinned.put(v.required("version_id").asText(),v.required("content_sha256").asText());
  for(var source:replay.required("scope_snapshot").required("members")){String id=source.required("source_version").asText();if(!pinned.containsKey(id))bad();selected.add(new PermissionProtocol.Basis(id,pinned.get(id)));}
  var proof=requireScenario(production.admission(),input,publication,selected,"RUN_CREATION");for(String id:proof.dependencyVersions())if(!pinned.containsKey(id))bad();
  var head=head(production.admission(),production.projectId(),input.request().agent_release_id(),input.request().document_type());
  var published=loadId(production.admission(),head.publication(),"RUN_CREATION");
  var ref=basis(schema.read(published.content()).required("request").required("specification"));if(!ref.content_sha256().equals(pinned.get(ref.version_id()))||!published.hash().equals(pinned.get(published.id())))bad();
  var spec=spec(production.admission(),load(production.admission(),ref,"RUN_CREATION"),"RUN_CREATION");
  if(!spec.request().required("specification").required("INPUTS").required("definition").asText().startsWith(RegulatoryComparisonSchema.PREFIX)){proof.requireUnchanged();return null;}
  var result=json.object();result.set("specification",json.tree(ref));result.set("selection",new RegulatoryComparisonSchema().tree(new RegulatoryComparisonSchema().definition(spec.request().required("specification").required("INPUTS").required("definition").asText())));
  proof.requireUnchanged();return result;
 }
 JsonNode requireDocumentScenario(TaskNodeProductionAuthority.Verified production,AgentPublicationAuthority.VerifiedPublication publication)throws Exception {
  var replay=production.replay().content();
  var input=new TaskInputSchema(json).create(ContentReadAuthorization.canonicalBytes(replay.required("task_input")));
  var selected=new ArrayList<PermissionProtocol.Basis>();var pinned=new HashMap<String,String>();
  for(var v:replay.required("dependency_versions"))pinned.put(v.required("version_id").asText(),v.required("content_sha256").asText());
  for(var source:replay.required("scope_snapshot").required("members")){String id=source.required("source_version").asText();if(!pinned.containsKey(id))bad();selected.add(new PermissionProtocol.Basis(id,pinned.get(id)));}
  var proof=requireScenario(production.admission(),input,publication,selected,"RUN_CREATION");for(String id:proof.dependencyVersions())if(!pinned.containsKey(id))bad();
  var head=head(production.admission(),production.projectId(),input.request().agent_release_id(),input.request().document_type());
  var published=loadId(production.admission(),head.publication(),"RUN_CREATION");
  var ref=basis(schema.read(published.content()).required("request").required("specification"));if(!ref.content_sha256().equals(pinned.get(ref.version_id()))||!published.hash().equals(pinned.get(published.id())))bad();
  var spec=spec(production.admission(),load(production.admission(),ref,"RUN_CREATION"),"RUN_CREATION");
  String definition=spec.request().required("specification").required("INPUTS").required("definition").asText();JsonNode selection;
  if(definition.startsWith(ReportScenarioSchema.PREFIX))selection=new ReportScenarioSchema().tree(new ReportScenarioSchema().definition(definition));
  else if(definition.startsWith(TranslationScenarioSchema.PREFIX))selection=new TranslationScenarioSchema().tree(new TranslationScenarioSchema().definition(definition));
  else if(definition.startsWith(MarketAnalysisScenarioSchema.PREFIX))selection=new MarketAnalysisScenarioSchema().tree(new MarketAnalysisScenarioSchema().definition(definition));
  else if(definition.startsWith(ProductTechnicalRequirementSchema.PREFIX))selection=new ProductTechnicalRequirementSchema().tree(new ProductTechnicalRequirementSchema().definition(definition));
  else if(definition.startsWith(RegistrationDossierSchema.PREFIX))selection=new RegistrationDossierSchema().tree(new RegistrationDossierSchema().definition(definition));
  else if(definition.startsWith(NonclinicalPrinciplesRiskSchema.PREFIX))selection=new NonclinicalPrinciplesRiskSchema().tree(new NonclinicalPrinciplesRiskSchema().definition(definition));
  else if(RegistrationLifecycleSchema.supportsDefinition(definition))selection=new RegistrationLifecycleSchema().tree(new RegistrationLifecycleSchema().definition(definition));
  else if(definition.startsWith(ClinicalEvaluationSchema.PREFIX))selection=new ClinicalEvaluationSchema().tree(new ClinicalEvaluationSchema().definition(definition));
  else if(definition.startsWith(InstructionsLabelSchema.PREFIX))selection=new InstructionsLabelSchema().tree(new InstructionsLabelSchema().definition(definition));
  else if(definition.startsWith(MultimodalQaSchema.PREFIX))selection=new MultimodalQaSchema().tree(new MultimodalQaSchema().definition(definition));
  else{proof.requireUnchanged();return null;}
  var result=json.object();result.set("specification",json.tree(ref));result.set("selection",selection);
  proof.requireUnchanged();return result;
 }
 public static final class Proof {
  private final ScenarioSpecificationService owner;private final EnterpriseAccess.Admission admission;private final TaskProtocol.ValidatedCreate input;private final AgentPublicationAuthority.VerifiedPublication publication;private final List<String> versions;private final JsonNode fingerprint;private final List<PermissionProtocol.Basis> selectedSources;private final String purpose;
  private Proof(ScenarioSpecificationService owner,EnterpriseAccess.Admission admission,TaskProtocol.ValidatedCreate input,AgentPublicationAuthority.VerifiedPublication publication,List<String> versions,JsonNode fingerprint,List<PermissionProtocol.Basis> selectedSources,String purpose){this.purpose=purpose;this.owner=owner;this.admission=admission;this.input=input;this.publication=publication;this.versions=versions;this.fingerprint=fingerprint.deepCopy();this.selectedSources=List.copyOf(selectedSources);}
  public List<String> dependencyVersions(){return versions;}public JsonNode authorizations(){return fingerprint.deepCopy();}
  public void requireUnchanged()throws Exception{var now=owner.requireScenario(admission,input,publication,selectedSources,purpose);if(!versions.equals(now.versions)||!fingerprint.equals(now.fingerprint))stale();}
 }
 private Row prior(EnterpriseAccess.Admission a,String key,String kind,String hash)throws IOException {
  var rows=query("WHERE tenant_id=? AND created_by=? AND kind=? AND idempotency_key=?",a.context().tenant_id(),a.actorId(),kind,key);
  if(rows.size()>1)bad();if(rows.isEmpty())return null;var row=loadId(a,rows.get(0).id(),PURPOSE);if(!hash.equals(row.requestHash()))throw new TenantAccessException(ErrorCode.IDEMPOTENCY_CONFLICT);return row;
 }
 private Row load(EnterpriseAccess.Admission a,PermissionProtocol.Basis basis,String purpose)throws IOException {var row=loadId(a,basis.version_id(),purpose);if(!basis.content_sha256().equals(row.hash()))bad();return row;}
 private Row loadId(EnterpriseAccess.Admission a,String id,String purpose)throws IOException {
  var rows=query("WHERE tenant_id=? AND record_id=?",a.context().tenant_id(),id);if(rows.size()!=1)unavailable();var row=rows.get(0);var content=schema.read(row.content());String storedVersion=content.required("schema_version").asText(),storedManifest=schema.manifest(storedVersion);
  if(!storedVersion.equals(content.required("request").required("schema_version").asText()))bad();
  if(!hash(content).equals(row.hash())||!storedManifest.equals(content.required("schema_manifest_sha256").asText())||!row.id().equals(content.required("record_id").asText())||!row.project().equals(content.required("project_id").asText())||!row.config().equals(content.required("config_id").asText())||!row.creator().equals(content.required("created_by").asText())||!row.created().toString().equals(content.required("created_at").asText())||!row.classification().equals(content.required("classification").asText())||!row.kind().equals(content.required("kind").asText())||!a.context().tenant_id().equals(content.required("tenant_id").asText())||!row.requestHash().equals(hash(schema.validate(row.kind(),content.required("request")))))bad();
  var metadata=db.queryForObject("SELECT COUNT(*) FROM scenario_record WHERE tenant_id=? AND record_id=? AND schema_version=? AND schema_manifest_sha256=? AND audit_id=? AND event_id=? AND parent_id <=> ?",Long.class,a.context().tenant_id(),id,storedVersion,storedManifest,content.required("audit_id").asText(),content.required("event_id").asText(),"Author".equals(row.kind())?null:content.required("request").required("Disable".equals(row.kind())?"publication":"specification").required("version_id").asText());if(metadata==null||metadata!=1)bad();
  var versions=db.queryForList("SELECT content_sha256 FROM version_registry WHERE tenant_id=? AND version_id=? AND owner_project_id=? AND entity_kind=?",String.class,a.context().tenant_id(),id,row.project(),entityKind(row.kind()));if(!List.of(row.hash()).equals(versions))bad();
  var edgeIds=db.queryForList("SELECT upstream_version_id FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=? AND relation_kind='SCENARIO_DEPENDENCY' AND actor_id=? AND created_at=? ORDER BY upstream_version_id",String.class,a.context().tenant_id(),id,row.creator(),row.created());
  var expected=new ArrayList<String>();for(var ref:content.required("dependency_versions"))expected.add(ref.asText());if(!expected.equals(edgeIds))bad();
  var edgeCount=db.queryForObject("SELECT COUNT(*) FROM lineage_edge WHERE tenant_id=? AND consumer_version_id=?",Long.class,a.context().tenant_id(),id);if(edgeCount==null||edgeCount!=edgeIds.size())bad();
  var audit=db.queryForList("SELECT resource_ref FROM audit_event WHERE tenant_id=? AND audit_id=? AND subject_id=? AND project_id=? AND action=? AND request_id=? AND result='SUCCESS' AND schema_manifest_sha256=?",String.class,a.context().tenant_id(),content.required("audit_id").asText(),row.creator(),row.project(),"scenario."+row.kind().toLowerCase(Locale.ROOT),id,storedManifest);
  var event=db.queryForList("SELECT payload FROM outbox_event WHERE tenant_id=? AND event_id=? AND aggregate_id=? AND aggregate_revision=1 AND event_type=?",String.class,a.context().tenant_id(),content.required("event_id").asText(),id,"scenario."+row.kind().toLowerCase(Locale.ROOT));
  if(audit.size()!=1||event.size()!=1||!content.equals(schema.read(audit.get(0)))||!content.equals(schema.read(event.get(0))))bad();
  resources.require(a,new PermissionProtocol.Scope(a.context().tenant_id(),row.project(),"CONFIGURATION",row.config()),"configuration.read",purpose);return row;
 }
 private List<Row> query(String suffix,Object... args){return db.query("SELECT record_id,project_id,config_id,kind,created_by,created_at,content_sha256,content_json,request_sha256,idempotency_key,classification FROM scenario_record "+suffix+" LIMIT 2",(r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toLocalDateTime(),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11)),args);}
 private Row save(EnterpriseAccess.Admission a,String key,String kind,String requestHash,ObjectNode content,List<PermissionProtocol.Basis> roots,Row parent)throws IOException {
  String id=UUID.randomUUID().toString(),tenant=a.context().tenant_id(),project=content.required("project_id").asText(),config=content.required("config_id").asText();content.put("record_id",id);String auditId=UUID.randomUUID().toString(),eventId=UUID.randomUUID().toString();content.put("audit_id",auditId).put("event_id",eventId);var direct=new TreeSet<String>();for(var root:roots)direct.add(root.version_id());for(var upstream:content.path("publication_dependency_versions"))direct.add(upstream.asText());content.set("dependency_versions",json.tree(direct));String canonical=json.canonical(content),hash=hash(content);LocalDateTime now=LocalDateTime.parse(content.required("created_at").asText());
  one(db.update("INSERT INTO version_registry(tenant_id,version_id,owner_project_id,entity_kind,content_sha256) VALUES(?,?,?,?,?)",tenant,id,project,entityKind(kind),hash));
  one(permissions.saveAudit(a.actorId(),project,auditId,"scenario."+kind.toLowerCase(Locale.ROOT),canonical,id,content.required("schema_manifest_sha256").asText(),settings.classification()));
  one(permissions.saveEvent(a.actorId(),eventId,id,"1","scenario."+kind.toLowerCase(Locale.ROOT),canonical));
  one(db.update("INSERT INTO scenario_record(tenant_id,project_id,record_id,config_id,kind,parent_id,idempotency_key,request_sha256,content_sha256,content_json,audit_id,event_id,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",tenant,project,id,config,kind,parent==null?null:parent.id(),key,requestHash,hash,canonical,auditId,eventId,content.required("schema_version").asText(),content.required("schema_manifest_sha256").asText(),a.actorId(),now,settings.classification()));
  var ids=new TreeSet<String>();for(var root:roots)ids.add(root.version_id());
  for(var upstream:content.path("publication_dependency_versions"))ids.add(upstream.asText());
  for(String upstream:ids)one(db.update("INSERT INTO lineage_edge(tenant_id,consumer_version_id,upstream_version_id,relation_kind,actor_id,decision_ref,created_at) VALUES(?,?,?,'SCENARIO_DEPENDENCY',?,NULL,?)",tenant,id,upstream,a.actorId(),now));
  return loadId(a,id,PURPOSE);
 }
 private void updateHead(EnterpriseAccess.Admission a,Row row,String release,String type,String expected){
  if("0".equals(expected))one(db.update("INSERT INTO scenario_task_entry(tenant_id,project_id,release_id,document_type,publication_id,last_record_id,status,revision,updated_at,schema_version,schema_manifest_sha256,created_by,created_at,classification) VALUES(?,?,?,?,?,?,'ACTIVE',1,?,'1.0.0',?,?,?,?)",a.context().tenant_id(),row.project(),release,type,row.id(),row.id(),row.created(),schema.manifest(),a.actorId(),row.created(),row.classification()));
  else one(db.update("UPDATE scenario_task_entry SET publication_id=?,last_record_id=?,status='ACTIVE',revision=revision+1,updated_at=? WHERE tenant_id=? AND project_id=? AND release_id=? AND document_type=? AND revision=?",row.id(),row.id(),row.created(),a.context().tenant_id(),row.project(),release,type,expected));
 }
 private Head head(EnterpriseAccess.Admission a,String project,String release,String type){var rows=db.query("SELECT publication_id,CAST(revision AS CHAR),status FROM scenario_task_entry WHERE tenant_id=? AND project_id=? AND release_id=? AND document_type=? AND schema_version='1.0.0' AND schema_manifest_sha256=? AND classification=? AND updated_at>=created_at AND (status<>'ACTIVE' OR last_record_id=publication_id) FOR UPDATE",(r,n)->new Head(r.getString(1),r.getString(2),r.getString(3)),a.context().tenant_id(),project,release,type,schema.manifest(),settings.classification());if(rows.size()!=1)unavailable();return rows.get(0);}
 private ObjectNode receipt(Row row){return json.object().put("schema_version","1.0.0").put("record_id",row.id()).put("content_sha256",row.hash()).put("kind",row.kind()).put("execution_eligible",false);}
 private static PermissionProtocol.Basis basis(JsonNode n){String id=n.required("version_id").asText(),hash=n.required("content_sha256").asText();WireValues.uuid(id);WireValues.hash(hash);return new PermissionProtocol.Basis(id,hash);}
 private static void add(Map<String,PermissionProtocol.Basis> refs,PermissionProtocol.Basis ref){var old=refs.putIfAbsent(ref.version_id(),ref);if(old!=null&&!old.equals(ref))bad();}
 private PermissionProtocol.Scope projectScope(EnterpriseAccess.Admission a,String project){return new PermissionProtocol.Scope(a.context().tenant_id(),project,"PROJECT",project);}
 private static String entityKind(String kind){return switch(kind){case "Author"->"SCENARIO_SPECIFICATION";case "Review"->"SCENARIO_REVIEW";case "Publish"->"SCENARIO_PUBLICATION";case "Disable"->"SCENARIO_DISABLE";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};}
 private static String action(String kind){return switch(kind){case "Author"->"capability.configure";case "Review"->"scenario.review";case "Publish","Disable"->"scenario.publish";default->throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);};}
 private String hash(JsonNode value)throws IOException{return DigestUtils.sha256Hex(json.canonical(value));}
 private static <T>T checked(ThrowingSupplier<T> fn){return fn.get();}
 private static void current(EnterpriseAccess.Admission a,String context){if(!context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);}
 private static void one(int n){if(n!=1)stale();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}private static void unavailable(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}private static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
}

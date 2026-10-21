package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import java.util.*;
import org.springframework.util.function.ThrowingSupplier;
/** Human, pre-job package read. Reuses only the original immutable package GET pipeline.
 * It neither creates a runtime principal nor adds a source to the task scope snapshot. */
public final class TaskPlanDraftPackagePreflight {
 private final EnterpriseAccess access;private final FormalCapabilityIdentityAuthority capabilities;private final FormalEvaluationContextPackages packages;private final PermissionSchema json;private final String hash;
 public TaskPlanDraftPackagePreflight(EnterpriseAccess access,FormalCapabilityIdentityAuthority capabilities,FormalEvaluationContextPackages packages,PermissionSchema json)throws Exception{this.access=access;this.capabilities=capabilities;this.packages=packages;this.json=json;try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/task-plan-draft-package.schema.json"))){hash=WireValues.sha256(in.readAllBytes());}}
 public static final class Selection {
  private final String tenant,actor,context,project;private final PermissionProtocol.Basis role,profile;private final JsonNode prompts,runtime;
  private Selection(String tenant,String actor,String context,String project,PermissionProtocol.Basis role,PermissionProtocol.Basis profile,JsonNode prompts,JsonNode runtime){this.tenant=tenant;this.actor=actor;this.context=context;this.project=project;this.role=role;this.profile=profile;this.prompts=prompts.deepCopy();this.runtime=runtime.deepCopy();}
 }
 public Selection select(TaskPlanDraftAdmission.Verified current)throws Exception {current.requireUnchanged();human(current.admission());var runtime=json.tree(current.releaseManifest().runtimeManifest());var artifact=runtime.required("profile").required("artifact");return new Selection(current.admission().context().tenant_id(),current.admission().actorId(),current.context(),current.project(),current.node().roleVersion(),new PermissionProtocol.Basis(text(artifact,"artifact_id"),text(artifact,"sha256")),json.tree(current.releaseManifest()).required("prompt_revisions"),runtime);}
 public Verified readHuman(ApplicationSessionIdentity.Verified subject,Selection selection)throws Exception {
  if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new TenantAccessException(ErrorCode.CONTEXT_CHANGED);
  var rolePlan=access.execute(subject,a->ThrowingSupplier.of(()->{requireSelection(a,selection);return packages.plan(a,selection.context,selection.project,capability(a,selection,selection.role,"ROLE"),TaskPlanDraftPromptCompiler.ROLE_REF,hash);}).get());
  var role=packages.read(subject,rolePlan);var roleBody=read(role,TaskPlanDraftPromptCompiler.ROLE_REF);TaskPlanDraftPromptCompiler.validateRole(roleBody);var promptBasis=basis(roleBody.required("prompt_basis"));boolean found=false;for(var prompt:selection.prompts)if(prompt.equals(json.tree(promptBasis)))found=true;if(!found)deny();
  if(!roleBody.required("profile_artifact").equals(selection.runtime.required("profile").required("artifact")))bad();
  var promptPlan=access.execute(subject,a->ThrowingSupplier.of(()->{requireSelection(a,selection);role.requireCurrent(a);return packages.plan(a,selection.context,selection.project,capability(a,selection,promptBasis,"PROMPT"),TaskPlanDraftPromptCompiler.PROMPT_REF,hash);}).get());
  var prompt=packages.read(subject,promptPlan);TaskPlanDraftPromptCompiler.validatePrompt(read(prompt,TaskPlanDraftPromptCompiler.PROMPT_REF));
  var profilePlan=access.execute(subject,a->ThrowingSupplier.of(()->{requireSelection(a,selection);role.requireCurrent(a);prompt.requireCurrent(a);return packages.plan(a,selection.context,selection.project,selection.profile,TaskPlanDraftPromptCompiler.PROFILE_REF,hash);}).get());
  var profile=packages.read(subject,profilePlan);var profileBody=read(profile,TaskPlanDraftPromptCompiler.PROFILE_REF);TaskPlanDraftPromptCompiler.validateProfile(profileBody);
  boolean adapter=false;for(var value:selection.runtime.required("event_adapters"))if("C07".equals(text(value,"component"))&&value.required("implementation").equals(profileBody.required("adapter_artifact")))adapter=true;if(!adapter)bad();
  var sources=new TreeSet<String>();for(var plan:List.of(rolePlan,promptPlan,profilePlan))sources.addAll(plan.sources());
  var result=new Verified(selection,promptBasis,role,prompt,profile,List.copyOf(sources));access.execute(subject,a->ThrowingSupplier.of(()->{result.requireCurrent(a);return null;}).get());return result;
 }
 public final class Verified {
  private final Selection selection;private final PermissionProtocol.Basis promptBasis;private final FormalEvaluationContextPackages.Read role,prompt,profile;private final List<String> sources;
  private Verified(Selection selection,PermissionProtocol.Basis promptBasis,FormalEvaluationContextPackages.Read role,FormalEvaluationContextPackages.Read prompt,FormalEvaluationContextPackages.Read profile,List<String> sources){this.selection=selection;this.promptBasis=promptBasis;this.role=role;this.prompt=prompt;this.profile=profile;this.sources=List.copyOf(sources);}
  private void requireCurrent(EnterpriseAccess.Admission a)throws Exception{requireSelection(a,selection);role.requireCurrent(a);prompt.requireCurrent(a);profile.requireCurrent(a);}
  public void requireFor(TaskPlanDraftAdmission.Verified current)throws Exception{var fresh=select(current);if(!identity(selection).equals(identity(fresh)))throw new TenantAccessException(ErrorCode.SCOPE_STALE);requireCurrent(current.admission());}
  public List<String> sourceVersions(){return sources;}
  public JsonNode canonicalPins(){var n=json.object().put("schema_version","1.0.0").put("node_kind","N01");n.set("selection",identity(selection));n.set("prompt_basis",json.tree(promptBasis));n.set("role_package",role.witness());n.set("prompt_package",prompt.witness());n.set("profile_package",profile.witness());n.set("source_versions",json.tree(sources));return n;}
 }
 private PermissionProtocol.Basis capability(EnterpriseAccess.Admission a,Selection s,PermissionProtocol.Basis basis,String kind)throws Exception{var governed=capabilities.require(a,basis,kind,s.project,"AGENT_PUBLICATION");var original=capabilities.requireRegisteredIdentity(s.tenant,s.actor,basis,kind,s.project);if(!governed.sourceArtifact().equals(original.sourceArtifact()))bad();return original.sourceArtifact();}
 private JsonNode identity(Selection s){var n=json.object().put("tenant_id",s.tenant).put("actor_id",s.actor).put("context_revision",s.context).put("project_id",s.project);n.set("role_basis",json.tree(s.role));n.set("profile_artifact",json.tree(s.profile));n.set("prompt_revisions",s.prompts);n.set("runtime",s.runtime);return n;}
 private static void human(EnterpriseAccess.Admission a){FormalEvaluationExecutionAdmission.current(a);if(a.formalWorker()||a.controlWorker()||a.recoveryWorker())deny();}
 private void requireSelection(EnterpriseAccess.Admission a,Selection s){human(a);if(!s.tenant.equals(a.context().tenant_id())||!s.actor.equals(a.actorId())||!s.context.equals(a.context().context_revision()))throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
 private JsonNode read(FormalEvaluationContextPackages.Read r,String ref)throws Exception{SchemaCatalogue.fromClasspath().bindPublished(ref,hash,r.bytes());return com.fasterxml.jackson.databind.json.JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(r.bytes());}
 private static PermissionProtocol.Basis basis(JsonNode n){return new PermissionProtocol.Basis(text(n,"version_id"),text(n,"content_sha256"));}private static String text(JsonNode n,String key){if(!n.required(key).isTextual())bad();return n.required(key).textValue();}private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
}

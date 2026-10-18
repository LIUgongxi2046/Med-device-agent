package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import com.ruoyi.medical.identity.ApplicationSessionIdentity;
/** Actual fixed SOURCE price bytes + current configuration binding. No user-supplied rates. */
public final class FormalInvocationPriceAuthority {
 private final EnterpriseAccess access;private final ModelConfigurationService models;private final FormalInvocationSourceOriginals originals;private final FormalInvocationSchema schema;private final int maximumBytes;
 public FormalInvocationPriceAuthority(EnterpriseAccess access,ModelConfigurationService models,FormalInvocationSourceOriginals originals,FormalInvocationSchema schema,int maximumBytes){this.access=access;this.models=models;this.originals=originals;this.schema=schema;if(maximumBytes<1)throw new IllegalArgumentException();this.maximumBytes=maximumBytes;}
 public final class Price {
  private final String revision,modelHash;private final FormalInvocationSourceOriginals.Read original;private final JsonNode document;private final FormalInvocationPricing.Schedule schedule;
  private Price(String revision,String modelHash,FormalInvocationSourceOriginals.Read original,JsonNode document,FormalInvocationPricing.Schedule schedule){this.revision=revision;this.modelHash=modelHash;this.original=original;this.document=document.deepCopy();this.schedule=schedule;}
  public FormalInvocationPricing.Schedule schedule(){return schedule;}public JsonNode canonicalDocument(){return document.deepCopy();}public String sourceAuthorizationSha256(){return original.authorizationSha256();}
  public void requireCurrent(EnterpriseAccess.Admission a)throws Exception{original.requireCurrent(a);JsonNode model=models.read(a,revision).required("data");if(!revision.equals(model.required("current_model_revision").textValue())||!modelHash.equals(model.required("content_sha256").textValue()))fail(ErrorCode.REVISION_CONFLICT);bind(model,document,original);}
 }
 public Price prepare(ApplicationSessionIdentity.Verified subject,String context,String revision,String project)throws Exception{
  JsonNode model=access.execute(subject,a->org.springframework.util.function.ThrowingSupplier.of(()->{if(!context.equals(a.context().context_revision()))fail(ErrorCode.CONTEXT_CHANGED);return models.read(a,revision).required("data");}).get());
  var definition=model.required("definition");var ref=definition.required("price_basis");var basis=new PermissionProtocol.Basis(ref.required("version_id").textValue(),ref.required("content_sha256").textValue());
  var read=originals.read(subject,context,basis,"MODEL_CONFIGURATION",project);var document=schema.read("PriceProfile",read.bytes(),maximumBytes);bind(model,document,read);
  var price=new Price(revision,model.required("content_sha256").textValue(),read,document,schema.price(read.sourceVersion(),document));access.execute(subject,a->org.springframework.util.function.ThrowingSupplier.of(()->{price.requireCurrent(a);return true;}).get());return price;
 }
 private static void bind(JsonNode model,JsonNode profile,FormalInvocationSourceOriginals.Read read){JsonNode definition=model.required("definition");if(!read.sourceVersion().equals(definition.required("price_basis").required("version_id").textValue())||!read.contentSha256().equals(definition.required("price_basis").required("content_sha256").textValue()))fail(ErrorCode.CONTENT_MISMATCH);for(String key:java.util.List.of("provider","model_id","model_version","currency"))if(!definition.required(key).equals(profile.required(key)))fail(ErrorCode.MODEL_POLICY_INCOMPLETE);}
 private static void fail(ErrorCode code){throw new TenantAccessException(code);}
}

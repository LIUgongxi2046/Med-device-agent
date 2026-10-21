package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static com.ruoyi.medical.persistence.NativeImageQaProtocol.*;

/** Opt-in authenticated API for bundled synthetic fixture only. Never registers FORMAL evidence. */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="medical.multimodal-qa-simulation",name="enabled",havingValue="true")
public final class NativeImageQaSimulationController {
 private final ApplicationSessionIdentity identities;private final EnterpriseAccess access;private final ProjectService projects;private final PermissionSchema json;
 private record Scope(String tenant,String project,String actor){}
 record Runtime(NativeImageQaSimulation.Journal journal,NativeImageQaMockProvider provider){}
 private final Map<Scope,Runtime> runtimes=new HashMap<>();
 public NativeImageQaSimulationController(ApplicationSessionIdentity i,EnterpriseAccess a,ProjectService p,PermissionSchema j){identities=i;access=a;projects=p;json=j;}
 // Admission is atomic across distinct scopes; simulation work never holds this lock.
 Runtime runtimeForScope(String tenant,String project,String actor){
  Scope scope=new Scope(tenant,project,actor);
  synchronized(runtimes){
   Runtime existing=runtimes.get(scope);
   if(existing!=null)return existing;
   if(runtimes.size()>=128)throw new IllegalArgumentException("SIMULATION_CAPACITY_REACHED");
   Runtime created=new Runtime(new NativeImageQaSimulation.Journal(10000),new NativeImageQaMockProvider());
   runtimes.put(scope,created);return created;
  }
 }
 static Image fixture()throws IOException,java.security.NoSuchAlgorithmException{
  var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,0xff0000);image.setRGB(1,0,0x00ff00);image.setRGB(0,1,0x0000ff);image.setRGB(1,1,0xffffff);
  var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);byte[] bytes=out.toByteArray();
  return new Image("01900000-0000-7000-8000-000000000019","01900000-0000-7000-8000-000000000020","fixture/b19-transport-grid.png",WireValues.sha256(bytes),"image/png",2,2,new Region(0,0,2,2),Base64.getEncoder().encodeToString(bytes));
 }
 @GetMapping("/api/multimodal-qa/simulations/projects/{projectId}/profile")
 public JsonNode profile(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String projectId)throws Exception{
  String revision=FormalAgentPublicationController.headers(request);FormalAgentPublicationController.noBody(request);var subject=identities.requireCurrent(request,auth);projects.read(subject,revision,projectId);
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version",VERSION);var out=json.object().put("schema_version",VERSION).put("mode","SIM").put("provider",PROVIDER).put("capability",CAPABILITY).put("synthetic",true).put("formal_evidence_eligible",false).put("maximum_scope_units",10000).put("persistence","PROCESS_LOCAL_ONLY");out.set("fixture",json.tree(fixture()));return out;
 }
 @PostMapping(value="/api/multimodal-qa/simulations/{operation}",consumes="application/json")
 public JsonNode operate(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String operation)throws Exception{
  String revision=FormalAgentPublicationController.headers(request);var subject=identities.requireCurrent(request,auth);
  String raw=new String(FormalAgentPublicationController.body(request,6*1024*1024),java.nio.charset.StandardCharsets.UTF_8);
  Context context;Context review=null;
  if("review".equals(operation)){JsonNode body=NativeImageQaProtocol.parse(raw);if(!body.isObject()||body.size()!=2||!body.has("answer")||!body.has("reviewer"))throw new IllegalArgumentException("REVIEW_CONTEXT_REQUIRED");context=read(body.required("answer").toString());review=read(body.required("reviewer").toString());}else context=read(raw);
  if(!Set.of("prepare","dispatch","recover","review").contains(operation))throw new IllegalArgumentException("OPERATION_INVALID");
  var authority=(NativeImageQaSimulation.Authority)c->{
   // Fixture-only consent/rights are local to this mock profile; this is NOT a production model.send receipt.
   require(c.images().equals(List.of(fixture())),"REGISTERED_FIXTURE_REQUIRED");
   projects.read(subject,revision,c.projectId());
   access.execute(subject,a->{if(!a.context().tenant_id().equals(c.tenantId())||!a.context().context_revision().equals(revision)||a.simulationWorker()||a.formalWorker()||a.controlWorker())throw new TenantAccessException(ErrorCode.ACCESS_DENIED);return true;});
  };
  authority.requireCurrent(context);
  Runtime runtime=runtimeForScope(context.tenantId(),context.projectId(),subject.user().user_id());
  var sim=new NativeImageQaSimulation(authority,runtime.provider(),runtime.journal());Object result;
  switch(operation){case "prepare"->result=Map.of("context_sha256",sim.prepare(context));case "dispatch"->result=sim.dispatch(context);case "recover"->result=sim.recover(context).<Object>map(r->r).orElse(Map.of("state","UNCERTAIN","retry_allowed",false));case "review"->result=sim.independentReview(context,review);default->throw new IllegalArgumentException();}
  response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version",VERSION);
  var out=json.object().put("schema_version",VERSION).put("mode","SIM").put("synthetic",true).put("formal_evidence_eligible",false).put("reserved_units",runtime.journal().reservedUnits()).put("settled_units",runtime.journal().settledUnits());out.set("data",json.tree(result));return out;
 }
 @ExceptionHandler({IllegalArgumentException.class,com.fasterxml.jackson.core.JsonProcessingException.class})void malformed(HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class)void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class)void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new com.ruoyi.medical.identity.IdentityErrorResponseWriter().write(r,SimulationRuleVersionController.errorStatus(e.code()),e.code());}
}

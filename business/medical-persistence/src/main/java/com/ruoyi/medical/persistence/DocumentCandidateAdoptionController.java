package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.*;
import jakarta.servlet.http.*;
import java.util.*;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnBean(DocumentCandidateAdoptionEntryService.class)
public final class DocumentCandidateAdoptionController {
 private final ApplicationSessionIdentity identities;private final DocumentCandidateAdoptionEntryService entry;private final DocumentCandidateAdoptionSchema schema;private final PermissionSchema json;private final DocumentWorkspaceSchema workspace;
 public DocumentCandidateAdoptionController(ApplicationSessionIdentity identities,DocumentCandidateAdoptionEntryService entry,PermissionSchema json)throws Exception{this.identities=identities;this.entry=entry;this.json=json;schema=new DocumentCandidateAdoptionSchema();workspace=new DocumentWorkspaceSchema();}
 @GetMapping("/api/documents/{id}/candidates")
 public ResponseEntity<JsonNode> directory(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception{
  WireValues.uuid(id);if(!"1.0.0".equals(one(request,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=one(request,"X-Context-Revision");PermissionSchema.revision(context);for(var field:request.getParameterMap().entrySet())if(!field.getKey().equals("after")||field.getValue().length!=1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
  var value=entry.directory(identities.requireCurrent(request,auth),context,id,request.getParameter("after"));workspace.validate("CandidateDirectory",value);return answer(response,json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),context,value)),200);
 }
 @PostMapping(value="/api/runs/{id}/document-candidates")
 public ResponseEntity<JsonNode> materialize(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception{var h=headers(request,id,true);if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return answer(response,entry.materialize(identities.requireCurrent(request,auth),h[0],id),201);}
 @GetMapping("/api/candidates/{id}")
 public ResponseEntity<JsonNode> candidate(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception{var h=headers(request,id,false);if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);var data=entry.candidateView(identities.requireCurrent(request,auth),h[0],id);response.setHeader("X-Resource-Revision",data.required("revision").asText());return answer(response,json.tree(new InterfaceProtocol.Envelope("1.0.0",UUID.randomUUID().toString(),data.required("revision").asText(),data)),200);}
 @GetMapping("/api/candidates/{id}/document-comparison")
 public ResponseEntity<JsonNode> read(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id)throws Exception{var h=headers(request,id,false);if(request.getInputStream().read()!=-1)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return answer(response,entry.read(identities.requireCurrent(request,auth),h[0],id),200);}
 @PostMapping(value="/api/candidates/{id}/adoption-previews",consumes="application/json")
 public ResponseEntity<JsonNode> preview(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception{var h=headers(request,id,true);var input=schema.read(body,"PreviewInput",DocumentCandidateAdoptionProtocol.PreviewInput.class);return answer(response,entry.preview(identities.requireCurrent(request,auth),h[0],h[1],id,input),201);}
 @PostMapping(value="/api/candidates/{id}/adoptions",consumes="application/json")
 public ResponseEntity<JsonNode> confirm(HttpServletRequest request,HttpServletResponse response,Authentication auth,@PathVariable String id,@RequestBody byte[] body)throws Exception{var h=headers(request,id,true);var input=schema.read(body,"ConfirmInput",DocumentCandidateAdoptionProtocol.ConfirmInput.class);return answer(response,entry.confirm(identities.requireCurrent(request,auth),h[0],h[1],id,input),201);}
 private String[] headers(HttpServletRequest r,String id,boolean write){WireValues.uuid(id);if(r.getQueryString()!=null)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);if(!"1.0.0".equals(one(r,"X-Schema-Version")))throw new SchemaRejection(ErrorCode.SCHEMA_UNSUPPORTED);String context=one(r,"X-Context-Revision"),key=write?one(r,"Idempotency-Key"):null;if(write)json.headers(context,key);else PermissionSchema.revision(context);return new String[]{context,key};}
 private static String one(HttpServletRequest r,String n){var values=Collections.list(r.getHeaders(n));if(values.size()!=1||values.get(0).isBlank())throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);return values.get(0);}
 private ResponseEntity<JsonNode> answer(HttpServletResponse response,JsonNode value,int status){response.setHeader("Cache-Control","no-store");response.setHeader("X-Schema-Version","1.0.0");return ResponseEntity.status(status).body(value);}
 @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class,java.nio.charset.CharacterCodingException.class,IllegalArgumentException.class}) void invalid(HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,ErrorCode.SCHEMA_INVALID);}
 @ExceptionHandler(SchemaRejection.class) void invalid(SchemaRejection e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,422,e.code());}
 @ExceptionHandler(TenantAccessException.class) void denied(TenantAccessException e,HttpServletResponse r)throws IOException{new IdentityErrorResponseWriter().write(r,e.code()==ErrorCode.ACCESS_DENIED?403:e.code()==ErrorCode.FILE_TOO_LARGE?413:409,e.code());}
}

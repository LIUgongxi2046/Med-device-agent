package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;import com.ruoyi.medical.contracts.*;import jakarta.servlet.http.HttpServletResponse;
/** Existing frozen public envelope and exact response headers. Revision belongs to the returned immutable record. */
final class FormalInvocationResponses {
 static JsonNode envelope(JsonNode data,String revision,HttpServletResponse response)throws Exception{
  String id=java.util.UUID.randomUUID().toString();var wire=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(ContentReadAuthorization.canonicalBytes(new InterfaceProtocol.Envelope("1.0.0",id,revision,data)));SchemaCatalogue.fromClasspath().validate("interfaces",wire,"Envelope");response.setHeader("X-Schema-Version","1.0.0");response.setHeader("X-Request-Id",id);response.setHeader("Cache-Control","no-store");return wire;
 }
}

package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.InterfaceProtocol;
import java.util.List;

final class DocumentTechnicalCheckProtocol {
 private DocumentTechnicalCheckProtocol() { }
 record Output(String artifactId,String kind,String schemaRef,S3ObjectWriter.WriteIntent intent,boolean attempted,
               InterfaceProtocol.ObjectRef reference) { }
 record Command(String checkId,String runId,String operationId,String manifestId,String createdAt,String deadline,
        String requestHash,String requestContext,String requestSession,String bodyHash,JsonNode request,JsonNode manifest,JsonNode baseline,JsonNode input,
        JsonNode coverage,JsonNode findings,List<Output> outputs) {
  Command {outputs=List.copyOf(outputs);}
 }
}

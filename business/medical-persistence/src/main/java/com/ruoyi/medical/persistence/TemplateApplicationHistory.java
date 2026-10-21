package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.persistence.mapper.TemplateMapper;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;

/** Verifies immutable decisions before either presentation or operational consumption. */
final class TemplateApplicationHistory {
    record History(String revision,List<JsonNode> decisions) { }
    static History load(TemplateMapper mapper,Map<String,Object> values,PermissionSchema json,
            TemplateApplicabilitySchema schema,String manifest,String classification)throws IOException {
        var reader=JsonMapper.builder().build();var heads=mapper.applicationHead(values);
        if(heads.size()>1)bad();String head="0";
        if(!heads.isEmpty()) {var row=reader.readTree(heads.get(0));metadata(row,manifest,classification);head=row.required("revision").asText();PermissionSchema.revision(head);}
        var result=new ArrayList<JsonNode>();BigInteger revision=BigInteger.ZERO;
        for(var raw:mapper.applicationDecisions(values)) {
            var row=reader.readTree(raw);metadata(row,manifest,classification);var receipt=row.required("receipt");
            if(!DigestUtils.sha256Hex(json.canonical(receipt)).equals(row.required("sha256").asText()))bad();
            schema.verifyEnvelope(receipt);var data=receipt.required("data");schema.verifyApplication("HumanDecisionReceipt",data);var d=data.required("decision");var request=d.required("request");
            revision=revision.add(BigInteger.ONE);
            if(!revision.toString().equals(row.required("revision").asText())||!revision.toString().equals(d.required("resulting_revision").asText())
                ||!revision.toString().equals(receipt.required("revision").asText())
                ||!new BigInteger(request.required("expected_revision").asText()).add(BigInteger.ONE).equals(revision)
                ||!values.get("templateId").equals(d.required("template_version").asText())
                ||!values.get("projectId").equals(request.required("project_id").asText())
                ||!values.get("profileId").equals(request.required("profile_revision").asText())
                ||!d.required("actor_id").equals(row.required("created_by"))||!java.time.Instant.parse(d.required("created_at").asText()).equals(java.time.Instant.parse(row.required("created_at").asText()))
                ||!request.required("reason").equals(row.required("reason"))
                ||!data.required("operation_id").equals(d.required("operation_id"))
                ||!DigestUtils.sha256Hex(json.canonical(d.required("baseline"))).equals(request.required("baseline_sha256").asText())
                ||!d.required("baseline").required("template").required("content_sha256").equals(request.required("template_sha256")))bad();
            ProjectSchema.text(request.required("reason").asText(),2000);result.add(d);
        }
        if(!revision.toString().equals(head))bad();return new History(head,List.copyOf(result));
    }
    private static void metadata(JsonNode row,String manifest,String classification) {
        if(!"1.0.0".equals(row.required("schema_version").asText())||!TemplateApplicabilitySchema.applicationManifestMatches(row.required("schema_manifest_sha256").asText(),manifest)
            ||!classification.equals(row.required("classification").asText()))bad();
        WireValues.uuid(row.required("created_by").asText());WireValues.utcTime(row.required("created_at").asText());
    }
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
}

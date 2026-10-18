package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.SimulationInvocationStore.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.InterfaceProtocol.ObjectRef;
import com.ruoyi.medical.contracts.SchemaCatalogue;
import com.ruoyi.medical.contracts.WireValues;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Durable local-write command, deliberately separate from a provider usage receipt. */
final class SimulationResultSchema {
    private final SimulationInvocationSchema schema;
    private final SchemaCatalogue catalogue;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    SimulationResultSchema(SimulationInvocationSchema schema)throws Exception {this.schema=schema;catalogue=SchemaCatalogue.fromClasspath();}
    record Item(String artifactId,String kind,String schemaRef,String schemaHash,S3ObjectWriter.WriteIntent intent,boolean writing,ObjectRef reference) { }
    record Command(String schemaVersion,List<Item> items) { Command { items=List.copyOf(items); } }
    record Accounting(long input,long output,long charged,String status,String usageOrigin,JsonNode fixtureUsage) { }
    String manifest(){return schema.manifest();}
    Command create(JsonNode request,JsonNode payload)throws Exception {
        var id=request.required("binding").required("identity");var items=new ArrayList<Item>();
        for(String part:List.of("result","provenance")) {
            byte[] bytes=bytes(payload,part);String artifact=UUID.randomUUID().toString();boolean result="result".equals(part);
            String document=result?"model-stream":"local-simulation-provenance";
            items.add(new Item(artifact,result?"MODEL_RESULT":"SIMULATION_PROVENANCE",catalogue.schemaRef(document,result?"ModelResult":null),catalogue.schemaDigest(document),
                new S3ObjectWriter.WriteIntent(UUID.randomUUID().toString(),"tenants/"+text(id,"tenant_id")+"/simulation-results/"+text(id,"invocation_id")+"/"+artifact,
                    WireValues.sha256(bytes),Integer.toString(bytes.length),"application/json"),false,null));
        }
        return new Command("1.0.0",items);
    }
    byte[] bytes(JsonNode payload,String part)throws Exception {return schema.canonical(payload.required(part)).getBytes(StandardCharsets.UTF_8);}
    String encode(Command command)throws Exception{return schema.canonical(json.valueToTree(command));}
    Command decode(String value)throws Exception{return json.readValue(value,Command.class);}
    void require(Command command,JsonNode request,JsonNode payload)throws Exception {
        if(!"1.0.0".equals(command.schemaVersion())||command.items().size()!=2) mismatch();
        var ids=new HashSet<String>();var writes=new HashSet<String>();var id=request.required("binding").required("identity");
        for(int n=0;n<2;n++) {
            var item=command.items().get(n);WireValues.uuid(item.artifactId());if(!ids.add(item.artifactId())||!writes.add(item.intent().writeId()))mismatch();
            String document=n==0?"model-stream":"local-simulation-provenance";byte[] bytes=bytes(payload,n==0?"result":"provenance");
            if(!item.kind().equals(n==0?"MODEL_RESULT":"SIMULATION_PROVENANCE")||!item.schemaRef().equals(catalogue.schemaRef(document,n==0?"ModelResult":null))
                ||!item.schemaHash().equals(catalogue.schemaDigest(document))||!item.intent().objectKey().equals("tenants/"+text(id,"tenant_id")+"/simulation-results/"+text(id,"invocation_id")+"/"+item.artifactId())
                ||!item.intent().sha256().equals(WireValues.sha256(bytes))||!item.intent().sizeBytes().equals(Integer.toString(bytes.length))||!item.intent().mediaType().equals("application/json"))mismatch();
            if(item.reference()!=null){if(!item.writing())mismatch();requireReference(item.intent(),item.reference());}
        }
    }
    Command writing(Command command,int index){var list=new ArrayList<>(command.items());var i=list.get(index);list.set(index,new Item(i.artifactId(),i.kind(),i.schemaRef(),i.schemaHash(),i.intent(),true,i.reference()));return new Command(command.schemaVersion(),list);}
    Command verified(Command command,int index,ObjectRef reference){var list=new ArrayList<>(command.items());var i=list.get(index);requireReference(i.intent(),reference);list.set(index,new Item(i.artifactId(),i.kind(),i.schemaRef(),i.schemaHash(),i.intent(),true,reference));return new Command(command.schemaVersion(),list);}
    static void requireReference(S3ObjectWriter.WriteIntent intent,ObjectRef reference) {
        if(reference==null||reference.objectVersion()==null||reference.objectVersion().isBlank()||"null".equals(reference.objectVersion())
            ||!intent.objectKey().equals(reference.objectKey())||!intent.sha256().equals(reference.sha256())||!intent.sizeBytes().equals(reference.sizeBytes())||!intent.mediaType().equals(reference.mediaType()))mismatch();
    }
    Accounting accounting(JsonNode request,JsonNode payload)throws Exception {
        // Reuse the exact canonical-byte policy in simulation_control.record_result, never fixture tokens.
        byte[] result=bytes(payload,"result"),proof=bytes(payload,"provenance");schema.receipt(request,result,proof);
        long input=request.required("binding").required("exact_input").required("units").longValue();
        JsonNode usage=payload.required("result").required("usage");
        if(!usage.isNull()&&!usage.required("source_receipt_id").equals(payload.required("provenance").required("receipt_id")))mismatch();
        return new Accounting(input,result.length,SimulationInvocationSchema.sum(input,result.length),
            "error".equals(payload.required("result").required("reason").required("kind").textValue())?"FAILED":"SUCCEEDED",
            usage.isNull()?"UNKNOWN":"SIMULATION_FIXTURE",usage.deepCopy());
    }
    static void mismatch(){SimulationInvocationSchema.mismatch();}
}

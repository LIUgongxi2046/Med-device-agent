package com.ruoyi.medical.persistence;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.ErrorCode;
import java.util.*;
/** One real physical object attempt. Historical completion never calls this producer. */
public final class TaskStrictFieldOutputProducer {
    private final TaskStrictFieldOutputLedger ledger;private final RuntimeObjectTransport transport;private final TaskStrictFieldSourceReader sources;private final TaskStrictFieldSchema schema;
    TaskStrictFieldOutputProducer(TaskStrictFieldOutputLedger ledger,RuntimeObjectTransport transport,TaskStrictFieldSourceReader sources,TaskStrictFieldSchema schema){this.ledger=ledger;this.transport=transport;this.sources=sources;this.schema=schema;}
    JsonNode produce(TaskStrictFieldNode node,TaskStrictFieldNode.Handle handle,JsonNode result)throws Exception{
        if(transport==null)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);var storage=transport.storageBinding();var input=node.execute(handle,c->c.input().input());var methodReads=new ArrayList<JsonNode>();for(var context:TaskStrictFieldOutputLedger.methodContexts(schema,input))methodReads.add(sources.readMethod(node,handle,context));
        var attempt=node.execute(handle,c->ledger.prepare(c,result,methodReads,storage));var transferred=transport.transferVerified(attempt,()->org.springframework.util.function.ThrowingSupplier.of(()->{transport.requireStorageBinding(storage);node.execute(handle,c->{ledger.require(c,attempt,result,storage);return null;});return null;}).get());
        return node.execute(handle,c->{transport.requireStorageBinding(storage);return ledger.register(c,attempt,result,transferred,storage);});
    }
}

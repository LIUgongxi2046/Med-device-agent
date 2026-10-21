package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.medical.contracts.*;
import com.ruoyi.medical.identity.ApplicationSessionIdentity;
import com.ruoyi.medical.persistence.erasure.*;
import java.time.Instant;
import org.springframework.stereotype.Component;
import static com.ruoyi.medical.persistence.erasure.ExternalErasure.*;

/** Normal authenticated application producer/consumer. Request bodies cannot provide approval,
 * completeness, receipts, endpoints or proof mode: all come from registered authority and stored review. */
@Component
public final class ExternalErasureService {
    private final SourceCopyAuthority sourceAuthority;
    private final ExternalErasureRegistry registry;
    public ExternalErasureService(SourceCopyAuthority sourceAuthority,ExternalErasureRegistry registry){this.sourceAuthority=sourceAuthority;this.registry=registry;}
    public record EraseRequest(String schemaVersion,String authorityId,String scopeId,Copy copy) {
        public EraseRequest {if(!VERSION.equals(schemaVersion)||authorityId==null||scopeId==null||copy==null)throw new IllegalArgumentException("INVALID_ERASE_REQUEST");}
    }
    private record Session(Approval approval,ExternalErasureAdapter adapter) {}
    private Session session(ApplicationSessionIdentity.Verified actor,String context,String source,String id,String scope) {
        Approval approval=sourceAuthority.externalOperation(actor,context,source,id,scope,a->a);
        var guard=new Authority(){
            public <T>T current(Approval expected,Action<T> action)throws Exception {
                return sourceAuthority.externalOperation(actor,context,source,id,scope,current->{
                    if(!current.equals(expected))throw new TenantAccessException(ErrorCode.SCOPE_STALE);return action.run();
                });
            }
            public Instant now(){return Instant.now();}
        };
        return new Session(approval,registry.adapter(approval,guard));
    }
    public JsonNode erase(ApplicationSessionIdentity.Verified actor,String context,String source,String key,byte[] body)throws Exception {
        RuntimeRetentionSchema.key(key);if(body==null||body.length>65536)throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);
        EraseRequest input;
        try{input=registry.mapper.readValue(body,EraseRequest.class);}catch(Exception malformed){throw new SchemaRejection(ErrorCode.SCHEMA_INVALID);}
        var session=session(actor,context,source,input.authorityId(),input.scopeId());
        // Namespacing prevents same key in another tenant, source or adapter from aliasing a prior operation.
        String original=org.apache.commons.codec.digest.DigestUtils.sha256Hex(session.approval().scope().tenant()+"/"+source+"/"+input.scopeId()+"/"+key);
        return registry.mapper.valueToTree(session.adapter().erase(new Delete(VERSION,session.approval().proof(),original,session.approval(),input.copy())));
    }
    public JsonNode recover(ApplicationSessionIdentity.Verified actor,String context,String source,String id,String scope,String key)throws Exception {
        RuntimeRetentionSchema.key(key);var session=session(actor,context,source,id,scope);
        String original=org.apache.commons.codec.digest.DigestUtils.sha256Hex(session.approval().scope().tenant()+"/"+source+"/"+scope+"/"+key);
        return registry.mapper.valueToTree(session.adapter().recover(session.approval(),original));
    }
    public JsonNode inventory(ApplicationSessionIdentity.Verified actor,String context,String source,String id,String scope)throws Exception {
        var session=session(actor,context,source,id,scope);return registry.mapper.valueToTree(session.adapter().inventory(session.approval()));
    }
}

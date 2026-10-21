package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Closed application-level configuration contract. The customer API document itself is
 * opaque here; only the exact-spec adapter interprets its endpoint/pagination semantics. */
final class CustomerConnectorProtocol {
    static final int MAX_ITEMS=25, MAX_ORIGINAL=8*1024*1024, MAX_PAGE_BYTES=32*1024*1024;
    static final String MANIFEST=manifest();
    static final JsonMapper JSON=JsonMapper.builder()
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    static JsonNode parse(byte[] bytes)throws Exception {if(bytes.length==0||bytes.length>65536)bad();return JSON.readTree(bytes);}
    static void definition(JsonNode b){
        closed(b,"schema_version","connector_id","project_id","expected_revision","interface_spec","account_mapping",
            "ownership_scope","credential_reference_id","credential_revision","expires_at","classification");
        if(!"1.0.0".equals(text(b,"schema_version")))bad(); uuid(text(b,"connector_id"));uuid(text(b,"project_id"));
        if(!text(b,"expected_revision").matches("0|[1-9][0-9]{0,18}"))bad();
        if(!Set.of("PUBLIC","INTERNAL","RESTRICTED","PERSONAL_HEALTH").contains(text(b,"classification")))bad();
        WireValues.utcTime(text(b,"expires_at"));
        if(!b.required("interface_spec").isNull()){
            var spec=b.required("interface_spec");closed(spec,"version","document");bounded(text(spec,"version"),128);
            if(!spec.required("document").isObject()||spec.required("document").isEmpty())bad();
        }
        if(!b.required("account_mapping").isNull()){
            var list=b.required("account_mapping");if(!list.isArray()||list.isEmpty()||list.size()>100)bad();var ids=new HashSet<String>();
            for(var item:list){closed(item,"external_account_id","user_id");bounded(text(item,"external_account_id"),512);uuid(text(item,"user_id"));if(!ids.add(text(item,"external_account_id")))bad();}
        }
        if(!b.required("ownership_scope").isNull()){
            var scope=b.required("ownership_scope");closed(scope,"project_id","external_scope","document_ownership_rules");
            if(!text(b,"project_id").equals(text(scope,"project_id"))||!scope.required("external_scope").isObject()||scope.required("external_scope").isEmpty()
                ||!scope.required("document_ownership_rules").isObject()||scope.required("document_ownership_rules").isEmpty())bad();
        }
        if(!b.required("credential_reference_id").isNull())uuid(text(b,"credential_reference_id"));
        if(!b.required("credential_revision").isNull())bounded(text(b,"credential_revision"),128);
        if(b.required("credential_reference_id").isNull()!=b.required("credential_revision").isNull())bad();
    }
    static List<String> missing(JsonNode b){var result=new ArrayList<String>();for(String key:List.of("interface_spec","account_mapping","ownership_scope","credential_reference_id"))if(b.required(key).isNull())result.add(key);return List.copyOf(result);}
    static void account(JsonNode definition,String external){bounded(external,512);boolean found=false;for(var m:definition.required("account_mapping"))if(external.equals(text(m,"external_account_id")))found=true;if(!found)deny();}
    static void exchanges(List<CustomerConnectorAdapter.Exchange> list)throws Exception {
        if(list.isEmpty()||list.size()>MAX_ITEMS+1)bad();long total=0;
        for(var e:list){bounded(e.requestMethod(),16);bounded(e.requestUri(),8192);var uri=URI.create(e.requestUri());if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getFragment()!=null)bad();
            if(e.responseStatus()<100||e.responseStatus()>599)bad();WireValues.utcTime(e.startedAt());WireValues.utcTime(e.completedAt());
            if(Instant.parse(e.completedAt()).isBefore(Instant.parse(e.startedAt())))bad();
            if(!e.requestHeaders().isArray()||!e.responseHeaders().isArray()||e.requestHeaders().size()>64||e.responseHeaders().size()>64)bad();
            // Authorization bytes must stay in the deployment credential vault. Originals
            // retain the exact non-secret transport fields and the existing credential handle.
            for(var headers:List.of(e.requestHeaders(),e.responseHeaders()))for(var h:headers){closed(h,"name","value");bounded(text(h,"name"),256);
                String value=text(h,"value");if(value.length()>8192||value.codePoints().anyMatch(Character::isISOControl))bad();
                if(Set.of("authorization","proxy-authorization","cookie","set-cookie","x-api-key").contains(text(h,"name").toLowerCase(Locale.ROOT)))deny();}
            total+=(long)e.requestBody().length+e.responseBody().length;if(total>MAX_PAGE_BYTES)bad();
        }
    }
    static void page(CustomerConnectorAdapter.Request request,CustomerConnectorAdapter.Page page)throws Exception{
        if(page.items().size()>request.maximumItems())bad();exchanges(page.exchanges());if(page.exchanges().stream().anyMatch(e->!e.complete()||e.responseStatus()<200||e.responseStatus()>299))bad();long size=0;var seen=new HashSet<String>();
        validateCursor(page.nextCursor(),0);if(canonicalBytes(page.nextCursor()).length>8192)bad();
        if(page.hasMore()&&page.nextCursor().equals(request.cursor()))bad();
        for(var i:page.items()){
            bounded(i.externalId(),512);bounded(i.externalVersion(),256);account(request.definition(),i.externalAccountId());
            if(!request.projectId().equals(i.ownerProjectId())||!seen.add(i.externalId()))bad();
            if(!i.metadata().isObject()||ContentReadAuthorization.canonicalBytes(i.metadata()).length>16384)bad();
            WireValues.utcTime(i.acquiredAt());size+=i.original().length;
            if(i.original().length>MAX_ORIGINAL||size>MAX_PAGE_BYTES||(!i.revoked()&&i.original().length==0)||i.revoked()&&i.original().length!=0)bad();
            if(!i.revoked()){bounded(i.mediaType(),128);bounded(i.originalUrl(),2048);}
        }
    }
    static void closed(JsonNode n,String... fields){ProfessionalRuntimeObservations.closed(n,Set.of(fields));}
    static String text(JsonNode n,String key){var v=n.required(key);if(!v.isTextual())bad();return v.textValue();}
    static String str(Map<String,Object> n,String key){var v=n.get(key);if(v==null)bad();return v.toString();}
    /** The JCS implementation requires a container root. Canonicalize a singleton array
     * and remove only its brackets so scalar cursors and identifiers retain JSON types. */
    static void validateCursor(JsonNode value,int depth){
        if(value==null||depth>64||!(value.isContainerNode()||value.isTextual()||value.isNumber()||value.isBoolean()||value.isNull()))bad();
        if(value.isFloatingPointNumber()&&!Double.isFinite(value.doubleValue()))bad();
        for(var child:value)validateCursor(child,depth+1);
    }
    static byte[] canonicalBytes(Object value)throws java.io.IOException{
        if(value instanceof Double number&&!Double.isFinite(number)||value instanceof Float single&&!Float.isFinite(single))bad();
        if(value instanceof JsonNode node&&node.isFloatingPointNumber()&&!Double.isFinite(node.doubleValue()))bad();
        byte[] wrapped=ContentReadAuthorization.canonicalBytes(Collections.singletonList(value));
        return Arrays.copyOfRange(wrapped,1,wrapped.length-1);
    }
    static String hash(Object value)throws Exception{return WireValues.sha256(canonicalBytes(value));}
    static String id(String tenant,String kind,String actor,String key){return UUID.nameUUIDFromBytes((tenant+"|"+kind+"|"+actor+"|"+key).getBytes(StandardCharsets.UTF_8)).toString();}
    static void uuid(String value){WireValues.uuid(value);}
    static void bounded(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))bad();}
    static <T>T one(List<T> rows){if(rows.size()!=1)deny();return rows.get(0);}
    static void changed(int count){if(count!=1)stale();}
    static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    static void deny(){throw new TenantAccessException(ErrorCode.ACCESS_DENIED);}
    static void stale(){throw new TenantAccessException(ErrorCode.SCOPE_STALE);}
    private static String manifest(){try(var in=CustomerConnectorProtocol.class.getResourceAsStream("/customer-connector.schema.json")){if(in==null)throw new IllegalStateException("CONNECTOR_SCHEMA_MISSING");return WireValues.sha256(in.readAllBytes());}catch(java.io.IOException | java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}

package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.medical.contracts.WireValues;
import com.ruoyi.medical.identity.ServiceClientTlsStore;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLParameters;

/** Fixed service-only route; fresh protected TLS material/client per call, no
 * redirect, cookie, browser session or automatic transport retry. */
final class TaskRunRelayHttpClientV2 {
    private final URI endpoint;private final ServiceClientTlsStore.ContextSource contexts;private final Duration timeout;private final int maximum;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    TaskRunRelayHttpClientV2(URI origin,ServiceClientTlsStore.ContextSource contexts,Duration timeout,int maximum){
        Objects.requireNonNull(origin);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||!Set.of("","/").contains(origin.getRawPath())||origin.getPort()==0||origin.getPort() < -1)throw new IllegalArgumentException("V2_RELAY_HTTPS_ORIGIN_REQUIRED");
        if(timeout==null||timeout.compareTo(Duration.ofMillis(100))<0||timeout.compareTo(Duration.ofMinutes(2))>0||maximum<4096||maximum>1048576)throw new IllegalArgumentException("BOUNDED_V2_RELAY_HTTP_REQUIRED");
        endpoint=origin.resolve(TaskRunRelayControllerV2.ROUTE);this.contexts=Objects.requireNonNull(contexts);this.timeout=timeout;this.maximum=maximum;
    }
    List<String> candidates(String tenant,String cursor)throws Exception{
        var request=request("LIST_ORIGINALS",tenant).putNull("after_event_id");if(cursor!=null){WireValues.uuid(cursor);request.put("after_event_id",cursor);}return readCandidates(call(request),cursor);
    }
    static List<String> readCandidates(JsonNode response,String cursor){
        ProfessionalRuntimeObservations.closed(response,Set.of("schema_version","event_ids","next_after_event_id"));version(response);
        var ids=response.required("event_ids");if(!ids.isArray()||ids.size()>100)invalid();var result=new ArrayList<String>();String prior=cursor==null?"":cursor;
        for(var id:ids){if(!id.isTextual())invalid();String value=id.textValue();WireValues.uuid(value);if(value.compareTo(prior)<=0)invalid();result.add(value);prior=value;}
        if(result.isEmpty()){if(!response.required("next_after_event_id").isNull())invalid();}else if(!prior.equals(response.required("next_after_event_id").textValue()))invalid();return List.copyOf(result);
    }
    boolean deliver(String tenant,String event)throws Exception{
        WireValues.uuid(event);var request=request("DELIVER_ORIGINAL",tenant).put("event_id",event);return readDelivered(call(request),tenant,event);
    }
    static boolean readDelivered(JsonNode response,String tenant,String event){
        ProfessionalRuntimeObservations.closed(response,Set.of("schema_version","tenant_id","event_id","state","receipt"));version(response);if(!tenant.equals(response.required("tenant_id").textValue())||!event.equals(response.required("event_id").textValue()))invalid();String state=response.required("state").textValue();
        if("SIGNALLED_NOT_CONSUMED".equals(state)){if(!response.required("receipt").isNull())invalid();return false;}
        var receipt=response.required("receipt");if(!receipt.isObject())invalid();
        if("PRE_RUNTIME_CANCELLED".equals(state)){if(!"TASK_PRE_RUNTIME_CANCELLED".equals(receipt.required("kind").textValue())||!"CANCELLED".equals(receipt.required("status").textValue())||!receipt.required("started").isBoolean()||receipt.required("started").booleanValue())invalid();WireValues.uuid(receipt.required("receipt_id").textValue());return true;}
        if("START_ACCEPTED".equals(state)){WireValues.uuid(receipt.required("temporal_run_id").textValue());return true;}
        if("CONSUMED".equals(state)){WireValues.uuid(receipt.required("commandId").textValue());WireValues.hash(receipt.required("commandSha256").textValue());if(!receipt.required("cancelRequested").isBoolean()||!receipt.required("cancelRequested").booleanValue())invalid();return true;}
        invalid();return false;
    }
    private ObjectNode request(String operation,String tenant){WireValues.uuid(tenant);return json.createObjectNode().put("schema_version","2.0.0").put("operation",operation).put("tenant_id",tenant);}
    private JsonNode call(ObjectNode body)throws Exception{
        var parameters=new SSLParameters();parameters.setProtocols(new String[]{"TLSv1.3"});parameters.setEndpointIdentificationAlgorithm("HTTPS");
        var client=HttpClient.newBuilder().sslContext(Objects.requireNonNull(contexts.create())).sslParameters(parameters).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
        var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
        var pending=client.sendAsync(request,info->{if(info.statusCode()!=200||!"application/json".equalsIgnoreCase(info.headers().firstValue("content-type").orElse("").split(";",2)[0].trim())||info.headers().firstValueAsLong("content-length").orElse(0)>maximum)throw new IllegalStateException("V2_RELAY_RESPONSE_REJECTED");return new LimitedBody(maximum);});
        try{var response=pending.get(timeout.toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);return json.readTree(response.body());}
        catch(InterruptedException interrupted){pending.cancel(true);Thread.currentThread().interrupt();throw interrupted;}
        catch(Exception failure){pending.cancel(true);throw failure;}
    }
    private static void version(JsonNode n){if(!"2.0.0".equals(n.required("schema_version").textValue()))invalid();}
    private static void invalid(){throw new IllegalArgumentException("V2_RELAY_RESPONSE_INVALID");}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private final long maximum;private long received;private java.util.concurrent.Flow.Subscription subscription;
        private LimitedBody(long maximum){this.maximum=maximum;}
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody(){return delegate.getBody();}
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){this.subscription=subscription;delegate.onSubscribe(subscription);}
        @Override public void onNext(List<java.nio.ByteBuffer> buffers){for(var buffer:buffers){received+=buffer.remaining();if(received>maximum){subscription.cancel();delegate.onError(new IllegalStateException("V2_RELAY_RESPONSE_CAPACITY"));return;}}delegate.onNext(buffers);}
        @Override public void onError(Throwable failure){delegate.onError(failure);}
        @Override public void onComplete(){delegate.onComplete();}
    }
}

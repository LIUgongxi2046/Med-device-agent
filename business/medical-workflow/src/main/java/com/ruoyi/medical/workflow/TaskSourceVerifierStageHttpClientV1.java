package com.ruoyi.medical.workflow;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskSourceVerifierStageProtocolV1.*;

/** Fixed private service route. A fresh operator-pinned SSLContext and HttpClient
 * per call prevent resumed TLS sessions bypassing current identity/revocation checks.
 * There is deliberately no transport retry after an ambiguous response. */
public final class TaskSourceVerifierStageHttpClientV1 implements TaskSourceVerifierStageActivitiesV1 {
    @FunctionalInterface public interface TlsContexts {SSLContext create()throws Exception;}
    private final URI endpoint;private final TlsContexts tls;private final Duration timeout;private final int maximumBytes;private final TaskSourceVerifierStageSchemaV1 schema;
    public TaskSourceVerifierStageHttpClientV1(URI origin,TlsContexts tls,Duration timeout,int maximumBytes,TaskSourceVerifierStageSchemaV1 schema){
        Objects.requireNonNull(origin);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||origin.getUserInfo()!=null||!Set.of("","/").contains(origin.getRawPath())||origin.getPort()==0||origin.getPort() < -1)throw new IllegalArgumentException("ACTIVITY_HTTPS_ORIGIN_REQUIRED");
        this.endpoint=origin.resolve(TaskSourceVerifierStageSchemaV1.ROUTE);this.tls=Objects.requireNonNull(tls);this.timeout=Objects.requireNonNull(timeout);this.schema=Objects.requireNonNull(schema);
        if(timeout.isZero()||timeout.isNegative()||timeout.compareTo(Duration.ofMinutes(5))>0||maximumBytes<1024||maximumBytes>16777216)throw new IllegalArgumentException("ACTIVITY_CAPACITY_REQUIRED");this.maximumBytes=maximumBytes;
    }
    private Object call(TaskSourceVerifierStageSchemaV1.Request wire){try{
        byte[] body=schema.request(wire);if(body.length>maximumBytes)throw new IllegalArgumentException("ACTIVITY_REQUEST_CAPACITY");
        var parameters=new SSLParameters();parameters.setProtocols(new String[]{"TLSv1.3"});parameters.setEndpointIdentificationAlgorithm("HTTPS");
        var client=HttpClient.newBuilder().sslContext(Objects.requireNonNull(tls.create())).sslParameters(parameters).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
        var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type","application/json").header("Accept","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var pending=client.sendAsync(request,info->{
            if(info.statusCode()!=200)throw new IllegalStateException("ACTIVITY_RESPONSE_REJECTED");
            var media=info.headers().firstValue("content-type").orElse("").split(";",2)[0].trim();if(!"application/json".equalsIgnoreCase(media))throw new IllegalStateException("ACTIVITY_RESPONSE_TYPE");
            if(info.headers().firstValueAsLong("content-length").orElse(0)>maximumBytes)throw new IllegalStateException("ACTIVITY_RESPONSE_CAPACITY");
            return new LimitedBody(maximumBytes);
        });
        HttpResponse<byte[]> response;
        try{response=pending.get(timeout.toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);}catch(Exception failure){pending.cancel(true);throw failure;}
        byte[] bytes=response.body();if(bytes.length==0)throw new IllegalStateException("ACTIVITY_RESPONSE_CAPACITY");
        return schema.response(bytes,wire);
    }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("ACTIVITY_TRANSPORT_INTERRUPTED",interrupted);}catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException("ACTIVITY_TRANSPORT_UNAVAILABLE",failure);}}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private final long maximum;private long received;private java.util.concurrent.Flow.Subscription subscription;
        private LimitedBody(long maximum){this.maximum=maximum;}
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody(){return delegate.getBody();}
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){this.subscription=subscription;delegate.onSubscribe(subscription);}
        @Override public void onNext(List<java.nio.ByteBuffer> buffers){for(var buffer:buffers){received+=buffer.remaining();if(received>maximum){subscription.cancel();delegate.onError(new IllegalStateException("ACTIVITY_RESPONSE_CAPACITY"));return;}}delegate.onNext(buffers);}
        @Override public void onError(Throwable failure){delegate.onError(failure);}
        @Override public void onComplete(){delegate.onComplete();}
    }
    @Override public InventoryV1 prepare(Reference r){return (InventoryV1)call(new TaskSourceVerifierStageSchemaV1.Request("1.0.0","PREPARE",r,null,null,null));}
    @Override public InventoryV1 inventory(Reference r){return (InventoryV1)call(new TaskSourceVerifierStageSchemaV1.Request("1.0.0","INVENTORY",r,null,null,null));}
    @Override public DispatchedV1 dispatch(Reference r,CommandV1 c){return (DispatchedV1)call(new TaskSourceVerifierStageSchemaV1.Request("1.0.0","DISPATCH",r,c,null,null));}
    @Override public RecoveryV1 recover(Reference r,CommandV1 c){return (RecoveryV1)call(new TaskSourceVerifierStageSchemaV1.Request("1.0.0","RECOVER",r,c,null,null));}
    @Override public CommitV1 commit(Reference r,DispatchedV1 d,ReviewCompletedV1 c){return (CommitV1)call(new TaskSourceVerifierStageSchemaV1.Request("1.0.0","COMMIT",r,null,d,c));}
}

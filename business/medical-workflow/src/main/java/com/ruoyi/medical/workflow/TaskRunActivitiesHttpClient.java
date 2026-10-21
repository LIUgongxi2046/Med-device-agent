package com.ruoyi.medical.workflow;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.*;
import static com.ruoyi.medical.workflow.WorkflowProtocol.*;
import static com.ruoyi.medical.workflow.TaskRunActivitySchema.Operation;

/** Fixed private service route. A fresh operator-pinned SSLContext and HttpClient
 * per call prevent resumed TLS sessions bypassing current identity/revocation checks.
 * There is deliberately no transport retry after an ambiguous response. */
public final class TaskRunActivitiesHttpClient implements RunActivities {
    @FunctionalInterface public interface TlsContexts {SSLContext create()throws Exception;}
    private final URI endpoint;private final TlsContexts tls;private final Duration timeout;private final int maximumBytes;private final TaskRunActivitySchema schema;
    public TaskRunActivitiesHttpClient(URI origin,TlsContexts tls,Duration timeout,int maximumBytes,TaskRunActivitySchema schema){
        Objects.requireNonNull(origin);if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getRawQuery()!=null||origin.getRawFragment()!=null||origin.getUserInfo()!=null||!Set.of("","/").contains(origin.getRawPath())||origin.getPort()==0||origin.getPort() < -1)throw new IllegalArgumentException("ACTIVITY_HTTPS_ORIGIN_REQUIRED");
        this.endpoint=origin.resolve(TaskRunActivitySchema.ROUTE);this.tls=Objects.requireNonNull(tls);this.timeout=Objects.requireNonNull(timeout);this.schema=Objects.requireNonNull(schema);
        if(timeout.isZero()||timeout.isNegative()||timeout.compareTo(Duration.ofMinutes(5))>0||maximumBytes<1024||maximumBytes>16777216)throw new IllegalArgumentException("ACTIVITY_CAPACITY_REQUIRED");this.maximumBytes=maximumBytes;
    }
    private <T>T call(Operation operation,Reference reference,Class<T> type,Object... fields){try{
        byte[] body=schema.encode(schema.request(operation,reference,fields));if(body.length>maximumBytes)throw new IllegalArgumentException("ACTIVITY_REQUEST_CAPACITY");
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
        return schema.readResponse(bytes,operation,reference,type);
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
    @Override public Plan load(Reference r){return call(Operation.LOAD,r,Plan.class);}
    @Override public Dispatch dispatch(Reference r,String step,String command){return call(Operation.DISPATCH,r,Dispatch.class,"step_id",step,"command_id",command);}
    @Override public Receipt commit(Reference r,Dispatch d,Completion c){return call(Operation.COMMIT,r,Receipt.class,"dispatch",d,"completion",c);}
    @Override public Receipt failure(Reference r,Dispatch d,String reason){return call(Operation.FAILURE,r,Receipt.class,"dispatch",d,"reason_code",reason);}
    @Override public Receipt dependencyFailed(Reference r,String step){return call(Operation.DEPENDENCY_FAILED,r,Receipt.class,"step_id",step);}
    @Override public CommandReceipt consume(Reference r,Command c){return call(Operation.CONSUME,r,CommandReceipt.class,"command",c);}
    @Override public Termination cancellation(Reference r,String command,String reason){return call(Operation.CANCELLATION,r,Termination.class,"command_id",command,"reason_code",reason);}
    @Override public FinalReceipt finish(Reference r,State state,String reason,List<Receipt> receipts,List<String> commands){return call(Operation.FINISH,r,FinalReceipt.class,"requested",state,"reason_code",reason,"receipts",receipts,"command_ids",commands);}
}

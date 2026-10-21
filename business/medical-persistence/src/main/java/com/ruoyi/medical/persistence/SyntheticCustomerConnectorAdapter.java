package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Demonstration contract only. Never an adapter for an actual customer's undocumented API.
 * Explicit registration, loopback HTTPS, verified TLS and SYNTHETIC response labels are mandatory.
 * Normal service configuration approval, credentials, grants and source-rights gates still apply. */
public final class SyntheticCustomerConnectorAdapter implements CustomerConnectorAdapter {
    public static final String VERSION="synthetic-customer-http/1.0.0";
    public static final String PROVIDER="SYNTHETIC_CUSTOMER_DEMO";
    private final URI endpoint;
    private final Path journal;
    private final HttpClient client;
    public static JsonNode specification(){
        var spec=JSON.createObjectNode().put("version",VERSION);
        spec.putObject("document").put("protocol","SYNTHETIC_ONLY").put("transport","loopback-https")
            .put("verify","POST /v1/verify").put("page","POST /v1/page")
            .put("cursor","immutable-fixture-offset").put("revocation","explicit-ownership-revoked");
        return spec;
    }
    public SyntheticCustomerConnectorAdapter(URI endpoint,Path journal,HttpClient client)throws IOException{
        if(!"https".equals(endpoint.getScheme())||!"127.0.0.1".equals(endpoint.getHost())||endpoint.getPort()<1
            ||!Set.of("","/").contains(endpoint.getPath())||endpoint.getRawQuery()!=null||endpoint.getRawFragment()!=null||endpoint.getRawUserInfo()!=null)
            throw new IllegalArgumentException("SYNTHETIC_LOOPBACK_HTTPS_REQUIRED");
        if(client.followRedirects()!=HttpClient.Redirect.NEVER)throw new IllegalArgumentException("REDIRECTS_FORBIDDEN");
        this.endpoint=endpoint;this.journal=journal.toAbsolutePath();this.client=client;Files.createDirectories(this.journal);
    }
    @Override public String specificationSha256(){try{return hash(specification());}catch(Exception e){throw new IllegalStateException(e);}}
    @Override public CredentialTarget credentialTarget(Request r){return new CredentialTarget(PROVIDER,endpoint.toString());}
    @Override public Verification verify(Request r,boolean recovery,Current current)throws Exception{
        var exchange=exchange(r,"verify",recovery,current);var b=response(exchange);
        closed(b,"mode","version","verified","external_account_id","actual_scope");
        if(!b.required("verified").isBoolean())bad();
        String account=text(b,"external_account_id");account(r.definition(),account);
        if(!b.required("actual_scope").equals(r.definition().required("ownership_scope").required("external_scope")))deny();
        return new Verification(b.required("verified").booleanValue(),account,b.required("actual_scope"),List.of(exchange));
    }
    @Override public Page readPage(Request r,boolean recovery,Current current)throws Exception{
        var exchange=exchange(r,"page",recovery,current);var b=response(exchange);
        closed(b,"mode","version","items","next_cursor","has_more");
        if(!b.required("items").isArray()||!b.required("has_more").isBoolean()||b.required("items").size()>r.maximumItems())bad();
        var items=new ArrayList<Item>();
        for(var i:b.required("items")){
            closed(i,"external_id","external_version","external_account_id","owner_project_id","media_type","original_url","acquired_at","original_base64","event");
            String event=text(i,"event");if(!Set.of("UPSERT","OWNERSHIP_REVOKED").contains(event))bad();
            var metadata=JSON.createObjectNode().put("mode","SYNTHETIC").put("contract",VERSION).put("event",event);
            items.add(new Item(text(i,"external_id"),text(i,"external_version"),text(i,"external_account_id"),text(i,"owner_project_id"),
                text(i,"media_type"),text(i,"original_url"),text(i,"acquired_at"),Base64.getDecoder().decode(text(i,"original_base64")),"OWNERSHIP_REVOKED".equals(event),metadata));
        }
        var result=new Page(items,b.required("next_cursor"),b.required("has_more").booleanValue(),List.of(exchange));page(r,result);current.require();return result;
    }
    private JsonNode response(Exchange e)throws Exception{
        if(e.responseStatus()!=200||!e.complete())throw new IOException("SYNTHETIC_HTTP_STATUS_"+e.responseStatus());
        var b=JSON.readTree(e.responseBody());if(!"SYNTHETIC".equals(text(b,"mode"))||!VERSION.equals(text(b,"version")))deny();return b;
    }
    private Exchange exchange(Request r,String kind,boolean recovery,Current current)throws Exception{
        current.require();if(!hash(r.definition()).equals(r.definitionSha256())||!r.definition().required("interface_spec").equals(specification())||r.maximumItems()<1||r.maximumItems()>MAX_ITEMS)bad();
        String key=hash(List.of(r.tenantId(),r.operationId(),kind));String digest=hash(List.of(r,endpoint.toString(),kind));
        Path lock=journal.resolve(key+".lock"),started=journal.resolve(key+".started"),done=journal.resolve(key+".json");
        try(var channel=FileChannel.open(lock,StandardOpenOption.CREATE,StandardOpenOption.WRITE);var fence=channel.tryLock()){
            if(fence==null)throw new IOException("SYNTHETIC_OPERATION_IN_PROGRESS");
            if(Files.exists(started)&&!Files.readString(started).equals(digest))throw new IOException("SYNTHETIC_IDEMPOTENCY_MISMATCH");
            if(Files.exists(done)){
                if(!Files.exists(started))throw new IOException("SYNTHETIC_JOURNAL_INCOMPLETE");
                var saved=JSON.readTree(Files.readAllBytes(done));if(!digest.equals(text(saved,"request_sha256")))bad();
                Exchange result=JSON.treeToValue(saved.required("exchange"),Exchange.class);exchanges(List.of(result));current.require();return result;
            }
            if(recovery||Files.exists(started))throw new IOException("SYNTHETIC_RECOVERY_REQUIRED_NO_RESEND");
            var body=JSON.createObjectNode().put("mode","SYNTHETIC").put("version",VERSION).put("operation_id",r.operationId())
                .put("project_id",r.projectId()).put("maximum_items",r.maximumItems());
            body.set("external_scope",r.definition().required("ownership_scope").required("external_scope"));body.set("cursor",r.cursor());
            var accounts=body.putArray("accounts");for(var a:r.definition().required("account_mapping"))accounts.add(text(a,"external_account_id"));
            byte[] bytes=JSON.writeValueAsBytes(body);URI target=endpoint.resolve("/v1/"+kind);
            durable(started,digest.getBytes(java.nio.charset.StandardCharsets.UTF_8));current.require();String begin=new java.time.format.DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(Instant.now());
            var request=HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
            var bounded=new BoundedBody(MAX_PAGE_BYTES-bytes.length,current);
            var pending=client.sendAsync(request,ignored->bounded);HttpResponse<byte[]> result;
            try{result=pending.get(10,TimeUnit.SECONDS);}catch(Exception failure){bounded.cancel();pending.cancel(true);throw failure;}
            byte[] received=result.body();
            current.require();var requestHeaders=JSON.createArrayNode();requestHeaders.addObject().put("name","Content-Type").put("value","application/json");
            var responseHeaders=JSON.createArrayNode();result.headers().map().forEach((name,values)->values.forEach(value->responseHeaders.addObject().put("name",name).put("value",value)));
            var exchange=new Exchange("POST",target.toString(),requestHeaders,bytes,result.statusCode(),responseHeaders,received,begin,new java.time.format.DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(Instant.now()),true);
            exchanges(List.of(exchange));var saved=JSON.createObjectNode().put("request_sha256",digest);saved.set("exchange",JSON.valueToTree(exchange));
            durable(done,JSON.writeValueAsBytes(saved));current.require();return exchange;
        }
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private final int limit;private final Current current;private Flow.Subscription subscription;
        BoundedBody(int limit,Current current){this.limit=limit;this.current=current;}
        @Override public CompletionStage<byte[]> getBody(){return body;}
        @Override public synchronized void onSubscribe(Flow.Subscription value){
            if(subscription!=null||body.isDone()){value.cancel();return;}subscription=value;value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> chunks){
            if(body.isDone())return;
            try{current.require();for(var chunk:chunks){if((long)output.size()+chunk.remaining()>limit)throw new IOException("SYNTHETIC_BODY_LIMIT");
                byte[] bytes=new byte[chunk.remaining()];chunk.get(bytes);output.write(bytes);}
                subscription.request(1);
            }catch(Exception failure){subscription.cancel();body.completeExceptionally(failure);}
        }
        @Override public synchronized void onError(Throwable error){body.completeExceptionally(error);}
        @Override public synchronized void onComplete(){body.complete(output.toByteArray());}
        synchronized void cancel(){if(subscription!=null)subscription.cancel();body.cancel(true);}
    }
    private void durable(Path destination,byte[] bytes)throws IOException{
        Path temporary=Files.createTempFile(journal,"pending-",".tmp");
        try{try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE)){var b=ByteBuffer.wrap(bytes);while(b.hasRemaining())file.write(b);file.force(true);}
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE);
            try(var directory=FileChannel.open(journal,StandardOpenOption.READ)){directory.force(true);}
        }finally{Files.deleteIfExists(temporary);}
    }
}

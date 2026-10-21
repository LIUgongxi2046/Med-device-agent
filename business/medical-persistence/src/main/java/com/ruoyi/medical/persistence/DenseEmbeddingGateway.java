package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.ruoyi.medical.contracts.ErrorCode;
import com.ruoyi.medical.contracts.WireValues;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;

/** Real, non-streaming embedding gateway. Durable started marker makes an uncertain send non-repeatable.
 * The original response is durably captured BEFORE decoding. Recovery can only consume that original.
 * Credentials and endpoint allowlist are deployment-owned, never request-supplied paths.
 */
public final class DenseEmbeddingGateway {
    public record Route(String tenant, String credentialReference, String credentialRevision,
            String provider, String endpoint, Path keyFile) { }
    public record Request(String tenant, String invocation, String inputSha256, String model,
            String modelVersion, int dimension, List<String> inputs,
            String credentialReference, String credentialRevision, String provider, String endpoint, int timeoutSeconds) { }
    public record Receipt(String responseSha256, String providerRequestId, String responseModel,
            long inputTokens, List<List<Double>> vectors) { }
    private final Path root;
    private final List<Route> routes;
    private final ApprovedCredentialAuthority credentials;
    private final PermissionSchema canonical;
    private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(10)).build();
    public DenseEmbeddingGateway(Path root, List<Route> routes, ApprovedCredentialAuthority credentials, PermissionSchema canonical) {
        this.root=root; this.routes=List.copyOf(routes); this.credentials=credentials; this.canonical=canonical;
    }
    void requireConfigured(String tenant,ModelConfigurationProtocol.Definition model) throws Exception {
        var candidates=routes.stream().filter(r->r.tenant().equals(tenant)&&r.credentialReference().equals(model.credential_reference_id())
            &&r.credentialRevision().equals(model.credential_revision())&&r.provider().equals(model.provider())&&r.endpoint().equals(model.base_url())).toList();
        if(candidates.size()!=1)throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        credentials.require(tenant,model.credential_reference_id(),model.credential_revision(),model.provider(),model.base_url());
        privatePath(root,true);Path key=candidates.get(0).keyFile();privatePath(key.getParent(),true);
        if(!new String(read(key,4096),StandardCharsets.US_ASCII).stripTrailing().matches("[!-~]{1,4096}"))fail();
    }
    public Receipt execute(Request request, boolean recoveryOnly, Runnable beforeSend) throws Exception {
        WireValues.uuid(request.tenant()); WireValues.uuid(request.invocation()); WireValues.hash(request.inputSha256());
        privatePath(root,true);
        Path journal=root.resolve(request.tenant()+"-"+request.invocation());
        if (!Files.exists(journal,LinkOption.NOFOLLOW_LINKS)) {
            if(recoveryOnly) unknown();
            Files.createDirectory(journal,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            forceDirectory(root);
        }
        privatePath(journal,true);
        try(var channel=FileChannel.open(journal.resolve("writer.lock"),Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            try(var lock=channel.tryLock()) {
                if(lock==null)unknown();
                return executeLocked(request,recoveryOnly,beforeSend,journal);
            }
        } catch(java.nio.channels.OverlappingFileLockException busy) {unknown();return null;}
    }
    private Receipt executeLocked(Request request,boolean recoveryOnly,Runnable beforeSend,Path journal)throws Exception {
        var identity=canonical.tree(Map.of("tenant",request.tenant(),"invocation",request.invocation(),
            "input_sha256",request.inputSha256(),"model",request.model(),"model_version",request.modelVersion(),
            "dimension",request.dimension(),"endpoint",request.endpoint(),"credential_revision",request.credentialRevision()));
        byte[] identityBytes=canonical.canonical(identity).getBytes(StandardCharsets.UTF_8);
        Path started=journal.resolve("started.json"), response=journal.resolve("response.json");
        if(Files.exists(started,LinkOption.NOFOLLOW_LINKS)) {
            if(!Arrays.equals(read(started,8192),identityBytes)) fail();
            if(!Files.exists(response,LinkOption.NOFOLLOW_LINKS)) unknown();
            return receipt(read(response,96*1024*1024),request);
        }
        if(recoveryOnly) unknown();
        var matching=routes.stream().filter(r->r.tenant().equals(request.tenant())
            &&r.credentialReference().equals(request.credentialReference())&&r.credentialRevision().equals(request.credentialRevision())
            &&r.provider().equals(request.provider())&&r.endpoint().equals(request.endpoint())).toList();
        if(matching.size()!=1) throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        var proof=credentials.require(request.tenant(),request.credentialReference(),request.credentialRevision(),request.provider(),request.endpoint());
        URI base=URI.create(request.endpoint());
        if(!"https".equals(base.getScheme())||base.getHost()==null||base.getRawUserInfo()!=null||base.getRawQuery()!=null||base.getRawFragment()!=null) fail();
        for(var address:InetAddress.getAllByName(base.getHost()))
            if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress()) fail();
        Path key=matching.get(0).keyFile(); privatePath(key.getParent(),true);
        String token=new String(read(key,4096),StandardCharsets.US_ASCII).stripTrailing();
        if(!token.matches("[!-~]{1,4096}")) fail();
        var body=canonical.object();body.put("model",request.modelVersion());body.put("encoding_format","float");
        body.put("dimensions",request.dimension());body.set("input",canonical.tree(request.inputs()));
        String payload=canonical.canonical(body);
        proof.requireCurrent();beforeSend.run();
        // This marker MUST survive even if the process dies before receiving any response.
        writeNew(started,identityBytes); forceDirectory(journal);
        HttpRequest call=HttpRequest.newBuilder(URI.create(request.endpoint().replaceAll("/+$","")+"/embeddings"))
            .timeout(Duration.ofSeconds(request.timeoutSeconds())).header("Content-Type","application/json")
            .header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        var received=client.send(call,HttpResponse.BodyHandlers.ofInputStream());
        byte[] raw;
        var deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"dense-body-deadline");thread.setDaemon(true);return thread;});
        try(var stream=received.body()) {
            deadline.schedule(()->{try{stream.close();}catch(java.io.IOException ignored){}},request.timeoutSeconds(),java.util.concurrent.TimeUnit.SECONDS);
            raw=stream.readNBytes(64*1024*1024+1);
        } finally {deadline.shutdownNow();}
        if(raw.length>64*1024*1024) unknown();
        var capture=canonical.object();capture.put("http_status",received.statusCode());
        capture.put("provider_request_id",received.headers().firstValue("x-request-id").orElse(""));
        capture.put("body_base64",Base64.getEncoder().encodeToString(raw));
        byte[] saved=canonical.canonical(capture).getBytes(StandardCharsets.UTF_8);
        Path temporary=journal.resolve("response.pending");writeNew(temporary,saved);
        Files.move(temporary,response,StandardCopyOption.ATOMIC_MOVE);forceDirectory(journal);
        return receipt(saved,request);
    }
    /** Only the already-authorized source purge worker calls this; never exposes raw model bytes. */
    void eraseOriginal(String tenant,String invocation) throws Exception {
        WireValues.uuid(tenant);WireValues.uuid(invocation);privatePath(root,true);
        Path journal=root.resolve(tenant+"-"+invocation);
        if(!Files.exists(journal,LinkOption.NOFOLLOW_LINKS))return;
        privatePath(journal,true);
        try(var channel=FileChannel.open(journal.resolve("writer.lock"),Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            try(var lock=channel.tryLock()) {
                if(lock==null)throw new TenantAccessException(ErrorCode.ACTION_STALE);
                for(String name:List.of("response.json","response.pending","started.json")) {
                    Path item=journal.resolve(name);if(Files.exists(item,LinkOption.NOFOLLOW_LINKS)){privatePath(item,false);Files.delete(item);}
                }
                Files.delete(journal.resolve("writer.lock"));Files.delete(journal);forceDirectory(root);
            }
        } catch(java.nio.channels.OverlappingFileLockException busy) {throw new TenantAccessException(ErrorCode.ACTION_STALE);}
        if(Files.exists(journal,LinkOption.NOFOLLOW_LINKS))fail();
    }
    private Receipt receipt(byte[] captured,Request request) throws Exception {
        var capture=json.readTree(captured);if(capture.required("http_status").intValue()!=200) unknown();
        byte[] raw=Base64.getDecoder().decode(capture.required("body_base64").textValue());var value=json.readTree(raw);
        String model=value.required("model").textValue();
        if(!request.modelVersion().equals(model)) fail();
        var usage=value.required("usage");var tokens=usage.required("prompt_tokens");
        if(!tokens.isIntegralNumber()||!tokens.canConvertToLong()||tokens.longValue()<0
            ||!usage.required("total_tokens").equals(tokens)) fail();
        var data=value.required("data");if(!data.isArray()||data.size()!=request.inputs().size()) fail();
        List<List<Double>> vectors=new ArrayList<>(Collections.nCopies(data.size(),null));
        for(var row:data) {
            var index=row.required("index");if(!index.isIntegralNumber()||!index.canConvertToInt()) fail();
            int n=index.intValue();if(n<0||n>=vectors.size()||vectors.get(n)!=null) fail();
            vectors.set(n,vector(row.required("embedding"),request.dimension()));
        }
        return new Receipt(DigestUtils.sha256Hex(captured),capture.required("provider_request_id").textValue(),model,tokens.longValue(),List.copyOf(vectors));
    }
    static List<Double> vector(JsonNode value,int dimension) {
        if(!value.isArray()||value.size()!=dimension) fail();
        var result=new ArrayList<Double>();double norm=0;
        for(var item:value){if(!item.isNumber()) fail();double v=item.doubleValue();if(!Double.isFinite(v)) fail();result.add(v);norm+=v*v;}
        if(!Double.isFinite(norm)||norm<=0) fail();return List.copyOf(result);
    }
    private static byte[] read(Path path,int maximum) throws Exception {
        privatePath(path,false);try(var stream=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] result=stream.readNBytes(maximum+1);if(result.length>maximum) fail();return result;
        }
    }
    private static void writeNew(Path path,byte[] value) throws Exception {
        try(var channel=FileChannel.open(path,Set.of(StandardOpenOption.WRITE,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
            var bytes=ByteBuffer.wrap(value);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
        }
    }
    private static void forceDirectory(Path path) throws Exception {try(var channel=FileChannel.open(path,StandardOpenOption.READ)){channel.force(true);}}
    private static void privatePath(Path path,boolean directory) throws Exception {
        if(path==null||!path.isAbsolute()||!path.toRealPath().equals(path)||Files.isSymbolicLink(path)) fail();
        var attrs=Files.readAttributes(path,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        var owner=Files.getOwner(Path.of(System.getProperty("user.home")));
        if(!attrs.owner().equals(owner)||!attrs.permissions().equals(PosixFilePermissions.fromString(directory?"rwx------":"rw-------"))
            ||directory!=attrs.isDirectory()||!directory&&(!attrs.isRegularFile()||((Number)Files.getAttribute(path,"unix:nlink")).intValue()!=1)) fail();
    }
    private static void fail(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}
    private static void unknown(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}

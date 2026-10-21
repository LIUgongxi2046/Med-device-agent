package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ruoyi.medical.contracts.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.util.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

/** Real Brave search and bounded original capture. Only search-result URLs under the
 * exact approved domain set can be fetched. DNS is resolved/checked once and pinned
 * into the connection manager; TLS still verifies the original hostname. No redirects,
 * cookies, compression, proxy inheritance, automatic retries or caller-selected endpoints. */
public final class ApprovedWebGateway {
    public static final String ENDPOINT="https://api.search.brave.com/res/v1/web/search";
    public record Route(String tenant,String credentialReference,String credentialRevision,Path keyFile){}
    public record Request(String tenant,String acquisition,String query,List<String> domains,int maximumResults,String credentialReference,String credentialRevision){}
    @FunctionalInterface public interface Current {void require() throws Exception;}
    private final Path root;private final List<Route> routes;private final ApprovedCredentialAuthority credentials;
    private final JsonMapper json=JsonMapper.builder().enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public ApprovedWebGateway(Path root,List<Route> routes,ApprovedCredentialAuthority credentials){this.root=root;this.routes=List.copyOf(routes);this.credentials=credentials;}
    public JsonNode execute(Request request,boolean recoveryOnly,Current current)throws Exception {
        WireValues.uuid(request.tenant());WireValues.uuid(request.acquisition());WireValues.uuid(request.credentialReference());if(request.query()==null||request.query().isBlank()||request.query().length()>2000||request.domains()==null||request.domains().isEmpty()||request.domains().size()>10||request.maximumResults()<1||request.maximumResults()>10)bad();request.domains().forEach(ApprovedWebGateway::domain);privatePath(root,true);current.require();
        Path directory=root.resolve(request.tenant()+"-"+request.acquisition());
        if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS)){if(recoveryOnly)unknown();Files.createDirectory(directory,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));force(root);}
        privatePath(directory,true);
        try(var channel=FileChannel.open(directory.resolve("lock"),Set.of(StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));var lock=channel.tryLock()){
            if(lock==null)unknown();byte[] identity=ContentReadAuthorization.canonicalBytes(request);Path intent=directory.resolve("identity.json");
            if(Files.exists(intent,LinkOption.NOFOLLOW_LINKS)){if(!Arrays.equals(read(intent,32768),identity))bad();}
            else {write(intent,identity);force(directory);}
            var matching=routes.stream().filter(r->r.tenant().equals(request.tenant())&&r.credentialReference().equals(request.credentialReference())&&r.credentialRevision().equals(request.credentialRevision())).toList();
            if(matching.size()!=1)unknown();var route=matching.get(0);
            var proof=credentials.require(request.tenant(),request.credentialReference(),request.credentialRevision(),"BRAVE_SEARCH",ENDPOINT);
            privatePath(route.keyFile().getParent(),true);String token=new String(read(route.keyFile(),4096),StandardCharsets.US_ASCII).stripTrailing();if(!token.matches("[!-~]{1,4096}"))bad();
            Current guard=()->{proof.requireCurrent();current.require();};
            String scoped=request.query()+" ("+String.join(" OR ",request.domains().stream().map(d->"site:"+domain(d)).toList())+")";
            URI search=URI.create(ENDPOINT+"?q="+URLEncoder.encode(scoped,StandardCharsets.UTF_8)+"&count="+request.maximumResults());
            JsonNode response=exchange(directory,"search",search,token,4*1024*1024,recoveryOnly,guard);
            var output=json.createObjectNode().put("schema_version","1.0.0").put("acquisition_id",request.acquisition()).put("query_sha256",WireValues.sha256(request.query().getBytes(StandardCharsets.UTF_8))).put("formal_evidence",false).put("complete",true);
            output.set("search_response",response);var captures=output.putArray("captures");var failures=output.putArray("failures");
            if(!response.required("complete").booleanValue()||response.required("status").intValue()!=200){output.put("complete",false);failures.addObject().put("phase","SEARCH").put("code","SEARCH_RESPONSE_NOT_SUCCESSFUL");return output;}
            JsonNode decoded;try{decoded=json.readTree(Base64.getDecoder().decode(response.required("body_base64").textValue()));}catch(Exception malformed){output.put("complete",false);failures.addObject().put("phase","SEARCH").put("code","SEARCH_RESPONSE_INVALID");return output;}
            var results=decoded.path("web").path("results");if(!results.isArray()){output.put("complete",false);failures.addObject().put("phase","SEARCH").put("code","SEARCH_RESPONSE_INVALID");return output;}
            var seen=new HashSet<String>();int ordinal=0;
            for(var result:results){if(ordinal>=request.maximumResults())break;String url=result.path("url").asText("");
                URI target;try{target=target(url,request.domains());}catch(RuntimeException rejected){continue;}
                if(!seen.add(target.toASCIIString()))continue;
                ordinal++;
                try {var capture=exchange(directory,"capture-"+ordinal,target,null,8*1024*1024,recoveryOnly,guard);captures.add(capture);
                    if(!capture.required("complete").booleanValue()||capture.required("status").intValue()!=200)output.put("complete",false);
                } catch(Exception unavailable) {guard.require();output.put("complete",false);failures.addObject().put("phase","CAPTURE").put("url",target.toASCIIString()).put("code","ORIGINAL_UNAVAILABLE_NO_RESEND");}
            }
            guard.require();output.put("formal_evidence",false);return output;
        }catch(java.nio.channels.OverlappingFileLockException e){unknown();return null;}
    }
    private JsonNode exchange(Path directory,String name,URI uri,String token,int maximum,boolean recoveryOnly,Current current)throws Exception {
        Path started=directory.resolve(name+".started"), saved=directory.resolve(name+".json");byte[] identity=uri.toASCIIString().getBytes(StandardCharsets.UTF_8);
        if(Files.exists(started,LinkOption.NOFOLLOW_LINKS)){
            if(!Arrays.equals(identity,read(started,8192)))bad();
            if(!Files.exists(saved,LinkOption.NOFOLLOW_LINKS)) {
                Path pending=directory.resolve(name+".pending");
                if(!Files.exists(pending,LinkOption.NOFOLLOW_LINKS))unknown();
                var recovered=json.readTree(read(pending,16*1024*1024));verify(recovered,uri);
                Files.move(pending,saved,StandardCopyOption.ATOMIC_MOVE);force(directory);
            }
            var original=json.readTree(read(saved,16*1024*1024));verify(original,uri);current.require();return original;
        }
        if(recoveryOnly)unknown();current.require();
        InetAddress[] addresses=InetAddress.getAllByName(uri.getHost());if(addresses.length==0)bad();for(var ip:addresses)publicAddress(ip);
        var configuration=RequestConfig.custom().setConnectTimeout(10000).setConnectionRequestTimeout(10000).setSocketTimeout(15000).build();
        try(var client=HttpClients.custom().setDefaultConnectionConfig(org.apache.http.config.ConnectionConfig.custom().setMessageConstraints(org.apache.http.config.MessageConstraints.custom().setMaxHeaderCount(64).setMaxLineLength(8192).build()).build()).setDefaultRequestConfig(configuration).setDnsResolver(host->{if(!host.equalsIgnoreCase(uri.getHost()))throw new UnknownHostException("UNAPPROVED_HOST");return addresses.clone();})
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().disableContentCompression().build()){
            var call=new HttpGet(uri);call.setHeader("Accept",token==null?"application/pdf,text/html,text/plain,application/json":"application/json");call.setHeader("User-Agent","MedicalApprovedAcquisition/1.0");
            if(token!=null)call.setHeader("X-Subscription-Token",token);
            current.require();write(started,identity);force(directory);
            var deadline=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"approved-web-deadline");t.setDaemon(true);return t;});
            try{
                deadline.schedule(call::abort,30,java.util.concurrent.TimeUnit.SECONDS);
                try(var response=client.execute(call)){
                    var result=json.createObjectNode().put("url",uri.toASCIIString()).put("status",response.getStatusLine().getStatusCode())
                        .put("status_line",response.getStatusLine().toString()).put("captured_at",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString());
                    var headers=result.putArray("headers");for(var header:response.getAllHeaders())headers.addObject().put("name",header.getName()).put("value",header.getValue());
                    byte[] bytes;boolean complete=true;String failure=null;
                    try(var body=response.getEntity()==null?java.io.InputStream.nullInputStream():response.getEntity().getContent()){
                        var buffer=new java.io.ByteArrayOutputStream();byte[] block=new byte[8192];
                        try{while(true){int n=body.read(block);if(n<0)break;if(buffer.size()+n>maximum){complete=false;failure="RESPONSE_TOO_LARGE";break;}buffer.write(block,0,n);current.require();}}
                        catch(Exception interrupted){complete=false;failure="BODY_INCOMPLETE";}bytes=buffer.toByteArray();
                    }
                    result.put("completed_at",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString()).put("complete",complete).put("failure",failure).put("sha256",WireValues.sha256(bytes)).put("size_bytes",bytes.length).put("body_base64",Base64.getEncoder().encodeToString(bytes));
                    Path pending=directory.resolve(name+".pending");write(pending,ContentReadAuthorization.canonicalBytes(result));Files.move(pending,saved,StandardCopyOption.ATOMIC_MOVE);force(directory);
                    current.require();return result;
                }
            }finally{deadline.shutdownNow();}
        }
    }
    static URI target(String value,List<String> domains){URI uri=URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||uri.getPort()!=-1&&uri.getPort()!=443||value.length()>4096||!domains.contains(domain(uri.getHost())))bad();return uri;}
    static String domain(String value){if(value==null||!value.equals(value.toLowerCase(Locale.ROOT))||value.length()>253||!value.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")||value.endsWith(".local")||value.endsWith(".internal")||value.endsWith(".localhost"))bad();return value;}
    static void publicAddress(InetAddress ip){byte[] a=ip.getAddress();if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress())bad();
        if(a.length==16){if((a[0]&0xe0)!=0x20)bad();if((a[0]&255)==0x20&&((a[1]&255)==2||(a[1]&255)==1&&((a[2]&255)==0&&(a[3]&255)==0||(a[2]&255)==0x0d&&(a[3]&255)==0xb8)))bad();}
        else{int x=a[0]&255,y=a[1]&255;if(x==0||x==10||x==127||x>=224||x==100&&y>=64&&y<=127||x==169&&y==254||x==172&&y>=16&&y<=31||x==192&&(y==0||y==168)||x==198&&(y==18||y==19||y==51)||x==203&&y==0)bad();}}
    private static void verify(JsonNode node,URI url)throws java.security.NoSuchAlgorithmException{byte[] body=Base64.getDecoder().decode(node.required("body_base64").textValue());if(!url.toASCIIString().equals(node.required("url").textValue())||!WireValues.sha256(body).equals(node.required("sha256").textValue())||body.length!=node.required("size_bytes").intValue())bad();Instant.parse(node.required("captured_at").textValue());}
    private static byte[] read(Path path,int max)throws Exception{privatePath(path,false);try(var in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){byte[] data=in.readNBytes(max+1);if(data.length>max)bad();return data;}}
    private static void write(Path path,byte[] value)throws Exception{try(var channel=FileChannel.open(path,Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))){var buffer=ByteBuffer.wrap(value);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}}
    private static void force(Path path)throws Exception{try(var c=FileChannel.open(path,StandardOpenOption.READ)){c.force(true);}}
    private static void privatePath(Path path,boolean directory)throws Exception{if(path==null||!path.isAbsolute()||!path.toRealPath().equals(path)||Files.isSymbolicLink(path))bad();var a=Files.readAttributes(path,PosixFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!a.owner().equals(Files.getOwner(Path.of(System.getProperty("user.home"))))||!a.permissions().equals(PosixFilePermissions.fromString(directory?"rwx------":"rw-------"))||directory!=a.isDirectory()||!directory&&(!a.isRegularFile()||((Number)Files.getAttribute(path,"unix:nlink")).intValue()!=1))bad();}
    private static void bad(){throw new TenantAccessException(ErrorCode.CONTENT_MISMATCH);}private static void unknown(){throw new TenantAccessException(ErrorCode.DEPENDENCY_UNAVAILABLE);}
}

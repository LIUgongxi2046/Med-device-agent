package com.ruoyi.medical.persistence;

import static com.ruoyi.medical.persistence.CustomerConnectorProtocol.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.*;

/** Real loopback HTTPS server; only immutable, deliberately fictional document fixtures.
 * TLS is verified using a generated ephemeral fixture trust anchor, never trust-all. */
final class SyntheticCustomerHttpFixture implements AutoCloseable {
    static final String PROJECT="00000000-0000-4000-8000-000000000002", ACCOUNT="SYNTHETIC_ACCOUNT_001";
    final HttpsServer server;
    final SSLContext tls;
    final AtomicInteger calls=new AtomicInteger();
    String scenario="normal";
    SyntheticCustomerHttpFixture(Path folder)throws Exception{
        Path store=folder.resolve("synthetic-tls.p12");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),"-genkeypair","-alias","synthetic", "-keyalg","RSA","-storetype","PKCS12",
            "-keystore",store.toString(),"-storepass","synthetic-fixture-only","-keypass","synthetic-fixture-only","-dname","CN=SYNTHETIC localhost",
            "-ext","SAN=ip:127.0.0.1","-validity","1","-noprompt").redirectErrorStream(true).redirectOutput(folder.resolve("keytool.log").toFile()).start();
        if(!process.waitFor(30,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("FIXTURE_KEYTOOL_TIMEOUT");}
        if(process.exitValue()!=0)throw new IllegalStateException("FIXTURE_KEYTOOL_FAILED");
        var keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(store)){keys.load(in,"synthetic-fixture-only".toCharArray());}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,"synthetic-fixture-only".toCharArray());
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(keys);
        tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),tm.getTrustManagers(),null);
        server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setHttpsConfigurator(new HttpsConfigurator(tls));
        server.createContext("/v1/verify",e->handle(e,true));server.createContext("/v1/page",e->handle(e,false));server.start();
    }
    URI endpoint(){return URI.create("https://127.0.0.1:"+server.getAddress().getPort());}
    HttpClient client(){return HttpClient.newBuilder().sslContext(tls).followRedirects(HttpClient.Redirect.NEVER).build();}
    private void handle(HttpExchange e,boolean verify)throws java.io.IOException{
        calls.incrementAndGet();try{
            var b=JSON.readTree(e.getRequestBody().readNBytes(65537));
            if(!"POST".equals(e.getRequestMethod())||!"SYNTHETIC".equals(b.path("mode").asText())||!SyntheticCustomerConnectorAdapter.VERSION.equals(b.path("version").asText())
                ||!PROJECT.equals(b.path("project_id").asText())||!b.path("accounts").equals(JSON.createArrayNode().add(ACCOUNT))
                ||!b.path("external_scope").equals(JSON.createObjectNode().put("fixture","SYNTHETIC"))) {e.sendResponseHeaders(403,-1);return;}
            var out=JSON.createObjectNode().put("mode","SYNTHETIC").put("version",SyntheticCustomerConnectorAdapter.VERSION);
            if(verify){out.put("verified",true).put("external_account_id",ACCOUNT);out.set("actual_scope",b.required("external_scope"));}
            else{
                var cursor=b.required("cursor");int offset=cursor.isNull()?0:cursor.path("offset").asInt(-1);
                if(!cursor.isNull()&&(!"SYNTHETIC_SNAPSHOT_1".equals(cursor.path("snapshot").asText())||offset<0||offset>3)){e.sendResponseHeaders(409,-1);return;}
                int limit=b.path("maximum_items").asInt();if(limit<1||limit>25){e.sendResponseHeaders(400,-1);return;}
                var items=out.putArray("items");int end=Math.min(3,offset+limit);
                for(int i=offset;i<end;i++){
                    boolean revoked="revocation".equals(scenario);String content="SYNTHETIC DEMONSTRATION DOCUMENT "+i+("conflict".equals(scenario)?" MODIFIED SAME VERSION":"");
                    items.addObject().put("external_id","SYNTHETIC_DOC_"+i).put("external_version","v1").put("external_account_id","foreign-account".equals(scenario)?"SYNTHETIC_OTHER":ACCOUNT)
                        .put("owner_project_id","foreign-project".equals(scenario)?"00000000-0000-4000-8000-000000000099":PROJECT)
                        .put("media_type","text/plain").put("original_url",endpoint()+"/synthetic/doc/"+i).put("acquired_at","2026-10-21T00:00:00Z")
                        .put("original_base64",revoked?"":Base64.getEncoder().encodeToString(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .put("event",revoked?"OWNERSHIP_REVOKED":"UPSERT");
                }
                out.putObject("next_cursor").put("snapshot","SYNTHETIC_SNAPSHOT_1").put("offset",end);out.put("has_more",end<3);
            }
            byte[] bytes=JSON.writeValueAsBytes(out);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);
        }catch(Exception failure){try{e.sendResponseHeaders(400,-1);}catch(Exception ignored){}}
        finally{e.close();}
    }
    @Override public void close(){server.stop(0);}
}

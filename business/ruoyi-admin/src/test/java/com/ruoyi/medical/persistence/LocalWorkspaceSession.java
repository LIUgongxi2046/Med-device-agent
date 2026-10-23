package com.ruoyi.medical.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

final class LocalWorkspaceSession {
    private LocalWorkspaceSession(){}
    static JsonNode owner(Path root)throws Exception{
        var json=JsonMapper.builder().build();var credentials=json.readTree(Files.readAllBytes(root.resolve(".implementation-work/runtime/owner-credentials.json")));
        var http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();String origin="http://127.0.0.1:18083";
        var csrfReply=http.send(HttpRequest.newBuilder(URI.create(origin+"/auth/csrf")).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,csrfReply.statusCode());var csrf=json.readTree(csrfReply.body());
        String form="username="+URLEncoder.encode(credentials.required("username").asText(),StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(credentials.required("password").asText(),StandardCharsets.UTF_8)+"&_csrf="+URLEncoder.encode(csrf.required("token").asText(),StandardCharsets.UTF_8);
        var login=http.send(HttpRequest.newBuilder(URI.create(origin+"/auth/login")).timeout(Duration.ofSeconds(60)).header("Origin",origin).header("Content-Type","application/x-www-form-urlencoded").header(csrf.required("header_name").asText(),csrf.required("token").asText()).POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.discarding());assertEquals(302,login.statusCode());
        var reply=http.send(HttpRequest.newBuilder(URI.create(origin+"/api/session")).timeout(Duration.ofSeconds(60)).header("X-Schema-Version","1.0.0").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,reply.statusCode());return json.readTree(reply.body()).required("data");
    }
}

package com.sportfit;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class GitHubStoreTests {
 HttpServer server; ObjectMapper json=new ObjectMapper(); String content,sha;AtomicInteger version=new AtomicInteger();boolean conflictOnce=false;GitHubAccountStore store;
 @BeforeEach void start()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/repos/test/SportFit/contents/",e->{try{int status;String result;if(e.getRequestMethod().equals("GET")){status=content==null?404:200;result=content==null?"{}":json.writeValueAsString(Map.of("content",content,"sha",sha));}else{var body=json.readTree(e.getRequestBody());if(conflictOnce){conflictOnce=false;status=409;result="{}";}else if(content!=null&&!sha.equals(body.path("sha").asText())){status=409;result="{}";}else{status=content==null?201:200;content=body.get("content").asText();sha="v"+version.incrementAndGet();result="{}";}}byte[] b=result.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();}catch(Exception ex){throw new RuntimeException(ex);}});server.start();store=new GitHubAccountStore(json,"fake-test-token","test/SportFit","main",Base64.getEncoder().encodeToString(new byte[32]),"http://127.0.0.1:"+server.getAddress().getPort());}
 @AfterEach void stop(){server.stop(0);}
 @Test void encryptedRoundTripAndConflictRetry()throws Exception{
  assertNull(store.read("user@example.test"));var data=new HashMap<String,Object>();data.put("email","user@example.test");data.put("password","bcrypt-placeholder");data.put("name","Private fitness profile");assertTrue(store.create("user@example.test",data));assertFalse(store.create("user@example.test",data));
  String envelope=new String(Base64.getDecoder().decode(content),StandardCharsets.UTF_8);assertFalse(envelope.contains("user@example.test"));assertFalse(envelope.contains("Private fitness profile"));assertEquals("AES-256-GCM",json.readTree(envelope).get("algorithm").asText());assertEquals("Private fitness profile",store.read("user@example.test").get("name"));
  conflictOnce=true;store.update("user@example.test",a->{a.put("name","Updated");return a;});assertEquals("Updated",store.read("user@example.test").get("name"));assertEquals(2,version.get());
 }
 @Test void wrongKeyAndTamperedDataAreRejected()throws Exception{
  store.create("user@example.test",Map.of("email","user@example.test"));byte[] other=new byte[32];other[0]=1;var wrong=new GitHubAccountStore(json,"fake-test-token","test/SportFit","main",Base64.getEncoder().encodeToString(other),"http://127.0.0.1:"+server.getAddress().getPort());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->wrong.read("user@example.test"));
  var envelope=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(Base64.getDecoder().decode(content));byte[] cipher=Base64.getDecoder().decode(envelope.get("ciphertext").asText());cipher[0]^=1;envelope.put("ciphertext",Base64.getEncoder().encodeToString(cipher));content=Base64.getEncoder().encodeToString(json.writeValueAsBytes(envelope));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->store.read("user@example.test"));
 }
}

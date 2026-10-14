package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.UnaryOperator;
@Repository @ConditionalOnProperty(name="sportfit.storage",havingValue="github")
public class GitHubAccountStore implements AccountStore {
 private final ObjectMapper json;private final String token,repo,branch,apiBase;private final SecretKeySpec key;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();private final SecureRandom random=new SecureRandom();
 public GitHubAccountStore(ObjectMapper json,@Value("${sportfit.github.token:}") String token,@Value("${sportfit.github.repository:retomydb/SportFit}") String repo,@Value("${sportfit.github.branch:main}") String branch,@Value("${sportfit.github.encryption-key:}") String encryptionKey,@Value("${sportfit.github.api-url:https://api.github.com}") String apiBase){
  this.json=json;this.token=token;this.repo=repo;this.branch=branch;this.apiBase=apiBase;
  if(token.isBlank()||!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))throw new IllegalStateException("GitHub storage requires a server-side token and owner/repository.");
  try{byte[] k=Base64.getDecoder().decode(encryptionKey);if(k.length!=32)throw new IllegalArgumentException();key=new SecretKeySpec(k,"AES");}catch(Exception e){throw new IllegalStateException("SPORTFIT_DATA_KEY must be a base64-encoded 32-byte encryption key.");}
 }
 record Remote(Map<String,Object> account,String sha){}
 private String path(String email){try{return "data/accounts/"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)))+".json.enc";}catch(Exception e){throw new IllegalStateException(e);}}
 private HttpResponse<String> request(String method,String path,Map<String,Object> payload){try{
  var uri=URI.create(apiBase+"/repos/"+repo+"/contents/"+path+(method.equals("GET")?"?ref="+java.net.URLEncoder.encode(branch,StandardCharsets.UTF_8):""));
  var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Authorization","Bearer "+token).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2022-11-28");
  if(method.equals("GET"))b.GET();else b.header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
  return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}catch(Exception e){throw unavailable();}}
 private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account storage is temporarily unavailable. Please try again.");}
 private Remote load(String email){var res=request("GET",path(email),null);if(res.statusCode()==404)return null;if(res.statusCode()!=200)throw unavailable();try{var body=json.readTree(res.body());String envelope=new String(Base64.getMimeDecoder().decode(body.get("content").asText()),StandardCharsets.UTF_8);var e=json.readTree(envelope);if(e.get("version").asInt()!=1)throw new IllegalStateException();byte[] nonce=Base64.getDecoder().decode(e.get("nonce").asText());Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(path(email).getBytes(StandardCharsets.UTF_8));byte[] plain=cipher.doFinal(Base64.getDecoder().decode(e.get("ciphertext").asText()));Map<String,Object> account=json.readValue(plain,new TypeReference<>(){});return new Remote(account,body.get("sha").asText());}catch(Exception e){throw unavailable();}}
 private String encrypt(String email,Map<String,Object> account){try{byte[] nonce=new byte[12];random.nextBytes(nonce);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD(path(email).getBytes(StandardCharsets.UTF_8));return json.writeValueAsString(Map.of("version",1,"algorithm","AES-256-GCM","nonce",Base64.getEncoder().encodeToString(nonce),"ciphertext",Base64.getEncoder().encodeToString(c.doFinal(json.writeValueAsBytes(account)))));}catch(Exception e){throw unavailable();}}
 private int save(String email,Map<String,Object> account,String sha){var payload=new HashMap<String,Object>();payload.put("message",sha==null?"Create encrypted SportFit account":"Update encrypted SportFit account");payload.put("branch",branch);payload.put("content",Base64.getEncoder().encodeToString(encrypt(email,account).getBytes(StandardCharsets.UTF_8)));if(sha!=null)payload.put("sha",sha);return request("PUT",path(email),payload).statusCode();}
 public Map<String,Object> read(String email){var remote=load(email);return remote==null?null:remote.account();}
 public boolean create(String email,Map<String,Object> account){if(load(email)!=null)return false;int status=save(email,account,null);if(status==201)return true;if(status==409||status==422){if(load(email)!=null)return false;}throw unavailable();}
 public void update(String email,UnaryOperator<Map<String,Object>> change){for(int attempt=0;attempt<5;attempt++){var remote=load(email);if(remote==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");var updated=change.apply(remote.account());int status=save(email,updated,remote.sha());if(status==200)return;if(status!=409&&status!=422)throw unavailable();}throw new ResponseStatusException(HttpStatus.CONFLICT,"Your account was updated elsewhere. Please try again.");}
}

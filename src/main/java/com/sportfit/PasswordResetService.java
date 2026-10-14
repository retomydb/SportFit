package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.*;
import jakarta.annotation.PreDestroy;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
@Service public class PasswordResetService {
 private static final Logger LOG=LoggerFactory.getLogger(PasswordResetService.class);
 private final AccountStore store;private final PasswordEncoder passwords;private final ResetEmailSender mail;private final SessionRevoker sessions;private final Clock clock;private final ObjectMapper json;private final String frontend;private final SecretKeySpec key;
 private final SecureRandom random=new SecureRandom();
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"sportfit-reset-mail");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final Map<String,List<Instant>> attempts=new HashMap<>();
 public PasswordResetService(AccountStore store,PasswordEncoder passwords,ResetEmailSender mail,SessionRevoker sessions,Clock clock,ObjectMapper json,@Value("${sportfit.frontend-url:https://retomydb.github.io/SportFit/}") String frontend,@Value("${sportfit.github.encryption-key:}") String secret){this.store=store;this.passwords=passwords;this.mail=mail;this.sessions=sessions;this.clock=clock;this.json=json;this.frontend=frontend;
  try{var uri=java.net.URI.create(frontend);if(!"https".equals(uri.getScheme())&&!Set.of("localhost","127.0.0.1").contains(uri.getHost()))throw new IllegalArgumentException();if(uri.getFragment()!=null||uri.getUserInfo()!=null)throw new IllegalArgumentException();byte[] master;if(secret.isBlank()){master=new byte[32];random.nextBytes(master);}else master=Base64.getDecoder().decode(secret);var digest=MessageDigest.getInstance("SHA-256");digest.update("sportfit-password-reset-v1".getBytes(StandardCharsets.UTF_8));this.key=new SecretKeySpec(digest.digest(master),"AES");}catch(Exception e){throw new IllegalStateException("Invalid password reset configuration");}
 }
 @PreDestroy void close(){workers.shutdownNow();}
 private synchronized boolean allow(String id,int max){Instant now=clock.instant(),cutoff=now.minus(Duration.ofMinutes(15));attempts.values().forEach(list->list.removeIf(t->!t.isAfter(cutoff)));attempts.entrySet().removeIf(e->e.getValue().isEmpty());if(attempts.size()>10000)return false;var list=attempts.computeIfAbsent(id,k->new ArrayList<>());if(list.size()>=max)return false;list.add(now);return true;}
 public void request(String email,String ip){if(!mail.available())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Password reset emails are temporarily unavailable. Please try again later.");String normalized=email.trim().toLowerCase(Locale.ROOT);if(!allow("ip:"+ip,10)||!allow("email:"+hash(normalized),3))return;
  try{workers.execute(()->issue(normalized));}catch(RejectedExecutionException e){LOG.warn("Password reset request queue is full");}
 }
 private void issue(String email){try{var account=store.read(email);if(account==null)return;Instant expires=clock.instant().plus(Duration.ofMinutes(30));String token=makeToken(email,expires),hash=hash(token);String password=(String)account.get("password");store.update(email,a->{if(!Objects.equals(a.get("password"),password))throw invalid();a.put("passwordReset",Map.of("hash",hash,"expiresAt",expires.toString()));return a;});
   try{mail.sendReset(email,frontend+(frontend.contains("?")?"&":"?")+"recovery="+UUID.randomUUID()+"#reset-password?token="+token);}catch(Exception e){store.update(email,a->{Object value=a.get("passwordReset");if(value instanceof Map<?,?> reset&&hash.equals(reset.get("hash")))a.remove("passwordReset");return a;});LOG.warn("Password reset email delivery failed; link invalidated");}
  }catch(Exception e){LOG.warn("Password reset request could not be completed");}}
 private String makeToken(String email,Instant expires){try{byte[] nonce=new byte[12];random.nextBytes(nonce);byte[] salt=new byte[32];random.nextBytes(salt);byte[] payload=json.writeValueAsBytes(Map.of("email",email,"expiresAt",expires.toString(),"random",Base64.getUrlEncoder().withoutPadding().encodeToString(salt)));var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD("sportfit-password-reset-v1".getBytes(StandardCharsets.UTF_8));byte[] ciphertext=cipher.doFinal(payload),all=new byte[nonce.length+ciphertext.length];System.arraycopy(nonce,0,all,0,12);System.arraycopy(ciphertext,0,all,12,ciphertext.length);return Base64.getUrlEncoder().withoutPadding().encodeToString(all);}catch(Exception e){throw new IllegalStateException("Reset token creation failed");}}
 private Map<String,String> parse(String token){try{byte[] all=Base64.getUrlDecoder().decode(token);if(all.length<29)throw invalid();var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(all,12)));cipher.updateAAD("sportfit-password-reset-v1".getBytes(StandardCharsets.UTF_8));var data=json.readTree(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)));String email=data.get("email").asText(),expires=data.get("expiresAt").asText();if(!Instant.parse(expires).isAfter(clock.instant()))throw invalid();return Map.of("email",email,"expiresAt",expires);}catch(Exception e){throw invalid();}}
 public void confirm(String token,String password){if(password.getBytes(StandardCharsets.UTF_8).length>72)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a password of at most 72 UTF-8 bytes.");var claims=parse(token);String email=claims.get("email"),hash=hash(token),encoded=passwords.encode(password);if(store.read(email)==null)throw invalid();store.update(email,a->{Object v=a.get("passwordReset");if(!(v instanceof Map<?,?> reset)||!MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),String.valueOf(reset.get("hash")).getBytes(StandardCharsets.UTF_8))||!Instant.parse(String.valueOf(reset.get("expiresAt"))).isAfter(clock.instant()))throw invalid();a.put("password",encoded);a.remove("passwordReset");return a;});sessions.revoke(email);}
 private String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"This reset link is invalid or has expired. Request a new link.");}
}

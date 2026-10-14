package com.sportfit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reset-tests;DB_CLOSE_DELAY=-1","debug=false"}) @AutoConfigureMockMvc
class PasswordResetTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired AccountStore store; @Autowired PasswordEncoder passwords; @Autowired PasswordResetService service;
 @MockitoBean ResetEmailSender mail; @MockitoBean Clock clock;
 final BlockingQueue<String> links=new LinkedBlockingQueue<>();String email;Instant now=Instant.parse("2026-10-14T20:00:00Z");
 @BeforeEach void setup()throws Exception{when(clock.instant()).thenReturn(now);when(mail.available()).thenReturn(true);doAnswer(call->{links.add(call.getArgument(1));return null;}).when(mail).sendReset(anyString(),anyString());email="reset-"+UUID.randomUUID()+"@example.test";var a=new HashMap<String,Object>();a.put("email",email);a.put("password",passwords.encode("Original123!"));a.put("profile",Map.of("name","Reset Athlete"));a.put("workouts",new ArrayList<>());a.put("plans",new ArrayList<>());a.put("wellness",new ArrayList<>());store.create(email,a);}
 String issue()throws Exception{service.request(email,UUID.randomUUID().toString());String url=links.poll(5,TimeUnit.SECONDS);assertNotNull(url,"Reset email should be generated");assertTrue(url.startsWith("https://retomydb.github.io/SportFit/?recovery="));assertTrue(url.contains("#reset-password?token="));return url.substring(url.indexOf("token=")+6);}
 @Test void resetIsSingleUseAndRevokesSessions()throws Exception{
  var login=mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password","Original123!")).andExpect(status().isOk()).andReturn();String session=login.getResponse().getHeader("X-Auth-Token");mvc.perform(get("/api/state").header("X-Auth-Token",session)).andExpect(status().isOk());
  var secondLogin=mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password","Original123!")).andExpect(status().isOk()).andReturn();String otherSession=secondLogin.getResponse().getHeader("X-Auth-Token");
  String token=issue();String stored=store.read(email).get("passwordReset").toString();assertFalse(stored.contains(token));assertFalse(new String(Base64.getUrlDecoder().decode(token),java.nio.charset.StandardCharsets.UTF_8).contains(email));
  mvc.perform(post("/api/password-reset/confirm").header("X-Auth-Token",session).with(csrf()).contentType("application/json").content("{\"token\":\""+token+"\",\"newPassword\":\"Replacement123!\"}")).andExpect(status().isOk());
  assertTrue(passwords.matches("Replacement123!",(String)store.read(email).get("password")));assertNull(store.read(email).get("passwordReset"));
  mvc.perform(get("/api/state").header("X-Auth-Token",session)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/state").header("X-Auth-Token",otherSession)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password","Original123!")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/login").with(csrf()).param("username",email).param("password","Replacement123!")).andExpect(status().isOk());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.confirm(token,"OtherPassword123!"));
 }
 @Test void expirationTamperingAndReplacement()throws Exception{
  String first=issue(),second=issue();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.confirm(first,"Replacement123!"));
  char replacement=second.charAt(20)=='A'?'B':'A';String changed=second.substring(0,20)+replacement+second.substring(21);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.confirm(changed,"Replacement123!"));
  when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(31)));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.confirm(second,"Replacement123!"));assertTrue(passwords.matches("Original123!",(String)store.read(email).get("password")));
 }
 @Test void knownAndUnknownEmailsHaveSameResponseAndCsrfProtection()throws Exception{
  String one=mvc.perform(post("/api/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
  String two=mvc.perform(post("/api/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\"missing-"+UUID.randomUUID()+"@example.test\"}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();assertEquals(one,two);assertNotNull(links.poll(5,TimeUnit.SECONDS));
  mvc.perform(post("/api/password-reset/request").contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isForbidden());
 }
 @Test void requestLimitsAndEmailFailureInvalidateToken()throws Exception{
  issue();issue();issue();service.request(email,UUID.randomUUID().toString());assertNull(links.poll(300,TimeUnit.MILLISECONDS));
  String other="failure-"+UUID.randomUUID()+"@example.test";var account=new HashMap<String,Object>(store.read(email));account.remove("passwordReset");account.put("email",other);store.create(other,account);CountDownLatch failed=new CountDownLatch(1);doAnswer(call->{failed.countDown();throw new IllegalStateException("Simulated email provider failure");}).when(mail).sendReset(eq(other),anyString());service.request(other,UUID.randomUUID().toString());assertTrue(failed.await(5,TimeUnit.SECONDS));for(int i=0;i<100&&store.read(other).get("passwordReset")!=null;i++)Thread.sleep(10);assertNull(store.read(other).get("passwordReset"));
 }
 @Test void signedInPasswordChangeInvalidatesOutstandingReset()throws Exception{
  String token=issue();mvc.perform(put("/api/password").with(user(email)).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"Original123!\",\"newPassword\":\"ChangedInProfile123!\"}")).andExpect(status().isOk());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.confirm(token,"Replacement123!"));
 }
}

package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"}) @AutoConfigureMockMvc
class ApiTests {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired AccountStore store; @Autowired ObjectMapper json;
 String account="{\"name\":\"Test Athlete\",\"email\":\"athlete@example.test\",\"password\":\"Testing123!\"}";
 @BeforeEach void reset()throws Exception{db.update("DELETE FROM account_documents");db.update("DELETE FROM wellness");db.update("DELETE FROM plans");db.update("DELETE FROM workouts");db.update("DELETE FROM users");mvc.perform(post("/api/register").with(csrf()).contentType("application/json").content(account)).andExpect(status().isCreated());}
 @Test void securityAndAuthentication()throws Exception{
  mvc.perform(get("/api/state")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/register").contentType("application/json").content(account)).andExpect(status().isForbidden());
  mvc.perform(post("/api/register").with(csrf()).contentType("application/json").content(account)).andExpect(status().isConflict());
  assertTrue(((String)store.read("athlete@example.test").get("password")).startsWith("$2a$"));
  var login=mvc.perform(post("/api/login").with(csrf()).param("username","athlete@example.test").param("password","Testing123!")).andExpect(status().isOk()).andReturn();
  mvc.perform(get("/api/state").header("X-Auth-Token",login.getResponse().getHeader("X-Auth-Token"))).andExpect(status().isOk()).andExpect(jsonPath("$.profile.name").value("Test Athlete"));
 }
 @Test void workoutValidationAndStorage()throws Exception{
  mvc.perform(post("/api/workouts").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content("{\"activity\":\"running\",\"duration\":1800,\"reps\":4,\"sets\":2,\"distance\":3.2}")).andExpect(status().isCreated()).andExpect(jsonPath("$.calories").value(360));
  mvc.perform(get("/api/state").with(user("athlete@example.test"))).andExpect(jsonPath("$.workouts.length()").value(1));
  mvc.perform(post("/api/workouts").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content("{\"activity\":\"running\",\"duration\":-2,\"reps\":0,\"sets\":0,\"distance\":0}")).andExpect(status().isBadRequest());
 }
 @Test void plansAreOwnedAndEditable()throws Exception{
  mvc.perform(post("/api/register").with(csrf()).contentType("application/json").content(account.replace("athlete@","other@"))).andExpect(status().isCreated());
  String plan="{\"date\":\"2026-10-20\",\"activity\":\"yoga\",\"duration\":25,\"completed\":false}";
  var res=mvc.perform(post("/api/plans").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content(plan)).andExpect(status().isCreated()).andReturn();
  String id=json.readTree(res.getResponse().getContentAsString()).get("id").asText();
  mvc.perform(delete("/api/plans/"+id).with(user("other@example.test")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get("/api/state").with(user("other@example.test"))).andExpect(jsonPath("$.plans.length()").value(0));
  mvc.perform(put("/api/plans/"+id).with(user("athlete@example.test")).with(csrf()).contentType("application/json").content(plan.replace("false","true"))).andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
  mvc.perform(delete("/api/plans/"+id).with(user("athlete@example.test")).with(csrf())).andExpect(status().isNoContent());
 }
 @Test void wellnessUpsertAndPasswordChange()throws Exception{
  String w="{\"date\":\"2026-10-14\",\"water\":250,\"sleep\":6.5,\"intensity\":9,\"heartRate\":70,\"recovery\":\"tired\",\"restDay\":true}";
  mvc.perform(put("/api/wellness").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content(w)).andExpect(status().isOk());
  mvc.perform(put("/api/wellness").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content(w.replace("250","500"))).andExpect(status().isOk());
  mvc.perform(get("/api/state").with(user("athlete@example.test"))).andExpect(jsonPath("$.wellness.length()").value(1)).andExpect(jsonPath("$.wellness[0].water").value(500));
  mvc.perform(put("/api/password").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewPassword123!\"}")).andExpect(status().isBadRequest());
  mvc.perform(put("/api/password").with(user("athlete@example.test")).with(csrf()).contentType("application/json").content("{\"currentPassword\":\"Testing123!\",\"newPassword\":\"NewPassword123!\"}")).andExpect(status().isOk());
 }
}

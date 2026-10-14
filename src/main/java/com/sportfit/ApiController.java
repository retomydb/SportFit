package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiController {
 final AccountStore store; final ObjectMapper json; final PasswordEncoder passwords;
 static final Set<String> SPORTS=Set.of("running","walking","cycling","swimming","football","basketball","yoga","strength","aerobic","stretching");
 ApiController(AccountStore store,ObjectMapper j,PasswordEncoder p){this.store=store;json=j;passwords=p;}
 record Registration(@NotBlank @Size(max=80) String name,@Email @NotBlank @Size(max=254) String email,@Size(min=8,max=72) String password){}
 record PasswordChange(@NotBlank String currentPassword,@Size(min=8,max=72) String newPassword){}
 record Profile(@NotBlank @Size(max=80) String name,@Min(13) @Max(120) int age,@DecimalMin("80") @DecimalMax("250") double height,@DecimalMin("25") @DecimalMax("350") double weight,@NotBlank String level,@Size(max=300) String objective,@Min(1) @Max(21) int weeklyTarget,List<String> preferences,List<String> savedActivities){}
 record Workout(@NotBlank String activity,@Min(1) @Max(86400) long duration,@Min(0) @Max(100000) int reps,@Min(0) @Max(1000) int sets,@DecimalMin("0") @DecimalMax("1000") double distance){}
 record Plan(@NotNull LocalDate date,@NotBlank String activity,@Min(5) @Max(300) int duration,boolean completed){}
 record Wellness(@NotNull LocalDate date,@Min(0) @Max(20000) int water,@DecimalMin("0") @DecimalMax("24") double sleep,@Min(1) @Max(10) int intensity,@Min(0) @Max(240) int heartRate,@NotBlank String recovery,boolean restDay){}
 String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException("Invalid data");}}
 Map<String,Object> decode(String s){try{return json.readValue(s,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 String email(Authentication a){return a.getName();}
 @SuppressWarnings("unchecked") Map<String,Object> profile(String email){return (Map<String,Object>)account(email).get("profile");}
 Map<String,Object> account(String email){var a=store.read(email);if(a==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found");return a;}
 @SuppressWarnings("unchecked") List<Map<String,Object>> list(Map<String,Object> a,String name){return (List<Map<String,Object>>)a.get(name);}
 void sport(String s){if(!SPORTS.contains(s))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a supported activity.");}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken t){return Map.of("token",t.getToken(),"headerName",t.getHeaderName());}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) Map<String,Boolean> register(@Valid @RequestBody Registration r){
  var p=new HashMap<String,Object>();p.put("name",r.name().trim());p.put("age",25);p.put("height",175);p.put("weight",70);p.put("level","beginner");p.put("objective","Build a consistent routine");p.put("weeklyTarget",3);p.put("preferences",List.of("running","yoga"));p.put("savedActivities",List.of());
  String email=r.email().trim().toLowerCase();var a=new HashMap<String,Object>();a.put("email",email);a.put("password",passwords.encode(r.password()));a.put("profile",p);a.put("workouts",new ArrayList<>());a.put("plans",new ArrayList<>());a.put("wellness",new ArrayList<>());if(!store.create(email,a))throw new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists.");return Map.of("ok",true);
 }
 @GetMapping("/state") Map<String,Object> state(Authentication a){String e=email(a);var record=account(e);return Map.of("profile",record.get("profile"),"email",e,"workouts",record.get("workouts"),"plans",record.get("plans"),"wellness",record.get("wellness"));}
 
 @PutMapping("/profile") Map<String,Object> updateProfile(Authentication a,@Valid @RequestBody Profile p){if(!Set.of("beginner","intermediate","advanced").contains(p.level()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid fitness level");if(p.preferences()==null||p.savedActivities()==null||!SPORTS.containsAll(p.preferences())||!SPORTS.containsAll(p.savedActivities()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid activities");store.update(email(a),record->{record.put("profile",decode(encode(p)));return record;});return profile(email(a));}
 @PutMapping("/password") Map<String,Boolean> changePassword(Authentication a,@Valid @RequestBody PasswordChange p){store.update(email(a),record->{if(!passwords.matches(p.currentPassword(),(String)record.get("password")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Current password is incorrect.");record.put("password",passwords.encode(p.newPassword()));return record;});return Map.of("ok",true);}
 @PostMapping("/workouts") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> workout(Authentication a,@Valid @RequestBody Workout w){sport(w.activity());var p=profile(email(a));double weight=((Number)p.get("weight")).doubleValue();double met=switch(w.activity()){case "running"->9.8;case "cycling"->7.5;case "swimming"->8;case "football","basketball","aerobic"->7;case "strength"->5;case "walking"->3.5;default->2.5;};var data=new HashMap<String,Object>();String id=UUID.randomUUID().toString();data.put("id",id);data.put("activity",w.activity());data.put("duration",w.duration());data.put("reps",w.reps());data.put("sets",w.sets());data.put("distance",w.distance());data.put("calories",Math.round(met*3.5*weight/200*w.duration()/60));data.put("endedAt",Instant.now().toString());store.update(email(a),record->{list(record,"workouts").add(data);return record;});return data;}
 @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> addPlan(Authentication a,@Valid @RequestBody Plan p){sport(p.activity());var d=new HashMap<String,Object>(decode(encode(p)));String id=UUID.randomUUID().toString();d.put("id",id);store.update(email(a),record->{list(record,"plans").add(d);return record;});return d;}
 @PutMapping("/plans/{id}") Map<String,Object> editPlan(Authentication a,@PathVariable String id,@Valid @RequestBody Plan p){sport(p.activity());var d=new HashMap<String,Object>(decode(encode(p)));d.put("id",id);store.update(email(a),record->{var plans=list(record,"plans");if(!plans.removeIf(plan->id.equals(plan.get("id"))))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");plans.add(d);return record;});return d;}
 @DeleteMapping("/plans/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deletePlan(Authentication a,@PathVariable String id){store.update(email(a),record->{if(!list(record,"plans").removeIf(p->id.equals(p.get("id"))))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");return record;});}
 @PutMapping("/wellness") Map<String,Object> wellness(Authentication a,@Valid @RequestBody Wellness w){if(w.date().isAfter(LocalDate.now().plusDays(1))||!Set.of("great","okay","tired","sore").contains(w.recovery()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid wellness record");String data=encode(w);store.update(email(a),record->{var items=list(record,"wellness");items.removeIf(item->w.date().toString().equals(item.get("date")));items.add(0,decode(data));return record;});return decode(data);}
}

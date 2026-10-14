package com.sportfit;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
import org.springframework.security.config.Customizer;
import java.util.*;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean UserDetailsService users(AccountStore store){return email -> {var a=store.read(email.toLowerCase().trim());if(a==null)throw new UsernameNotFoundException("Invalid credentials");return User.withUsername((String)a.get("email")).password((String)a.get("password")).roles("USER").build();};}
 @Bean CorsConfigurationSource cors(@Value("${sportfit.allowed-origins:http://localhost:8087,http://127.0.0.1:8087}") String origins){var c=new CorsConfiguration();c.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN","X-Auth-Token"));c.setExposedHeaders(List.of("X-Auth-Token"));c.setAllowCredentials(false);var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",c);return source;}
 @Bean SecurityFilterChain security(HttpSecurity h)throws Exception{return h.cors(Customizer.withDefaults()).authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/app.js","/styles.css","/config.js","/vendor/**","/images/**","/api/csrf","/api/register","/error").permitAll().anyRequest().authenticated()).formLogin(f->f.loginProcessingUrl("/api/login").successHandler((req,res,auth)->{res.setContentType("application/json");res.getWriter().write("{\"ok\":true}");}).failureHandler((req,res,e)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"error\":\"Email or password is incorrect.\"}");})).logout(l->l.logoutUrl("/api/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204))).exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->res.sendError(401))).build();}
}

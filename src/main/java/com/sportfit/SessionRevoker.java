package com.sportfit;
import org.springframework.stereotype.Component;
import org.springframework.session.*;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import java.util.Map;
@Component public class SessionRevoker {
 private final Map<String,Session> sessions;private final MapSessionRepository repository;
 public SessionRevoker(Map<String,Session> sessions,MapSessionRepository repository){this.sessions=sessions;this.repository=repository;}
 public void revoke(String email){sessions.forEach((id,s)->{Object context=s.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);if(context instanceof SecurityContext c&&c.getAuthentication()!=null&&email.equals(c.getAuthentication().getName()))repository.deleteById(id);});}
}

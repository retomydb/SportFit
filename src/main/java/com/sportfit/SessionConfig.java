package com.sportfit;
import org.springframework.context.annotation.*;
import org.springframework.session.*;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.*;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
@Configuration @EnableSpringHttpSession
public class SessionConfig {
 @Bean MapSessionRepository sessionRepository(){var r=new MapSessionRepository(new ConcurrentHashMap<>());r.setDefaultMaxInactiveInterval(Duration.ofHours(8));return r;}
 @Bean HttpSessionIdResolver httpSessionIdResolver(){return HeaderHttpSessionIdResolver.xAuthToken();}
}

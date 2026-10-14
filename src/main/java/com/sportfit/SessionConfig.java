package com.sportfit;
import org.springframework.context.annotation.*;
import org.springframework.session.*;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.*;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Configuration @EnableSpringHttpSession
public class SessionConfig {
 @Bean Clock resetClock(){return Clock.systemUTC();}
 @Bean Map<String,Session> sessionMap(){return new ConcurrentHashMap<>();}
 @Bean MapSessionRepository sessionRepository(Map<String,Session> sessionMap){var r=new MapSessionRepository(sessionMap);r.setDefaultMaxInactiveInterval(Duration.ofHours(8));return r;}
 @Bean HttpSessionIdResolver httpSessionIdResolver(){return HeaderHttpSessionIdResolver.xAuthToken();}
}

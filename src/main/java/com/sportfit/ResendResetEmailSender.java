package com.sportfit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Component
public class ResendResetEmailSender implements ResetEmailSender {
 private final String key,from,base;private final ObjectMapper json;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 public ResendResetEmailSender(ObjectMapper json,@Value("${sportfit.mail.api-key:}") String key,@Value("${sportfit.mail.from:}") String from,@Value("${sportfit.mail.api-url:https://api.resend.com}") String base){this.json=json;this.key=key;this.from=from;this.base=base;}
 public boolean available(){return !key.isBlank()&&!from.isBlank();}
 public void sendReset(String email,String url){
  if(!available())throw new IllegalStateException("Reset email is not configured");
  try{String text="A password reset was requested for your SportFit Pro account.\n\nChoose a new password: "+url+"\n\nThis link expires in 30 minutes and can be used once. If you did not request this, ignore this message. Your password has not changed.\n\nSportFit Pro";
   var body=Map.of("from",from,"to",List.of(email),"subject","Reset your SportFit Pro password","text",text);
   var request=HttpRequest.newBuilder(URI.create(base+"/emails")).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+key).header("Content-Type","application/json").header("Idempotency-Key","sportfit-reset-"+UUID.randomUUID()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
   var response=http.send(request,HttpResponse.BodyHandlers.discarding());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Email provider rejected delivery");
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Reset email delivery failed");}catch(Exception e){throw new IllegalStateException("Reset email delivery failed");}
 }
}

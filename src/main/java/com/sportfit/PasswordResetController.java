package com.sportfit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.Map;
@RestController @RequestMapping("/api/password-reset")
public class PasswordResetController {
 private final PasswordResetService reset;
 public PasswordResetController(PasswordResetService reset){this.reset=reset;}
 record Request(@Email @NotBlank @Size(max=254) String email){}
 record Confirm(@NotBlank @Size(max=2048) String token,@NotNull @Size(min=8,max=72) String newPassword){}
 @PostMapping("/request") @ResponseStatus(HttpStatus.ACCEPTED) Map<String,String> request(@Valid @RequestBody Request body,HttpServletRequest req){reset.request(body.email(),req.getRemoteAddr());return Map.of("message","If an account exists for that email, a password reset link will arrive shortly. Check your spam folder too.");}
 @PostMapping("/confirm") Map<String,String> confirm(@Valid @RequestBody Confirm body,HttpServletRequest request){reset.confirm(body.token(),body.newPassword());var session=request.getSession(false);if(session!=null)session.invalidate();return Map.of("message","Password updated. Sign in with your new password.");}
}

package cn.campus.exchange.api;

import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.repository.Store;
import cn.campus.exchange.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  private final AuthService auth;
  private final Store db;

  public AuthController(AuthService auth, Store db) {
    this.auth = auth;
    this.db = db;
  }

  @GetMapping("/health")
  public Object health() {
    return ApiResult.ok(
        java.util.Map.of(
            "database", db.count("SELECT 1") == 1 ? "up" : "down", "service", "Campus Exchange"));
  }

  @PostMapping("/auth/register")
  public Object register(@Valid @RequestBody Requests.Register r) {
    return ApiResult.ok(auth.register(r));
  }

  @PostMapping("/auth/login")
  public Object login(@Valid @RequestBody Requests.Login r) {
    return ApiResult.ok(auth.login(r));
  }

  @PostMapping("/auth/logout")
  public Object logout(@RequestHeader(value = "Authorization", required = false) String bearer) {
    auth.logout(bearer);
    return ApiResult.ok(null);
  }

  @GetMapping("/me")
  public Object me() {
    return ApiResult.ok(auth.profile(SecurityConfig.actor().id()));
  }

  @PutMapping("/me")
  public Object update(@Valid @RequestBody Requests.Profile r) {
    return ApiResult.ok(auth.updateProfile(SecurityConfig.actor().id(), r));
  }
}

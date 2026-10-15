package cn.campus.exchange.api;

import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final AdminService admin;

  public AdminController(AdminService admin) {
    this.admin = admin;
  }

  @GetMapping("/dashboard")
  public Object dashboard() {
    return ApiResult.ok(admin.dashboard());
  }

  @GetMapping("/goods")
  public Object goods(
      @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
    return ApiResult.ok(admin.goods(status, q));
  }

  @PostMapping("/goods/{id}/review")
  public Object review(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    admin.review(id, SecurityConfig.actor().id(), r);
    return ApiResult.ok(null);
  }

  @GetMapping("/users")
  public Object users(@RequestParam(required = false) String q) {
    return ApiResult.ok(admin.users(q));
  }

  @PostMapping("/users/{id}/status")
  public Object user(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    admin.user(id, SecurityConfig.actor().id(), r);
    return ApiResult.ok(null);
  }

  @GetMapping("/reports")
  public Object reports(@RequestParam(required = false) String status) {
    return ApiResult.ok(admin.reports(status));
  }

  @PostMapping("/reports/{id}/handle")
  public Object handle(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    admin.handle(id, SecurityConfig.actor().id(), r);
    return ApiResult.ok(null);
  }

  @GetMapping("/orders")
  public Object orders(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String buyer,
      @RequestParam(required = false) String seller,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    return ApiResult.ok(admin.orders(status, buyer, seller, from, to));
  }

  @GetMapping("/audit")
  public Object audit() {
    return ApiResult.ok(admin.audit());
  }
}

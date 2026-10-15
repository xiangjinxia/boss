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
  public Object dashboard(
      @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
    return ApiResult.ok(admin.dashboard(from, to));
  }

  @GetMapping("/goods")
  public Object goods(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.goods(status, q, page, size));
  }

  @PostMapping("/goods/{id}/review")
  public Object review(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    admin.review(id, SecurityConfig.actor().id(), r);
    return ApiResult.ok(null);
  }

  @GetMapping("/users")
  public Object users(
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.users(q, page, size));
  }

  @PostMapping("/users/{id}/status")
  public Object user(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    admin.user(id, SecurityConfig.actor().id(), r);
    return ApiResult.ok(null);
  }

  @GetMapping("/reports")
  public Object reports(
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.reports(status, page, size));
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
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.orders(status, buyer, seller, from, to, page, size));
  }

  @GetMapping("/audit")
  public Object audit(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.audit(page, size));
  }

  @GetMapping("/categories")
  public Object categories(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.categories(page, size));
  }

  @PostMapping("/categories")
  public Object createCategory(@Valid @RequestBody Requests.Category r) {
    return ApiResult.ok(admin.category(null, SecurityConfig.actor().id(), r));
  }

  @PutMapping("/categories/{id}")
  public Object editCategory(@PathVariable long id, @Valid @RequestBody Requests.Category r) {
    return ApiResult.ok(admin.category(id, SecurityConfig.actor().id(), r));
  }

  @DeleteMapping("/categories/{id}")
  public Object deleteCategory(@PathVariable long id) {
    admin.deleteCategory(id, SecurityConfig.actor().id());
    return ApiResult.ok(null);
  }

  @GetMapping("/announcements")
  public Object announcements(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(admin.announcements(page, size));
  }

  @PostMapping("/announcements")
  public Object createAnnouncement(@Valid @RequestBody Requests.Announcement r) {
    return ApiResult.ok(admin.announcement(null, SecurityConfig.actor().id(), r));
  }

  @PutMapping("/announcements/{id}")
  public Object editAnnouncement(
      @PathVariable long id, @Valid @RequestBody Requests.Announcement r) {
    return ApiResult.ok(admin.announcement(id, SecurityConfig.actor().id(), r));
  }

  @DeleteMapping("/announcements/{id}")
  public Object deleteAnnouncement(@PathVariable long id) {
    admin.deleteAnnouncement(id, SecurityConfig.actor().id());
    return ApiResult.ok(null);
  }
}

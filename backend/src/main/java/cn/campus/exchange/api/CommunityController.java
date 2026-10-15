package cn.campus.exchange.api;

import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommunityController {
  private final CommunityService community;
  private final DiscoveryService discovery;

  public CommunityController(CommunityService community, DiscoveryService discovery) {
    this.community = community;
    this.discovery = discovery;
  }

  @GetMapping("/discovery")
  public Object discovery(@RequestParam(defaultValue = "4") int size) {
    return ApiResult.ok(discovery.discover(SecurityConfig.actor(), size));
  }

  @GetMapping("/announcements")
  public Object announcements() {
    return ApiResult.ok(community.announcements());
  }

  @GetMapping("/sellers/{id}")
  public Object seller(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "8") int size) {
    return ApiResult.ok(community.seller(id, page, size));
  }

  @GetMapping("/sellers/{id}/reviews")
  public Object reviews(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResult.ok(community.reviews(id, page, size));
  }

  @PostMapping("/orders/{id}/review")
  public Object review(@PathVariable long id, @Valid @RequestBody Requests.Review r) {
    return ApiResult.ok(community.review(id, SecurityConfig.actor(), r));
  }

  @GetMapping("/me/notifications")
  public Object notifications(
      @RequestParam(defaultValue = "false") boolean unread,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(community.notifications(SecurityConfig.actor(), unread, page, size));
  }

  @PostMapping("/me/notifications/{id}/read")
  public Object read(@PathVariable long id) {
    community.read(SecurityConfig.actor(), id);
    return ApiResult.ok(null);
  }

  @PostMapping("/me/notifications/read-all")
  public Object readAll() {
    community.read(SecurityConfig.actor(), null);
    return ApiResult.ok(null);
  }
}

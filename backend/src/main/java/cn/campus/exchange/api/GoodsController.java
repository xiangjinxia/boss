package cn.campus.exchange.api;

import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.service.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GoodsController {
  private final GoodsService goods;
  private final OrderService orders;

  public GoodsController(GoodsService goods, OrderService orders) {
    this.goods = goods;
    this.orders = orders;
  }

  @GetMapping("/categories")
  public Object categories() {
    return ApiResult.ok(goods.categories());
  }

  @GetMapping("/public/stats")
  public Object stats() {
    return ApiResult.ok(goods.publicStats());
  }

  @GetMapping("/goods")
  public Object list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long category,
      @RequestParam(required = false) BigDecimal min,
      @RequestParam(required = false) BigDecimal max,
      @RequestParam(required = false) Integer days,
      @RequestParam(defaultValue = "newest") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "12") int size) {
    return ApiResult.ok(goods.browse(q, category, min, max, days, sort, page, size));
  }

  @GetMapping("/goods/{id}")
  public Object detail(@PathVariable long id) {
    return ApiResult.ok(goods.detail(id, SecurityConfig.actor()));
  }

  @PostMapping("/goods")
  public Object create(@Valid @RequestBody Requests.Goods r) {
    return ApiResult.ok(java.util.Map.of("id", goods.save(null, SecurityConfig.actor(), r)));
  }

  @PutMapping("/goods/{id}")
  public Object edit(@PathVariable long id, @Valid @RequestBody Requests.Goods r) {
    return ApiResult.ok(java.util.Map.of("id", goods.save(id, SecurityConfig.actor(), r)));
  }

  @PostMapping("/goods/{id}/offline")
  public Object offline(@PathVariable long id) {
    goods.offline(id, SecurityConfig.actor());
    return ApiResult.ok(null);
  }

  @PutMapping("/goods/{id}/favorite")
  public Object favorite(@PathVariable long id) {
    goods.favorite(id, SecurityConfig.actor(), true);
    return ApiResult.ok(null);
  }

  @DeleteMapping("/goods/{id}/favorite")
  public Object unfavorite(@PathVariable long id) {
    goods.favorite(id, SecurityConfig.actor(), false);
    return ApiResult.ok(null);
  }

  @GetMapping("/goods/{id}/messages")
  public Object messages(@PathVariable long id) {
    return ApiResult.ok(goods.messages(id, SecurityConfig.actor()));
  }

  @PostMapping("/goods/{id}/messages")
  public Object send(@PathVariable long id, @Valid @RequestBody Requests.Text r) {
    return ApiResult.ok(goods.send(id, SecurityConfig.actor(), r));
  }

  @GetMapping("/me/goods")
  public Object mine() {
    return ApiResult.ok(goods.mine(SecurityConfig.actor().id()));
  }

  @GetMapping("/me/favorites")
  public Object favorites() {
    return ApiResult.ok(goods.favorites(SecurityConfig.actor()));
  }

  @GetMapping("/me/summary")
  public Object summary() {
    return ApiResult.ok(goods.summary(SecurityConfig.actor()));
  }

  @GetMapping("/me/messages")
  public Object inbox() {
    return ApiResult.ok(goods.inbox(SecurityConfig.actor()));
  }

  @PostMapping("/reports")
  public Object report(@Valid @RequestBody Requests.Report r) {
    return ApiResult.ok(goods.report(SecurityConfig.actor(), r));
  }

  @GetMapping("/me/reports")
  public Object reports() {
    return ApiResult.ok(goods.reports(SecurityConfig.actor()));
  }

  @GetMapping("/me/orders")
  public Object orders(@RequestParam(defaultValue = "buying") String side) {
    return ApiResult.ok(orders.mine(SecurityConfig.actor(), side));
  }

  @PostMapping("/orders")
  public Object order(@Valid @RequestBody Requests.CreateOrder r) {
    return ApiResult.ok(orders.create(SecurityConfig.actor(), r.goodsId()));
  }

  @PostMapping("/orders/{id}/actions")
  public Object action(@PathVariable long id, @Valid @RequestBody Requests.Action r) {
    orders.action(id, SecurityConfig.actor(), r);
    return ApiResult.ok(null);
  }
}

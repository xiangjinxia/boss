package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.config.SecurityConfig.Actor;
import cn.campus.exchange.repository.Store;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DiscoveryService {
  private final Store db;

  public DiscoveryService(Store db) {
    this.db = db;
  }

  public Object discover(Actor a, int size) {
    require(size >= 1 && size <= 12, 400, "推荐数量为 1–12");
    var favorites =
        a == null
            ? List.<Map<String, Object>>of()
            : db.list(
                "SELECT g.category_id,c.name,g.price FROM favorite f JOIN goods g ON"
                    + " g.id=f.goods_id JOIN category c ON c.id=g.category_id WHERE f.user_id=?"
                    + " ORDER BY f.id DESC LIMIT 200",
                a.id());
    Map<Long, Long> counts = new HashMap<>();
    Map<Long, String> names = new HashMap<>();
    List<Double> prices = new ArrayList<>();
    for (var f : favorites) {
      long c = Store.id(f, "category_id");
      counts.merge(c, 1L, Long::sum);
      names.put(c, f.get("name").toString());
      prices.add(((Number) f.get("price")).doubleValue());
    }
    prices.sort(Double::compare);
    double center =
        prices.isEmpty()
            ? 0
            : prices.size() % 2 == 1
                ? prices.get(prices.size() / 2)
                : (prices.get(prices.size() / 2 - 1) + prices.get(prices.size() / 2)) / 2;
    double maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
    boolean personal = !favorites.isEmpty();
    var args = new ArrayList<Object>();
    String where = " WHERE g.status='LISTED' AND u.status='ACTIVE'";
    if (a != null) {
      where +=
          " AND g.seller_id<>? AND NOT EXISTS (SELECT 1 FROM favorite mine WHERE mine.user_id=? AND"
              + " mine.goods_id=g.id)";
      args.add(a.id());
      args.add(a.id());
    }
    var candidates =
        db.list(
            GoodsService.CARD.replace(
                    "SELECT g.*", "SELECT TIMESTAMPDIFF(HOUR,g.created_at,NOW()) age_hours,g.*")
                + where,
            args.toArray());
    for (var g : candidates) {
      long category = Store.id(g, "category_id");
      double categoryPart = personal ? 45 * counts.getOrDefault(category, 0L) / maxCount : 0;
      double price = ((Number) g.get("price")).doubleValue();
      double pricePart =
          personal ? 25 * Math.max(0, 1 - Math.abs(price - center) / Math.max(center, 1)) : 0;
      double popularity =
          Math.min(1, Math.log1p(((Number) g.get("favorite_count")).doubleValue()) / Math.log(11));
      double recency = 1 / (1 + Math.max(0, ((Number) g.get("age_hours")).doubleValue()) / 168);
      double popularPart = (personal ? 20 : 55) * popularity;
      double recentPart = (personal ? 10 : 45) * recency;
      var reasons = new ArrayList<String>();
      if (categoryPart > 0) reasons.add("与你收藏的" + g.get("category_name") + "同类");
      if (personal && pricePart >= 17.5) reasons.add("接近你的收藏价格");
      if (((Number) g.get("favorite_count")).intValue() > 0) reasons.add("有同学收藏");
      if (reasons.isEmpty()) reasons.add(personal ? "发现不同类别的新好物" : "近期上架的新好物");
      g.put("recommendation_reason", String.join(" · ", reasons));
      g.put(
          "recommendation_score",
          Math.round((categoryPart + pricePart + popularPart + recentPart) * 100) / 100.0);
      g.put(
          "score_parts",
          Map.of(
              "category",
              round(categoryPart),
              "price",
              round(pricePart),
              "popularity",
              round(popularPart),
              "recency",
              round(recentPart)));
      g.remove("age_hours");
    }
    candidates.sort(
        Comparator.<Map<String, Object>>comparingDouble(
                g -> ((Number) g.get("recommendation_score")).doubleValue())
            .reversed()
            .thenComparing(
                Comparator.comparingLong((Map<String, Object> g) -> Store.id(g, "id")).reversed()));
    var popularArgs = new ArrayList<Object>();
    String popularWhere = " WHERE g.status='LISTED' AND u.status='ACTIVE'";
    if (a != null) {
      popularWhere += " AND g.seller_id<>?";
      popularArgs.add(a.id());
    }
    popularArgs.add(size);
    var profile =
        counts.entrySet().stream()
            .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
            .map(e -> Map.of("name", names.get(e.getKey()), "count", e.getValue()))
            .toList();
    return Map.of(
        "recommended",
        candidates.stream().limit(size).toList(),
        "popular",
        db.list(
            GoodsService.CARD
                + popularWhere
                + " ORDER BY favorite_count DESC,g.created_at DESC,g.id DESC LIMIT ?",
            popularArgs.toArray()),
        "mode",
        personal ? "PERSONALIZED" : "DISCOVERY",
        "profile",
        profile,
        "favoriteCount",
        favorites.size(),
        "priceCenter",
        round(center),
        "method",
        personal
            ? "根据最近 200 次收藏：类别 45%、价格接近度 25%、收藏热度 20%、新鲜度 10%。已排除自己的商品和已收藏商品。"
            : "暂无收藏偏好，按收藏热度 55% 和新鲜度 45% 发现好物。登录并收藏后会更新推荐。");
  }

  private double round(double x) {
    return Math.round(x * 100) / 100.0;
  }
}

package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.api.*;
import cn.campus.exchange.config.SecurityConfig.Actor;
import cn.campus.exchange.repository.Store;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final Store db;

  public OrderService(Store db) {
    this.db = db;
  }

  public static final String SELECT =
      "SELECT o.*,b.nickname buyer_name,s.nickname seller_name,g.status goods_status FROM"
          + " trade_order o JOIN app_user b ON b.id=o.buyer_id JOIN app_user s ON s.id=o.seller_id"
          + " JOIN goods g ON g.id=o.goods_id ";

  @Transactional
  public Object create(Actor a, long goodsId) {
    var g = db.one("SELECT * FROM goods WHERE id=? FOR UPDATE", goodsId);
    require(!Objects.equals(g.get("seller_id"), a.id()), 400, "不能购买自己发布的商品");
    require("LISTED".equals(g.get("status")), 409, "该商品当前不可下单");
    require(
        db.count("SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'", g.get("seller_id"))
            == 1,
        409,
        "卖家当前不可交易");
    require(
        db.count(
                "SELECT COUNT(*) FROM trade_order WHERE goods_id=? AND buyer_id=? AND status IN"
                    + " ('PENDING','CONFIRMED')",
                goodsId,
                a.id())
            == 0,
        409,
        "你已有该商品的待处理订单");
    var image =
        db.one(
                "SELECT image_url FROM goods_image WHERE goods_id=? ORDER BY sort_order LIMIT 1",
                goodsId)
            .get("image_url");
    long id =
        db.insert(
            "INSERT INTO trade_order(goods_id,buyer_id,seller_id,goods_title,goods_image,amount)"
                + " VALUES (?,?,?,?,?,?)",
            goodsId,
            a.id(),
            g.get("seller_id"),
            g.get("title"),
            image,
            g.get("price"));
    return Map.of("id", id);
  }

  @Transactional
  public void action(long id, Actor a, Requests.Action r) {
    // Every transition locks goods first, then order. This same ordering is used by edits and
    // review.
    var initial = db.one("SELECT goods_id FROM trade_order WHERE id=?", id);
    var g = db.one("SELECT * FROM goods WHERE id=? FOR UPDATE", initial.get("goods_id"));
    var o = db.one("SELECT * FROM trade_order WHERE id=? FOR UPDATE", id);
    boolean buyer = Store.id(o, "buyer_id") == a.id(), seller = Store.id(o, "seller_id") == a.id();
    require(buyer || seller, 403, "只能操作自己的买卖订单");
    var state = o.get("status").toString();
    long goods = Store.id(o, "goods_id");
    switch (r.action()) {
      case "confirm" -> {
        require(seller, 403, "只有卖家可以接受订单");
        require(state.equals("PENDING") && g.get("status").equals("LISTED"), 409, "订单已处理或商品已被预订");
        require(
            db.count(
                    "SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'",
                    o.get("buyer_id"))
                == 1,
            409,
            "买家账号已禁用");
        db.update("UPDATE goods SET status='RESERVED' WHERE id=?", goods);
        db.update("UPDATE trade_order SET status='CONFIRMED' WHERE id=?", id);
        db.update(
            "UPDATE trade_order SET status='CANCELLED',cancel_reason='商品已被其他买家预订' WHERE goods_id=?"
                + " AND id<>? AND status='PENDING'",
            goods,
            id);
      }
      case "reject" -> {
        require(seller, 403, "只有卖家可以拒绝订单");
        require(state.equals("PENDING"), 409, "订单已处理");
        db.update(
            "UPDATE trade_order SET status='CANCELLED',cancel_reason=? WHERE id=?",
            reason(r, "卖家拒绝了交易"),
            id);
      }
      case "cancel" -> {
        require(Set.of("PENDING", "CONFIRMED").contains(state), 409, "该订单不能取消");
        db.update(
            "UPDATE trade_order SET status='CANCELLED',cancel_reason=? WHERE id=?",
            reason(r, buyer ? "买家取消交易" : "卖家取消交易"),
            id);
        if (state.equals("CONFIRMED") && g.get("status").equals("RESERVED"))
          db.update("UPDATE goods SET status='LISTED' WHERE id=?", goods);
      }
      case "complete" -> {
        require(buyer, 403, "只有买家可以确认收货");
        require(state.equals("CONFIRMED") && g.get("status").equals("RESERVED"), 409, "订单当前不能完成");
        db.update("UPDATE trade_order SET status='COMPLETED',completed_at=NOW() WHERE id=?", id);
        db.update("UPDATE goods SET status='SOLD' WHERE id=?", goods);
      }
      default -> throw new BusinessException(400, "不支持的订单操作");
    }
  }

  private String reason(Requests.Action r, String fallback) {
    return r.reason() == null || r.reason().isBlank() ? fallback : r.reason().trim();
  }

  public Object mine(Actor a, String side) {
    return db.list(
        SELECT
            + " WHERE "
            + ("selling".equals(side) ? "o.seller_id" : "o.buyer_id")
            + "=? ORDER BY o.id DESC",
        a.id());
  }
}

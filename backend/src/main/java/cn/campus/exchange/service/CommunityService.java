package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.api.Requests;
import cn.campus.exchange.config.SecurityConfig.Actor;
import cn.campus.exchange.repository.Store;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {
  private final Store db;
  private final WorkflowService workflow;

  public CommunityService(Store db, WorkflowService workflow) {
    this.db = db;
    this.workflow = workflow;
  }

  public Object seller(long id, int page, int size) {
    paging(page, size);
    var profile =
        db.one(
            "SELECT id,nickname,avatar,bio,created_at FROM app_user WHERE id=? AND status='ACTIVE'",
            id);
    var stats =
        db.one(
            "SELECT (SELECT COUNT(*) FROM goods WHERE seller_id=? AND status='LISTED')"
                + " listed,(SELECT COUNT(*) FROM trade_order WHERE seller_id=? AND"
                + " status='COMPLETED') completed,(SELECT COUNT(*) FROM trade_review WHERE"
                + " seller_id=?) review_count,(SELECT ROUND(AVG(rating),1) FROM trade_review WHERE"
                + " seller_id=?) rating",
            id,
            id,
            id,
            id);
    var goods =
        db.list(
            GoodsService.CARD
                + " WHERE g.seller_id=? AND g.status='LISTED' ORDER BY g.id DESC LIMIT ? OFFSET ?",
            id,
            size,
            (page - 1) * size);
    return Map.of(
        "profile",
        profile,
        "stats",
        stats,
        "goods",
        Map.of("items", goods, "total", stats.get("listed"), "page", page, "size", size));
  }

  public Object reviews(long id, int page, int size) {
    paging(page, size);
    db.one("SELECT id FROM app_user WHERE id=? AND status='ACTIVE'", id);
    return Map.of(
        "items",
        db.list(
            "SELECT r.id,r.rating,r.content,r.created_at,u.nickname buyer_name,u.avatar"
                + " buyer_avatar,o.goods_title,o.goods_id FROM trade_review r JOIN app_user u ON"
                + " u.id=r.buyer_id JOIN trade_order o ON o.id=r.order_id WHERE r.seller_id=? ORDER"
                + " BY r.id DESC LIMIT ? OFFSET ?",
            id,
            size,
            (page - 1) * size),
        "total",
        db.count("SELECT COUNT(*) FROM trade_review WHERE seller_id=?", id),
        "page",
        page,
        "size",
        size);
  }

  @Transactional
  public Object review(long order, Actor a, Requests.Review r) {
    var o = db.one("SELECT * FROM trade_order WHERE id=? FOR UPDATE", order);
    require(Store.id(o, "buyer_id") == a.id(), 403, "只有该订单的买家可以评价");
    require("COMPLETED".equals(o.get("status")), 409, "完成交易后才能评价");
    require(r.content().trim().length() >= 5, 400, "评价至少填写 5 个字");
    require(
        db.count("SELECT COUNT(*) FROM trade_review WHERE order_id=?", order) == 0,
        409,
        "该订单已评价，请勿重复提交");
    long id =
        db.insert(
            "INSERT INTO trade_review(order_id,buyer_id,seller_id,rating,content) VALUES"
                + " (?,?,?,?,?)",
            order,
            a.id(),
            o.get("seller_id"),
            r.rating(),
            r.content().trim());
    workflow.event(order, a.id(), "REVIEWED", "买家已提交交易评价（" + r.rating() + " 星）");
    workflow.notify(
        Store.id(o, "seller_id"),
        "REVIEW",
        "收到交易评价",
        o.get("goods_title") + "：" + r.content().trim(),
        "/sellers/" + o.get("seller_id"));
    return Map.of("id", id);
  }

  public Object notifications(Actor a, boolean unread, int page, int size) {
    paging(page, size);
    String where = " WHERE user_id=?" + (unread ? " AND is_read=FALSE" : "");
    return Map.of(
        "items",
        db.list(
            "SELECT * FROM notification" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            a.id(),
            size,
            (page - 1) * size),
        "total",
        db.count("SELECT COUNT(*) FROM notification" + where, a.id()),
        "unread",
        db.count("SELECT COUNT(*) FROM notification WHERE user_id=? AND is_read=FALSE", a.id()),
        "page",
        page,
        "size",
        size);
  }

  public void read(Actor a, Long id) {
    if (id == null)
      db.update("UPDATE notification SET is_read=TRUE WHERE user_id=? AND is_read=FALSE", a.id());
    else {
      db.one("SELECT id FROM notification WHERE id=? AND user_id=?", id, a.id());
      db.update("UPDATE notification SET is_read=TRUE WHERE id=? AND user_id=?", id, a.id());
    }
  }

  public Object announcements() {
    return db.list(
        "SELECT id,title,content,pinned,updated_at FROM announcement WHERE status='PUBLISHED' ORDER"
            + " BY pinned DESC,updated_at DESC,id DESC LIMIT 20");
  }

  public static void paging(int page, int size) {
    require(page >= 1 && page <= 100000 && size >= 1 && size <= 100, 400, "分页参数不正确");
  }
}

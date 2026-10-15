package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.api.*;
import cn.campus.exchange.config.SecurityConfig.Actor;
import cn.campus.exchange.repository.Store;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsService {
  private final Store db;
  private final WorkflowService workflow;

  public GoodsService(Store db, WorkflowService workflow) {
    this.db = db;
    this.workflow = workflow;
  }

  public static final String CARD =
      "SELECT g.*,c.name category_name,u.nickname seller_name,u.avatar seller_avatar,(SELECT"
          + " image_url FROM goods_image WHERE goods_id=g.id ORDER BY sort_order LIMIT 1)"
          + " cover,(SELECT COUNT(*) FROM favorite WHERE goods_id=g.id) favorite_count FROM goods g"
          + " JOIN category c ON c.id=g.category_id JOIN app_user u ON u.id=g.seller_id ";

  public Object categories() {
    return db.list(
        "SELECT c.*,(SELECT COUNT(*) FROM goods g JOIN app_user u ON u.id=g.seller_id WHERE"
            + " g.category_id=c.id AND g.status='LISTED' AND u.status='ACTIVE') goods_count FROM"
            + " category c WHERE c.status='ACTIVE' ORDER BY c.sort_order");
  }

  public Object publicStats() {
    return db.one(
        "SELECT (SELECT COUNT(*) FROM app_user WHERE role='STUDENT') users,(SELECT COUNT(*) FROM"
            + " goods g JOIN app_user u ON u.id=g.seller_id WHERE g.status='LISTED' AND"
            + " u.status='ACTIVE') goods,(SELECT COUNT(*) FROM trade_order WHERE"
            + " status='COMPLETED') completed");
  }

  public Object browse(
      String q,
      Long category,
      BigDecimal min,
      BigDecimal max,
      Integer days,
      String sort,
      int page,
      int size) {
    require(page >= 1 && size >= 1 && size <= 48, 400, "分页参数不正确");
    require(q == null || q.length() <= 80, 400, "关键词最多 80 字");
    require(min == null || min.signum() >= 0, 400, "最低价格不能为负数");
    require(max == null || max.signum() >= 0, 400, "最高价格不能为负数");
    require(min == null || max == null || min.compareTo(max) <= 0, 400, "价格区间不正确");
    var where = new StringBuilder(" WHERE g.status='LISTED' AND u.status='ACTIVE'");
    var args = new ArrayList<Object>();
    if (q != null && !q.isBlank()) {
      where.append(" AND (g.title LIKE ? OR g.description LIKE ?)");
      args.add("%" + q.trim() + "%");
      args.add("%" + q.trim() + "%");
    }
    if (category != null) {
      where.append(" AND g.category_id=?");
      args.add(category);
    }
    if (min != null) {
      where.append(" AND g.price>=?");
      args.add(min);
    }
    if (max != null) {
      where.append(" AND g.price<=?");
      args.add(max);
    }
    if (days != null) {
      require(days >= 1 && days <= 365, 400, "时间范围为 1–365 天");
      where.append(" AND g.created_at>=DATE_SUB(NOW(),INTERVAL ? DAY)");
      args.add(days);
    }
    long total =
        db.count(
            "SELECT COUNT(*) FROM goods g JOIN app_user u ON u.id=g.seller_id" + where,
            args.toArray());
    String order =
        switch (sort == null ? "" : sort) {
          case "price_asc" -> "g.price ASC,g.id DESC";
          case "price_desc" -> "g.price DESC,g.id DESC";
          default -> "g.created_at DESC,g.id DESC";
        };
    args.add(size);
    args.add((page - 1) * size);
    return Map.of(
        "items",
        db.list(CARD + where + " ORDER BY " + order + " LIMIT ? OFFSET ?", args.toArray()),
        "total",
        total,
        "page",
        page,
        "size",
        size);
  }

  public Map<String, Object> detail(long id, Actor a) {
    var g = db.one(CARD + " WHERE g.id=?", id);
    boolean owner = a != null && (Store.id(g, "seller_id") == a.id() || a.role().equals("ADMIN"));
    require(
        owner
            || (Set.of("LISTED", "RESERVED", "SOLD").contains(g.get("status"))
                && db.count(
                        "SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE'",
                        g.get("seller_id"))
                    > 0),
        404,
        "商品尚未公开或已下架");
    g.put(
        "images",
        db
            .list("SELECT image_url FROM goods_image WHERE goods_id=? ORDER BY sort_order", id)
            .stream()
            .map(x -> x.get("image_url"))
            .toList());
    g.put(
        "is_favorite",
        a != null
            && db.count("SELECT COUNT(*) FROM favorite WHERE user_id=? AND goods_id=?", a.id(), id)
                > 0);
    return g;
  }

  @Transactional
  public long save(Long id, Actor a, Requests.Goods r) {
    require(
        db.count("SELECT COUNT(*) FROM category WHERE id=? AND status='ACTIVE'", r.categoryId())
            > 0,
        400,
        "分类不存在");
    List<String> old = List.of();
    if (id != null) {
      var g = db.one("SELECT * FROM goods WHERE id=? FOR UPDATE", id);
      require(Store.id(g, "seller_id") == a.id(), 403, "只能编辑本人商品");
      require(!Set.of("RESERVED", "SOLD").contains(g.get("status")), 409, "已预订或已售出的商品不可编辑");
      old =
          db.list("SELECT image_url FROM goods_image WHERE goods_id=?", id).stream()
              .map(x -> x.get("image_url").toString())
              .toList();
    }
    for (var img : r.images())
      require(
          old.contains(img)
              || db.count(
                      "SELECT COUNT(*) FROM upload_file WHERE image_url=? AND user_id=?",
                      img,
                      a.id())
                  > 0,
          400,
          "商品图片必须由本人上传");
    if (id == null)
      id =
          db.insert(
              "INSERT INTO goods(seller_id,category_id,title,price,condition_level,description)"
                  + " VALUES (?,?,?,?,?,?)",
              a.id(),
              r.categoryId(),
              r.title().trim(),
              r.price(),
              r.conditionLevel(),
              r.description().trim());
    else {
      db.update(
          "UPDATE goods SET"
              + " category_id=?,title=?,price=?,condition_level=?,description=?,status='PENDING',reject_reason=''"
              + " WHERE id=?",
          r.categoryId(),
          r.title().trim(),
          r.price(),
          r.conditionLevel(),
          r.description().trim(),
          id);
      workflow.cancelOrders(id, a.id(), "商品修改后重新审核", false);
      db.update("DELETE FROM goods_image WHERE goods_id=?", id);
    }
    for (int i = 0; i < r.images().size(); i++)
      db.update(
          "INSERT INTO goods_image(goods_id,image_url,sort_order) VALUES (?,?,?)",
          id,
          r.images().get(i),
          i);
    return id;
  }

  @Transactional
  public void offline(long id, Actor a) {
    var g = db.one("SELECT * FROM goods WHERE id=? FOR UPDATE", id);
    require(Store.id(g, "seller_id") == a.id(), 403, "只能下架本人商品");
    require(!Set.of("RESERVED", "SOLD").contains(g.get("status")), 409, "请先取消未完成交易；已售出商品不可重新操作");
    db.update("UPDATE goods SET status='OFFLINE' WHERE id=?", id);
    workflow.cancelOrders(id, a.id(), "卖家已下架商品", false);
  }

  public Object mine(long user) {
    return db.list(CARD + " WHERE g.seller_id=? ORDER BY g.id DESC", user);
  }

  public Object favorites(Actor a) {
    return db.list(
        CARD + " JOIN favorite f ON f.goods_id=g.id WHERE f.user_id=? ORDER BY f.created_at DESC",
        a.id());
  }

  @Transactional
  public void favorite(long id, Actor a, boolean enabled) {
    if (enabled) {
      detail(id, a);
      db.update(
          "INSERT INTO favorite(user_id,goods_id) VALUES (?,?) ON DUPLICATE KEY UPDATE"
              + " user_id=VALUES(user_id)",
          a.id(),
          id);
    } else db.update("DELETE FROM favorite WHERE user_id=? AND goods_id=?", a.id(), id);
  }

  public Object messages(long id, Actor a) {
    detail(id, a);
    return db.list(
        "SELECT m.*,u.nickname sender_name,u.avatar sender_avatar,p.content"
            + " reply_content,pu.nickname reply_name FROM message m JOIN app_user u ON"
            + " u.id=m.sender_id LEFT JOIN message p ON p.id=m.reply_to_id LEFT JOIN app_user pu ON"
            + " pu.id=p.sender_id WHERE m.goods_id=? ORDER BY m.id",
        id);
  }

  @Transactional
  public Object send(long id, Actor a, Requests.Text r) {
    var g = detail(id, a);
    require(Set.of("LISTED", "RESERVED").contains(g.get("status")), 409, "此商品当前不能继续留言");
    if (r.replyToId() != null) {
      db.one("SELECT id FROM message WHERE id=? AND goods_id=?", r.replyToId(), id);
      require(Store.id(g, "seller_id") == a.id(), 403, "仅卖家可回复商品咨询");
    }
    long message =
        db.insert(
            "INSERT INTO message(goods_id,sender_id,reply_to_id,content) VALUES (?,?,?,?)",
            id,
            a.id(),
            r.replyToId(),
            r.content().trim());
    long receiver =
        r.replyToId() == null
            ? Store.id(g, "seller_id")
            : Store.id(
                db.one("SELECT sender_id FROM message WHERE id=?", r.replyToId()), "sender_id");
    if (receiver != a.id())
      workflow.notify(
          receiver,
          "MESSAGE",
          r.replyToId() == null ? "收到商品咨询" : "卖家回复了你的留言",
          g.get("title") + "：" + r.content().trim(),
          "/goods/" + id);
    return Map.of("id", message);
  }

  public Object inbox(Actor a) {
    return db.list(
        "SELECT m.*,u.nickname sender_name,g.title goods_title,g.seller_id,p.content reply_content"
            + " FROM message m JOIN goods g ON g.id=m.goods_id JOIN app_user u ON u.id=m.sender_id"
            + " LEFT JOIN message p ON p.id=m.reply_to_id WHERE g.seller_id=? OR m.sender_id=? OR"
            + " p.sender_id=? ORDER BY m.id DESC LIMIT 200",
        a.id(),
        a.id(),
        a.id());
  }

  public Object report(Actor a, Requests.Report r) {
    detail(r.goodsId(), a);
    require(
        db.count(
                "SELECT COUNT(*) FROM report WHERE reporter_id=? AND goods_id=? AND"
                    + " status='PENDING'",
                a.id(),
                r.goodsId())
            == 0,
        409,
        "你已举报该商品，请等待处理");
    return Map.of(
        "id",
        db.insert(
            "INSERT INTO report(reporter_id,goods_id,reason) VALUES (?,?,?)",
            a.id(),
            r.goodsId(),
            r.reason().trim()));
  }

  public Object summary(Actor a) {
    return db.one(
        "SELECT (SELECT COUNT(*) FROM goods WHERE seller_id=?) published,(SELECT COUNT(*) FROM"
            + " favorite WHERE user_id=?) favorites,(SELECT COUNT(*) FROM trade_order WHERE"
            + " buyer_id=?) buying,(SELECT COUNT(*) FROM trade_order WHERE seller_id=?) selling",
        a.id(),
        a.id(),
        a.id(),
        a.id());
  }

  public Object reports(Actor a) {
    return db.list(
        "SELECT r.*,g.title goods_title FROM report r JOIN goods g ON g.id=r.goods_id WHERE"
            + " r.reporter_id=? ORDER BY r.id DESC",
        a.id());
  }
}

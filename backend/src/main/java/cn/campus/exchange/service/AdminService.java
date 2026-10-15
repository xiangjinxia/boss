package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.api.*;
import cn.campus.exchange.repository.Store;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  private final Store db;

  public AdminService(Store db) {
    this.db = db;
  }

  public Object goods(String status, String q) {
    var args = new ArrayList<Object>();
    var where = new StringBuilder(" WHERE 1=1");
    if (status != null && !status.isBlank()) {
      where.append(" AND g.status=?");
      args.add(status);
    }
    if (q != null && !q.isBlank()) {
      where.append(" AND g.title LIKE ?");
      args.add("%" + q.trim() + "%");
    }
    return db.list(GoodsService.CARD + where + " ORDER BY g.id DESC LIMIT 500", args.toArray());
  }

  @Transactional
  public void review(long id, long admin, Requests.Action r) {
    var g = db.one("SELECT * FROM goods WHERE id=? FOR UPDATE", id);
    String reason = r.reason() == null ? "" : r.reason().trim();
    switch (r.action()) {
      case "approve" -> {
        require(g.get("status").equals("PENDING"), 409, "只能审核待审核商品");
        db.update("UPDATE goods SET status='LISTED',reject_reason='' WHERE id=?", id);
      }
      case "reject" -> {
        require(g.get("status").equals("PENDING"), 409, "只能审核待审核商品");
        require(!reason.isBlank(), 400, "请填写驳回原因");
        db.update("UPDATE goods SET status='REJECTED',reject_reason=? WHERE id=?", reason, id);
      }
      case "offline" -> {
        require(!Set.of("SOLD", "OFFLINE").contains(g.get("status")), 409, "已售出或已下架商品不能重复下架");
        require(!reason.isBlank(), 400, "请填写下架原因");
        db.update(
            "UPDATE trade_order SET status='CANCELLED',cancel_reason=? WHERE goods_id=? AND status"
                + " IN ('PENDING','CONFIRMED')",
            "管理员下架：" + reason,
            id);
        db.update("UPDATE goods SET status='OFFLINE',reject_reason=? WHERE id=?", reason, id);
      }
      default -> throw new BusinessException(400, "不支持的审核操作");
    }
    audit(admin, "goods", id, r.action(), reason);
  }

  public Object users(String q) {
    return db.list(
        "SELECT id,username,nickname,role,status,created_at FROM app_user WHERE username LIKE ? OR"
            + " nickname LIKE ? ORDER BY id DESC LIMIT 500",
        "%" + (q == null ? "" : q) + "%",
        "%" + (q == null ? "" : q) + "%");
  }

  @Transactional
  public void user(long id, long admin, Requests.Action r) {
    var u = db.one("SELECT * FROM app_user WHERE id=? FOR UPDATE", id);
    require(!u.get("role").equals("ADMIN"), 403, "管理员账号不可禁用");
    require(Set.of("disable", "enable").contains(r.action()), 400, "不支持的用户操作");
    if (r.action().equals("disable"))
      require(
          db.count(
                  "SELECT COUNT(*) FROM trade_order WHERE (buyer_id=? OR seller_id=?) AND"
                      + " status='CONFIRMED'",
                  id,
                  id)
              == 0,
          409,
          "该用户有待线下交易订单，请交易双方先取消或完成");
    db.update(
        "UPDATE app_user SET status=? WHERE id=?",
        r.action().equals("disable") ? "DISABLED" : "ACTIVE",
        id);
    if (r.action().equals("disable")) db.update("DELETE FROM auth_session WHERE user_id=?", id);
    audit(admin, "user", id, r.action(), r.reason() == null ? "" : r.reason());
  }

  public Object reports(String status) {
    return status == null || status.isBlank()
        ? db.list(
            "SELECT r.*,g.title goods_title,u.nickname reporter_name FROM report r JOIN goods g ON"
                + " g.id=r.goods_id JOIN app_user u ON u.id=r.reporter_id ORDER BY r.id DESC LIMIT"
                + " 500")
        : db.list(
            "SELECT r.*,g.title goods_title,u.nickname reporter_name FROM report r JOIN goods g ON"
                + " g.id=r.goods_id JOIN app_user u ON u.id=r.reporter_id WHERE r.status=? ORDER BY"
                + " r.id DESC LIMIT 500",
            status);
  }

  @Transactional
  public void handle(long id, long admin, Requests.Action r) {
    var report = db.one("SELECT * FROM report WHERE id=? FOR UPDATE", id);
    require(report.get("status").equals("PENDING"), 409, "举报已处理");
    require(Set.of("resolve", "reject").contains(r.action()), 400, "不支持的举报操作");
    require(r.reason() != null && !r.reason().isBlank(), 400, "请填写处理结果");
    db.update(
        "UPDATE report SET status=?,result=?,handled_by=?,handled_at=NOW() WHERE id=?",
        r.action().equals("resolve") ? "RESOLVED" : "REJECTED",
        r.reason().trim(),
        admin,
        id);
    audit(admin, "report", id, r.action(), r.reason());
  }

  public Object orders(String status, String buyer, String seller, String from, String to) {
    var args = new ArrayList<Object>();
    var where = new StringBuilder(" WHERE 1=1");
    if (status != null && !status.isBlank()) {
      where.append(" AND o.status=?");
      args.add(status);
    }
    if (buyer != null && !buyer.isBlank()) {
      where.append(" AND (b.nickname LIKE ? OR b.username LIKE ?)");
      args.add("%" + buyer + "%");
      args.add("%" + buyer + "%");
    }
    if (seller != null && !seller.isBlank()) {
      where.append(" AND (s.nickname LIKE ? OR s.username LIKE ?)");
      args.add("%" + seller + "%");
      args.add("%" + seller + "%");
    }
    try {
      if (from != null && !from.isBlank()) {
        where.append(" AND o.created_at>=?");
        args.add(LocalDate.parse(from).atStartOfDay());
      }
      if (to != null && !to.isBlank()) {
        where.append(" AND o.created_at<?");
        args.add(LocalDate.parse(to).plusDays(1).atStartOfDay());
      }
    } catch (java.time.format.DateTimeParseException e) {
      throw new BusinessException(400, "日期格式应为 YYYY-MM-DD");
    }
    return db.list(OrderService.SELECT + where + " ORDER BY o.id DESC LIMIT 500", args.toArray());
  }

  public Object dashboard() {
    var kpi =
        db.one(
            "SELECT (SELECT COUNT(*) FROM app_user WHERE role='STUDENT') users,(SELECT COUNT(*)"
                + " FROM goods) goods,(SELECT COUNT(*) FROM goods WHERE status='PENDING')"
                + " pending,(SELECT COUNT(*) FROM trade_order WHERE status='COMPLETED')"
                + " completed,(SELECT COALESCE(SUM(amount),0) FROM trade_order WHERE"
                + " status='COMPLETED') amount,(SELECT COUNT(*) FROM report WHERE status='PENDING')"
                + " reports");
    var days = new ArrayList<Map<String, Object>>();
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (int i = 6; i >= 0; i--) {
      var d = today.minusDays(i);
      var row =
          db.one(
              "SELECT COUNT(*) count,COALESCE(SUM(amount),0) amount FROM trade_order WHERE"
                  + " status='COMPLETED' AND completed_at>=? AND completed_at<?",
              d.atStartOfDay(),
              d.plusDays(1).atStartOfDay());
      row.put("date", d.toString());
      days.add(row);
    }
    return Map.of(
        "kpi",
        kpi,
        "trend",
        days,
        "categories",
        db.list(
            "SELECT c.name,COUNT(g.id) count FROM category c LEFT JOIN goods g ON"
                + " g.category_id=c.id GROUP BY c.id,c.name ORDER BY c.sort_order"),
        "definition",
        "成交笔数与金额仅统计 COMPLETED 订单；近 7 日按北京时间完成日期分组；分类数量包含全部状态商品。");
  }

  public Object audit() {
    return db.list(
        "SELECT a.*,u.nickname admin_name FROM audit_log a JOIN app_user u ON u.id=a.admin_id ORDER"
            + " BY a.id DESC LIMIT 200");
  }

  private void audit(long admin, String type, long id, String action, String detail) {
    db.update(
        "INSERT INTO audit_log(admin_id,target_type,target_id,action,detail) VALUES (?,?,?,?,?)",
        admin,
        type,
        id,
        action,
        detail);
  }
}

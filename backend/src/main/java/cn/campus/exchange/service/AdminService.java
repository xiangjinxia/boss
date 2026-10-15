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
  private final WorkflowService workflow;

  public AdminService(Store db, WorkflowService workflow) {
    this.db = db;
    this.workflow = workflow;
  }

  public Object goods(String status, String q, int page, int size) {
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
    return page(GoodsService.CARD + where + " ORDER BY g.id DESC", args, page, size);
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
        workflow.cancelOrders(id, admin, "管理员下架：" + reason, true);
        db.update("UPDATE goods SET status='OFFLINE',reject_reason=? WHERE id=?", reason, id);
      }
      default -> throw new BusinessException(400, "不支持的审核操作");
    }
    workflow.notify(
        Store.id(g, "seller_id"),
        "AUDIT",
        switch (r.action()) {
          case "approve" -> "商品审核通过";
          case "reject" -> "商品审核被驳回";
          default -> "商品已下架";
        },
        g.get("title") + "：" + (reason.isBlank() ? "已在校园集市公开展示" : reason),
        "/goods/" + id);
    audit(admin, "goods", id, r.action(), reason);
  }

  public Object users(String q, int page, int size) {
    String search = "%" + (q == null ? "" : q.trim()) + "%";
    return page(
        "SELECT id,username,nickname,role,status,created_at FROM app_user WHERE username LIKE ? OR"
            + " nickname LIKE ? ORDER BY id DESC",
        new ArrayList<>(List.of(search, search)),
        page,
        size);
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

  public Object reports(String status, int page, int size) {
    var args = new ArrayList<Object>();
    String sql =
        "SELECT r.*,g.title goods_title,u.nickname reporter_name FROM report r JOIN goods g ON"
            + " g.id=r.goods_id JOIN app_user u ON u.id=r.reporter_id";
    if (status != null && !status.isBlank()) {
      sql += " WHERE r.status=?";
      args.add(status);
    }
    return page(sql + " ORDER BY r.id DESC", args, page, size);
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
    workflow.notify(
        Store.id(report, "reporter_id"), "REPORT", "举报处理结果", r.reason().trim(), "/me?tab=reports");
    audit(admin, "report", id, r.action(), r.reason());
  }

  public Object orders(
      String status, String buyer, String seller, String from, String to, int page, int size) {
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
    if (from != null && !from.isBlank() && to != null && !to.isBlank())
      require(!LocalDate.parse(from).isAfter(LocalDate.parse(to)), 400, "开始日期不能晚于结束日期");
    return page(OrderService.SELECT + where + " ORDER BY o.id DESC", args, page, size);
  }

  public Object dashboard(String from, String to) {
    var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDate start, end;
    try {
      start = from == null || from.isBlank() ? today.minusDays(6) : LocalDate.parse(from);
      end = to == null || to.isBlank() ? today : LocalDate.parse(to);
    } catch (java.time.format.DateTimeParseException e) {
      throw new BusinessException(400, "日期格式应为 YYYY-MM-DD");
    }
    require(
        !start.isAfter(end) && java.time.temporal.ChronoUnit.DAYS.between(start, end) <= 89,
        400,
        "日期范围为 1–90 天，开始日期不能晚于结束日期");
    var kpi =
        db.one(
            "SELECT (SELECT COUNT(*) FROM app_user WHERE role='STUDENT') users,(SELECT COUNT(*)"
                + " FROM goods) goods,(SELECT COUNT(*) FROM goods WHERE status='PENDING')"
                + " pending,(SELECT COUNT(*) FROM trade_order WHERE status='COMPLETED')"
                + " completed,(SELECT COALESCE(SUM(amount),0) FROM trade_order WHERE"
                + " status='COMPLETED') amount,(SELECT COUNT(*) FROM report WHERE status='PENDING')"
                + " reports");
    var days = new ArrayList<Map<String, Object>>();
    for (var d = start; !d.isAfter(end); d = d.plusDays(1)) {
      var row =
          db.one(
              "SELECT COUNT(*) count,COALESCE(SUM(amount),0) amount FROM trade_order WHERE"
                  + " status='COMPLETED' AND completed_at>=? AND completed_at<?",
              d.atStartOfDay(),
              d.plusDays(1).atStartOfDay());
      row.put("date", d.toString());
      days.add(row);
    }
    var period =
        db.one(
            "SELECT COUNT(*) created,COALESCE(SUM(status='COMPLETED'),0)"
                + " cohort_completed,COALESCE(SUM(status='CANCELLED'),0)"
                + " cancelled,COALESCE(SUM(status='PENDING'),0)"
                + " pending,COALESCE(SUM(status='CONFIRMED'),0) confirmed FROM trade_order WHERE"
                + " created_at>=? AND created_at<?",
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay());
    period.putAll(
        db.one(
            "SELECT COUNT(*) completed,COALESCE(SUM(amount),0) amount FROM trade_order WHERE"
                + " status='COMPLETED' AND completed_at>=? AND completed_at<?",
            start.atStartOfDay(),
            end.plusDays(1).atStartOfDay()));
    return Map.of(
        "from",
        start.toString(),
        "to",
        end.toString(),
        "period",
        period,
        "kpi",
        kpi,
        "trend",
        days,
        "categories",
        db.list(
            "SELECT c.name,COUNT(g.id) count FROM category c LEFT JOIN goods g ON"
                + " g.category_id=c.id GROUP BY c.id,c.name ORDER BY c.sort_order"),
        "definition",
        "顶部指标为累计数据；趋势与区间成交按北京时间完成日期统计。区间预约及其状态按创建日期统计；分类数量为累计全部状态商品。");
  }

  public Object audit(int page, int size) {
    return page(
        "SELECT a.*,u.nickname admin_name FROM audit_log a JOIN app_user u ON u.id=a.admin_id ORDER"
            + " BY a.id DESC",
        new ArrayList<>(),
        page,
        size);
  }

  public Object categories(int page, int size) {
    return page(
        "SELECT c.*,(SELECT COUNT(*) FROM goods g WHERE g.category_id=c.id) goods_count FROM"
            + " category c ORDER BY c.sort_order,c.id",
        new ArrayList<>(),
        page,
        size);
  }

  @Transactional
  public Object category(Long id, long admin, Requests.Category r) {
    if (id == null)
      id =
          db.insert(
              "INSERT INTO category(name,icon,sort_order,status) VALUES (?,?,?,?)",
              r.name().trim(),
              r.icon(),
              r.sortOrder(),
              r.status());
    else {
      db.one("SELECT id FROM category WHERE id=? FOR UPDATE", id);
      db.update(
          "UPDATE category SET name=?,icon=?,sort_order=?,status=? WHERE id=?",
          r.name().trim(),
          r.icon(),
          r.sortOrder(),
          r.status(),
          id);
    }
    audit(admin, "category", id, "save", r.name().trim());
    return Map.of("id", id);
  }

  @Transactional
  public void deleteCategory(long id, long admin) {
    db.one("SELECT id FROM category WHERE id=? FOR UPDATE", id);
    require(
        db.count("SELECT COUNT(*) FROM goods WHERE category_id=?", id) == 0,
        409,
        "分类已关联商品，请改为停用以保留历史记录");
    db.update("DELETE FROM category WHERE id=?", id);
    audit(admin, "category", id, "delete", "");
  }

  public Object announcements(int page, int size) {
    return page(
        "SELECT a.*,u.nickname admin_name FROM announcement a LEFT JOIN app_user u ON"
            + " u.id=a.created_by ORDER BY a.pinned DESC,a.id DESC",
        new ArrayList<>(),
        page,
        size);
  }

  @Transactional
  public Object announcement(Long id, long admin, Requests.Announcement r) {
    if (id == null)
      id =
          db.insert(
              "INSERT INTO announcement(title,content,status,pinned,created_by) VALUES (?,?,?,?,?)",
              r.title().trim(),
              r.content().trim(),
              r.status(),
              r.pinned(),
              admin);
    else {
      db.one("SELECT id FROM announcement WHERE id=? FOR UPDATE", id);
      db.update(
          "UPDATE announcement SET title=?,content=?,status=?,pinned=?,updated_at=NOW() WHERE id=?",
          r.title().trim(),
          r.content().trim(),
          r.status(),
          r.pinned(),
          id);
    }
    audit(admin, "announcement", id, "save", r.title().trim() + " / " + r.status());
    return Map.of("id", id);
  }

  @Transactional
  public void deleteAnnouncement(long id, long admin) {
    db.one("SELECT id FROM announcement WHERE id=? FOR UPDATE", id);
    db.update("DELETE FROM announcement WHERE id=?", id);
    audit(admin, "announcement", id, "delete", "");
  }

  private Object page(String sql, List<Object> args, int page, int size) {
    CommunityService.paging(page, size);
    long total = db.count("SELECT COUNT(*) FROM (" + sql + ") page_total", args.toArray());
    var paged = new ArrayList<>(args);
    paged.add(size);
    paged.add((page - 1) * size);
    return Map.of(
        "items",
        db.list(sql + " LIMIT ? OFFSET ?", paged.toArray()),
        "total",
        total,
        "page",
        page,
        "size",
        size);
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

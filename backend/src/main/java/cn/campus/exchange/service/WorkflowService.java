package cn.campus.exchange.service;

import cn.campus.exchange.repository.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {
  private final Store db;

  public WorkflowService(Store db) {
    this.db = db;
  }

  public void notify(long user, String kind, String title, String content, String link) {
    db.update(
        "INSERT INTO notification(user_id,kind,title,content,link) VALUES (?,?,?,?,?)",
        user,
        kind,
        title,
        content,
        link);
  }

  public void event(long order, Long actor, String type, String detail) {
    db.update(
        "INSERT INTO order_event(order_id,actor_id,event_type,detail) VALUES (?,?,?,?)",
        order,
        actor,
        type,
        detail);
  }

  public void orderNotice(long order, String title, String detail) {
    var o = db.one("SELECT buyer_id,seller_id,goods_title FROM trade_order WHERE id=?", order);
    for (String side : new String[] {"buyer_id", "seller_id"})
      notify(
          Store.id(o, side),
          "ORDER",
          title,
          o.get("goods_title") + "：" + detail,
          "/orders/" + order);
  }

  // Callers lock the goods row first. Cancellations and their history/notifications commit
  // together.
  @Transactional
  public void cancelOrders(long goods, Long actor, String reason, boolean includeConfirmed) {
    var rows =
        db.list(
            "SELECT id FROM trade_order WHERE goods_id=? AND status IN "
                + (includeConfirmed ? "('PENDING','CONFIRMED')" : "('PENDING')")
                + " FOR UPDATE",
            goods);
    for (var row : rows) {
      long order = Store.id(row, "id");
      db.update(
          "UPDATE trade_order SET status='CANCELLED',cancel_reason=? WHERE id=?", reason, order);
      event(order, actor, "CANCELLED", reason);
      orderNotice(order, "订单已取消", reason);
    }
  }
}

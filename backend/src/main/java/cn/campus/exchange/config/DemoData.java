package cn.campus.exchange.config;

import cn.campus.exchange.repository.Store;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoData implements ApplicationRunner {
  private final Store db;
  private final BCryptPasswordEncoder encoder;

  @Value("${app.demo-data}")
  boolean enabled;

  public DemoData(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled || db.count("SELECT COUNT(*) FROM app_user") > 0) return;
    String hash = encoder.encode("Campus123!");
    String[] usernames = {"admin", "seller", "buyer", "xiaolin", "achen", "zhouzhou"};
    String[] names = {"校园管理员", "小林同学", "阿晴", "林间晚风", "阿辰", "周周"};
    for (int i = 0; i < usernames.length; i++)
      db.update(
          "INSERT INTO app_user(username,password_hash,nickname,role,bio) VALUES (?,?,?,?,?)",
          usernames[i],
          hash,
          names[i],
          i == 0 ? "ADMIN" : "STUDENT",
          i == 0 ? "让校园交易更安心" : "让闲置流动，让美好发生。");
    String[][] products = {
      {"富士 X-A5 微单相机 · 留住校园的光", "1280", "2", "camera"},
      {"数据结构与算法分析（C 语言描述）", "25", "1", "books"},
      {"漫步者 W820NB 双金标降噪耳机", "139", "2", "headphones"},
      {"一盏温暖的光 · 可调光阅读台灯", "45", "3", "lamp"},
      {"迪卡侬城市通勤自行车", "280", "4", "bike"},
      {"日常帆布包 · 装下一整个周末", "28", "5", "bag"},
      {"高等数学 上下册 · 同济第七版", "32", "1", "books"},
      {"罗技 Pebble 无线鼠标 · 奶油白", "59", "2", "mouse"},
      {"九成新尤尼克斯羽毛球拍", "110", "4", "racket"},
      {"宿舍小推车 · 收纳的快乐", "48", "3", "shelf"},
      {"拍立得相册与可爱贴纸一套", "18", "6", "camera"},
      {"MUJI 无印良品保温杯 350ml", "35", "3", "cup"},
      {"计算机网络 · 谢希仁第八版", "22", "1", "books"},
      {"有线机械键盘 · 茶轴 87 键", "95", "2", "keyboard"},
      {"轻量户外双肩包 · 周末去走走", "68", "5", "bag"},
      {"瑜伽垫 6mm · 附收纳带", "29", "4", "racket"},
      {"毕业季闲置 · 实木桌面书架", "36", "3", "shelf"},
      {"考研英语真题集 · 无笔记", "40", "1", "books"},
      {"小米蓝牙音箱 · 随时有音乐", "65", "2", "headphones"},
      {"全新文具礼盒 · 开学小惊喜", "20", "6", "books"},
      {"纯棉格纹衬衫 · M 码", "39", "5", "bag"},
      {"便携手冲咖啡杯 · 附滤纸", "55", "3", "cup"},
      {"入门滑板 · 带护具", "99", "4", "bike"},
      {"线性代数与学习指导", "16", "1", "books"}
    };
    for (int i = 0; i < products.length; i++) {
      var p = products[i];
      long seller = i == 0 ? 2 : 2 + (i % 5);
      if (seller == 3) seller = 4;
      long id =
          db.insert(
              "INSERT INTO"
                  + " goods(seller_id,category_id,title,price,condition_level,description,status,created_at)"
                  + " VALUES (?,?,?,?,?,?, 'LISTED', DATE_SUB(NOW(),INTERVAL ? HOUR))",
              seller,
              Integer.parseInt(p[2]),
              p[0],
              new BigDecimal(p[1]),
              i % 4 == 0 ? "几乎全新" : "九成新",
              "自用闲置，实物状态如图，功能正常。\n" + p[0] + "，平时使用很爱惜，现因整理宿舍转让。\n支持校内当面检查，交易地点可留言商量。喜欢的话欢迎先咨询～",
              i * 3);
      db.update(
          "INSERT INTO goods_image(goods_id,image_url,sort_order) VALUES (?,?,0)",
          id,
          "/samples/" + p[3] + ".svg");
    }
    for (int i = 0; i < 3; i++) {
      long id =
          db.insert(
              "INSERT INTO goods(seller_id,category_id,title,price,condition_level,description)"
                  + " VALUES (2,?,?,?,'九成新','毕业整理宿舍，欢迎校内自提。')",
              i + 1,
              new String[] {"大学物理教材 · 待审核示例", "无线蓝牙鼠标 · 待审核示例", "桌面收纳盒 · 待审核示例"}[i],
              new BigDecimal(20 + i * 15));
      db.update(
          "INSERT INTO goods_image(goods_id,image_url,sort_order) VALUES (?,?,0)",
          id,
          "/samples/" + new String[] {"books", "mouse", "shelf"}[i] + ".svg");
    }
    for (int day = 0; day < 7; day++) {
      for (int j = 0; j < (day % 3) + 1; j++) {
        var title = "往期成交 · 校园好物 " + day + "-" + j;
        long g =
            db.insert(
                "INSERT INTO"
                    + " goods(seller_id,category_id,title,price,condition_level,description,status)"
                    + " VALUES (2,1,?,?,'九成新','用于展示成交统计的历史样例。','SOLD')",
                title,
                new BigDecimal(20 + day * 8 + j * 5));
        db.update(
            "INSERT INTO goods_image(goods_id,image_url,sort_order) VALUES"
                + " (?,'/samples/books.svg',0)",
            g);
        db.update(
            "INSERT INTO"
                + " trade_order(goods_id,buyer_id,seller_id,goods_title,goods_image,amount,status,created_at,completed_at)"
                + " VALUES (?,3,2,?,'/samples/books.svg',?,'COMPLETED',DATE_SUB(NOW(),INTERVAL ?"
                + " DAY),DATE_SUB(NOW(),INTERVAL ? DAY))",
            g,
            title,
            new BigDecimal(20 + day * 8 + j * 5),
            day,
            day);
      }
    }
    db.update(
        "INSERT INTO order_event(order_id,actor_id,event_type,detail,created_at) SELECT"
            + " id,buyer_id,'CREATED','买家发起交易（历史演示数据）',created_at FROM trade_order");
    db.update(
        "INSERT INTO order_event(order_id,actor_id,event_type,detail,created_at) SELECT"
            + " id,buyer_id,'COMPLETED','买家确认完成（历史演示数据）',completed_at FROM trade_order WHERE"
            + " status='COMPLETED' AND completed_at IS NOT NULL");
    db.update("INSERT INTO favorite(user_id,goods_id) VALUES (3,1),(3,2),(3,4)");
    long message =
        db.insert(
            "INSERT INTO message(goods_id,sender_id,content) VALUES (1,3,'同学你好，相机有配套的充电器和肩带吗？')");
    db.update(
        "INSERT INTO message(goods_id,sender_id,reply_to_id,content) VALUES"
            + " (1,2,?,'有的，原装充电器和肩带都在，可以当面试拍～')",
        message);
    db.update(
        "INSERT INTO report(reporter_id,goods_id,reason) VALUES (3,11,'想请管理员核实商品描述与图片是否一致。')");
  }
}

-- Additive upgrade: never alter the already-applied V1 migration.
CREATE TABLE trade_review (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL UNIQUE,
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 rating INT NOT NULL,
 content VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (order_id) REFERENCES trade_order(id),
 FOREIGN KEY (buyer_id) REFERENCES app_user(id),
 FOREIGN KEY (seller_id) REFERENCES app_user(id),
 CHECK (rating BETWEEN 1 AND 5),
 INDEX idx_review_seller (seller_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE order_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 actor_id BIGINT NULL,
 event_type VARCHAR(24) NOT NULL,
 detail VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (order_id) REFERENCES trade_order(id),
 FOREIGN KEY (actor_id) REFERENCES app_user(id),
 INDEX idx_event_order (order_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO order_event(order_id,actor_id,event_type,detail,created_at)
 SELECT id,buyer_id,'CREATED','买家发起交易',created_at FROM trade_order;
-- The old schema did not store confirmation timestamps. Preserve that uncertainty.
INSERT INTO order_event(order_id,event_type,detail,created_at)
 SELECT id,'LEGACY',CONCAT('升级前的订单状态：',status,'；早期过程未记录'),updated_at
 FROM trade_order WHERE status IN ('CONFIRMED','CANCELLED');
INSERT INTO order_event(order_id,actor_id,event_type,detail,created_at)
 SELECT id,buyer_id,'COMPLETED','买家确认完成（历史记录）',completed_at
 FROM trade_order WHERE status='COMPLETED' AND completed_at IS NOT NULL;
CREATE TABLE notification (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 kind VARCHAR(24) NOT NULL,
 title VARCHAR(100) NOT NULL,
 content VARCHAR(1000) NOT NULL,
 link VARCHAR(200) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES app_user(id),
 INDEX idx_notification_user (user_id,is_read,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE announcement (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 title VARCHAR(80) NOT NULL,
 content VARCHAR(2000) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 pinned BOOLEAN NOT NULL DEFAULT FALSE,
 created_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO announcement(title,content,status,pinned) VALUES
 ('校园面交小贴士','先通过商品留言确认成色和配件，再发起预约。卖家接受后，在校内公共场所检查实物；收到商品后由买家确认完成，并留下真实评价。','PUBLISHED',TRUE);

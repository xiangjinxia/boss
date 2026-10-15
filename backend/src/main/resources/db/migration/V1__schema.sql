CREATE TABLE app_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(24) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 nickname VARCHAR(24) NOT NULL,
 avatar VARCHAR(200) NOT NULL DEFAULT '',
 bio VARCHAR(200) NOT NULL DEFAULT '',
 role VARCHAR(16) NOT NULL DEFAULT 'STUDENT',
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE category (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(32) NOT NULL UNIQUE,
 icon VARCHAR(24) NOT NULL,
 sort_order INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE goods (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 seller_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 title VARCHAR(80) NOT NULL,
 price DECIMAL(10,2) NOT NULL,
 condition_level VARCHAR(16) NOT NULL,
 description TEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 reject_reason VARCHAR(500) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY (seller_id) REFERENCES app_user(id),
 FOREIGN KEY (category_id) REFERENCES category(id),
 INDEX idx_goods_public (status,category_id,created_at),
 INDEX idx_goods_seller (seller_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE goods_image (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 goods_id BIGINT NOT NULL,
 image_url VARCHAR(200) NOT NULL,
 sort_order INT NOT NULL,
 FOREIGN KEY (goods_id) REFERENCES goods(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE upload_file (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 image_url VARCHAR(200) NOT NULL UNIQUE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE favorite (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 goods_id BIGINT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (user_id) REFERENCES app_user(id),
 FOREIGN KEY (goods_id) REFERENCES goods(id),
 UNIQUE KEY uq_favorite (user_id,goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 goods_id BIGINT NOT NULL,
 sender_id BIGINT NOT NULL,
 reply_to_id BIGINT NULL,
 content VARCHAR(500) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (goods_id) REFERENCES goods(id),
 FOREIGN KEY (sender_id) REFERENCES app_user(id),
 FOREIGN KEY (reply_to_id) REFERENCES message(id),
 INDEX idx_message_goods (goods_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE trade_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 goods_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 goods_title VARCHAR(80) NOT NULL,
 goods_image VARCHAR(200) NOT NULL,
 amount DECIMAL(10,2) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 cancel_reason VARCHAR(500) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 completed_at DATETIME NULL,
 FOREIGN KEY (goods_id) REFERENCES goods(id),
 FOREIGN KEY (buyer_id) REFERENCES app_user(id),
 FOREIGN KEY (seller_id) REFERENCES app_user(id),
 active_goods_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'CONFIRMED' THEN goods_id ELSE NULL END) STORED,
 UNIQUE KEY uq_one_confirmed_order (active_goods_id),
 INDEX idx_order_buyer (buyer_id,status),
 INDEX idx_order_seller (seller_id,status),
 INDEX idx_order_goods (goods_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE report (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 reporter_id BIGINT NOT NULL,
 goods_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 result VARCHAR(500) NOT NULL DEFAULT '',
 handled_by BIGINT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 handled_at DATETIME NULL,
 FOREIGN KEY (reporter_id) REFERENCES app_user(id),
 FOREIGN KEY (goods_id) REFERENCES goods(id),
 FOREIGN KEY (handled_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE audit_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 admin_id BIGINT NOT NULL,
 target_type VARCHAR(24) NOT NULL,
 target_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 detail VARCHAR(500) NOT NULL DEFAULT '',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (admin_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE auth_session (
 token_hash CHAR(64) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 expires_at DATETIME NOT NULL,
 FOREIGN KEY (user_id) REFERENCES app_user(id),
 INDEX idx_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO category(name,icon,sort_order) VALUES
 ('教材书籍','book',1),('数码电子','laptop',2),('生活好物','coffee',3),('运动户外','bike',4),('服饰配件','shirt',5),('其他闲置','box',6);

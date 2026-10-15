package cn.campus.exchange.service;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.api.*;
import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.repository.Store;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final Store db;
  private final BCryptPasswordEncoder encoder;

  public AuthService(Store db, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.encoder = encoder;
  }

  @Transactional
  public Map<String, Object> register(Requests.Register r) {
    require(
        db.count("SELECT COUNT(*) FROM app_user WHERE username=?", r.username()) == 0,
        409,
        "账号已存在");
    var id =
        db.insert(
            "INSERT INTO app_user(username,password_hash,nickname) VALUES (?,?,?)",
            r.username(),
            encoder.encode(r.password()),
            r.nickname().trim());
    return session(id);
  }

  public Map<String, Object> login(Requests.Login r) {
    var rows =
        db.list("SELECT id,password_hash,status FROM app_user WHERE username=?", r.username());
    require(
        !rows.isEmpty()
            && encoder.matches(r.password(), rows.getFirst().get("password_hash").toString()),
        401,
        "账号或密码错误");
    require("ACTIVE".equals(rows.getFirst().get("status")), 403, "账号已禁用，请联系管理员");
    return session(Store.id(rows.getFirst(), "id"));
  }

  private Map<String, Object> session(long id) {
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("DELETE FROM auth_session WHERE expires_at<=NOW()");
    db.update(
        "INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES"
            + " (?,?,DATE_ADD(NOW(),INTERVAL 7 DAY))",
        SecurityConfig.hash(token),
        id);
    return Map.of("token", token, "user", profile(id));
  }

  public Map<String, Object> profile(long id) {
    return db.one(
        "SELECT id,username,nickname,avatar,bio,role,status,created_at FROM app_user WHERE id=?",
        id);
  }

  public Map<String, Object> updateProfile(long id, Requests.Profile r) {
    var avatar = r.avatar() == null ? "" : r.avatar();
    require(
        avatar.isEmpty()
            || db.count(
                    "SELECT COUNT(*) FROM upload_file WHERE user_id=? AND image_url=?", id, avatar)
                > 0,
        400,
        "请上传本人的头像图片");
    db.update(
        "UPDATE app_user SET nickname=?,bio=?,avatar=? WHERE id=?",
        r.nickname().trim(),
        r.bio() == null ? "" : r.bio().trim(),
        avatar,
        id);
    return profile(id);
  }

  public void logout(String bearer) {
    if (bearer != null && bearer.startsWith("Bearer "))
      db.update(
          "DELETE FROM auth_session WHERE token_hash=?", SecurityConfig.hash(bearer.substring(7)));
  }
}

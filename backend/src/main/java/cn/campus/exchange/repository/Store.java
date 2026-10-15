package cn.campus.exchange.repository;

import cn.campus.exchange.api.BusinessException;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Store {
  public final JdbcTemplate jdbc;

  public Store(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty()) throw new BusinessException(404, "记录不存在或已不可访问");
    return rows.getFirst();
  }

  public long count(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long insert(String sql, Object... args) {
    var holder = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
          return s;
        },
        holder);
    return Objects.requireNonNull(holder.getKey()).longValue();
  }

  public static long id(Map<String, Object> row, String name) {
    return ((Number) row.get(name)).longValue();
  }
}

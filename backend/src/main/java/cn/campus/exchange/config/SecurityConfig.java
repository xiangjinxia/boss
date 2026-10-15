package cn.campus.exchange.config;

import cn.campus.exchange.api.ApiResult;
import cn.campus.exchange.repository.Store;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {
  @Value("${app.uploads}")
  String uploads;

  public record Actor(long id, String role) {}

  public static Actor actor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.getPrincipal() instanceof Actor a ? a : null;
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, Store db, ObjectMapper mapper) throws Exception {
    var filter =
        new OncePerRequestFilter() {
          @Override
          protected void doFilterInternal(
              HttpServletRequest req, HttpServletResponse res, FilterChain chain)
              throws ServletException, IOException {
            var header = req.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
              var rows =
                  db.list(
                      "SELECT u.id,u.role FROM auth_session s JOIN app_user u ON s.user_id=u.id"
                          + " WHERE s.token_hash=? AND s.expires_at>NOW() AND u.status='ACTIVE'",
                      hash(header.substring(7)));
              if (!rows.isEmpty()) {
                var r = rows.getFirst();
                var a = new Actor(Store.id(r, "id"), r.get("role").toString());
                SecurityContextHolder.getContext()
                    .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                            a, null, List.of(new SimpleGrantedAuthority("ROLE_" + a.role()))));
              }
            }
            chain.doFilter(req, res);
          }
        };
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/login", "/api/auth/register", "/api/health")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/goods",
                        "/api/goods/*",
                        "/api/goods/*/messages",
                        "/api/categories",
                        "/api/public/stats",
                        "/api/discovery",
                        "/api/announcements",
                        "/api/sellers/*",
                        "/api/sellers/*/reviews",
                        "/samples/**",
                        "/uploads/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json;charset=UTF-8");
                          mapper.writeValue(res.getWriter(), ApiResult.error(401, "请先登录，或账号已被禁用"));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json;charset=UTF-8");
                          mapper.writeValue(res.getWriter(), ApiResult.error(403, "需要管理员权限"));
                        }))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/uploads/**")
        .addResourceLocations(Path.of(uploads).toAbsolutePath().toUri().toString() + "/");
  }
}

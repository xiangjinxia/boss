package cn.campus.exchange.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class Requests {
  public record Register(
      @Pattern(regexp = "[A-Za-z0-9_]{4,24}", message = "账号须为 4–24 位字母、数字或下划线") @NotNull
          String username,
      @NotNull @Size(min = 8, max = 64, message = "密码长度为 8–64 位") String password,
      @NotBlank @Size(max = 24, message = "昵称最多 24 字") String nickname) {}

  public record Login(@NotBlank String username, @NotBlank String password) {}

  public record Profile(
      @NotBlank @Size(max = 24) String nickname,
      @Size(max = 200) String bio,
      @Size(max = 200) String avatar) {}

  public record Goods(
      @NotBlank @Size(max = 80, message = "标题最多 80 字") String title,
      @NotNull @Positive Long categoryId,
      @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2)
          BigDecimal price,
      @NotBlank @Pattern(regexp = "全新|几乎全新|九成新|八成新|有使用痕迹") String conditionLevel,
      @NotBlank @Size(max = 3000, message = "描述最多 3000 字") String description,
      @NotNull @Size(min = 1, max = 5, message = "请上传 1–5 张图片") List<@NotBlank String> images) {}

  public record Text(
      @NotBlank @Size(max = 500, message = "内容最多 500 字") String content, Long replyToId) {}

  public record CreateOrder(@NotNull @Positive Long goodsId) {}

  public record Action(@NotBlank String action, @Size(max = 500) String reason) {}

  public record Report(@NotNull @Positive Long goodsId, @NotBlank @Size(max = 500) String reason) {}

  public record Review(
      @NotNull @Min(1) @Max(5) Integer rating,
      @NotBlank @Size(min = 5, max = 500, message = "评价为 5–500 字") String content) {}

  public record Category(
      @NotBlank @Size(max = 32) String name,
      @NotBlank @Pattern(regexp = "book|laptop|coffee|bike|shirt|box|camera|leaf") String icon,
      @Min(0) @Max(9999) int sortOrder,
      @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

  public record Announcement(
      @NotBlank @Size(max = 80) String title,
      @NotBlank @Size(max = 2000) String content,
      @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED") String status,
      boolean pinned) {}
}

package cn.campus.exchange.api;

import static cn.campus.exchange.api.BusinessException.require;

import cn.campus.exchange.config.SecurityConfig;
import cn.campus.exchange.repository.Store;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {
  private final Path dir;
  private final Store db;

  public UploadController(@Value("${app.uploads}") String path, Store db) throws IOException {
    this.dir = Path.of(path).toAbsolutePath();
    this.db = db;
    Files.createDirectories(dir);
  }

  @PostMapping("/api/uploads")
  public Object upload(@RequestParam("file") MultipartFile file) throws IOException {
    require(!file.isEmpty() && file.getSize() <= 5 * 1024 * 1024, 400, "请选择不超过 5MB 的图片");
    String name =
        Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
    require(
        name.matches(".*\\.(png|jpg|jpeg)$")
            && Set.of("image/png", "image/jpeg")
                .contains(Optional.ofNullable(file.getContentType()).orElse("")),
        400,
        "仅支持 PNG、JPG 图片，且类型必须匹配");
    try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(input);
      require(readers.hasNext(), 400, "图片内容无法识别");
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        require(Set.of("png", "jpeg", "jpg").contains(format), 400, "图片实际格式不受支持");
        require(
            (name.endsWith(".png")
                    && format.equals("png")
                    && "image/png".equals(file.getContentType()))
                || ((name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    && Set.of("jpeg", "jpg").contains(format)
                    && "image/jpeg".equals(file.getContentType())),
            400,
            "图片后缀、类型与内容不一致");
        int w = reader.getWidth(0), h = reader.getHeight(0);
        require(w > 0 && h > 0 && (long) w * h <= 12_000_000, 400, "图片像素不得超过 1200 万");
        var image = reader.read(0);
        String safe = UUID.randomUUID() + ".png";
        // Decode and re-encode; uploaded executable payloads and metadata are never served
        // verbatim.
        require(ImageIO.write(image, "png", dir.resolve(safe).toFile()), 400, "图片转换失败");
        String url = "/uploads/" + safe;
        db.update(
            "INSERT INTO upload_file(user_id,image_url) VALUES (?,?)",
            SecurityConfig.actor().id(),
            url);
        return ApiResult.ok(Map.of("url", url));
      } finally {
        reader.dispose();
      }
    } catch (javax.imageio.IIOException e) {
      throw new BusinessException(400, "图片已损坏或格式不正确");
    }
  }
}

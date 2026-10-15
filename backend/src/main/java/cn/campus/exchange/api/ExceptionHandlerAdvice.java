package cn.campus.exchange.api;

import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ExceptionHandlerAdvice {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status()).body(ApiResult.error(e.status(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var first = e.getBindingResult().getFieldErrors().getFirst();
    return ResponseEntity.badRequest().body(ApiResult.error(400, first.getDefaultMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return ResponseEntity.badRequest().body(ApiResult.error(400, "请求参数格式不正确"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> size(Exception e) {
    return ResponseEntity.status(413).body(ApiResult.error(413, "每张图片不能超过 5MB"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409).body(ApiResult.error(409, "数据已存在或操作与当前状态冲突"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    LoggerFactory.getLogger(getClass()).error("Unexpected API failure", e);
    return ResponseEntity.internalServerError().body(ApiResult.error(500, "服务暂时不可用，请稍后重试"));
  }
}

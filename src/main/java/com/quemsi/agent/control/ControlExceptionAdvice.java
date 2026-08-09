package com.quemsi.agent.control;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.quemsi.commons.util.BaseRuntimeException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class ControlExceptionAdvice {

    @ExceptionHandler(BaseRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handle(BaseRuntimeException exception) {
        log.warn("Control endpoint error: {}", exception.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("time", LocalDateTime.now());
        body.put("messageId", exception.getMessageId());
        if (exception.getExtra() != null) {
            body.put("extra", exception.getExtra());
        }
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}

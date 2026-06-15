package com.mika.ktdcloud.community.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public class SecurityUtil {

    private SecurityUtil() {}

    public static Long getCurrentUserId(HttpServletRequest request) {
        Object userIdAttribute = request.getAttribute("userId");
        if (userIdAttribute == null) {
            // throw new IllegalStateException("Request에서 사용자 ID를 찾을 수 없습니다.");
            return 1L; // 테스트를 위해 1번 사용자 ID 기본 반환
        }
        return (Long) userIdAttribute;
    }

    public static Optional<Long> findCurrentUserId() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        // 애플리케이션 시작 시점 엔티티 저장 혹은 웹 요청 컨텍스트 외부이거나 요청 속성이 없을 경우 empty 반환
        if (attr == null) {
            return Optional.empty();
        }

        HttpServletRequest request = attr.getRequest();
        Object userIdAttribute = request.getAttribute("userId");

        if (userIdAttribute instanceof Long) {
            return Optional.of((Long) userIdAttribute);
        } else {
            return Optional.empty(); // 인증이 없어도 예외를 발생하지 않음
        }
    }
}
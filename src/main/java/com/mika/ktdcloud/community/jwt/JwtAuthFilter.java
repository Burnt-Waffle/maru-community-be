package com.mika.ktdcloud.community.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;

    @Value("${app.web-server-url}")
    private String webServerUrl;

    // 필터 제외 경로 목록
    private static final String[] EXCLUDED_PATHS = {
            "/api/v1/terms",
            "/api/v1/users/signup",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/posts", // /v1 추가
            "/actuator/health"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean shouldSkip = Arrays.stream(EXCLUDED_PATHS).anyMatch(path::startsWith);
        System.out.println("[DEBUG] Incoming Path: " + path + ", Should Skip: " + shouldSkip);
        return shouldSkip;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws IOException, ServletException {

        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        Optional<String> token = extractTokenFromHeader(request);

        if (token.isEmpty()) {
            System.out.println("[DEBUG] 401 Error (No Token) for Path: " + path);

            response.setHeader("Access-Control-Allow-Origin", webServerUrl);
            response.setHeader("Access-Control-Allow-Credentials", "true");

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Missing Authorization header");
            return;
        }

        if (!validateAndSetAttributes(token.get(), request)) {
            System.out.println("[DEBUG] 401 Error (No Token) for Path: " + path);

            response.setHeader("Access-Control-Allow-Origin", webServerUrl);
            response.setHeader("Access-Control-Allow-Credentials", "true");

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid token or expired token");
            return;
        }

        chain.doFilter(request, response);
    }

    private Optional<String> extractTokenFromHeader(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("Authorization"))
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring(7));
    }

    private boolean validateAndSetAttributes(String token, HttpServletRequest request) {
        try {
            var jws = jwtProvider.parse(token);
            Claims body = jws.getBody();
            request.setAttribute("userId", Long.valueOf(body.getSubject()));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}

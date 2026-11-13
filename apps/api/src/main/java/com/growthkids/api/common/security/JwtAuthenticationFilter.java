package com.growthkids.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.error.ErrorResponse;
import com.growthkids.api.common.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 从 Authorization: Bearer 解析 JWT 并写入 SecurityContext（规范 §70：JWT → CurrentUser）。
 * token 非法/过期时直接返回规范 §68 错误响应。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AuthPrincipal principal = jwtService.parseAccessToken(token);
            var authorities =
                    List.of(
                            new SimpleGrantedAuthority(
                                    "ROLE_" + principal.role().toUpperCase(Locale.ROOT)));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, ex);
        }
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.code().status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ErrorResponse body =
                ErrorResponse.of(ex.code(), ex.getMessage(), ex.details(), RequestIdFilter.currentRequestId());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

package com.banking.api.config;

import com.banking.api.entity.User;
import com.banking.api.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Refuses every authenticated request from an account an authorizer suspended or locked, and
 * refuses requests from a lock that is still in force. Login itself is already blocked at
 * Keycloak (the user is disabled when the request is approved); this filter is what stops the
 * access tokens those users already hold from continuing to work, so "cannot access the
 * system" holds for the whole suspension or lock window.
 *
 * <p>Deliberately not a {@code @Component}: it is constructed inside the security filter
 * chain in {@link SecurityConfig}, so Boot never also registers it at container level ahead of
 * the chain - where no authentication exists yet and it would silently skip every request.</p>
 *
 * <p>Instantiated rather than injected: Spring Boot 4 no longer auto-configures a
 * {@code com.fasterxml} ObjectMapper bean.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class UserAccessStateFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> user = userRepository.findByKeycloakUserId(jwt.getSubject());
        if (user.isPresent()) {
            User account = user.get();
            if (Boolean.TRUE.equals(account.getSuspended())) {
                refuse(response, 403, "ACCOUNT_SUSPENDED",
                        "This account is suspended. An approved reinstatement is required before it can be used again.");
                return;
            }
            LocalDateTime lockedUntil = account.getLockedUntil();
            if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
                refuse(response, 403, "ACCOUNT_LOCKED",
                        "This account is locked until " + lockedUntil
                                + ". Access returns automatically when the lock expires.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void refuse(
            HttpServletResponse response, int status, String code, String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", code);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

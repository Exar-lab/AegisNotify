package com.aegisnotify.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Gateway-only informative 403 handler (design decision A2).
 *
 * <p>Names the scope the caller's token was missing without leaking token
 * contents, user identity, or realm internals. Downstream services keep
 * Spring's opaque default 403 body — this handler exists once, here.</p>
 */
@Component
public class ScopeAwareAccessDeniedHandler implements ServerAccessDeniedHandler {

  private static final String UNKNOWN_SCOPE = "unknown";

  private final RouteScopeRules routeScopeRules;
  private final ObjectMapper objectMapper;

  public ScopeAwareAccessDeniedHandler(RouteScopeRules routeScopeRules, ObjectMapper objectMapper) {
    this.routeScopeRules = routeScopeRules;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
    return requiredScopeFor(exchange)
        .defaultIfEmpty(UNKNOWN_SCOPE)
        .flatMap(scope -> writeResponse(exchange, scope));
  }

  private Mono<String> requiredScopeFor(ServerWebExchange exchange) {
    return Flux.fromIterable(routeScopeRules.rules())
        .concatMap(rule -> rule.matcher().matches(exchange)
            .filter(MatchResult::isMatch)
            .map(match -> rule.scope()))
        .next();
  }

  private Mono<Void> writeResponse(ServerWebExchange exchange, String requiredScope) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", "insufficient_scope");
    body.put("message", "Access denied: the token is missing a required scope.");
    body.put("requiredScope", requiredScope);
    body.put("path", exchange.getRequest().getPath().value());
    body.put("timestamp", Instant.now().toString());

    byte[] bytes = writeAsBytes(body);

    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  private byte[] writeAsBytes(Map<String, Object> body) {
    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize the 403 response body", e);
    }
  }
}

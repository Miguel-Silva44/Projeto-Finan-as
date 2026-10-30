package br.com.financa.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(1)
public class AuthRateLimit extends OncePerRequestFilter {
  private record Window(long start, int count) {}

  private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
  private final Clock clock;

  public AuthRateLimit(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getMethod().equals("POST") && request.getRequestURI().startsWith("/auth/")) {
      long now = clock.millis();
      attempts.entrySet().removeIf(entry -> now - entry.getValue().start() > 900000);
      Window window =
          attempts.compute(
              request.getRemoteAddr(),
              (key, old) ->
                  old == null || now - old.start() > 900000
                      ? new Window(now, 1)
                      : new Window(old.start(), old.count() + 1));
      if (window.count() > 30) {
        response.setStatus(429);
        response.setHeader("Retry-After", "900");
        response.setContentType("text/plain;charset=UTF-8");
        response
            .getWriter()
            .write("Muitas tentativas. Aguarde 15 minutos antes de tentar novamente.");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}

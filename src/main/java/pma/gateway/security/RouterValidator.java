package pma.gateway.security;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Decide quais rotas são abertas (não exigem token).
 * Todo o resto passa pelo AuthorizationFilter.
 */
@Component
public class RouterValidator {

  private record OpenRoute(HttpMethod method, String pattern) {
  }

  private static final List<OpenRoute> OPEN_ROUTES = List.of(
      new OpenRoute(HttpMethod.POST, "/auth/register"),
      new OpenRoute(HttpMethod.POST, "/auth/login"),
      new OpenRoute(null, "/health-check"),
      new OpenRoute(null, "/info"),
      new OpenRoute(null, "/**/health-check"),
      new OpenRoute(null, "/gateway/actuator/**"));

  private final AntPathMatcher matcher = new AntPathMatcher();

  public boolean isSecured(ServerHttpRequest request) {
    // preflight de CORS nunca carrega token
    if (HttpMethod.OPTIONS.equals(request.getMethod())) {
      return false;
    }
    String path = request.getPath().value();
    return OPEN_ROUTES.stream().noneMatch(route ->
        (route.method() == null || route.method().equals(request.getMethod()))
            && matcher.match(route.pattern(), path));
  }

}

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

  /**
   * Rotas protegidas que só o papel "admin" pode chamar (RBAC). Os outros recebem 403.
   * POST /accounts e /accounts/login são de uso interno do Auth: pelo gateway, só admin.
   */
  private static final List<OpenRoute> ADMIN_ROUTES = List.of(
      new OpenRoute(HttpMethod.GET, "/accounts"),
      new OpenRoute(HttpMethod.POST, "/accounts"),
      new OpenRoute(HttpMethod.POST, "/accounts/login"),
      new OpenRoute(HttpMethod.DELETE, "/accounts/**"));

  public static final String ROLE_ADMIN = "admin";

  private final AntPathMatcher matcher = new AntPathMatcher();

  public boolean isSecured(ServerHttpRequest request) {
    // preflight de CORS nunca carrega token
    if (HttpMethod.OPTIONS.equals(request.getMethod())) {
      return false;
    }
    return !matchesAny(OPEN_ROUTES, request);
  }

  public boolean isAdminOnly(ServerHttpRequest request) {
    return matchesAny(ADMIN_ROUTES, request);
  }

  private boolean matchesAny(List<OpenRoute> routes, ServerHttpRequest request) {
    String path = request.getPath().value();
    return routes.stream().anyMatch(route ->
        (route.method() == null || route.method().equals(request.getMethod()))
            && matcher.match(route.pattern(), path));
  }

}

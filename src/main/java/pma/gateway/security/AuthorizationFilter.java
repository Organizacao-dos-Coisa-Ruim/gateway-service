package pma.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Filtro global de autorização.
 *
 * 1. Remove headers de identidade vindos do cliente (evita que alguém se passe por outro usuário).
 * 2. Rotas abertas seguem direto.
 * 3. Rotas protegidas exigem "Authorization: Bearer <token>", validado no Auth.
 * 4. Com token válido, repassa id-account e role-account para o serviço de destino.
 */
@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

  public static final String HEADER_ID_ACCOUNT = "id-account";
  public static final String HEADER_ROLE_ACCOUNT = "role-account";
  private static final String BEARER = "Bearer ";

  private final RouterValidator routerValidator;
  private final AuthService authService;

  public AuthorizationFilter(RouterValidator routerValidator, AuthService authService) {
    this.routerValidator = routerValidator;
    this.authService = authService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(h -> {
          h.remove(HEADER_ID_ACCOUNT);
          h.remove(HEADER_ROLE_ACCOUNT);
        })
        .build();

    if (!routerValidator.isSecured(request)) {
      return chain.filter(exchange.mutate().request(request).build());
    }

    String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith(BEARER)) {
      return reject(exchange, HttpStatus.UNAUTHORIZED);
    }
    String jwt = authorization.substring(BEARER.length()).trim();

    // Primeiro decide (liberar ou negar), depois executa. Assim erros do serviço de destino
    // não são confundidos com erros de autenticação.
    return authService.solve(jwt)
        .filter(solved -> solved.idAccount() != null)
        .map(solved -> Decision.allow(request.mutate()
            .header(HEADER_ID_ACCOUNT, solved.idAccount())
            .header(HEADER_ROLE_ACCOUNT, solved.role() == null ? "" : solved.role())
            .build()))
        // Auth respondeu sem idAccount
        .defaultIfEmpty(Decision.deny(HttpStatus.UNAUTHORIZED))
        // 4xx do Auth = token inválido/expirado; qualquer outro erro = Auth indisponível
        .onErrorResume(e -> Mono.just(Decision.deny(
            e instanceof WebClientResponseException wcre && wcre.getStatusCode().is4xxClientError()
                ? HttpStatus.UNAUTHORIZED
                : HttpStatus.SERVICE_UNAVAILABLE)))
        .flatMap(decision -> decision.request() != null
            ? chain.filter(exchange.mutate().request(decision.request()).build())
            : reject(exchange, decision.status()));
  }

  private record Decision(ServerHttpRequest request, HttpStatus status) {
    static Decision allow(ServerHttpRequest request) {
      return new Decision(request, null);
    }

    static Decision deny(HttpStatus status) {
      return new Decision(null, status);
    }
  }

  private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
    exchange.getResponse().setStatusCode(status);
    return exchange.getResponse().setComplete();
  }

  /** Roda antes do roteamento. */
  @Override
  public int getOrder() {
    return -1;
  }

}

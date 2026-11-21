package pma.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Adiciona headers de segurança em TODA resposta do gateway (achados do scan OWASP ZAP, issue #78).
 *
 * É um WebFilter (e não um GlobalFilter do Gateway) para cobrir também o que não é roteado:
 * o /health-check do próprio gateway e as respostas 401/403 do AuthorizationFilter.
 *
 * Os headers são gravados em beforeCommit, logo antes de a resposta sair, para não serem
 * sobrescritos pelos headers que vêm do serviço de destino.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    exchange.getResponse().beforeCommit(() -> {
      HttpHeaders headers = exchange.getResponse().getHeaders();
      // o navegador não tenta "adivinhar" o tipo do conteúdo (ex.: tratar JSON como HTML/script)
      headers.set("X-Content-Type-Options", "nosniff");
      // outros sites não podem embutir as respostas da API (<img>, <script>...); fetch com CORS segue funcionando
      headers.set("Cross-Origin-Resource-Policy", "same-origin");
      // a API não deve ser exibida dentro de <iframe> (clickjacking)
      headers.set("X-Frame-Options", "DENY");
      // não vazar a URL da API (com ids) no header Referer de outras requisições
      headers.set("Referrer-Policy", "no-referrer");
      return Mono.empty();
    });
    return chain.filter(exchange);
  }

}

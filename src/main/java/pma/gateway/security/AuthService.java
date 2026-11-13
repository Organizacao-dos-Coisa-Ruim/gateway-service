package pma.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Cliente do serviço Auth. O gateway é reativo (WebFlux), por isso usa WebClient em vez de Feign.
 *
 * Contrato esperado do Auth:
 *   POST /auth/solve  body: {"jwt": "<token>"}
 *   200 -> {"idAccount": "...", "role": "..."}
 *   4xx -> token inválido ou expirado
 */
@Service
public class AuthService {

  public record SolveIn(String jwt) {
  }

  public record SolveOut(String idAccount, String role) {
  }

  private final WebClient webClient;

  public AuthService(WebClient.Builder builder, @Value("${pma.auth.url}") String authUrl) {
    this.webClient = builder.baseUrl(authUrl).build();
  }

  public Mono<SolveOut> solve(String jwt) {
    return webClient.post()
        .uri("/auth/solve")
        .bodyValue(new SolveIn(jwt))
        .retrieve()
        .bodyToMono(SolveOut.class);
  }

}

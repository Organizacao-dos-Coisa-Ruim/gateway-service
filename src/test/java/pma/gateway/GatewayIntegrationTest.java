package pma.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Sobe o gateway de verdade e um servidor HTTP falso que faz o papel do Auth e do Account.
 */
// métricas ficam desligadas em testes por padrão; religamos para testar o endpoint do Prometheus
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

  private static final String VALID_TOKEN = "token-valido";

  private static final HttpServer backend = startBackend();

  /** Headers de identidade que o "Account" falso recebeu na última chamada. */
  private static final AtomicReference<String> receivedIdAccount = new AtomicReference<>();
  private static final AtomicReference<String> receivedRole = new AtomicReference<>();

  @Value("${local.server.port}")
  private int port;

  private WebTestClient client;

  @DynamicPropertySource
  static void routes(DynamicPropertyRegistry registry) {
    String url = "http://localhost:" + backend.getAddress().getPort();
    registry.add("AUTH_URL", () -> url);
    registry.add("ACCOUNT_URL", () -> url);
  }

  @BeforeEach
  void setUp() {
    client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    receivedIdAccount.set(null);
    receivedRole.set(null);
  }

  @AfterAll
  static void stopBackend() {
    backend.stop(0);
  }

  @Test
  void healthCheckIsOpen() {
    client.get().uri("/health-check").exchange().expectStatus().isOk();
  }

  @Test
  void infoReturnsNameAndHostname() {
    client.get().uri("/info").exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.name").isEqualTo("gateway")
        .jsonPath("$.hostname").exists();
  }

  @Test
  void prometheusMetricsAreExposed() {
    client.get().uri("/gateway/actuator/prometheus").exchange().expectStatus().isOk();
  }

  @Test
  void securedRouteWithoutTokenReturns401() {
    client.get().uri("/accounts").exchange().expectStatus().isUnauthorized();
    assertThat(receivedIdAccount.get()).isNull();
  }

  @Test
  void securedRouteWithInvalidTokenReturns401() {
    client.get().uri("/accounts")
        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido")
        .exchange().expectStatus().isUnauthorized();
  }

  @Test
  void securedRouteWithValidTokenForwardsIdentity() {
    client.get().uri("/accounts")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
        .exchange().expectStatus().isOk();
    assertThat(receivedIdAccount.get()).isEqualTo("123");
    assertThat(receivedRole.get()).isEqualTo("USER");
  }

  @Test
  void spoofedIdentityHeaderIsRemoved() {
    client.get().uri("/accounts")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
        .header("id-account", "hacker")
        .exchange().expectStatus().isOk();
    assertThat(receivedIdAccount.get()).isEqualTo("123");
  }

  @Test
  void openRouteDoesNotNeedTokenAndStripsIdentity() {
    client.post().uri("/auth/login")
        .header("id-account", "hacker")
        .exchange().expectStatus().isOk();
    assertThat(receivedIdAccount.get()).isNull();
  }

  @Test
  void corsPreflightIsAllowedWithoutToken() {
    client.options().uri("/accounts")
        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  // --- backend falso ---------------------------------------------------------

  private static HttpServer startBackend() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/auth/solve", GatewayIntegrationTest::solve);
      server.createContext("/auth/login", ex -> {
        receivedIdAccount.set(ex.getRequestHeaders().getFirst("id-account"));
        respond(ex, 200, "{}");
      });
      server.createContext("/accounts", ex -> {
        receivedIdAccount.set(ex.getRequestHeaders().getFirst("id-account"));
        receivedRole.set(ex.getRequestHeaders().getFirst("role-account"));
        respond(ex, 200, "[]");
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void solve(HttpExchange ex) throws IOException {
    String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (body.contains(VALID_TOKEN)) {
      respond(ex, 200, "{\"idAccount\":\"123\",\"role\":\"USER\"}");
    } else {
      respond(ex, 401, "");
    }
  }

  private static void respond(HttpExchange ex, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      ex.getResponseBody().write(bytes);
    }
    ex.close();
  }

}

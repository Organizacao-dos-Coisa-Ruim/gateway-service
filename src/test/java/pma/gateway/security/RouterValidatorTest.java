package pma.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class RouterValidatorTest {

  private final RouterValidator validator = new RouterValidator();

  private boolean secured(HttpMethod method, String path) {
    return validator.isSecured(MockServerHttpRequest.method(method, path).build());
  }

  @Test
  void openRoutes() {
    assertThat(secured(HttpMethod.POST, "/auth/login")).isFalse();
    assertThat(secured(HttpMethod.POST, "/auth/register")).isFalse();
    assertThat(secured(HttpMethod.GET, "/health-check")).isFalse();
    assertThat(secured(HttpMethod.GET, "/info")).isFalse();
    assertThat(secured(HttpMethod.GET, "/accounts/health-check")).isFalse();
    assertThat(secured(HttpMethod.GET, "/gateway/actuator/prometheus")).isFalse();
    assertThat(secured(HttpMethod.OPTIONS, "/orders")).isFalse();
  }

  @Test
  void securedRoutes() {
    assertThat(secured(HttpMethod.GET, "/accounts")).isTrue();
    assertThat(secured(HttpMethod.GET, "/auth/login")).isTrue();
    assertThat(secured(HttpMethod.POST, "/orders")).isTrue();
    assertThat(secured(HttpMethod.GET, "/products/1")).isTrue();
  }

}

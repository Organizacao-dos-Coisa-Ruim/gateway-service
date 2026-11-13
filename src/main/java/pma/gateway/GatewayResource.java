package pma.gateway;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints próprios do gateway (não roteados para outros serviços).
 * Usados pelos probes do Kubernetes e pelo teste de carga + HPA.
 */
@RestController
public class GatewayResource {

  @Value("${spring.application.name}")
  private String name;

  @GetMapping("/health-check")
  public ResponseEntity<Void> healthCheck() {
    return ResponseEntity.ok().build();
  }

  /** Inclui o hostname para mostrar qual pod respondeu (útil na demo do HPA). */
  @GetMapping("/info")
  public ResponseEntity<Map<String, String>> info() {
    return ResponseEntity.ok(Map.of(
        "name", name,
        "hostname", hostname(),
        "timestamp", Instant.now().toString()));
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "unknown";
    }
  }

}

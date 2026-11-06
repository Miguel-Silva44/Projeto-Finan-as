package br.com.financa.controller;

import br.com.financa.service.HealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  private final HealthService health;

  public HealthController(HealthService health) {
    this.health = health;
  }

  @GetMapping("/health/ready")
  public ResponseEntity<String> ready() {
    return health.ready()
        ? ResponseEntity.ok("ready")
        : ResponseEntity.status(503).body("unavailable");
  }
}

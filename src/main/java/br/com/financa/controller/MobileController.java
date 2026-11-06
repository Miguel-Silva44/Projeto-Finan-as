package br.com.financa.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class MobileController {
  private final String packageName;
  private final List<String> fingerprints;

  public MobileController(
      @Value("${app.android.package:}") String packageName,
      @Value("${app.android.fingerprints:}") String fingerprints) {
    this.packageName = packageName;
    this.fingerprints =
        Arrays.stream(fingerprints.split(","))
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .toList();
    if (!this.fingerprints.isEmpty()
        && (!packageName.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
            || this.fingerprints.stream()
                .anyMatch(value -> !value.matches("(?i)([0-9a-f]{2}:){31}[0-9a-f]{2}"))))
      throw new IllegalArgumentException("Associação Android inválida.");
  }

  @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<List<Map<String, Object>>> association() {
    List<Map<String, Object>> links =
        fingerprints.isEmpty()
            ? List.of()
            : List.of(
                Map.of(
                    "relation",
                    List.of("delegate_permission/common.handle_all_urls"),
                    "target",
                    Map.of(
                        "namespace",
                        "android_app",
                        "package_name",
                        packageName,
                        "sha256_cert_fingerprints",
                        fingerprints)));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links);
  }

  @GetMapping({"/privacy", "/support", "/account-deletion"})
  public String information(jakarta.servlet.http.HttpServletRequest request, Model model) {
    model.addAttribute("document", request.getRequestURI());
    return "mobile-info";
  }
}

package br.com.financa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:mobile;MODE=PostgreSQL;NON_KEYWORDS=MONTH;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "app.jobs-enabled=false",
      "app.android.package=br.com.financa.controle.hml",
      "app.android.fingerprints=AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB:AB"
    })
@AutoConfigureMockMvc
class MobileIntegrationTest {
  @Autowired MockMvc mvc;

  @Test
  void publicInstallationResourcesDoNotRequireSession() throws Exception {
    mvc.perform(get("/manifest.webmanifest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.start_url").value("/dashboard"))
        .andExpect(jsonPath("$.icons.length()").value(3));
    for (String path :
        new String[] {
          "/sw.js",
          "/offline.html",
          "/icons/icon-192.png",
          "/icons/icon-512.png",
          "/icons/maskable-512.png",
          "/privacy",
          "/support",
          "/account-deletion"
        }) mvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void associationPublishesOnlyConfiguredCertificateWithoutAuthentication() throws Exception {
    mvc.perform(get("/.well-known/assetlinks.json"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(jsonPath("$[0].target.package_name").value("br.com.financa.controle.hml"))
        .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints.length()").value(1));
  }

  @Test
  void installationResourcesDoNotExposePrivateRoutesOrDisableCsrf() throws Exception {
    for (String path :
        new String[] {"/dashboard", "/me/export", "/exports/transactions", "/settings"})
      mvc.perform(get(path)).andExpect(status().is3xxRedirection());
    mvc.perform(post("/me/delete")).andExpect(status().isForbidden());
    mvc.perform(get("/auth/login"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
        .andExpect(
            header()
                .string(
                    "Content-Security-Policy",
                    org.hamcrest.Matchers.containsString("worker-src 'self'")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/manifest.webmanifest")));
  }
}

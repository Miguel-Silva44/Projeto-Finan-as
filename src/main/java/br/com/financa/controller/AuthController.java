package br.com.financa.controller;

import br.com.financa.service.IdentityService;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
public class AuthController {
  private final IdentityService identity;

  public AuthController(IdentityService identity) {
    this.identity = identity;
  }

  @GetMapping({"/login", "/register", "/recover", "/reset"})
  public String page(
      jakarta.servlet.http.HttpServletRequest request,
      @RequestParam(defaultValue = "") String token,
      Model model) {
    model.addAttribute("mode", request.getRequestURI().substring(6));
    model.addAttribute("token", token);
    return "auth";
  }

  @PostMapping("/register")
  public String register(@RequestParam Map<String, String> form, RedirectAttributes flash) {
    identity.register(form);
    flash.addFlashAttribute(
        "message", "Se os dados estiverem aptos, enviaremos um link de confirmação.");
    return "redirect:/auth/login";
  }

  @PostMapping("/recover")
  public String recover(@RequestParam String email, RedirectAttributes flash) {
    identity.recover(email);
    flash.addFlashAttribute(
        "message", "Se o e-mail estiver cadastrado, enviaremos um link para redefinir sua senha.");
    return "redirect:/auth/login";
  }

  @GetMapping("/verify")
  public String verify(@RequestParam String token, Model model) {
    model.addAttribute("mode", "verify");
    model.addAttribute("token", token);
    return "auth";
  }

  @PostMapping("/verify")
  public String confirm(@RequestParam String token, RedirectAttributes flash) {
    identity.verify(token);
    flash.addFlashAttribute("message", "E-mail confirmado. Você já pode entrar.");
    return "redirect:/auth/login";
  }

  @PostMapping("/reset")
  public String reset(
      @RequestParam String token, @RequestParam String password, RedirectAttributes flash) {
    identity.reset(token, password);
    flash.addFlashAttribute("message", "Senha atualizada. Entre novamente.");
    return "redirect:/auth/login";
  }
}

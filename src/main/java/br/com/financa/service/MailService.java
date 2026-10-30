package br.com.financa.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
  private final JavaMailSender sender;
  private final String mode, from;

  public MailService(
      JavaMailSender sender,
      @Value("${app.mail-mode}") String mode,
      @Value("${app.mail-from}") String from) {
    this.sender = sender;
    this.mode = mode;
    this.from = from;
  }

  public void send(String to, String subject, String body) {
    if ("file".equals(mode)) {
      try {
        Path path = Path.of("data", "mail");
        Files.createDirectories(path);
        Files.writeString(
            path.resolve(UUID.randomUUID() + ".eml"),
            "From: "
                + from
                + "\nTo: "
                + to
                + "\nSubject: "
                + subject
                + "\nContent-Type: text/plain; charset=UTF-8\n\n"
                + body,
            StandardCharsets.UTF_8);
      } catch (Exception e) {
        throw new IllegalStateException("Não foi possível gravar o e-mail de desenvolvimento.", e);
      }
      return;
    }
    if (!"smtp".equals(mode)) throw new IllegalStateException("MAIL_MODE deve ser file ou smtp.");
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    sender.send(message);
  }
}

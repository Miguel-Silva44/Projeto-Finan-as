package br.com.financa.controller;

import br.com.financa.model.DomainException;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class ErrorHandler {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ErrorHandler.class);

  @ExceptionHandler(DomainException.class)
  public org.springframework.web.servlet.ModelAndView domain(DomainException error) {
    var model = new org.springframework.web.servlet.ModelAndView("failure");
    model.addObject("errorMessage", error.getMessage());
    model.addObject("code", error.getCode());
    model.addObject("correlation", UUID.randomUUID().toString());
    model.setStatus(
        error.getCode().equals("NOT_FOUND")
            ? HttpStatus.NOT_FOUND
            : error.getCode().equals("CONFLICT")
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_CONTENT);
    return model;
  }

  @ExceptionHandler(DataAccessException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public String database(DataAccessException error, Model model) {
    String correlation = UUID.randomUUID().toString();
    LOG.warn("Falha de persistência {} tipo {}", correlation, error.getClass().getSimpleName());
    model.addAttribute(
        "errorMessage",
        "Não foi possível concluir. Nenhuma alteração parcial foi salva. Recarregue e tente novamente.");
    model.addAttribute("code", "PERSISTENCE_CONFLICT");
    model.addAttribute("correlation", correlation);
    return "failure";
  }
}

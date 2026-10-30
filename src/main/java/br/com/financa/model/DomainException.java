package br.com.financa.model;

public class DomainException extends RuntimeException {
  private final String code;

  public DomainException(String message) {
    this("VALIDATION", message);
  }

  public DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}

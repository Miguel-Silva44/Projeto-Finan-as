package br.com.financa.config;

import br.com.financa.repository.IdentityRepository;
import br.com.financa.service.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "app.jobs-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DailyJobs {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DailyJobs.class);
  private final IdentityRepository users;
  private final PlanningService planning;
  private final CardService cards;

  public DailyJobs(IdentityRepository users, PlanningService planning, CardService cards) {
    this.users = users;
    this.planning = planning;
    this.cards = cards;
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 10000)
  public void run() {
    for (String user : users.activeIds())
      try {
        planning.generate(user);
        cards.closeDue(user);
      } catch (RuntimeException e) {
        LOG.error(
            "Falha na tarefa financeira; execução será repetida. Tipo: {}",
            e.getClass().getSimpleName());
      }
    try {
      users.purge();
    } catch (RuntimeException e) {
      LOG.error("Falha ao remover contas excluídas. Tipo: {}", e.getClass().getSimpleName());
    }
  }
}

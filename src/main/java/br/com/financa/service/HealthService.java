package br.com.financa.service;

import javax.sql.DataSource;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
  private final DataSource source;
  private final ApplicationAvailability availability;

  public HealthService(DataSource source, ApplicationAvailability availability) {
    this.source = source;
    this.availability = availability;
  }

  public boolean ready() {
    if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return false;
    try (var connection = source.getConnection();
        var statement = connection.createStatement()) {
      statement.setQueryTimeout(3);
      try (var rows =
          statement.executeQuery(
              "SELECT COUNT(*) FROM flyway_schema_history WHERE success=FALSE")) {
        return rows.next() && rows.getLong(1) == 0;
      }
    } catch (Exception e) {
      return false;
    }
  }
}

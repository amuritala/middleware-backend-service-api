package com.banking.api.config;

import com.banking.api.entity.ApprovalActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Widens the enum check on {@code approval_requests.action_type} to cover the current action
 * types. Hibernate emits that constraint when it first creates the table but never revisits it
 * under {@code ddl-auto=update}, so without this sweep the first suspend, reinstatement or
 * time-lock request would be rejected by Postgres the moment it is inserted. Dropping and
 * re-adding the constraint with the enum's own names keeps it in step with the code, on a fresh
 * database as well as on existing ones.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalActionTypeConstraintFixer implements CommandLineRunner {

    private static final String CONSTRAINT = "approval_requests_action_type_check";

    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        String allowed = Arrays.stream(ApprovalActionType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE approval_requests DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            statement.execute("ALTER TABLE approval_requests ADD CONSTRAINT " + CONSTRAINT
                    + " CHECK (action_type IN (" + allowed + "))");
            log.info("approval_requests.action_type constraint covers all {} action types",
                    ApprovalActionType.values().length);
        } catch (Exception exception) {
            // Loud, but not fatal: the API still serves every existing action type.
            log.error("Could not widen {}: {}", CONSTRAINT, exception.getMessage());
        }
    }
}

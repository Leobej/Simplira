package io.github.leobej.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Every integration test runs against a throwaway Postgres 18 with the real Flyway migrations
// applied, so no local database is required.
//
// The container is started once here and deliberately never stopped: JUnit's @Container would
// stop it after the first test class, and any later class sharing the same cached Spring context
// would then be pointing at a dead container. Ryuk removes it when the JVM exits.
@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }
}

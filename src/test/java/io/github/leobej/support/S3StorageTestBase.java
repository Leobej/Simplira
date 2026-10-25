package io.github.leobej.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

// For suites that store files: a real S3-compatible server, so uploads, signed links and deletes
// are the SDK's actual requests rather than a mock's idea of them. Every subclass shares this one
// container and - with identical properties - one Spring context.
public abstract class S3StorageTestBase extends PostgresTestBase {

    // Started once and never stopped, like the Postgres container, for the same reason.
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:5.2.3")
            .withExposedPorts(9090)
            .waitingFor(Wait.forListeningPort());

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("simplira.storage.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9090));
        registry.add("simplira.storage.region", () -> "us-east-1");
        registry.add("simplira.storage.access-key", () -> "test");
        registry.add("simplira.storage.secret-key", () -> "test");
        registry.add("simplira.storage.path-style", () -> "true");
        registry.add("simplira.storage.create-bucket-if-missing", () -> "true");
    }
}

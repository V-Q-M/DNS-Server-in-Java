package io.gitlab.teamproject2026;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.BindMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.containers.wait.strategy.*;
import java.time.*;
import java.time.temporal.*;

import com.github.dockerjava.api.command.InspectContainerResponse.Mount;

import io.gitlab.teamproject2026.cache.*;

public abstract class AbstractDNSCacheTests {

    static protected CacheConnectionProvider connectionProvider = null; 

    @Container
    public static PostgreSQLContainer postgres = new PostgreSQLContainer(
        DockerImageName.parse("postgres:18.4-alpine3.23")
    );

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    

    @BeforeEach
    void setUp(){
        connectionProvider = new CacheConnectionProvider(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
    }
}

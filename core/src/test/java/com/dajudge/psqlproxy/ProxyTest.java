/*
 * Copyright 2020 The psqlproxy developers (see CONTRIBUTORS)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.dajudge.psqlproxy;

import com.dajudge.proxybase.ProxyApplication;
import com.dajudge.proxybase.config.Endpoint;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BiConsumer;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.sql.DriverManager.getConnection;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.testcontainers.utility.MountableFile.forHostPath;

public class ProxyTest {
    private static final String DB_DATABASE = "testdb";
    private static final String DB_USERNAME = "ir0nm4n";
    private static final String DB_PASSWORD = "p3pp3rp0tt5";
    private static final TestCertificateAuthority CERTIFICATE_AUTHORITY = new TestCertificateAuthority("CN=test-ca");
    private static final TestCertificateAuthority.ServerKeyPair SERVER_KEY_PAIR = CERTIFICATE_AUTHORITY
            .newServerKeyPair("CN=localhost");
    private static final Path TEMP_DIR = createTempDir();
    private static final @NotNull File SERVER_KEY = writeTempFile(
            TEMP_DIR,
            "server.key",
            SERVER_KEY_PAIR.getPrivateKey()
    );
    private static final @NotNull File SERVER_CERT = writeTempFile(
            TEMP_DIR,
            "server.crt",
            SERVER_KEY_PAIR.getCertificate()
    );
    private static final String SERVER_CERT_PATH = "/var/lib/postgresql/server.crt";
    private static final String SERVER_KEY_PATH = "/var/lib/postgresql/server.key";
    private static final String MOUNT_SERVER_CERT_PATH = "/tmp/server.crt";
    private static final String MOUNT_SERVER_KEY_PATH = "/tmp/server.key";
    private static final @NotNull File ENTRYPOINT_FILE = writeTempFile(
            TEMP_DIR,
            "entrypoint.sh",
            entrypoint(
                    MOUNT_SERVER_KEY_PATH,
                    MOUNT_SERVER_CERT_PATH,
                    SERVER_KEY_PATH,
                    SERVER_CERT_PATH
            ).getBytes(US_ASCII)
    );
    private static final String ENTRYPOINT_PATH = "/test-entrypoint.sh";

    @Test
    public void can_connect_with_proxy_with_ssl() {
        withProxy(this::runJdbcTest, true);
    }

    @Test
    public void can_connect_with_proxy_without_ssl() {
        withProxy(this::runJdbcTest, false);
    }

    private void runJdbcTest(final String host, final Integer port) {
        try (final Connection conn = getConnection(format("jdbc:postgresql://%s:%s/%s", host, port, DB_DATABASE))) {
            assertEquals("PostgreSQL", conn.getMetaData().getDatabaseProductName());
        } catch (final SQLException t) {
            throw new AssertionError(t);
        }
    }

    private void withProxy(final BiConsumer<String, Integer> runnable, final boolean sslEnabled) {
        withPostgres((realHost, realPort) -> {
            try (final ProxyApplication proxy = new ProstgresProxy(
                    new Endpoint(realHost, realPort),
                    new Endpoint("localhost", 55432),
                    DB_USERNAME,
                    DB_PASSWORD
            )) {
                runnable.accept("localhost", 55432);
            }
        }, sslEnabled);
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "False positive" // see https://sourceforge.net/p/findbugs/bugs/1169/
    )
    private void withPostgres(final BiConsumer<String, Integer> runnable, final boolean sslEnabled) {
        try (final PostgreSQLContainer<?> c = createDatabaseContainer(sslEnabled)) {
            c.start();
            runnable.accept("localhost", c.getMappedPort(5432));
        }
    }

    @NotNull
    private PostgreSQLContainer<?> createDatabaseContainer(final boolean sslEnabled) {
        final PostgreSQLContainer<?> container = new PostgreSQLContainer<>()
                .withDatabaseName(DB_DATABASE)
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD);
        if (sslEnabled) {
            container.withCopyFileToContainer(forHostPath(ENTRYPOINT_FILE.getAbsolutePath()), ENTRYPOINT_PATH)
                    .withCopyFileToContainer(forHostPath(SERVER_CERT.getAbsolutePath()), MOUNT_SERVER_CERT_PATH)
                    .withCopyFileToContainer(forHostPath(SERVER_KEY.getAbsolutePath()), MOUNT_SERVER_KEY_PATH)
                    .withCreateContainerCmdModifier(cmd -> cmd
                            .withEntrypoint("sh", ENTRYPOINT_PATH)
                            .withCmd(
                                    "-l",
                                    "-c", "ssl_cert_file=" + SERVER_CERT_PATH,
                                    "-c", "ssl_key_file=" + SERVER_KEY_PATH
                            ));
        }
        return container;
    }

    private static String entrypoint(
            final String mountedKeyFile,
            final String mountedCertFile,
            final String targetKeyFile,
            final String targetCertFile
    ) {
        return join("\n", asList(
                "set -e",
                format("cp %s %s", mountedKeyFile, targetKeyFile),
                format("cp %s %s", mountedCertFile, targetCertFile),
                format("chmod 600 %s", targetKeyFile),
                format("chmod 600 %s", targetCertFile),
                format("chown postgres %s", targetKeyFile),
                format("chown postgres %s", targetCertFile),
                format("ls -la %s", targetKeyFile),
                format("ls -la %s", targetCertFile),
                "exec /docker-entrypoint.sh \"$@\""
        ));
    }

    @NotNull
    private static File writeTempFile(final Path tempDir, final String filename, final byte[] data) {
        try {
            final File cert = new File(tempDir.toFile(), filename);
            Files.write(cert.toPath(), data);
            return cert;
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write temp file", e);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("psqlproxy-test-");
        } catch (final IOException e) {
            throw new RuntimeException("Failed to create temporary directory");
        }
    }
}

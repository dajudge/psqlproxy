package com.dajudge.psqlproxy.testutil;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static java.util.Optional.empty;
import static org.testcontainers.utility.MountableFile.forHostPath;

public class PostgresContainerFactory {
    public static final String DB_DATABASE = "testdb";
    public static final String DB_USERNAME = "ir0nm4n";
    public static final String DB_PASSWORD = "p3pp3rp0tt5";
    private static final TestCertificateAuthority CERTIFICATE_AUTHORITY = new TestCertificateAuthority("CN=test-ca");
    private static final TestCertificateAuthority.ServerKeyPair SERVER_KEY_PAIR = CERTIFICATE_AUTHORITY
            .newServerKeyPair("CN=localhost");
    private static final Path TEMP_DIR = createTempDir();
    private static final File SERVER_KEY = writeTempFile(
            TEMP_DIR,
            "server.key",
            SERVER_KEY_PAIR.getPrivateKey()
    );
    private static final File SERVER_CERT = writeTempFile(
            TEMP_DIR,
            "server.crt",
            SERVER_KEY_PAIR.getCertificate()
    );
    private static final String SERVER_CERT_PATH = "/var/lib/postgresql/server.crt";
    private static final String SERVER_KEY_PATH = "/var/lib/postgresql/server.key";
    private static final String MOUNT_SERVER_CERT_PATH = "/tmp/server.crt";
    private static final String MOUNT_SERVER_KEY_PATH = "/tmp/server.key";
    private static final File ENTRYPOINT_FILE = writeTempFile(
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

    public static PostgreSQLContainer<?> createDatabaseContainer(final boolean sslEnabled) {
        return createDatabaseContainer(sslEnabled, empty());
    }

    public static PostgreSQLContainer<?> createDatabaseContainer(
            final boolean sslEnabled,
            final Optional<Network> network
    ) {
        final PostgreSQLContainer<?> container = new PostgreSQLContainer<>()
                .withDatabaseName(DB_DATABASE)
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD);

        network.ifPresent(container::setNetwork);

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

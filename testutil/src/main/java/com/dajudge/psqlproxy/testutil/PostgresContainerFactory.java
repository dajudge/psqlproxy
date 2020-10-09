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

package com.dajudge.psqlproxy.testutil;

import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
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
    private static final TestCertificateAuthority CERTIFICATE_AUTHORITY = new TestCertificateAuthority(
            "CN=test-ca",
            "pkcs12"
    );
    private static final String SERVER_CERT_PATH = "/var/lib/postgresql/server.crt";
    private static final String SERVER_KEY_PATH = "/var/lib/postgresql/server.key";
    private static final String MOUNT_SERVER_CERT_PATH = "/tmp/server.crt";
    private static final String MOUNT_SERVER_KEY_PATH = "/tmp/server.key";

    private final Path tempDir = createTempDir();
    private final File serverKey;
    private final File serverCert;
    private final File entrypointFile = writeTempFile(
            tempDir,
            "entrypoint.sh",
            entrypoint(
                    MOUNT_SERVER_KEY_PATH,
                    MOUNT_SERVER_CERT_PATH,
                    SERVER_KEY_PATH,
                    SERVER_CERT_PATH
            ).getBytes(US_ASCII)
    );
    private static final String ENTRYPOINT_PATH = "/test-entrypoint.sh";

    public PostgresContainerFactory(final String hostnameInCertificate) {
        final TestCertificateAuthority.ServerKeyPair serverKeypair = CERTIFICATE_AUTHORITY
                .newServerKeyPair("CN=" + hostnameInCertificate);
        serverKey = writeTempFile(tempDir, "server.key", serverKeypair.getPrivateKey());
        serverCert = writeTempFile(tempDir, "server.crt", serverKeypair.getCertificate());
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

    public PostgreSQLContainer<?> createDatabaseContainer(final boolean sslEnabled) {
        return createDatabaseContainer(sslEnabled, empty());
    }

    public PostgreSQLContainer<?> createDatabaseContainer(
            final boolean sslEnabled,
            final Optional<Network> network
    ) {
        final PostgreSQLContainer<?> container = new PostgreSQLContainer<>()
                .withDatabaseName(DB_DATABASE)
                .withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD);

        network.ifPresent(container::setNetwork);

        if (sslEnabled) {
            container.withCopyFileToContainer(forHostPath(entrypointFile.getAbsolutePath()), ENTRYPOINT_PATH)
                    .withCopyFileToContainer(forHostPath(serverCert.getAbsolutePath()), MOUNT_SERVER_CERT_PATH)
                    .withCopyFileToContainer(forHostPath(serverKey.getAbsolutePath()), MOUNT_SERVER_KEY_PATH)
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

    public byte[] getTrustStore(final String password) {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            CERTIFICATE_AUTHORITY.getTrustStore().store(bos, password.toCharArray());
            return bos.toByteArray();
        } catch (final KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }
}

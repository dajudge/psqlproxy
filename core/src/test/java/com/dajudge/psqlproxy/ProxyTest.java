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
import com.dajudge.proxybase.certs.Filesystem;
import com.dajudge.proxybase.certs.KeyStoreConfig;
import com.dajudge.proxybase.config.DownstreamSslConfig;
import com.dajudge.proxybase.config.Endpoint;
import com.dajudge.psqlproxy.protocol.PostgresSslConfig;
import com.dajudge.psqlproxy.testutil.PostgresContainerFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import static com.dajudge.psqlproxy.testutil.PostgresContainerFactory.*;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.sql.DriverManager.getConnection;
import static java.util.Optional.empty;
import static org.junit.Assert.assertEquals;

public class ProxyTest {
    @Test
    public void succeeds_with_ssl() {
        withProxy(this::expectSucceedingJdbcTest, "localhost", true, true, true);
    }

    @Test
    public void succeeds_without_optional_ssl() {
        withProxy(this::expectSucceedingJdbcTest, "irrelevant", false, false, true);
    }

    @Test
    public void fails_without_required_ssl() {
        withProxy(this::expectFailedJdbcTest, "irrelevant", false, true, true);
    }

    @Test
    public void fails_with_wrong_hostname_when_checked() {
        withProxy(this::expectFailedJdbcTest, "wrong", true, true, true);
    }

    @Test
    public void succeeds_with_wrong_hostname_when_unchecked() {
        withProxy(this::expectSucceedingJdbcTest, "wrong", true, true, false);
    }

    private void expectSucceedingJdbcTest(final Endpoint endpoint) {
        try (final Connection conn = createConnection(endpoint)) {
            assertEquals("PostgreSQL", conn.getMetaData().getDatabaseProductName());
        } catch (final SQLException t) {
            throw new AssertionError(t);
        }
    }

    @SuppressWarnings(value = "PMD.EmptyCatchBlock") // For the happy path
    private void expectFailedJdbcTest(final Endpoint endpoint) {
        try (final Connection conn = createConnection(endpoint)) {
            throw new AssertionError("Expected connection failure");
        } catch (final SQLException t) {
            // Happy path
        }
    }

    private void withProxy(
            final Consumer<Endpoint> runnable,
            final String sslHostname,
            final boolean enableServerSsl,
            final boolean sslRequired,
            final boolean checkHostname
    ) {
        withPostgres(sslHostname, postgres -> {
            final Endpoint proxyEndpoint = new Endpoint("localhost", 55432);
            try (final ProxyApplication proxy = createProxy(postgres, proxyEndpoint, sslRequired, checkHostname)) {
                runnable.accept(proxyEndpoint);
            }
        }, enableServerSsl);
    }

    @NotNull
    private PostgresProxy createProxy(
            final PostgresServer psql,
            final Endpoint proxyEndpoint,
            final boolean sslRequired,
            final boolean hostnameVerificationEnabled
    ) {
        final String truststorePassword = UUID.randomUUID().toString();
        final String truststoreLocation = UUID.randomUUID().toString();
        final String truststorePasswordLocation = UUID.randomUUID().toString();
        final KeyStoreConfig trustStore = new KeyStoreConfig(
                truststoreLocation,
                "".toCharArray(),
                truststorePasswordLocation,
                "".toCharArray(),
                null,
                "pkcs12",
                30000);
        final DownstreamSslConfig downstreamSslConfig = new DownstreamSslConfig(
                trustStore,
                empty(),
                hostnameVerificationEnabled
        );
        final Filesystem filesystem = new HashMap<String, byte[]>() {{
            put(truststoreLocation, psql.getTrustStore(truststorePassword));
            put(truststorePasswordLocation, truststorePassword.getBytes(UTF_8));
        }}::get;
        final PostgresSslConfig sslConfig = new PostgresSslConfig(
                sslRequired,
                downstreamSslConfig,
                psql.getEndpoint().getHost(),
                System::currentTimeMillis,
                filesystem
        );
        final PostgresProxy postgresProxy = new PostgresProxy(new PostgresProxyConfig(
                psql.getEndpoint(),
                proxyEndpoint,
                DB_USERNAME,
                DB_PASSWORD,
                sslConfig
        ));
        return postgresProxy;
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE",
            justification = "False positive" // see https://sourceforge.net/p/findbugs/bugs/1169/
    )
    private void withPostgres(
            final String hostnameInCertificate,
            final Consumer<PostgresServer> runnable,
            final boolean sslEnabled
    ) {
        final PostgresContainerFactory factory = new PostgresContainerFactory(hostnameInCertificate);
        try (final PostgreSQLContainer<?> c = factory.createDatabaseContainer(sslEnabled)) {
            c.start();
            final Endpoint endpoint = new Endpoint("localhost", c.getMappedPort(5432));
            runnable.accept(new PostgresServer() {
                @Override
                public Endpoint getEndpoint() {
                    return endpoint;
                }

                @Override
                public byte[] getTrustStore(final String password) {
                    return factory.getTrustStore(password);
                }
            });
        }
    }

    private Connection createConnection(final Endpoint endpoint) throws SQLException {
        return getConnection(format("jdbc:postgresql://%s:%s/%s", endpoint.getHost(), endpoint.getPort(), DB_DATABASE));
    }

    interface PostgresServer {
        Endpoint getEndpoint();

        byte[] getTrustStore(String password);
    }
}

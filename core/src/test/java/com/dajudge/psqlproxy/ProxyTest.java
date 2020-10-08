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
import com.dajudge.psqlproxy.testutil.PostgresContainerFactory;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BiConsumer;

import static com.dajudge.psqlproxy.testutil.PostgresContainerFactory.*;
import static java.lang.String.format;
import static java.sql.DriverManager.getConnection;
import static org.junit.Assert.assertEquals;

public class ProxyTest {
    @Test
    public void can_connect_with_proxy_with_ssl() {
        withProxy(this::runJdbcTest, true, true);
    }

    @Test
    public void can_connect_with_proxy_without_ssl() {
        withProxy(this::runJdbcTest, false, false);
    }

    @Test()
    public void fails_without_required_ssl() {
        withProxy(this::expectFailedJdbcTest, false, true);
    }

    private void runJdbcTest(final String host, final Integer port) {
        try (final Connection conn = createConnection(host, port)) {
            assertEquals("PostgreSQL", conn.getMetaData().getDatabaseProductName());
        } catch (final SQLException t) {
            throw new AssertionError(t);
        }
    }

    @SuppressWarnings(value = "PMD.EmptyCatchBlock") // For the happy path
    private void expectFailedJdbcTest(final String host, final Integer port) {
        try (final Connection conn = createConnection(host, port)) {
            throw new AssertionError("Expected connection failure");
        } catch (final SQLException t) {
            // Happy path
        }
    }

    private void withProxy(
            final BiConsumer<String, Integer> runnable,
            final boolean enableServerSsl,
            final boolean requireSsl
    ) {
        withPostgres((realHost, realPort) -> {
            try (final ProxyApplication proxy = new PostgresProxy(new PostgresProxyConfig(
                    new Endpoint(realHost, realPort),
                    new Endpoint("localhost", 55432),
                    PostgresContainerFactory.DB_USERNAME,
                    DB_PASSWORD,
                    requireSsl
            ))) {
                runnable.accept("localhost", 55432);
            }
        }, enableServerSsl);
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

    private Connection createConnection(final String host, final Integer port) throws SQLException {
        return getConnection(format("jdbc:postgresql://%s:%s/%s", host, port, DB_DATABASE));
    }
}

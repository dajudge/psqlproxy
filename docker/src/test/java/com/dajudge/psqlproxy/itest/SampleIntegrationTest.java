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

package com.dajudge.psqlproxy.itest;

import com.dajudge.proxybase.config.Endpoint;
import com.dajudge.psqlproxy.testutil.PostgresContainerFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static com.dajudge.psqlproxy.testutil.PostgresContainerFactory.*;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.sql.DriverManager.getConnection;
import static org.junit.Assert.assertEquals;
import static org.testcontainers.containers.Network.newNetwork;

public class SampleIntegrationTest {
    private static final String POSTGRES_SERVER_HOSTNAME = "psql-server";
    private static final Endpoint PROXY_ENDPOINT = new Endpoint("0.0.0.0", 40000);
    private static final Endpoint POSTGRES_ENDPOINT = new Endpoint(POSTGRES_SERVER_HOSTNAME, 5432);
    private static PostgreSQLContainer<?> postgres;
    private static ProxyContainer<?> proxy;

    @Test
    public void runTest() throws SQLException {
        try (final Connection c = createProxiedConnection()) {
            assertEquals("PostgreSQL", c.getMetaData().getDatabaseProductName());
        }
    }

    private Connection createProxiedConnection() throws SQLException {
        return createConnection("localhost", proxy.getMappedPort(PROXY_ENDPOINT.getPort()));
    }

    private Connection createConnection(final String host, final Integer port) throws SQLException {
        return getConnection(format("jdbc:postgresql://%s:%s/%s", host, port, DB_DATABASE));
    }

    @BeforeClass
    public static void createTestbed() {
        final Network network = newNetwork();
        final PostgresContainerFactory factory = new PostgresContainerFactory(POSTGRES_SERVER_HOSTNAME);
        postgres = factory.createDatabaseContainer(true, Optional.of(network))
                .withNetworkAliases(POSTGRES_SERVER_HOSTNAME);
        postgres.start();
        final String trustStorePassword = UUID.randomUUID().toString();
        proxy = new ProxyContainer<>(network, System.getProperty("psqlproxyImage"))
                .withPostgres(POSTGRES_ENDPOINT)
                .withCredentials(DB_USERNAME, DB_PASSWORD)
                .withBindAddress(PROXY_ENDPOINT)
                .withSslRequired(true)
                .withTrustStore("/truststore.p12", "/truststore.pwd");
        proxy.start();
        proxy.copyFileToContainer(Transferable.of(factory.getTrustStore(trustStorePassword)), "/truststore.p12");
        proxy.copyFileToContainer(Transferable.of(trustStorePassword.getBytes(UTF_8)), "/truststore.pwd");
    }

    @AfterClass
    public static void deleteTestbed() {
        proxy.close();
        postgres.close();
    }
}

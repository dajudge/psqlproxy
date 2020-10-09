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

import com.dajudge.proxybase.certs.KeyStoreConfig;
import com.dajudge.proxybase.config.DownstreamSslConfig;
import com.dajudge.proxybase.config.Endpoint;
import com.dajudge.psqlproxy.protocol.PostgresSslConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import static com.dajudge.proxybase.certs.Filesystem.DEFAULT_FILESYSTEM;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.Integer.parseUnsignedInt;
import static java.util.Optional.empty;

@ApplicationScoped
public class Startup {
    private static final String PREFIX = "PSQLPROXY_";
    private static final String ENV_POSTGRES_HOSTNAME = PREFIX + "POSTGRES_HOSTNAME";
    private static final String ENV_POSTGRES_PORT = PREFIX + "POSTGRES_PORT";
    private static final String ENV_BIND_ADDRESS = PREFIX + "BIND_ADDRESS";
    private static final String ENV_BIND_PORT = PREFIX + "BIND_PORT";
    private static final String ENV_USERNAME = PREFIX + "USERNAME";
    private static final String ENV_PASSWORD = PREFIX + "PASSWORD";
    private static final String ENV_REQUIRE_SSL = PREFIX + "REQUIRE_SSL";
    private static final String ENV_VERIFY_HOSTNAME = PREFIX + "VERIFY_HOSTNAME";
    private static final String ENV_TRUSTSTORE_LOCATION = PREFIX + "TRUSTSTORE_LOCATION";
    private static final String ENV_TRUSTSTORE_PASSWORD_LOCATION = PREFIX + "TRUSTSTORE_PASSWORD_LOCATION";
    private static final String ENV_TRUSTSTORE_UPDATE_INTERVAL_SECS = PREFIX + "TRUSTSTURE_UPDATE_INTERVAL_SECS";
    private static final int MSECS_PER_SEC = 1000;
    private PostgresProxy app;

    void onStart(@Observes StartupEvent ev) {
        final String postgresHost = requireEnv(ENV_POSTGRES_HOSTNAME);
        final int postgresPort = parseUnsignedInt(requireEnv(ENV_POSTGRES_PORT));
        final String proxyHost = requireEnv(ENV_BIND_ADDRESS);
        final int proxyPort = parseUnsignedInt(requireEnv(ENV_BIND_PORT));
        final String username = requireEnv(ENV_USERNAME);
        final String password = requireEnv(ENV_PASSWORD);
        final boolean requireSsl = parseBoolean(requireEnv(ENV_REQUIRE_SSL));
        final boolean verifyHostname = parseBoolean(optionalEnv(ENV_VERIFY_HOSTNAME, "true"));
        final String trustStoreLocation = requireEnv(ENV_TRUSTSTORE_LOCATION);
        final String trustStorePasswordLocation = requireEnv(ENV_TRUSTSTORE_PASSWORD_LOCATION);
        final int updateIntervalSecs = parseInt(optionalEnv(ENV_TRUSTSTORE_UPDATE_INTERVAL_SECS, "30"));
        final KeyStoreConfig keystoreConfig = new KeyStoreConfig(
                trustStoreLocation,
                "".toCharArray(),
                trustStorePasswordLocation,
                "".toCharArray(),
                null,
                "pkcs12",
                updateIntervalSecs * MSECS_PER_SEC
        );
        final DownstreamSslConfig downstreamSslConfig = new DownstreamSslConfig(
                keystoreConfig,
                empty(),
                verifyHostname
        );
        final PostgresSslConfig postgresSslConfig = new PostgresSslConfig(
                requireSsl,
                downstreamSslConfig,
                postgresHost,
                System::currentTimeMillis,
                DEFAULT_FILESYSTEM
        );
        app = new PostgresProxy(new PostgresProxyConfig(
                new Endpoint(postgresHost, postgresPort),
                new Endpoint(proxyHost, proxyPort),
                username,
                password,
                postgresSslConfig
        ));
    }

    private String optionalEnv(final String var, final String def) {
        final String value = System.getenv(var);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        return value;
    }

    private String requireEnv(final String var) {
        final String value = System.getenv(var);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required environment variable: " + var);
        }
        return value;
    }

    void onStop(@Observes ShutdownEvent ev) {
        app.close();
    }
}

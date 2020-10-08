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

import com.dajudge.proxybase.config.Endpoint;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseUnsignedInt;

@ApplicationScoped
public class Startup {
    public static final String ENV_PSQLPROXY_POSTGRES_HOSTNAME = "PSQLPROXY_POSTGRES_HOSTNAME";
    public static final String ENV_PSQLPROXY_POSTGRES_PORT = "PSQLPROXY_POSTGRES_PORT";
    public static final String ENV_PSQLPROXY_BIND_ADDRESS = "PSQLPROXY_BIND_ADDRESS";
    public static final String ENV_PSQLPROXY_BIND_PORT = "PSQLPROXY_BIND_PORT";
    public static final String ENV_PSQLPROXY_USERNAME = "PSQLPROXY_USERNAME";
    public static final String ENV_PSQLPROXY_PASSWORD = "PSQLPROXY_PASSWORD";
    public static final String ENV_PSQLPROXY_REQUIRE_SSL = "PSQLPROXY_REQUIRE_SSL";
    private PostgresProxy app;

    void onStart(@Observes StartupEvent ev) {
        final String postgresHost = requireEnv(ENV_PSQLPROXY_POSTGRES_HOSTNAME);
        final int postgresPort = parseUnsignedInt(requireEnv(ENV_PSQLPROXY_POSTGRES_PORT));
        final String proxyHost = requireEnv(ENV_PSQLPROXY_BIND_ADDRESS);
        final int proxyPort = parseUnsignedInt(requireEnv(ENV_PSQLPROXY_BIND_PORT));
        final String username = requireEnv(ENV_PSQLPROXY_USERNAME);
        final String password = requireEnv(ENV_PSQLPROXY_PASSWORD);
        final boolean requireSsl = parseBoolean(requireEnv(ENV_PSQLPROXY_REQUIRE_SSL));
        app = new PostgresProxy(new PostgresProxyConfig(
                new Endpoint(postgresHost, postgresPort),
                new Endpoint(proxyHost, proxyPort),
                username,
                password,
                requireSsl
        ));
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

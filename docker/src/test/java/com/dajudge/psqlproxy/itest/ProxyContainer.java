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
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import static java.lang.String.valueOf;

public class ProxyContainer<T extends ProxyContainer<T>> extends GenericContainer<T> {
    public ProxyContainer(final Network network, final String dockerImageName) {
        super(dockerImageName);
        this.withNetwork(network)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(ProxyContainer.class)))
                .withImagePullPolicy(imageName -> false)
                .withEnv("PSQLPROXY_LOG_LEVEL", "DEBUG");
    }

    public ProxyContainer<T> withSslRequired(final boolean sslRequired) {
        return this.withEnv("PSQLPROXY_REQUIRE_SSL", String.valueOf(sslRequired));
    }

    public ProxyContainer<T> withPostgres(final Endpoint endpoint) {
        return this.withEnv("PSQLPROXY_POSTGRES_HOSTNAME", endpoint.getHost())
                .withEnv("PSQLPROXY_POSTGRES_PORT", valueOf(endpoint.getPort()));
    }

    public ProxyContainer<T> withCredentials(final String username, final String password) {
        return this.withEnv("PSQLPROXY_USERNAME", username)
                .withEnv("PSQLPROXY_PASSWORD", password);
    }

    public ProxyContainer<T> withBindAddress(final Endpoint endpoint) {
        return this.withEnv("PSQLPROXY_BIND_PORT", valueOf(endpoint.getPort()))
                .withEnv("PSQLPROXY_BIND_ADDRESS", endpoint.getHost())
                .withExposedPorts(endpoint.getPort(), 8080);
    }

    public T withTrustStore(final String trustStoreLocation, final String trustStorePasswordLocation) {
        return this.withEnv("PSQLPROXY_TRUSTSTORE_LOCATION", trustStoreLocation)
                .withEnv("PSQLPROXY_TRUSTSTORE_PASSWORD_LOCATION", trustStorePasswordLocation);
    }
}

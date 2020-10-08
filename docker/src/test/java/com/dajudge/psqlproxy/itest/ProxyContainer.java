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

import com.dajudge.psqlproxy.PostgresProxyConfig;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import static java.lang.String.valueOf;

public class ProxyContainer<T extends ProxyContainer<T>> extends GenericContainer<T> {
    public ProxyContainer(final Network network, final String dockerImageName, final PostgresProxyConfig config) {
        super(dockerImageName);
        this.withNetwork(network)
                .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(ProxyContainer.class)))
                .withExposedPorts(config.getProxyEndpoint().getPort(), 8080)
                .withImagePullPolicy(imageName -> false)
                .withEnv("PSQLPROXY_POSTGRES_HOSTNAME", config.getServerEndpoint().getHost())
                .withEnv("PSQLPROXY_POSTGRES_PORT", valueOf(config.getServerEndpoint().getPort()))
                .withEnv("PSQLPROXY_BIND_ADDRESS", config.getProxyEndpoint().getHost())
                .withEnv("PSQLPROXY_BIND_PORT", valueOf(config.getProxyEndpoint().getPort()))
                .withEnv("PSQLPROXY_USERNAME", config.getUsername())
                .withEnv("PSQLPROXY_PASSWORD", config.getPassword())
                .withEnv("PSQLPROXY_REQUIRE_SSL", String.valueOf(config.isSslRequired()))
                .withEnv("PSQLPROXY_LOG_LEVEL", "DEBUG");
    }
}

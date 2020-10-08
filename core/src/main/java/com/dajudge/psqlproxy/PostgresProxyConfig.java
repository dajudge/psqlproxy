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

public class PostgresProxyConfig {
    private final Endpoint serverEndpoint;
    private final Endpoint proxyEndpoint;
    private final String username;
    private final String password;
    private final boolean sslRequired;

    public PostgresProxyConfig(
            final Endpoint serverEndpoint,
            final Endpoint proxyEndpoint,
            final String username,
            final String password,
            final boolean sslRequired
    ) {
        this.serverEndpoint = serverEndpoint;
        this.proxyEndpoint = proxyEndpoint;
        this.username = username;
        this.password = password;
        this.sslRequired = sslRequired;
    }

    public Endpoint getServerEndpoint() {
        return serverEndpoint;
    }

    public Endpoint getProxyEndpoint() {
        return proxyEndpoint;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSslRequired() {
        return sslRequired;
    }
}

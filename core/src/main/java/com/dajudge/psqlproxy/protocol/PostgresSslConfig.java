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

package com.dajudge.psqlproxy.protocol;

import com.dajudge.proxybase.certs.Filesystem;
import com.dajudge.proxybase.config.DownstreamSslConfig;

import java.util.function.Supplier;

public class PostgresSslConfig {
    private final boolean sslRequired;
    private final DownstreamSslConfig config;
    private final String downstreamHostname;
    private final Supplier<Long> clock;
    private final Filesystem filesystem;

    public PostgresSslConfig(
            final boolean sslRequired,
            final DownstreamSslConfig config,
            final String downstreamHostname,
            final Supplier<Long> clock,
            final Filesystem filesystem
    ) {
        this.sslRequired = sslRequired;
        this.config = config;
        this.downstreamHostname = downstreamHostname;
        this.clock = clock;
        this.filesystem = filesystem;
    }

    public boolean isSslRequired() {
        return sslRequired;
    }

    public DownstreamSslConfig getConfig() {
        return config;
    }

    public String getDownstreamHostname() {
        return downstreamHostname;
    }

    public Supplier<Long> getClock() {
        return clock;
    }

    public Filesystem getFilesystem() {
        return filesystem;
    }
}

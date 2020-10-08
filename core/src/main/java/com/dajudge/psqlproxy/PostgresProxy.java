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
import com.dajudge.proxybase.ProxyChannelFactory;
import com.dajudge.proxybase.ProxyChannelFactory.ProxyChannelInitializer;
import com.dajudge.proxybase.RelayingChannelInboundHandler;
import com.dajudge.proxybase.config.Endpoint;
import com.dajudge.psqlproxy.protocol.*;
import com.dajudge.psqlproxy.protocol.TypedFrameHandler.ContinueFrameProcessor;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.function.Consumer;

public class PostgresProxy extends ProxyApplication {
    public PostgresProxy(final PostgresProxyConfig config) {
        super(createProxyChannel(
                config.getServerEndpoint(),
                config.getProxyEndpoint(),
                config.getUsername(),
                config.getPassword(),
                config.isSslRequired()
        ));
    }

    private static Consumer<ProxyChannelFactory> createProxyChannel(
            final Endpoint serverEndpoint,
            final Endpoint proxyEndpoint,
            final String username,
            final String password,
            final boolean requireSsl
    ) {
        final ProxyChannelInitializer initializer = (upstreamChannel, downstreamChannel) -> {
            configureUpstream(username, upstreamChannel, downstreamChannel);
            configureDownstream(username, password, upstreamChannel, downstreamChannel, requireSsl);
        };
        return factory -> factory.createProxyChannel(
                proxyEndpoint,
                serverEndpoint,
                initializer
        );
    }

    private static void configureUpstream(
            final String username,
            final Channel upstreamChannel,
            final Channel downstreamChannel
    ) {
        final ContinueFrameProcessor finalProcessor = new ContinueFrameProcessor();
        upstreamChannel.pipeline().addLast(new UpstreamSslRejectHandler());
        upstreamChannel.pipeline().addLast(new UpstreamStartupHandler(username, new TypedFrameHandler(finalProcessor)));
        upstreamChannel.pipeline().addLast(forwardTo("downstream", downstreamChannel));
    }

    private static void configureDownstream(
            final String username,
            final String password,
            final Channel upstreamChannel,
            final Channel downstreamChannel,
            final boolean requireSsl
    ) {
        final ContinueFrameProcessor finalProcessor = new ContinueFrameProcessor();
        final DownstreamStartupProcessor downstreamStartupProcessor = new DownstreamStartupProcessor(
                username,
                password,
                finalProcessor
        );
        downstreamChannel.pipeline().addLast(new TypedFrameHandler(downstreamStartupProcessor));
        downstreamChannel.pipeline().addLast(forwardTo("upstream", upstreamChannel));
        downstreamChannel.pipeline().addFirst(new DownstreamSslActivationHandler(requireSsl));
    }

    private static ChannelInboundHandlerAdapter forwardTo(final String direction, final Channel fwd) {
        return new RelayingChannelInboundHandler(direction, fwd);
    }

}

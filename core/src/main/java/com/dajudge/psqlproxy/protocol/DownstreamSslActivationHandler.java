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

import com.dajudge.psqlproxy.protocol.exception.ProtocolErrorException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class DownstreamSslActivationHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DownstreamSslActivationHandler.class);
    private final List<Object> messageBuffer = new ArrayList<>();

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        if (messageBuffer.isEmpty()) {
            LOG.info("Requesting SSL communication with server");
            final ByteBuf buffer = Unpooled.buffer(8);
            buffer.writeInt(8);
            buffer.writeShort(1234);
            buffer.writeShort(5679);
            ctx.writeAndFlush(buffer, promise);
        }
        LOG.info("Buffering message: {}", msg);
        messageBuffer.add(msg);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        final ByteBuf buffer = (ByteBuf) msg;
        final char type = (char) buffer.readByte();
        LOG.info("Received server response: {}", type);
        switch (type) {
            case 'E':
                throw new RuntimeException("Server error");
            case 'N':
                LOG.info("Server denied SSL");
                ctx.pipeline().remove(this);
                break;
            case 'S':
                LOG.info("Server accepted SSL");
                ctx.pipeline().replace(this, "SSL", createSslHandler());
                break;
            default:
                throw new ProtocolErrorException("Unhandled server response type: " + type);
        }
        LOG.info("Flushing message buffer");
        messageBuffer.forEach(ctx::writeAndFlush);
    }

    @Override
    public void deregister(final ChannelHandlerContext ctx, final ChannelPromise promise) throws Exception {
        messageBuffer.forEach(m -> {
            if (m instanceof ByteBuf) {
                ((ByteBuf) m).release();
            }
        });
    }

    private ChannelHandler createSslHandler() {
        try {
            final SSLContext clientContext = SSLContext.getInstance("TLS");
            clientContext.init(null, new TrustManager[]{TrustAllTrustManager.INSTANCE}, null);
            final SSLEngine engine = clientContext.createSSLEngine();
            engine.setUseClientMode(true);
            return new SslHandler(engine);
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private enum TrustAllTrustManager implements X509TrustManager {

        INSTANCE;

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}

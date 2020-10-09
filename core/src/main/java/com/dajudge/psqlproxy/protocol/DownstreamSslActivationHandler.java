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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.dajudge.proxybase.DownstreamSslHandlerFactory.createDownstreamSslHandler;

public class DownstreamSslActivationHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DownstreamSslActivationHandler.class);
    private final List<Object> messageBuffer = new ArrayList<>();
    private final PostgresSslConfig sslConfig;

    public DownstreamSslActivationHandler(final PostgresSslConfig sslConfig) {
        this.sslConfig = sslConfig;
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
        if (messageBuffer.isEmpty()) {
            LOG.trace("Requesting SSL communication with server");
            final ByteBuf buffer = Unpooled.buffer(8);
            buffer.writeInt(8);
            buffer.writeShort(1234);
            buffer.writeShort(5679);
            ctx.writeAndFlush(buffer, promise);
        }
        LOG.trace("Buffering message: {}", msg);
        messageBuffer.add(msg);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        final ByteBuf buffer = (ByteBuf) msg;
        final char type = (char) buffer.readByte();
        LOG.trace("Received server response: {}", type);
        switch (type) {
            case 'E':
                throw new ProtocolErrorException("Server error");
            case 'N':
                if (sslConfig.isSslRequired()) {
                    LOG.warn("Server denied required SSL, terminating connection");
                    ctx.close();
                } else {
                    LOG.debug("Server denied optional SSL, continuing in plaintext");
                    ctx.pipeline().remove(this);
                }
                break;
            case 'S':
                LOG.debug("Server accepted SSL");
                final ChannelHandler downstreamSslHandler = createDownstreamSslHandler(
                        sslConfig.getConfig(),
                        sslConfig.getDownstreamHostname(),
                        sslConfig.getClock(),
                        sslConfig.getFilesystem()
                );
                ctx.pipeline().replace(this, "SSL", downstreamSslHandler);
                break;
            default:
                throw new ProtocolErrorException("Unhandled server response type: " + type);
        }
        LOG.trace("Flushing message buffer");
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
}

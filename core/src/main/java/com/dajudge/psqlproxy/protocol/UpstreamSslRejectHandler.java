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

import com.dajudge.proxybase.AbstractSingleChunkedMessageInboundHandler;
import com.dajudge.psqlproxy.protocol.frames.UntypedFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpstreamSslRejectHandler extends AbstractSingleChunkedMessageInboundHandler<UntypedFrame> {
    private static final Logger LOG = LoggerFactory.getLogger(UpstreamSslRejectHandler.class);
    private static final int SSL_REQUEST_LENGTH = 4;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    @Override
    protected void onMessageComplete(final ChannelHandlerContext ctx, final UntypedFrame message) {
        final ByteBuf payload = message.getChunks().get(1);
        if (payload.readableBytes() != SSL_REQUEST_LENGTH) {
            ctx.fireChannelRead(message.all());
            return;
        }
        final short major = payload.readShort();
        final short minor = payload.readShort();
        if (isSslRequest(major, minor)) {
            LOG.debug("Rejecting client's SSL request");
            final ByteBuf buffer = Unpooled.buffer(1);
            buffer.writeByte('N');
            ctx.writeAndFlush(buffer);
            message.release();
        } else {
            payload.resetReaderIndex();
            ctx.fireChannelRead(message.all());
        }
    }

    private boolean isSslRequest(final short major, final short minor) {
        // https://github.com/pgjdbc/pgjdbc/blob/f3abb4eb19357ac353d4a1e59d2920135619ad9a/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L411
        return major == 1234 && minor == 5679;
    }

    @Override
    protected UntypedFrame createNewMessage() {
        return new UntypedFrame();
    }
}

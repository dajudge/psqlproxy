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

import com.dajudge.psqlproxy.protocol.TypedFrameHandler.FrameProcessor;
import com.dajudge.psqlproxy.protocol.exception.ProtocolErrorException;
import com.dajudge.psqlproxy.protocol.exception.ServerErrorException;
import com.dajudge.psqlproxy.protocol.frames.TypedFrame;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.postgresql.util.MD5Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.buffer.Unpooled.buffer;
import static java.nio.charset.StandardCharsets.UTF_8;

public class DownstreamStartupProcessor implements FrameProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(DownstreamStartupProcessor.class);
    private final FrameProcessor next;
    private final String username;
    private final String password;

    // https://github.com/pgjdbc/pgjdbc/blob/f3abb4eb19357ac353d4a1e59d2920135619ad9a/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L58
    private static final int AUTH_REQ_OK = 0;
    private static final int AUTH_REQ_MD5 = 5;

    public DownstreamStartupProcessor(final String username, final String password, final FrameProcessor next) {
        this.username = username;
        this.password = password;
        this.next = next;
    }

    @Override
    public FrameProcessor process(final ChannelHandlerContext ctx, final TypedFrame message) {
        // https://github.com/pgjdbc/pgjdbc/blob/f3abb4eb19357ac353d4a1e59d2920135619ad9a/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L508
        switch (message.getType()) {
            case 'E':
                return handleErrorResponse(ctx, message);
            case 'R':
                return handleAuthenticationRequest(ctx, message);
            default:
                throw new ProtocolErrorException("Unhandled startup response type: " + message.getType());
        }
    }

    private FrameProcessor handleErrorResponse(final ChannelHandlerContext ctx, final TypedFrame message) {
        final ByteBuf payload = message.getChunks().get(1);
        try {
            final byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes, 0, bytes.length);
            throw new ServerErrorException(new String(bytes, UTF_8));
        } finally {
            payload.resetReaderIndex();
        }
    }

    private FrameProcessor handleAuthenticationRequest(
            final ChannelHandlerContext ctx,
            final TypedFrame message
    ) {
        final ByteBuf payload = message.getChunks().get(1);
        final int requestType = payload.readInt();
        switch (requestType) {
            case AUTH_REQ_MD5:
                handleMd5(ctx, payload);
                message.release();
                return this;
            case AUTH_REQ_OK:
                LOG.info("Authentication successful");
                payload.resetReaderIndex();
                ctx.fireChannelRead(message.all());
                return next;
            default:
                throw new ProtocolErrorException("Unhandled authentication type requested: " + requestType);
        }
    }

    private FrameProcessor handleMd5(final ChannelHandlerContext ctx, final ByteBuf payload) {
        LOG.info("MD5 authentication requested");
        final byte[] salt = new byte[4];
        payload.readBytes(salt, 0, salt.length);
        byte[] digest = MD5Digest.encode(username.getBytes(UTF_8), password.getBytes(UTF_8), salt);
        final ByteBuf authMessage = buffer(digest.length + 1);
        authMessage.writeBytes(digest, 0, digest.length);
        authMessage.writeByte(0);
        authMessage.resetReaderIndex();
        ctx.writeAndFlush(new TypedFrame('p', authMessage).all());
        return this;
    }
}

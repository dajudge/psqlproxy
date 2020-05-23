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

import com.dajudge.proxybase.AbstractChunkedMessageStreamInboundHandler;
import com.dajudge.psqlproxy.protocol.frames.TypedFrame;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypedFrameHandler extends AbstractChunkedMessageStreamInboundHandler<TypedFrame> {
    private static final Logger LOG = LoggerFactory.getLogger(TypedFrameHandler.class);
    private FrameProcessor frameProcessor;

    public TypedFrameHandler(final FrameProcessor initialFrameProcessor) {
        this.frameProcessor = initialFrameProcessor;
    }

    @Override
    protected void onMessageComplete(final ChannelHandlerContext ctx, final TypedFrame message) {
        LOG.info("Typed frame '{}': {} bytes", message.getType(), message.getChunks().get(1).readableBytes());
        frameProcessor = frameProcessor.process(ctx, message);
    }

    @Override
    protected TypedFrame createNewMessage() {
        return new TypedFrame();
    }

    public interface FrameProcessor {
        FrameProcessor process(ChannelHandlerContext ctx, TypedFrame message);
    }

    public static class ContinueFrameProcessor implements FrameProcessor {
        @Override
        public FrameProcessor process(final ChannelHandlerContext ctx, final TypedFrame message) {
            ctx.fireChannelRead(message.all());
            return this;
        }
    }
}

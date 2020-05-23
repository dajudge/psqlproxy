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
import com.dajudge.psqlproxy.protocol.messages.StartupMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayList;
import java.util.List;

public class UpstreamStartupHandler extends AbstractSingleChunkedMessageInboundHandler<UntypedFrame> {
    private final String username;

    public UpstreamStartupHandler(final String username, final ChannelHandler nextHandler) {
        super(nextHandler);
        this.username = username;
    }

    @Override
    protected void onMessageComplete(
            final ChannelHandlerContext ctx,
            final UntypedFrame message
    ) {
        // https://github.com/pgjdbc/pgjdbc/blob/f3abb4eb19357ac353d4a1e59d2920135619ad9a/pgjdbc/src/main/java/org/postgresql/core/v3/ConnectionFactoryImpl.java#L455
        final ByteBuf payload = message.getChunks().get(1);
        final StartupMessage parsedMessage = new StartupMessage(payload);
        final List<String> newParams = new ArrayList<>();
        final List<String> oldParams = parsedMessage.getParams();
        for (int i = 0; i < oldParams.size(); i++) {
            if (oldParams.get(i).equals("user")) {
                i++;
            } else {
                newParams.add(oldParams.get(i));
            }
        }
        newParams.add("user");
        newParams.add(username);
        message.release();
        final StartupMessage newStartupMessage = new StartupMessage(
                parsedMessage.getMajorVersion(),
                parsedMessage.getMinorVersion(),
                newParams
        );
        final UntypedFrame newFrame = new UntypedFrame(newStartupMessage.serialize());
        ctx.fireChannelRead(newFrame.all());
    }

    @Override
    protected UntypedFrame createNewMessage() {
        return new UntypedFrame();
    }
}

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

package com.dajudge.psqlproxy.protocol.frames;

import com.dajudge.proxybase.AbstractChunkedMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.List;

import static java.util.Arrays.asList;

public class UntypedFrame extends AbstractChunkedMessage {
    private static final int SIZE_CHUNK_INDEX = 0;

    public UntypedFrame() {
        super(4);
    }

    public UntypedFrame(final ByteBuf payload) {
        super(asList(header(payload.readableBytes()), payload));
    }

    private static ByteBuf header(final int payloadBytes) {
        final ByteBuf header = Unpooled.buffer(4);
        header.writeInt(payloadBytes + 4);
        return header;
    }

    @Override
    protected int nextChunkSize(final List<ByteBuf> chunks) {
        if (messageComplete(chunks)) {
            return NO_MORE_CHUNKS;
        }
        final ByteBuf sizeChunk = chunks.get(SIZE_CHUNK_INDEX);
        try {
            return (int) sizeChunk.readUnsignedInt() - 4;
        } finally {
            sizeChunk.resetReaderIndex();
        }
    }

    private boolean messageComplete(final List<ByteBuf> chunks) {
        return chunks.size() == 2;
    }
}

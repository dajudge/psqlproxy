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

package com.dajudge.psqlproxy.protocol.messages;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;

public class StartupMessage {
    private final short majorVersion;
    private final short minorVersion;
    private final List<String> params;

    public StartupMessage(final ByteBuf payload) {
        majorVersion = payload.readShort();
        minorVersion = payload.readShort();
        params = unmodifiableList(readParams(payload));
    }

    public StartupMessage(final short majorVersion, final short minorVersion, final List<String> params) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.params = unmodifiableList(params);
    }

    public ByteBuf serialize() {
        List<byte[]> paramsBytes = params.stream()
                .map(p -> p.getBytes(UTF_8))
                .collect(toList());
        final int paramsLength = 1 + params.size() + paramsBytes.stream().map(a -> a.length)
                .reduce(Integer::sum)
                .orElse(0);
        final ByteBuf buffer = Unpooled.buffer(4 + paramsLength);
        buffer.writeShort(majorVersion);
        buffer.writeShort(minorVersion);
        paramsBytes.forEach(b -> {
            buffer.writeBytes(b, 0, b.length);
            buffer.writeByte(0);
        });
        buffer.writeByte(0);
        return buffer;
    }

    @SuppressWarnings(value = "PMD.NullAssignment") // See comments below
    private static List<String> readParams(final ByteBuf payload) {
        final List<String> params = new ArrayList<>();
        ByteArrayOutputStream bos = null;
        while (true) {
            final byte b = payload.readByte();
            if (b == 0) {
                if (bos == null) {
                    // 0 char when no param currently being parsed flags end of params
                    break;
                }
                params.add(new String(bos.toByteArray(), UTF_8));
                bos = null; // No param currently being parsed
            } else {
                if (bos == null) {
                    // New param begins
                    bos = new ByteArrayOutputStream();
                }
                bos.write(b);
            }
        }
        return params;
    }

    public short getMajorVersion() {
        return majorVersion;
    }

    public short getMinorVersion() {
        return minorVersion;
    }

    public List<String> getParams() {
        return params;
    }

    @Override
    public String toString() {
        return "StartupMessage{" +
                "majorVersion=" + majorVersion +
                ", minorVersion=" + minorVersion +
                ", params=" + params +
                '}';
    }
}

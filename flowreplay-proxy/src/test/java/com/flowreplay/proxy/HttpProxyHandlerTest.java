package com.flowreplay.proxy;

import com.flowreplay.core.model.TrafficRecord;
import com.flowreplay.core.recorder.TrafficRecorder;
import com.sun.net.httpserver.HttpServer;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HttpProxyHandlerTest {

    private static HttpServer upstream;
    private static int upstreamPort;

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstreamPort = upstream.getAddress().getPort();

        upstream.createContext("/api/test", exchange -> {
            byte[] body = "hahaha".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @Test
    void forwardsOriginFormUriAndReturnsUpstreamBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpProxyHandler("localhost", upstreamPort, noopRecorder()));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "/api/test",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.HOST, "localhost:8081");

        channel.writeInbound(request);
        channel.runPendingTasks();
        channel.flushOutbound();

        Object outbound = channel.readOutbound();
        assertNotNull(outbound);
        assertInstanceOf(FullHttpResponse.class, outbound);

        FullHttpResponse response = (FullHttpResponse) outbound;
        assertEquals(200, response.status().code());
        assertEquals("hahaha", response.content().toString(StandardCharsets.UTF_8));

        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    void forwardsAbsoluteFormUriAndReturnsUpstreamBody() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpProxyHandler("localhost", upstreamPort, noopRecorder()));

        FullHttpRequest request = new DefaultFullHttpRequest(
            HttpVersion.HTTP_1_1,
            HttpMethod.GET,
            "http://localhost:8081/api/test?x=1",
            Unpooled.EMPTY_BUFFER
        );
        request.headers().set(HttpHeaderNames.HOST, "localhost:8081");

        channel.writeInbound(request);
        channel.runPendingTasks();
        channel.flushOutbound();

        Object outbound = channel.readOutbound();
        assertNotNull(outbound);
        assertInstanceOf(FullHttpResponse.class, outbound);

        FullHttpResponse response = (FullHttpResponse) outbound;
        assertEquals(200, response.status().code());
        assertEquals("hahaha", response.content().toString(StandardCharsets.UTF_8));

        response.release();
        channel.finishAndReleaseAll();
    }

    private static TrafficRecorder noopRecorder() {
        return new TrafficRecorder() {
            @Override
            public void record(TrafficRecord record) {
            }

            @Override
            public void close() {
            }
        };
    }
}

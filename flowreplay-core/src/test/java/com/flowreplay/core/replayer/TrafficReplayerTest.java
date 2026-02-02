package com.flowreplay.core.replayer;

import com.flowreplay.core.model.RequestData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TrafficReplayerTest {

    @Test
    void normalizesAbsoluteFormUriWhenBuildingReplayRequest() throws Exception {
        TrafficReplayer replayer = new TrafficReplayer("http://localhost:9090");
        RequestData requestData = new RequestData(
            "GET",
            "http://localhost:8081/api/test?x=1",
            Map.of(),
            null,
            Map.of()
        );

        HttpRequest request = invokeBuildHttpRequest(replayer, requestData);
        assertEquals(URI.create("http://localhost:9090/api/test?x=1"), request.uri());
    }

    @Test
    void keepsOriginFormUriWhenBuildingReplayRequest() throws Exception {
        TrafficReplayer replayer = new TrafficReplayer("http://localhost:9090");
        RequestData requestData = new RequestData(
            "POST",
            "/api/test",
            Map.of("Content-Type", "text/plain"),
            "hello".getBytes(StandardCharsets.UTF_8),
            Map.of()
        );

        HttpRequest request = invokeBuildHttpRequest(replayer, requestData);
        assertEquals(URI.create("http://localhost:9090/api/test"), request.uri());
        assertEquals("POST", request.method());
    }

    private static HttpRequest invokeBuildHttpRequest(TrafficReplayer replayer, RequestData requestData) throws Exception {
        Method m = TrafficReplayer.class.getDeclaredMethod("buildHttpRequest", RequestData.class);
        m.setAccessible(true);
        return (HttpRequest) m.invoke(replayer, requestData);
    }
}


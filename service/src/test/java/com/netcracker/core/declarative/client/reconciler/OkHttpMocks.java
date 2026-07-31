package com.netcracker.core.declarative.client.reconciler;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test helper that makes a mocked {@link OkHttpClient} return a canned HTTP response,
 * so reconcilers that issue okhttp calls directly can be unit-tested without a real server.
 */
final class OkHttpMocks {

    private OkHttpMocks() {
    }

    static void stub(OkHttpClient client, int code, String body) {
        Call call = mock(Call.class);
        try {
            when(call.execute()).thenReturn(response(code, body));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(client.newCall(any())).thenReturn(call);
    }

    static void stubThrow(OkHttpClient client, Throwable throwable) {
        Call call = mock(Call.class);
        try {
            when(call.execute()).thenThrow(throwable);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        when(client.newCall(any())).thenReturn(call);
    }

    static Response response(int code, String body) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://localhost").build())
                .protocol(Protocol.HTTP_1_1)
                .message("mock")
                .code(code)
                .body(ResponseBody.create(body == null ? "" : body, MediaType.parse("application/json")))
                .build();
    }
}

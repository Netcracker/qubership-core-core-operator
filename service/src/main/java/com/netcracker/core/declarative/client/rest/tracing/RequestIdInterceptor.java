package com.netcracker.core.declarative.client.rest.tracing;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.MDC;

import java.io.IOException;

import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;

public class RequestIdInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String requestId = MDC.get(X_REQUEST_ID);
        if (requestId == null) {
            return chain.proceed(original);
        }
        Request withHeader = original.newBuilder()
                .header(X_REQUEST_ID, requestId)
                .build();
        return chain.proceed(withHeader);
    }
}

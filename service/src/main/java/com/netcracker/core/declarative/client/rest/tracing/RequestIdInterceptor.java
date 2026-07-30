package com.netcracker.core.declarative.client.rest.tracing;

import com.netcracker.cloud.headerstracking.filters.context.RequestIdContext;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;

public class RequestIdInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(RequestIdInterceptor.class);

    @Override
    public @NonNull Response intercept(Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();
        String requestId = RequestIdContext.get();
        if (requestId == null) {
            log.warn("No request id in context, sending request to url={} without {} header", chain.request().url(), X_REQUEST_ID);
        } else {
            builder.header(X_REQUEST_ID, requestId);
        }
        return chain.proceed(builder.build());
    }
}

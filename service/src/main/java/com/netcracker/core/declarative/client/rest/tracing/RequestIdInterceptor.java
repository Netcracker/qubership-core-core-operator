package com.netcracker.core.declarative.client.rest.tracing;

import com.netcracker.cloud.headerstracking.filters.context.RequestIdContext;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

import static com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID;

public class RequestIdInterceptor implements Interceptor {
    @Override
    public @NonNull Response intercept(Chain chain) throws IOException {
        Request withHeader = chain.request().newBuilder()
                .header(X_REQUEST_ID, RequestIdContext.get())
                .build();
        return chain.proceed(withHeader);
    }
}

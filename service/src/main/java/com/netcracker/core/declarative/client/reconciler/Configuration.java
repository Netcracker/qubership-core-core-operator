package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.cloud.quarkus.security.auth.M2MManager;
import com.netcracker.cloud.security.core.utils.k8s.M2MClientFactory;
import com.netcracker.core.declarative.client.rest.CompositeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeClient;
import com.netcracker.core.declarative.client.rest.DeclarativeRequest;
import com.netcracker.core.declarative.client.rest.DeclarativeResponse;
import com.netcracker.core.declarative.client.rest.deprecated.MeshClientV3;
import jakarta.ws.rs.core.Response;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import com.netcracker.core.declarative.service.*;
import io.quarkus.arc.DefaultBean;
import io.quarkus.restclient.runtime.QuarkusRestClientBuilder;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;

@Slf4j
public class Configuration {
    @Produces
    @Named("maasDeclarativeClient")
    @ApplicationScoped
    public DeclarativeClient maasDeclarativeClient(@ConfigProperty(name = "quarkus.rest-client.maas-client.url") String maasUrl, ObjectMapper objectMapper) {
        return buildDeclarativeClient(M2MClientFactory.getMaasOkHttpClient(() -> M2MManager.getInstance().getToken().getTokenValue()), maasUrl, objectMapper);
    }

    @Produces
    @Named("meshDeclarativeClient")
    @ApplicationScoped
    public MeshClientV3 meshDeclarativeClient(@ConfigProperty(name = "quarkus.rest-client.mesh-client-v3.url") String meshUrl, RestClientCustomizer restClientCustomizer) {
        return restClientCustomizer.customize(new QuarkusRestClientBuilder().baseUri(URI.create(meshUrl)))
                .build(MeshClientV3.class);
    }

    @Produces
    @Named("idpExtensionsDeclarativeClient")
    @ApplicationScoped
    public DeclarativeClient idpExtensionsDeclarativeClient(@ConfigProperty(name = "quarkus.rest-client.idp-extensions-client.url") String idpExtensionsUrl, RestClientCustomizer restClientCustomizer) {
        return createXaasDeclarativeClient(idpExtensionsUrl, restClientCustomizer);
    }

    @Produces
    @Named("keyManagerDeclarativeClient")
    @ApplicationScoped
    public DeclarativeClient keyManagerDeclarativeClient(@ConfigProperty(name = "quarkus.rest-client.key-manager-client.url") String keyManagerUrl, RestClientCustomizer restClientCustomizer) {
        return createXaasDeclarativeClient(keyManagerUrl, restClientCustomizer);
    }

    @Produces
    @Named("dbaasDeclarativeClient")
    @ApplicationScoped
    public DeclarativeClient dbaasDeclarativeClient(@ConfigProperty(name = "quarkus.rest-client.dbaas-client.url") String dbaasUrl, ObjectMapper objectMapper) {
        return buildDeclarativeClient(M2MClientFactory.getDbaasOkHttpClient(() -> M2MManager.getInstance().getToken().getTokenValue()), dbaasUrl, objectMapper);
    }

    @Produces
    @ApplicationScoped
    public List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifier(
            @ConfigProperty(name = "quarkus.rest-client.maas-client.url") String maasUrl,
            @ConfigProperty(name = "quarkus.rest-client.dbaas-client.url") String dbaasUrl,
            @ConfigProperty(name = "cloud.composite.structure.xaas.receivers") List<String> receiversConfig,
            @ConfigProperty(name = "cloud.composite.structure.xaas.read-timeout") Long readTimeout,
            @ConfigProperty(name = "cloud.composite.structure.xaas.connect-timeout") Long connectTimeout,
            ObjectMapper objectMapper,
            RestClientCustomizer restClientCustomizer
    ) {
        List<String> receiversConfigLowercase = receiversConfig.stream().map(String::toLowerCase).toList();
        return Map.of(
                        MAAS_NAME, maasUrl,
                        DBAAS_NAME, dbaasUrl
                )
                .entrySet()
                .stream()
                .filter(xaas -> receiversConfigLowercase.contains(xaas.getKey().toLowerCase()))
                .map(xaas -> {
                    CompositeClient client;
                    if (MAAS_NAME.equalsIgnoreCase(xaas.getKey())) {
                        OkHttpClient httpClient = M2MClientFactory.getMaasOkHttpClient(() -> M2MManager.getInstance().getToken().getTokenValue())
                                .newBuilder()
                                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                                .build();
                        client = buildCompositeClient(httpClient, xaas.getValue(), objectMapper);
                    } else if (DBAAS_NAME.equalsIgnoreCase(xaas.getKey())) {
                        OkHttpClient httpClient = M2MClientFactory.getDbaasOkHttpClient(() -> M2MManager.getInstance().getToken().getTokenValue())
                                .newBuilder()
                                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                                .build();
                        client = buildCompositeClient(httpClient, xaas.getValue(), objectMapper);
                    } else {
                        client = restClientCustomizer.customize(new QuarkusRestClientBuilder()
                                        .baseUri(URI.create(xaas.getValue()))
                                        .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                                        .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS))
                                .build(CompositeClient.class);
                    }
                    return new CompositeStructureUpdateNotifier(xaas.getKey(), client);
                })
                .toList();
    }

    @Produces
    @ApplicationScoped
    public CompositeConsulUpdater compositeConsulUpdater(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            @ConfigProperty(name = "quarkus.consul-source-config.agent.url") URL consulUrl,
            @ConfigProperty(name = "quarkus.consul-source-config.agent.enabled") boolean consulEnabled,
            @ConfigProperty(name = "cloud.composite.structure.consul.update-timeout") Long timeout,
            ConsulClientFactory consulClientFactory,
            Instance<TokenStorage> consulTokenStorage) { // TokenStorage in Singleton scope. Lazy inject.
        if (!consulEnabled) {
            return new NoopCompositeConsulUpdaterImpl();
        }
        return new CompositeConsulUpdaterImpl(namespace, consulClientFactory, consulTokenStorage.get());
    }

    @Produces
    @ApplicationScoped
    public ConsulClientFactory consulClientFactory(Vertx vertx,
                                                   @ConfigProperty(name = "quarkus.consul-source-config.agent.url") URL consulUrl,
                                                   @ConfigProperty(name = "cloud.composite.structure.consul.update-timeout") Long timeout) {
        return new ConsulClientFactory() {
            @Override
            public ConsulClient create(String token) {
                return ConsulClient.create(vertx.getDelegate(), new ConsulClientOptions()
                                .setHost(consulUrl.getHost())
                                .setPort(consulUrl.getPort())
                                .setTimeout(timeout)
                                .setAclToken(token)
                );
            }

            @Override
            public ConsulClient create(String token, long timeout) {
                return ConsulClient.create(vertx.getDelegate(), new ConsulClientOptions()
                                .setHost(consulUrl.getHost())
                                .setPort(consulUrl.getPort())
                                .setTimeout(timeout)
                                .setAclToken(token)
                );
            }
        };
    }

    @Produces
    @ApplicationScoped
    public TenantService tenantService(ConsulClientFactory consulClientFactory,
                                       Instance<TokenStorage> consulTokenStorage) {
        return new TenantService(consulClientFactory, consulTokenStorage.get());
    }

    @Produces
    @ApplicationScoped
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public RestClientCustomizer restClientCustomizer() {
        return builder -> builder;
    }

    public interface RestClientCustomizer {
        RestClientBuilder customize(RestClientBuilder builder);
    }

    private DeclarativeClient createXaasDeclarativeClient(String xaasUrl, RestClientCustomizer restClientCustomizer) {
        return restClientCustomizer.customize(new QuarkusRestClientBuilder()
                        .baseUri(URI.create(xaasUrl)))
                .build(DeclarativeClient.class);
    }

    private static final MediaType JSON = MediaType.get("application/json");

    private DeclarativeClient buildDeclarativeClient(OkHttpClient httpClient, String rawBaseUrl, ObjectMapper mapper) {
        String baseUrl = rawBaseUrl.replaceAll("/+$", "");
        return (DeclarativeClient) Proxy.newProxyInstance(
                DeclarativeClient.class.getClassLoader(),
                new Class<?>[]{DeclarativeClient.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "apply" -> {
                        String apiVersion = (String) args[0];
                        DeclarativeRequest request = (DeclarativeRequest) args[1];
                        okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                                .url(baseUrl + "/api/declarations/v" + apiVersion + "/apply")
                                .header(com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID, requestId())
                                .post(RequestBody.create(mapper.writeValueAsBytes(request), JSON))
                                .build();
                        yield executeDeclarative(httpClient, httpRequest, mapper);
                    }
                    case "getStatus" -> {
                        String apiVersion = (String) args[0];
                        String trackingId = (String) args[1];
                        okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                                .url(baseUrl + "/api/declarations/v" + apiVersion + "/operation/" + trackingId + "/status")
                                .header(com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID, requestId())
                                .get()
                                .build();
                        yield executeDeclarative(httpClient, httpRequest, mapper);
                    }
                    default -> throw new UnsupportedOperationException("Unknown method: " + method.getName());
                }
        );
    }

    private CompositeClient buildCompositeClient(OkHttpClient httpClient, String rawBaseUrl, ObjectMapper mapper) {
        String baseUrl = rawBaseUrl.replaceAll("/+$", "");
        return compositeRequest -> {
            try {
                okhttp3.Request httpRequest = new okhttp3.Request.Builder()
                        .url(baseUrl + "/api/composite/v1/structures")
                        .header(com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID, requestId())
                        .post(RequestBody.create(mapper.writeValueAsBytes(compositeRequest), JSON))
                        .build();
                try (okhttp3.Response okResponse = httpClient.newCall(httpRequest).execute()) {
                    byte[] bodyBytes = okResponse.body() != null ? okResponse.body().bytes() : new byte[0];
                    String bodyString = bodyBytes.length > 0 ? new String(bodyBytes) : null;
                    return Response.status(okResponse.code()).entity(bodyString).build();
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to call composite structures", e);
            }
        };
    }

    private static Response executeDeclarative(OkHttpClient httpClient, okhttp3.Request request, ObjectMapper mapper) throws Exception {
        try (okhttp3.Response okResponse = httpClient.newCall(request).execute()) {
            byte[] bodyBytes = okResponse.body() != null ? okResponse.body().bytes() : new byte[0];
            Object entity = null;
            if (bodyBytes.length > 0) {
                try {
                    entity = mapper.readValue(bodyBytes, DeclarativeResponse.class);
                } catch (Exception e) {
                    entity = new String(bodyBytes);
                }
            }
            return Response.status(okResponse.code()).entity(entity).build();
        }
    }

    private static String requestId() {
        String id = org.slf4j.MDC.get(com.netcracker.core.declarative.client.constants.Constants.X_REQUEST_ID);
        return id != null ? id : "";
    }
}

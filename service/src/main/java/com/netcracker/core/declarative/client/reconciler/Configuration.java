package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.cloud.consul.provider.common.TokenStorage;
import com.netcracker.cloud.quarkus.security.auth.M2MManager;
import com.netcracker.cloud.security.core.utils.k8s.M2MClientFactory;
import com.netcracker.core.declarative.client.rest.tracing.RequestIdInterceptor;
import com.netcracker.core.declarative.service.*;
import io.vertx.ext.consul.ConsulClient;
import io.vertx.ext.consul.ConsulClientOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;

@Slf4j
public class Configuration {
    @Produces
    @Named("maasHttpClient")
    @ApplicationScoped
    public OkHttpClient maasHttpClient() {
        return configure(M2MClientFactory.getMaasOkHttpClient(m2mToken()));
    }

    @Produces
    @Named("dbaasHttpClient")
    @ApplicationScoped
    public OkHttpClient dbaasHttpClient() {
        return configure(M2MClientFactory.getDbaasOkHttpClient(m2mToken()));
    }

    @Produces
    @Named("keyManagerHttpClient")
    @ApplicationScoped
    public OkHttpClient keyManagerHttpClient() {
        return configure(M2MClientFactory.getM2mOkHttpClient(m2mToken()));
    }

    @Produces
    @Named("idpExtensionsHttpClient")
    @ApplicationScoped
    public OkHttpClient idpExtensionsHttpClient() {
        return configure(M2MClientFactory.getM2mOkHttpClient(m2mToken()));
    }

    @Produces
    @Named("meshHttpClient")
    @ApplicationScoped
    public OkHttpClient meshHttpClient() {
        return configure(M2MClientFactory.getM2mOkHttpClient(m2mToken()));
    }

    private static Supplier<String> m2mToken() {
        return () -> M2MManager.getInstance().getToken().getTokenValue();
    }

    private static OkHttpClient configure(OkHttpClient base) {
        return base.newBuilder()
                .addInterceptor(new RequestIdInterceptor())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Produces
    @ApplicationScoped
    public List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifier(
            @ConfigProperty(name = "maas.internal.address") String maasUrl,
            @ConfigProperty(name = "api.dbaas.address") String dbaasUrl,
            @ConfigProperty(name = "cloud.composite.structure.xaas.receivers") List<String> receiversConfig,
            @ConfigProperty(name = "cloud.composite.structure.xaas.read-timeout") Long readTimeout,
            @ConfigProperty(name = "cloud.composite.structure.xaas.connect-timeout") Long connectTimeout,
            ObjectMapper objectMapper
    ) {
        List<String> receiversConfigLowercase = receiversConfig.stream().map(String::toLowerCase).toList();
        return Map.of(
                        MAAS_NAME, maasUrl,
                        DBAAS_NAME, dbaasUrl
                )
                .entrySet()
                .stream()
                // take only XaaSes enlisted in receivers config compare ignoring case
                .filter(xaas -> receiversConfigLowercase.contains(xaas.getKey().toLowerCase()))
                .map(xaas -> {
                    OkHttpClient baseClient = DBAAS_NAME.equalsIgnoreCase(xaas.getKey())
                            ? M2MClientFactory.getDbaasOkHttpClient(m2mToken())
                            : M2MClientFactory.getMaasOkHttpClient(m2mToken());

                    OkHttpClient okHttpClient = baseClient.newBuilder()
                            .addInterceptor(new RequestIdInterceptor())
                            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                            .build();

                    return new CompositeStructureUpdateNotifier(xaas.getKey(), okHttpClient, xaas.getValue(), objectMapper);
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
}

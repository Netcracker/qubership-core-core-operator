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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.DBAAS_NAME;
import static com.netcracker.core.declarative.client.reconciler.CompositeReconciler.MAAS_NAME;

@Slf4j
public class Configuration {
    private static final Map<String, Function<Supplier<String>, OkHttpClient>> XAAS_CLIENT_FACTORIES = Map.of(
            MAAS_NAME.toLowerCase(), M2MClientFactory::getMaasOkHttpClient,
            DBAAS_NAME.toLowerCase(), M2MClientFactory::getDbaasOkHttpClient);

    @ConfigProperty(name = "cloud.http-client.connect-timeout")
    Duration clientConnectTimeout;

    @ConfigProperty(name = "cloud.http-client.read-timeout")
    Duration clientReadTimeout;

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

    private OkHttpClient configure(OkHttpClient base) {
        return base.newBuilder()
                .addInterceptor(new RequestIdInterceptor())
                .connectTimeout(clientConnectTimeout)
                .readTimeout(clientReadTimeout)
                .build();
    }

    @Produces
    @ApplicationScoped
    public List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifier(
            @ConfigProperty(name = "cloud.composite.structure.xaas.address") Map<String, String> xaasAddresses,
            @ConfigProperty(name = "cloud.composite.structure.xaas.receivers") List<String> receiversConfig,
            @ConfigProperty(name = "cloud.composite.structure.xaas.read-timeout") Long readTimeout,
            @ConfigProperty(name = "cloud.composite.structure.xaas.connect-timeout") Long connectTimeout,
            ObjectMapper objectMapper
    ) {
        List<String> receiversConfigLowercase = receiversConfig.stream().map(String::toLowerCase).toList();
        return xaasAddresses.entrySet()
                .stream()
                // take only XaaSes enlisted in receivers config compare ignoring case
                .filter(xaas -> receiversConfigLowercase.contains(xaas.getKey().toLowerCase()))
                .map(xaas -> new CompositeStructureUpdateNotifier(
                        xaas.getKey(),
                        compositeStructureClient(xaas.getKey(), readTimeout, connectTimeout),
                        xaas.getValue(),
                        objectMapper))
                .toList();
    }

    /**
     * XaaSes reachable only through their own agent need a dedicated client, everything else talks plain m2m.
     */
    private static OkHttpClient compositeStructureClient(String xaasName, long readTimeout, long connectTimeout) {
        return XAAS_CLIENT_FACTORIES
                .getOrDefault(xaasName.toLowerCase(), M2MClientFactory::getM2mOkHttpClient)
                .apply(m2mToken())
                .newBuilder()
                .addInterceptor(new RequestIdInterceptor())
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .build();
    }

    @Produces
    @ApplicationScoped
    public CompositeConsulUpdater compositeConsulUpdater(
            @ConfigProperty(name = "cloud.microservice.namespace") String namespace,
            @ConfigProperty(name = "quarkus.consul-source-config.agent.enabled") boolean consulEnabled,
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

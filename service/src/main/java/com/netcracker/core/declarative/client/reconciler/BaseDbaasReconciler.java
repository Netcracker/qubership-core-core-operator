package com.netcracker.core.declarative.client.reconciler;

import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import com.netcracker.core.declarative.resources.dbaas.Dbaas;

public abstract class BaseDbaasReconciler<T extends Dbaas> extends PoolingReconciler<T> {

    public BaseDbaasReconciler(KubernetesClient client, OkHttpClient httpClient, String baseUrl) {
        super(client, httpClient, baseUrl);
    }

    protected BaseDbaasReconciler() {
    }
}

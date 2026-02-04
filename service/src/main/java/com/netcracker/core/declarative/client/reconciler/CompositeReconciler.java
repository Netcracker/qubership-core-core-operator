package com.netcracker.core.declarative.client.reconciler;

import com.netcracker.core.declarative.service.composite.CompositeStructureWatcher;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GradualRetry;
import jakarta.inject.Inject;
import com.netcracker.core.declarative.resources.composite.Composite;
import com.netcracker.core.declarative.service.CompositeConsulUpdater;
import com.netcracker.core.declarative.service.CompositeCRHolder;
import com.netcracker.core.declarative.service.CompositeStructureUpdateNotifier;

import java.util.List;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), name = "CompositeReconciler")
@GradualRetry(maxAttempts = -1)
public class CompositeReconciler extends BaseCompositeReconciler<Composite> {

    @Inject
    @SuppressWarnings("unused")
    public CompositeReconciler(
            KubernetesClient client,
            CompositeConsulUpdater compositeConsulUpdater,
            List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers,
            CompositeStructureWatcher compositeStructureWatcher,
            CompositeCRHolder compositeCRHolder
    ) {
        super(client, compositeConsulUpdater, compositeStructureUpdateNotifiers, compositeStructureWatcher, compositeCRHolder);
    }
}

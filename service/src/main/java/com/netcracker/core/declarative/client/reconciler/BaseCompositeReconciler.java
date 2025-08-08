package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.qubership.core.declarative.client.rest.Condition;
import org.qubership.core.declarative.client.rest.ProcessStatus;
import org.qubership.core.declarative.exception.NoopConsulException;
import org.qubership.core.declarative.resources.base.CoreCondition;
import org.qubership.core.declarative.resources.base.CoreResource;
import org.qubership.core.declarative.resources.base.Phase;
import org.qubership.core.declarative.resources.composite.Composite;
import org.qubership.core.declarative.service.CompositeConsulUpdater;
import org.qubership.core.declarative.service.CompositeSpec;
import org.qubership.core.declarative.service.CompositeStructureUpdateNotifier;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.qubership.core.declarative.client.constants.Constants.VALIDATED_STEP_NAME;

@Slf4j
public abstract class BaseCompositeReconciler<T extends Composite> extends CoreReconciler<T> {
    public static final String MAAS_NAME = "MaaS";
    public static final String DBAAS_NAME = "DBaaS";

    public static final String COMPOSITE_STRUCTURE_UPDATED_STEP_NAME = "CompositeStructureUpdated";
    public static final Function<String, String> XAAS_UPDATED_STEP_NAME = (String id) -> "%sUpdated".formatted(StringUtils.capitalize(id));

    private CompositeConsulUpdater compositeConsulUpdater;
    private List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers;

    public BaseCompositeReconciler(
            KubernetesClient client,
            CompositeConsulUpdater compositeConsulUpdater,
            List<CompositeStructureUpdateNotifier> compositeStructureUpdateNotifiers
    ) {
        super(client);
        this.compositeConsulUpdater = compositeConsulUpdater;
        this.compositeStructureUpdateNotifiers = compositeStructureUpdateNotifiers;
    }

    protected BaseCompositeReconciler() {
    }

    @Override
    public UpdateControl<T> reconcileInternal(T composite) throws Exception {
        log.info("Reconcile composite Resource {}", composite);

        CompositeSpec compositeSpec = fromResource(composite);
        log.info("Parsed composite specification: {}", compositeSpec);

        if (!isCompleted(composite, VALIDATED_STEP_NAME)) {
            try {
                compositeSpec.validate();
                completeStep(composite, VALIDATED_STEP_NAME);
            } catch (Exception e) {
                composite.getStatus().setPhase(Phase.INVALID_CONFIGURATION);
                return failStep(composite, VALIDATED_STEP_NAME, "error validate composite structure", e.getMessage());
            }
        }

        if (!isCompleted(composite, COMPOSITE_STRUCTURE_UPDATED_STEP_NAME)) {
            try {
                compositeConsulUpdater.updateCompositeStructureInConsul(compositeSpec);
                completeStep(composite, COMPOSITE_STRUCTURE_UPDATED_STEP_NAME);
            } catch (NoopConsulException nce) {
                log.warn("Consul integration is disabled; skip composite CR processing");
                return failStepNoRetry(composite, COMPOSITE_STRUCTURE_UPDATED_STEP_NAME, "consul disabled", "Consul integration is disabled; skip composite CR processing");
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                return failStep(composite, COMPOSITE_STRUCTURE_UPDATED_STEP_NAME, "consul update error", e.getMessage());
            }
        }

        Set<String> namespaceList = compositeConsulUpdater.getCompositeMembers(compositeSpec.getCompositeId());

        log.info("Update XaaSes...");
        for (CompositeStructureUpdateNotifier step : compositeStructureUpdateNotifiers) {
            String stepId = XAAS_UPDATED_STEP_NAME.apply(step.getXaasName());
            if (isCompleted(composite, stepId)) {
                continue;
            }

            try {
                log.info("Send composite structure to {}", step.getXaasName());
                step.notify(compositeSpec.getCompositeId(), namespaceList);
                completeStep(composite, stepId);
            } catch (Exception e) {
                log.error("Notification failed for {}", step.getXaasName(), e);
                return failStep(composite, stepId, step.getXaasName() + " notify error", e.getMessage());
            }
        }

        log.info("Composite resource successfully processed");
        return setPhaseAndReschedule(composite, Phase.UPDATED_PHASE);
    }

    private UpdateControl<T> failStep(T resource, String type, String message, String reason) {
        buildCondition(resource, new Condition(type, ProcessStatus.FAILED, reason, message));
        client.getRawClient().resource(resource).updateStatus();
        return setPhaseAndReschedule(resource, Phase.BACKING_OFF);
    }

    private UpdateControl<T> failStepNoRetry(T resource, String type, String message, String reason) {
        buildCondition(resource, new Condition(type, ProcessStatus.FAILED, reason, message));
        client.getRawClient().resource(resource).updateStatus();
        return setPhaseAndReschedule(resource, Phase.UPDATED_PHASE);
    }

    private void completeStep(T resource, String type) {
        buildCondition(resource, new Condition(type, ProcessStatus.COMPLETED, "", ""));
    }

    private static boolean isCompleted(CoreResource resource, String type) {
        CoreCondition condition = resource.getStatus().getConditions().get(type);
        return condition != null && condition.getStatus();
    }

    public static CompositeSpec fromResource(CoreResource resource) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(resource.getSpec().getValue(), CompositeSpec.class);
    }
}
package com.netcracker.core.declarative.client.reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcracker.cloud.core.error.rest.tmf.TmfErrorResponse;
import com.netcracker.core.declarative.client.cache.RetryResourceCache;
import com.netcracker.core.declarative.client.k8s.DeclarativeKubernetesClient;
import com.netcracker.core.declarative.client.rest.*;
import com.netcracker.core.declarative.client.rest.Condition;
import com.netcracker.core.declarative.resources.base.CoreCondition;
import com.netcracker.core.declarative.resources.base.CoreResource;
import com.netcracker.core.declarative.resources.base.DeclarativeStatus;
import com.netcracker.core.declarative.resources.base.Phase;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.ServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.netcracker.core.declarative.client.constants.Constants.*;
import static com.netcracker.core.declarative.resources.base.Phase.*;
import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static jakarta.servlet.http.HttpServletResponse.*;

public abstract class CoreReconciler<T extends CoreResource> implements Reconciler<T> {
    private static final String REPORTING_INSTANCE = "core-operator";
    private static final String REPORTING_COMPONENT = "nc-operator";
    private static final String WARNING = "Warning";
    private static final String V1 = "v1";
    private static final String EVENT = "Event";
    private static final String DEFAULT_API_VERSION = "1";
    private static final String PROCESSED_BY_OPERATOR_VER_PROPERTY = "processed-by-operator-ver";
    private static final String SESSION_ID_LABEL_KEY = "deployment.netcracker.com/sessionId";

    private static final Logger log = LoggerFactory.getLogger(CoreReconciler.class);
    protected DeclarativeKubernetesClient client;
    protected DeclarativeClient declarativeClient;
    protected RetryResourceCache retryResourceCache;
    @Inject
    protected ObjectMapper objectMapper;

    @ConfigProperty(name = "DEPLOYMENT_SESSION_ID")
    protected String deploymentSessionId;

    @SuppressWarnings("unused")
    public CoreReconciler() {
    }

    public CoreReconciler(KubernetesClient client) {
        this.client = new DeclarativeKubernetesClient(client);
        this.retryResourceCache = new RetryResourceCache();
    }

    public CoreReconciler(KubernetesClient client, DeclarativeClient declarativeClient) {
        this.client = new DeclarativeKubernetesClient(client);
        this.declarativeClient = declarativeClient;
        this.retryResourceCache = new RetryResourceCache();
    }

    public UpdateControl<T> reconcile(T resource, Context<T> context) throws Exception {
        setupLogFormat(resource.getStatus(), resource);
        //if CR validation fails there's no need for further processing
        if (!isResourceValid(resource)) {
            log.error("resource failed validation, one of the mandatory fields is missing");
            resource.getStatus().setPhase(INVALID_CONFIGURATION);
            addOrUpdateCondition(resource.getStatus().getConditions(),
                    new Condition(VALIDATED_STEP_NAME, ProcessStatus.FAILED, "Invalid CR Configuration", "One of the mandatory CR fields is missing"));
            return UpdateControl.patchStatus(resource);
        }

        Phase phase = resource.getStatus().getPhase();
        String processedByOperatorVer = resource.getStatus().getAdditionalPropertyAsString(PROCESSED_BY_OPERATOR_VER_PROPERTY);
        // Reconcile after new operator deployment
        if ((StringUtils.isEmpty(processedByOperatorVer) || !processedByOperatorVer.equalsIgnoreCase(deploymentSessionId)) && (phase == UPDATED_PHASE || phase == INVALID_CONFIGURATION)) {
            log.info("SessionId on CR={} and Operator={} are different, clear conditions and reconcile.", processedByOperatorVer, deploymentSessionId);
            resource.getStatus().removeAdditionalProperty(PROCESSED_BY_OPERATOR_VER_PROPERTY);
            resource.getStatus().getConditions().clear();
            return setPhaseAndReschedule(resource, UPDATING);
        }

        Long generation = resource.getMetadata().getGeneration();
        if (resource.getStatus().getObservedGeneration() != null && !Objects.equals(resource.getStatus().getObservedGeneration(), generation)) {
            //this means that someone manually updated the resource, we must clear all conditions as they no longer reflect updated object status
            log.info("Resource was updated, clear conditions and reconcile. Generation={}, ObservedGeneration={}", generation, resource.getStatus().getObservedGeneration());
            resource.getStatus().getConditions().clear();
            resource.getStatus().removeAdditionalProperty(PROCESSED_BY_OPERATOR_VER_PROPERTY);
            Phase p = resource.getStatus().getPhase();
            //set to Updating to force this CR to be reconciled again
            if (p == UPDATED_PHASE || p == INVALID_CONFIGURATION) {
                p = UPDATING;
            }
            return setPhaseAndReschedule(resource, p);
        }

        log.debug("reconciling phase={}", phase);
        try {
            return switch (phase) {
                case UNKNOWN -> {
                    MDC.put(X_REQUEST_ID, UUID.randomUUID().toString());
                    resource.getStatus().setRequestId(MDC.get(X_REQUEST_ID));
                    yield setPhaseAndReschedule(resource, UPDATING);
                }
                case UPDATING, BACKING_OFF -> reconcileInternal(resource);
                case WAITING_FOR_DEPENDS -> reconcilePooling(resource);
                case UPDATED_PHASE -> {
                    log.info("Successfully finished processing CR");
                    resource.getStatus().getConditions().clear();
                    retryResourceCache.remove(ResourceID.fromResource(resource));
                    yield UpdateControl.patchStatus(resource);
                }
                case INVALID_CONFIGURATION -> {
                    log.info("CR content is invalid, no additional processing is possible");
                    yield UpdateControl.noUpdate();
                }
            };
        } catch (Exception e) {
            return this.errorHandler(e, resource);
        }
    }

    protected UpdateControl<T> reconcileInternal(T t) throws Exception {
        log.debug("Reconcile Resource {}", t);
        try (Response response = declarativeClient.apply(getApiVersion(), declarativeRequestBuilder(t))) {
            return switch (response.getStatusInfo().getStatusCode()) {
                case SC_ACCEPTED -> {
                    log.debug("Received status={} from microservice, rescheduling reconciliation to wait for dependencies resolution", SC_ACCEPTED);
                    buildCondition(t, response.readEntity(DeclarativeResponse.class));
                    yield setPhaseAndReschedule(t, WAITING_FOR_DEPENDS);
                }
                case SC_OK -> setPhaseAndReschedule(t, UPDATED_PHASE);
                default ->
                        throw new ServerErrorException(String.format("Unexpected status=%s received from Microservice", response.getStatusInfo().getStatusCode()), 500);
            };
        }
    }

    protected UpdateControl<T> reconcilePooling(T t) throws Exception {
        return UpdateControl.noUpdate();
    }

    protected String getApiVersion() {
        return DEFAULT_API_VERSION;
    }

    @SuppressWarnings("unused")
    protected void fireEvent(T resource, String message, String reason) {
        log.info("firing event for resource with message={} and reason={}", message, reason);
        ObjectReference ref = new ObjectReferenceBuilder()
                .withKind(resource.getKind())
                .withAdditionalProperties(Map.of("subKind", resource.getSubKind()))
                .withNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .withUid(resource.getMetadata().getUid())
                .withApiVersion(resource.getApiVersion())
                .build();
        Optional<Event> event = client.getRawClient().resources(Event.class)
                .inNamespace(resource.getMetadata().getNamespace())
                .withInvolvedObject(ref)
                .list().getItems().stream()
                .filter(e -> {
                    if (e.getReason() != null) {
                        return e.getReason().equals(reason);
                    } else return true;
                })
                .filter(e -> {
                    if (e.getMessage() != null) {
                        return e.getMessage().equals(message);
                    } else return true;
                })
                .filter(e -> e.getMetadata().getName().startsWith(resource.getMetadata().getName() + "-" + resource.getMetadata().getUid()))
                .filter(e -> e.getMetadata().getLabels().get(X_REQUEST_ID).equals(resource.getStatus().getRequestId()))
                .findFirst();
        if (event.isPresent()) {
            Event e = event.get();
            e.setLastTimestamp(Instant.now().toString());
            e.setCount(e.getCount() + 1);
            client.getRawClient().resource(e).inNamespace(resource.getMetadata().getNamespace()).patch();
        } else {
            Map<String, String> labels = new HashMap<>();
            labels.put("app.kubernetes.io/processed-by-operator", REPORTING_INSTANCE);
            labels.put("app.kubernetes.io/part-of", "Cloud-Core");
            labels.put(X_REQUEST_ID, resource.getStatus().getRequestId());
            labels.put(SESSION_ID_LABEL_KEY, getSessionIdLabel(resource));
            ObjectMeta metadata = new ObjectMetaBuilder()
                    .withName(resource.getMetadata().getName() + "-" + resource.getMetadata().getUid() + "." + UUID.randomUUID())
                    .withLabels(labels)
                    .withNamespace(resource.getMetadata().getNamespace()).build();
            Event ev = new EventBuilder()
                    .withType(WARNING)
                    .withApiVersion(V1)
                    .withKind(EVENT)
                    .withInvolvedObject(ref)
                    .withMetadata(metadata)
                    .withReportingComponent(REPORTING_COMPONENT)
                    .withReportingInstance(REPORTING_INSTANCE)
                    .withNewSource(REPORTING_COMPONENT, REPORTING_INSTANCE)
                    .withReason(reason)
                    .withMessage(message)
                    .withFirstTimestamp(Instant.now().toString())
                    .withLastTimestamp(Instant.now().toString())
                    .withCount(1)
                    .build();
            client.getRawClient().resource(ev).inNamespace(resource.getMetadata().getNamespace()).create();
        }
    }

    protected UpdateControl<T> errorHandler(Exception e, T resource) {
        log.error("Error happened while processing CR");
        if (e instanceof WebApplicationException exception) {
            try {
                TmfErrorResponse resp = exception.getResponse().readEntity(TmfErrorResponse.class);
                if (resp != null) {
                    buildCondition(resource, resp);
                }
            } catch (Exception ex) {
                log.error("failed to read response as TmfErrorResponse", ex);
                buildExceptionCondition(resource, ex);
            }
        } else {
            buildExceptionCondition(resource, e);
        }
        client.getRawClient().resource(resource).updateStatus();

        return setPhaseAndReschedule(resource, Phase.BACKING_OFF);
    }

    private void buildExceptionCondition(T resource, Exception ex) {
        buildCondition(resource, new Condition(TYPE_UNKNOWN, ProcessStatus.FAILED, abbreviateError(ex.getMessage()), MESSAGE_UNKNOWN));
    }

    private String abbreviateError(String message) {
        return StringUtils.abbreviate(message, 200);
    }

    protected DeclarativeRequest declarativeRequestBuilder(T resource) {
        HashMap<String, Object> meta = new HashMap<>();
        meta.put("name", resource.getMetadata().getName());
        meta.put("namespace", resource.getMetadata().getNamespace());
        meta.put("microserviceName", getLabelOrAlternative(resource, "app.kubernetes.io/name", "app.kubernetes.io/instance"));
        return DeclarativeRequest.builder()
                .apiVersion(resource.getApiVersion())
                .kind(resource.getKind())
                .subKind(resource.getSubKind())
                .spec(resource.getSpec().getValue())
                .metadata(meta)
                .build();
    }

    protected UpdateControl<T> setPhaseAndReschedule(T resource, Phase phase) {
        MDC.put(PHASE, phase.getValue());
        log.info("Transitioning to phase={}", phase);
        resource.getStatus().setObservedGeneration(resource.getMetadata().getGeneration());
        resource.getStatus().setPhase(phase);
        if (phase == UPDATED_PHASE) {
            resource.getStatus().setAdditionalProperty(PROCESSED_BY_OPERATOR_VER_PROPERTY, deploymentSessionId);
        }
        int nextDelay = retryResourceCache.getNextDelay(phase, ResourceID.fromResource(resource));
        return UpdateControl.patchStatus(resource).rescheduleAfter(nextDelay, TimeUnit.SECONDS);
    }

    private UpdateControl<T> setPhaseInvalidIfRetryLimitReached(T resource) {
        MDC.put(PHASE, INVALID_CONFIGURATION.getValue());
        resource.getStatus().setPhase(INVALID_CONFIGURATION);

        Condition condition = new Condition(VALIDATED_STEP_NAME, ProcessStatus.FAILED, CR_RETRY_EXCEEDED_REASON, CR_RETRY_EXCEEDED_MESSAGE);
        addOrUpdateCondition(resource.getStatus().getConditions(), condition);

        fireEvent(resource, condition.message(), condition.reason());

        return UpdateControl.patchStatus(resource);
    }

    protected void buildCondition(T resource, Object responseBody) {
        switch (responseBody) {
            case TmfErrorResponse err -> {
                if (getTmfErrorStatus(err) == SC_BAD_REQUEST) {
                    resource.getStatus().setPhase(INVALID_CONFIGURATION);
                    MDC.put(PHASE, INVALID_CONFIGURATION.getValue());
                } else {
                    resource.getStatus().setPhase(BACKING_OFF);
                    MDC.put(PHASE, BACKING_OFF.getValue());
                }
                addOrUpdateCondition(resource.getStatus().getConditions(), new Condition(getErrorType(err), ProcessStatus.FAILED, buildReason(err), err.getDetail()));
                if (err.getErrors() != null && !err.getErrors().isEmpty()) {
                    err.getErrors().forEach(tmfError -> log.error("TmfError={}", printResource(tmfError)));
                }
                fireEvent(resource, err.getDetail(), err.getReason());
            }
            case DeclarativeResponse resp -> {
                resource.getStatus().setTrackingId(resp.getTrackingId());
                List<Condition> conditions = resp.getConditions();
                for (Condition condition : conditions) {
                    addOrUpdateCondition(resource.getStatus().getConditions(), condition);
                }
            }
            case Condition condition -> addOrUpdateCondition(resource.getStatus().getConditions(), condition);
            default -> {
                log.error("Unexpected type of response={} received when trying to reconcile resource with trackingId={}", responseBody.getClass(), resource.getStatus().getTrackingId());
                throw new ServerErrorException(String.format("Unexpected type of response=%s received when trying to reconcile resource with trackingId=%s ", responseBody.getClass(), resource.getStatus().getTrackingId()), 500);
            }
        }
    }

    private String buildReason(TmfErrorResponse err) {
        StringBuilder builder = new StringBuilder();
        if (err.getCode() != null || !err.getCode().isEmpty()) {
            builder.append(err.getCode());
            builder.append(":");
        }
        builder.append(err.getReason());
        return builder.toString();
    }

    /**
     * Conditions are added only when they are unique. Non-unique conditions will have their Transition and LastUpdateTime updated.
     * When Condition with ProcessStatus.Completed is processed, all previous unsuccessful conditions (status==false) will be cleared.
     *
     * @param conditions
     * @param condition
     */
    protected void addOrUpdateCondition(Map<String, CoreCondition> conditions, Condition condition) {
        CoreCondition newCondition = CoreCondition.builder()
                .lastTransitionTime(Calendar.getInstance().getTime().toString())
                .lastUpdateTime(Calendar.getInstance().getTime().toString())
                .state(condition.state())
                .type(condition.type())
                .status(condition.state().equals(ProcessStatus.COMPLETED))
                .build();
        if (!StringUtils.isEmpty(condition.message())) {
            newCondition.setMessage(condition.message());
        }
        if (!StringUtils.isEmpty(condition.reason())) {
            newCondition.setReason(condition.reason());
        }
        conditions.put(newCondition.getType(), newCondition);
    }

    protected String getLabelOrAlternative(T resource, String labelName, String oldName) {
        String labelValue = resource.getMetadata().getLabels().get(labelName);
        if (labelValue == null) {
            labelValue = resource.getMetadata().getLabels().get(oldName);
        }
        return labelValue != null ? labelValue : "";
    }

    private int getTmfErrorStatus(TmfErrorResponse err) {
        if (err.getStatus() == null || err.getStatus().isEmpty()) {
            log.warn("TmfErrorResponse={} has no Status, default value=500 will be used", err);
            return 500;
        } else {
            try {
                return Integer.parseInt(err.getStatus());
            } catch (NumberFormatException ex) {
                log.error("Failed to parse status={} as Http Status, default value=500 will be used", err.getStatus());
                return 500;
            }
        }
    }

    private String getSessionIdLabel(T resource) {
        String result = resource.getMetadata().getLabels().get(SESSION_ID_LABEL_KEY);
        if (result == null) {
            return "";
        }
        return result;
    }

    /**
     * This minimal validation will do for now
     */
    private boolean isResourceValid(T resource) {
        return !isNullOrEmpty(resource.getApiVersion())
                && !isNullOrEmpty(resource.getKind())
                && !isNullOrEmpty(resource.getSubKind())
                && !isNullOrEmpty(resource.getMetadata().getName())
                && resource.getSpec() != null;
    }

    private void setupLogFormat(DeclarativeStatus status, T resource) {
        if (status.getRequestId() != null) {
            MDC.put(X_REQUEST_ID, status.getRequestId());
            MDC.put(SESSION_ID_KEY, getSessionIdLabel(resource));
        }
        MDC.put(RESOURCE_NAME, resource.getMetadata().getName() == null ? "-" : resource.getMetadata().getName());
        MDC.put(KIND, resource.getKind() == null ? "-" : resource.getKind());
        MDC.put(SUB_KIND, resource.getSubKind() == null ? "-" : resource.getSubKind());
        MDC.put(PHASE, resource.getStatus().getPhase().getValue());
    }

    private String printResource(Object resource) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resource);
        } catch (Exception ex) {
            log.error("Failed to parse resource={} as Json", resource);
            throw new IllegalStateException("Failed to parse resource as Json");
        }
    }

    private String getErrorType(TmfErrorResponse response) {
        Map<String, Object> meta = response.getMeta();
        if (meta == null || meta.isEmpty()) {
            return TYPE_UNKNOWN;
        }
        String typeFromMeta = (String) meta.get("type");
        return Objects.requireNonNullElse(typeFromMeta, TYPE_UNKNOWN);
    }
}
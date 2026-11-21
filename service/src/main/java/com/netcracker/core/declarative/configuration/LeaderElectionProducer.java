package com.netcracker.core.declarative.configuration;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import jakarta.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Lets only one core-operator pod reconcile at a time.
 * <p>
 * Each pod writes its deployment session into the status of the CRs it finishes, and resets a CR that carries
 * another session. Without a leader, a pod of the previous deployment that is still running during a rollout
 * undoes what the new pods have finished. If that pod stops halfway, the CR stays in Updating, because the
 * other pods do not react to status changes. With a lease, the pods that do not hold it keep their caches but
 * do not reconcile, and the pod that takes the lease over reconciles every CR again.
 * <p>
 * Quarkus Operator SDK uses this bean in the prod profile only. A {@code null} result leaves leader election off.
 */
@Slf4j
public class LeaderElectionProducer {

    static final String LEASE_NAME = "core-operator-leader";

    @Produces
    LeaderElectionConfiguration leaderElectionConfiguration(
            @ConfigProperty(name = "cloud.operator.leader-election.enabled", defaultValue = "true") boolean enabled) {
        if (!enabled) {
            log.warn("Leader election is disabled, every core-operator pod reconciles on its own");
            return null;
        }
        log.info("Leader election is enabled, lease '{}'", LEASE_NAME);
        return new LeaderElectionConfiguration(LEASE_NAME);
    }
}

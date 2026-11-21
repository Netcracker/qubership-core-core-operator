package com.netcracker.core.declarative.configuration;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderElectionProducerTest {

    private final LeaderElectionProducer producer = new LeaderElectionProducer();

    @Test
    void producesLeaseInOwnNamespaceWhenEnabled() {
        LeaderElectionConfiguration configuration = producer.leaderElectionConfiguration(true);

        assertThat(configuration.getLeaseName()).isEqualTo("core-operator-leader");
        assertThat(configuration.getLeaseNamespace()).isEmpty();
    }

    @Test
    void producesNothingWhenDisabled() {
        assertThat(producer.leaderElectionConfiguration(false)).isNull();
    }
}

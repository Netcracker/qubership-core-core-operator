package com.netcracker.core.declarative.service.composite.consul.model;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsulPrefixSnapshotTest {

    @Test
    void buildsSortedImmutableViewOfEntries() {
        GetValue first = mock(GetValue.class);
        when(first.getKey()).thenReturn("b");
        when(first.getDecodedValue()).thenReturn("value-b");

        GetValue second = mock(GetValue.class);
        when(second.getKey()).thenReturn("a");
        when(second.getDecodedValue()).thenReturn("value-a");

        GetValue third = mock(GetValue.class);
        when(third.getKey()).thenReturn("c");
        when(third.getDecodedValue()).thenReturn("value-c");

        ConsulPrefixSnapshot snapshot = ConsulPrefixSnapshot.fromGetValues(
                Arrays.asList(first, second, third), 123L);

        assertThat(snapshot.getIndex()).isEqualTo(123L);
        assertThat(snapshot.getValue("a")).isEqualTo("value-a");
        assertThat(snapshot.getValue("b")).isEqualTo("value-b");
        assertThat(snapshot.getValue("c")).isEqualTo("value-c");

        Set<String> keys = snapshot.getKeySet();
        assertThat(keys).containsExactlyInAnyOrder("a", "b", "c");
        assertThatThrownBy(() -> keys.add("d")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void handlesNullListsGracefully() {
        ConsulPrefixSnapshot emptySnapshot = ConsulPrefixSnapshot.fromGetValues(null, 0L);

        assertThat(emptySnapshot.getIndex()).isZero();
        assertThat(emptySnapshot.getKeySet()).isEmpty();
        assertThat(emptySnapshot.getValue("missing")).isNull();
    }

    @Test
    void handlesEmptyListGracefully() {
        ConsulPrefixSnapshot snapshot = ConsulPrefixSnapshot.fromGetValues(Collections.emptyList(), 55L);

        assertThat(snapshot.getIndex()).isEqualTo(55L);
        assertThat(snapshot.getKeySet()).isEmpty();
        assertThat(snapshot.getValue("missing")).isNull();
    }
}

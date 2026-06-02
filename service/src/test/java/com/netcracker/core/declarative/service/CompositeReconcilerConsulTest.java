package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.ConsulTestResource;
import com.netcracker.core.declarative.model.CompositeMembersList;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = ConsulTestResource.class)
public class CompositeReconcilerConsulTest {

    @Inject
    CompositeConsulUpdater compositeConsulUpdater;

    @Test
    public void deleteSubTree() throws Exception {
        compositeConsulUpdater.updateCompositeStructureInConsul("test-namespace-baseline", new CompositeSpec("", "test-namespace-baseline", "", null));
        CompositeMembersList compositeMembers = compositeConsulUpdater.getCompositeMembers("test-namespace-baseline");
        assertTrue(compositeMembers.members().contains("test-namespace-baseline"));

        compositeConsulUpdater.updateCompositeStructureInConsul("test-namespace", new CompositeSpec("", "test-namespace", "", new CompositeSpec.CompositeSpecBaseline("", "test-namespace-baseline", "")));
        compositeMembers = compositeConsulUpdater.getCompositeMembers("test-namespace-baseline");
        assertTrue(compositeMembers.members().contains("test-namespace-baseline"));

        compositeConsulUpdater.updateCompositeStructureInConsul("test-namespace", new CompositeSpec("", "test-namespace", "", new CompositeSpec.CompositeSpecBaseline("", "test-namespace-baseline", "")));
        compositeMembers = compositeConsulUpdater.getCompositeMembers("test-namespace-baseline");
        assertTrue(compositeMembers.members().contains("test-namespace-baseline"));
    }
}
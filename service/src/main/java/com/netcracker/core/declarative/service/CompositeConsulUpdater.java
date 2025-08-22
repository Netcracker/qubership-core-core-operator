package com.netcracker.core.declarative.service;

import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface CompositeConsulUpdater {
    void updateCompositeStructureInConsul(CompositeSpec compositeSpec) throws ExecutionException, InterruptedException;

    Set<String> getCompositeMembers(String compositeId) throws ExecutionException, InterruptedException;
}

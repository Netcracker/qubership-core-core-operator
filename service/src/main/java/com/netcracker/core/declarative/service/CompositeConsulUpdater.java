package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.model.CompositeMembersList;

import java.util.concurrent.ExecutionException;

public interface CompositeConsulUpdater {
    void updateCompositeStructureInConsul(CompositeSpec compositeSpec) throws ExecutionException, InterruptedException;

    void updateCompositeStructureInConsul(String namespace, CompositeSpec compositeSpec) throws ExecutionException, InterruptedException;

    CompositeMembersList getCompositeMembers(String compositeId) throws ExecutionException, InterruptedException;
}

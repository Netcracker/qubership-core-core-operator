package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.exception.NoopConsulException;

import java.util.Set;
import java.util.concurrent.ExecutionException;

public class NoopCompositeConsulUpdaterImpl implements CompositeConsulUpdater {

    @Override
    public void updateCompositeStructureInConsul(CompositeSpec compositeSpec) {
        throw new NoopConsulException();
    }

    @Override
    public void updateCompositeStructureInConsul(String namespace, CompositeSpec compositeSpec) throws ExecutionException, InterruptedException {
        throw new NoopConsulException();
    }

    @Override
    public Set<String> getCompositeMembers(String compositeId) {
        throw new NoopConsulException();
    }

}

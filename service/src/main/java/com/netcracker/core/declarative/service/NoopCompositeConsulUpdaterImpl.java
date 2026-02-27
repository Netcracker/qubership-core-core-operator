package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.exception.NoopConsulException;
import com.netcracker.core.declarative.model.CompositeMembersList;

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
    public CompositeMembersList getCompositeMembers(String compositeId) {
        throw new NoopConsulException();
    }

}

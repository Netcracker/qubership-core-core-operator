package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.exception.NoopConsulException;

import java.util.Set;

public class NoopCompositeConsulUpdaterImpl implements CompositeConsulUpdater {

    @Override
    public void updateCompositeStructureInConsul(CompositeSpec compositeSpec) {
        throw new NoopConsulException();
    }

    @Override
    public Set<String> getCompositeMembers(String compositeId) {
        throw new NoopConsulException();
    }

    @Override
    public void setNamespace(String namespace) {
        throw new NoopConsulException();
    }
}

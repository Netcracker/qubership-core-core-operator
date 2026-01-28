package com.netcracker.core.declarative.service;

import com.netcracker.core.declarative.resources.composite.Composite;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe holder for the current {@link Composite}.
 * <p>
 * This bean provides a centralized location for storing and accessing
 * the composite structure custom resource across different components of the application.
 */
@ApplicationScoped
public class CompositeCRHolder {

    private final AtomicReference<Composite> ref = new AtomicReference<>();

    public void set(Composite spec) {
        ref.set(spec);
    }

    public Composite get() {
        return ref.get();
    }
}

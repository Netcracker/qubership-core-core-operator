package com.netcracker.core.declarative.service.composite;

/**
 * Identity of the {@value #NAME} ConfigMap that holds the composite platform topology.
 * <p>
 * Shared by every component that reads or writes it ({@link CompositeStructureWatcher},
 * {@link CompositeStructureChangeListener}, {@link TopologyConfigMapPublisher}) so the
 * name and data key are defined in exactly one place.
 */
public final class TopologyConfigMap {

    /** ConfigMap name. */
    public static final String NAME = "topology";

    /** Key under {@code data} holding the serialized topology JSON payload. */
    public static final String DATA_KEY = "data";

    private TopologyConfigMap() {
    }
}

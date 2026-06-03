package com.netcracker.core.declarative.service.composite.model.transformation;

import com.netcracker.core.declarative.service.CompositeSpec;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;

import java.util.List;

/**
 * Converts a {@link CompositeSpec} (the Composite CR spec) into a {@link CompositeStructure}.
 */
public class CompositeSpecTransformer {

    public CompositeStructure transform(CompositeSpec spec) {
        if (spec.isBaseline()) {
            CompositeStructure.NamespaceRoles baseline =
                    roles(spec.getControllerNamespace(), spec.getOriginNamespace(), spec.getPeerNamespace());
            return baseline == null ? null : new CompositeStructure(baseline, null);
        }

        CompositeSpec.CompositeSpecBaseline bl = spec.getBaseline();
        CompositeStructure.NamespaceRoles baseline =
                roles(bl.getControllerNamespace(), bl.getOriginNamespace(), bl.getPeerNamespace());
        CompositeStructure.NamespaceRoles satellite =
                roles(spec.getControllerNamespace(), spec.getOriginNamespace(), spec.getPeerNamespace());

        List<CompositeStructure.NamespaceRoles> satellites = satellite == null ? null : List.of(satellite);
        if (baseline == null && satellites == null) {
            return null;
        }
        return new CompositeStructure(baseline, satellites);
    }

    private static CompositeStructure.NamespaceRoles roles(String controller, String origin, String peer) {
        String c = trimToNull(controller);
        String o = trimToNull(origin);
        String p = trimToNull(peer);
        if (c == null && o == null && p == null) {
            return null;
        }
        return new CompositeStructure.NamespaceRoles(c, o, p);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

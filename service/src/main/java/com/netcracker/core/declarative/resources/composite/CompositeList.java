package com.netcracker.core.declarative.resources.composite;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.model.annotation.Kind;

@Kind("CompositeList")
public class CompositeList extends DefaultKubernetesResourceList<Composite> {
}

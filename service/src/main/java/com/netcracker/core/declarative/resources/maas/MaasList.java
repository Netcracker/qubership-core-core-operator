package com.netcracker.core.declarative.resources.maas;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.model.annotation.Kind;

@Kind("MaaSList")
public class MaasList extends DefaultKubernetesResourceList<Maas> {
}

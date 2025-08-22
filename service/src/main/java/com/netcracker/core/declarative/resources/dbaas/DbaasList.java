package com.netcracker.core.declarative.resources.dbaas;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.model.annotation.Kind;

@Kind("DBaaSList")
public class DbaasList extends DefaultKubernetesResourceList<Dbaas> {
}

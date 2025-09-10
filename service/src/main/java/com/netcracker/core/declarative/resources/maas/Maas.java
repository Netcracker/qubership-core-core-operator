package com.netcracker.core.declarative.resources.maas;

import com.netcracker.core.declarative.resources.base.CoreResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.*;

@Group(CoreResource.GROUP)
@Version(CoreResource.VERSION)
@Plural("maases")
@Kind("MaaS")
public class Maas extends CoreResource implements Namespaced {
}
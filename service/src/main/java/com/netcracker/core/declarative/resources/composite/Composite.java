package com.netcracker.core.declarative.resources.composite;

import com.netcracker.core.declarative.resources.base.CoreResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.*;

@Group(CoreResource.GROUP)
@Version(CoreResource.VERSION)
@Plural("composites")
@Singular("composite")
@Kind("Composite")
public class Composite extends CoreResource implements Namespaced {
}

package com.netcracker.core.declarative.resources.mesh;

import com.netcracker.core.declarative.resources.base.CoreResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Version;

@Group(CoreResource.GROUP)
@Version(CoreResource.VERSION)
@Plural("meshes")
@Kind("Mesh")
public class Mesh extends CoreResource implements Namespaced {
}

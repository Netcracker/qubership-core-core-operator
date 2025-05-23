package org.qubership.core.declarative.resources.dbaas;

import org.qubership.core.declarative.resources.base.CoreResource;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.model.annotation.*;

@Group(CoreResource.GROUP)
@Version(CoreResource.VERSION)
@Plural("dbaases")
@Singular("dbaas")
@Kind("DBaaS")
public class Dbaas extends CoreResource implements Namespaced {
}

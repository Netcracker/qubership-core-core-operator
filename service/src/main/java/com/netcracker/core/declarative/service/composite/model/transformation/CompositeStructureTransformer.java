package com.netcracker.core.declarative.service.composite.model.transformation;

import com.netcracker.cloud.quarkus.consul.client.model.GetValue;
import com.netcracker.core.declarative.service.composite.model.CompositeStructure;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureConfigMapPayload;
import com.netcracker.core.declarative.service.composite.model.CompositeStructureParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Converts Consul KV entries ({@code composite/{prefix}/structure/{namespace}/{attribute}})
 * into a {@link CompositeStructure} by grouping namespaces into baseline and satellites
 * with their blue-green roles (controller/origin/peer).
 */
@ApplicationScoped
@Slf4j
public class CompositeStructureTransformer {
    private static final Pattern COMPOSITE_STRUCTURE_ENTRY_PATTERN = Pattern.compile("^composite/[^/]+/structure/(?<namespace>[^/]+)/(?<attribute>[^/]+)$");
    private static final String DEFAULT_CLOUD_PROVIDER = "OnPrem";

    private final String cloudProvider;

    @Inject
    public CompositeStructureTransformer(@ConfigProperty(name = "CLOUD_PROVIDER", defaultValue = DEFAULT_CLOUD_PROVIDER) String cloudProvider) {
        this.cloudProvider = cloudProvider;
    }

    public CompositeStructureConfigMapPayload transform(List<GetValue> values) {
        log.debug("Transforming {} Consul KV entries", values.size());
        List<Namespace> namespaces = parseNamespaces(values);
        log.debug("Parsed {} namespaces from Consul data", namespaces.size());

        CompositeStructure compositeStructure = new CompositeStructure(
                buildBaseline(namespaces),
                buildSatellites(namespaces)
        );
        return new CompositeStructureConfigMapPayload(cloudProvider, compositeStructure);
    }

    private List<Namespace> parseNamespaces(List<GetValue> values) {
        return values.stream()
                .map(CompositeStructureTransformer::getParsedEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ParsedEntry::namespace,
                        Collectors.toMap(ParsedEntry::attribute, ParsedEntry::value, (v1, v2) -> v2)))
                .entrySet()
                .stream()
                .map(entry -> buildNamespace(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Namespace buildNamespace(String name, Map<String, String> attrs) {
        return new Namespace(
                name,
                parseEnum(CompositeRole.class, attrs.get("compositeRole"), "composite role"),
                parseEnum(BlueGreenRole.class, attrs.get("bluegreenRole"), "blue-green role"),
                attrs.get("controllerNamespace")
        );
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CompositeStructureParseException(
                    "Invalid " + description + ": " + value, e);
        }
    }

    private CompositeStructure.NamespaceRoles buildBaseline(List<Namespace> namespaces) {
        List<Namespace> baselineNamespaces = namespaces.stream()
                .filter(entry -> entry.compositeRole() == CompositeRole.BASELINE)
                .toList();

        return buildNamespaceRoles(baselineNamespaces).orElse(null);
    }

    private List<CompositeStructure.NamespaceRoles> buildSatellites(List<Namespace> namespaces) {
        return namespaces.stream()
                .filter(entry -> entry.compositeRole() == CompositeRole.SATELLITE)
                .collect(Collectors.groupingBy(
                        this::resolveSatelliteKey,
                        TreeMap::new,
                        Collectors.toList()))
                .values().stream()
                .flatMap(list -> buildNamespaceRoles(list).stream())
                .toList();
    }

    private String resolveSatelliteKey(Namespace entry) {
        if (entry.blueGreenRole() == BlueGreenRole.CONTROLLER) {
            return entry.name();
        }
        String controllerNamespace = entry.controllerNamespace();
        if (controllerNamespace != null && !controllerNamespace.isBlank()) {
            return controllerNamespace;
        }
        return entry.name();
    }

    private Optional<CompositeStructure.NamespaceRoles> buildNamespaceRoles(Collection<Namespace> namespaceEntries) {
        if (namespaceEntries.isEmpty()) {
            return Optional.empty();
        }

        String controller = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.CONTROLLER);
        String origin = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.ORIGIN);
        String peer = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.PEER);

        if (origin == null) {
            origin = findNamespaceByBlueGreenRole(namespaceEntries, null);
        }

        if (controller == null && origin == null && peer == null) {
            return Optional.empty();
        }

        return Optional.of(new CompositeStructure.NamespaceRoles(controller, origin, peer));
    }

    private String findNamespaceByBlueGreenRole(Collection<Namespace> namespaces, BlueGreenRole blueGreenRole) {
        return namespaces.stream()
                .filter(entry -> entry.blueGreenRole() == blueGreenRole)
                .map(Namespace::name)
                .collect(toUniqueOrNull("Multiple namespaces with the same blue-green role '" + blueGreenRole + "'"));
    }

    private static ParsedEntry getParsedEntry(GetValue gv) {
        Matcher m = COMPOSITE_STRUCTURE_ENTRY_PATTERN.matcher(gv.getKey());
        return m.matches() ? new ParsedEntry(m.group("namespace"), m.group("attribute"), gv.getDecodedValue()) : null;
    }

    private static Collector<String, ?, String> toUniqueOrNull(String errorMessage) {
        return Collectors.collectingAndThen(Collectors.toList(), list -> {
            if (list.size() > 1) {
                throw new CompositeStructureParseException(errorMessage + ": " + list);
            }
            return list.isEmpty() ? null : list.getFirst();
        });
    }


    private record ParsedEntry(String namespace, String attribute, String value) {}

    private record Namespace(String name, CompositeRole compositeRole,
                             BlueGreenRole blueGreenRole, String controllerNamespace) {}

    private enum CompositeRole {
        BASELINE,
        SATELLITE
    }

    private enum BlueGreenRole {
        CONTROLLER,
        ORIGIN,
        PEER
    }
}

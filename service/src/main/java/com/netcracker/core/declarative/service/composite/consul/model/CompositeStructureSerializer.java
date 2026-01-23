package com.netcracker.core.declarative.service.composite.consul.model;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts Consul KV entries ({@code composite/{prefix}/structure/{namespace}/{attribute}})
 * into a {@link CompositeStructure} by grouping namespaces into baseline and satellites
 * with their blue-green roles (controller/origin/peer).
 */
public class CompositeStructureSerializer {
    private static final Pattern STRUCTURE_ENTRY_PATTERN = Pattern.compile("^composite/[^/]+/structure/([^/]+)/([^/]+)$");

    public static CompositeStructure toPayload(ConsulPrefixSnapshot consulPrefixSnapshot) {
        List<Namespace> namespaces = parseNamespaces(consulPrefixSnapshot);
        return new CompositeStructure(
                buildBaseline(namespaces),
                buildSatellites(namespaces)
        );
    }

    private static List<Namespace> parseNamespaces(ConsulPrefixSnapshot snapshot) {
        Map<String, Map<String, String>> attributesByNamespace = snapshot.getKeySet().stream()
                .sorted()
                .map(key -> {
                    Matcher m = STRUCTURE_ENTRY_PATTERN.matcher(key);
                    return m.matches()
                            ? new ParsedEntry(m.group(1), m.group(2), snapshot.getValue(key))
                            : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ParsedEntry::namespace,
                        LinkedHashMap::new,
                        Collectors.toMap(ParsedEntry::attribute, ParsedEntry::value, (v1, v2) -> v2)));

        return attributesByNamespace.entrySet().stream()
                .map(entry -> buildNamespace(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Namespace buildNamespace(String name, Map<String, String> attrs) {
        return new Namespace(
                name,
                parseEnum(CompositeRole.class, attrs.get("compositeRole"), "composite role"),
                parseEnum(BlueGreenRole.class, attrs.get("bluegreenRole"), "blue-green role"),
                attrs.get("controllerNamespace")
        );
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConsulSnapshotSerializationException(
                    "Invalid " + description + ": " + value, e);
        }
    }

    private static NamespaceRoles buildBaseline(List<Namespace> namespaces) {
        List<Namespace> baselineNamespaces = namespaces.stream()
                .filter(entry -> entry.compositeRole() == CompositeRole.BASELINE)
                .toList();

        return buildNamespaceRoles(baselineNamespaces).orElse(null);
    }

    private static List<NamespaceRoles> buildSatellites(List<Namespace> namespaces) {
        return namespaces.stream()
                .filter(entry -> entry.compositeRole() == CompositeRole.SATELLITE)
                .collect(Collectors.groupingBy(
                        CompositeStructureSerializer::resolveSatelliteKey,
                        TreeMap::new,
                        Collectors.toList()))
                .values().stream()
                .flatMap(list -> buildNamespaceRoles(list).stream())
                .toList();
    }

    private static String resolveSatelliteKey(Namespace entry) {
        if (entry.blueGreenRole() == BlueGreenRole.CONTROLLER) {
            return entry.name();
        }
        String controllerNamespace = entry.controllerNamespace();
        if (controllerNamespace != null && !controllerNamespace.isBlank()) {
            return controllerNamespace;
        }
        return entry.name();
    }

    private static Optional<NamespaceRoles> buildNamespaceRoles(Collection<Namespace> namespaceEntries) {
        if (namespaceEntries.isEmpty()) {
            return Optional.empty();
        }

        String controller = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.CONTROLLER);
        String origin = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.ORIGIN);
        String peer = findNamespaceByBlueGreenRole(namespaceEntries, BlueGreenRole.PEER);

        if (origin == null) {
            origin = namespaceEntries.stream()
                    .filter(entry -> entry.blueGreenRole() == null)
                    .map(Namespace::name)
                    .sorted()
                    .findFirst()
                    .orElse(null);
        }

        if (controller == null && origin == null && peer == null) {
            return Optional.empty();
        }

        return Optional.of(new NamespaceRoles(controller, origin, peer));
    }

    private static String findNamespaceByBlueGreenRole(Collection<Namespace> namespaces, BlueGreenRole blueGreenRole) {
        return namespaces.stream()
                .filter(entry -> entry.blueGreenRole() == blueGreenRole)
                .map(Namespace::name)
                .min(Comparator.naturalOrder())
                .orElse(null);
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

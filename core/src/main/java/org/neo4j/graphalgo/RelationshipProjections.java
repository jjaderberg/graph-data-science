/*
 * Copyright (c) 2017-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.graphalgo;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

public final class RelationshipProjections {

    private static final RelationshipProjections EMPTY = new RelationshipProjections(emptyMap());

    public static RelationshipProjections of(@Nullable String type) {
        if (StringUtils.isEmpty(type)) {
            return EMPTY;
        }
        ElementIdentifier identifier = new ElementIdentifier(type);
        RelationshipProjection filter = RelationshipProjection.of(type);
        return create(singletonMap(identifier, filter));
    }

    public static RelationshipProjections of(Map<String, ?> map) {
        Map<ElementIdentifier, RelationshipProjection> filters = new LinkedHashMap<>();
        map.forEach((name, spec) -> {
            ElementIdentifier identifier = new ElementIdentifier(name);
            RelationshipProjection filter = RelationshipProjection.fromObject(spec, identifier);
            // sanity
            if (filters.put(identifier, filter) != null) {
                throw new IllegalStateException(String.format("Duplicate key: %s", name));
            }
        });
        return create(filters);
    }

    public static RelationshipProjections of(Iterable<?> items) {
        Map<ElementIdentifier, RelationshipProjection> filters = new LinkedHashMap<>();
        for (Object item : items) {
            RelationshipProjections relationshipProjections = fromObject(item);
            filters.putAll(relationshipProjections.filters);
        }
        return create(filters);
    }

    public static RelationshipProjections fromObject(Object object) {
        if (object == null) {
            return of(emptyMap());
        }
        if (object instanceof String) {
            return of((String) object);
        }
        if (object instanceof Map) {
            @SuppressWarnings("unchecked") Map<String, ?> map = (Map) object;
            return of(map);
        }
        if (object instanceof Iterable) {
            Iterable<?> list = (Iterable) object;
            return of(list);
        }
        throw new IllegalArgumentException(String.format(
            "Cannot construct a relationship filter out of a %s",
            object.getClass().getName()
        ));
    }

    private static RelationshipProjections create(Map<ElementIdentifier, RelationshipProjection> filters) {
        if (filters.values().stream().allMatch(RelationshipProjection::isEmpty)) {
            return EMPTY;
        }

        Map<String, Long> entriesPerType = filters
            .values()
            .stream()
            .collect(groupingBy(RelationshipProjection::type, counting()));

        String duplicateTypes = entriesPerType.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(entry -> String.format("'%s'", entry.getKey()))
            .collect(Collectors.joining(", '"));

        if (!duplicateTypes.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                "Duplicate relationship type(s): %s",
                duplicateTypes
            ));
        }
        return new RelationshipProjections(unmodifiableMap(filters));
    }

    private final Map<ElementIdentifier, RelationshipProjection> filters;

    private RelationshipProjections(Map<ElementIdentifier, RelationshipProjection> filters) {
        this.filters = filters;
    }

    public RelationshipProjection getFilter(ElementIdentifier identifier) {
        RelationshipProjection filter = filters.get(identifier);
        if (filter == null) {
            throw new IllegalArgumentException("Relationship type identifier does not exist: " + identifier);
        }
        return filter;
    }

    public Collection<RelationshipProjection> allFilters() {
        return filters.values();
    }

    public RelationshipProjections addPropertyMappings(PropertyMappings mappings) {
        if (!mappings.hasMappings()) {
            return this;
        }
        Map<ElementIdentifier, RelationshipProjection> newFilters = filters.entrySet().stream().collect(toMap(
            Map.Entry::getKey,
            e -> e.getValue().withAdditionalPropertyMappings(mappings)
        ));
        if (newFilters.isEmpty()) {
            // TODO: special identifier for 'SELECT ALL'
            newFilters.put(new ElementIdentifier("*"), RelationshipProjection.empty().withAdditionalPropertyMappings(mappings));
        }
        return create(newFilters);
    }

    public String typeFilter() {
        if (isEmpty()) {
            return "";
        }
        return filters.values().stream().map(f -> f.type()).collect(Collectors.joining("|"));
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }

    public static RelationshipProjections empty() {
        return EMPTY;
    }

    public Map<String, Object> toObject() {
        Map<String, Object> value = new LinkedHashMap<>();
        filters.forEach((identifier, filter) -> {
            value.put(identifier.name, filter.toObject());
        });
        return value;
    }
}
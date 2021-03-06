/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.centrality;

import org.apache.commons.lang3.StringUtils;
import org.immutables.value.Value;
import org.neo4j.graphalgo.annotation.Configuration;
import org.neo4j.graphalgo.annotation.ValueClass;
import org.neo4j.graphalgo.config.AlgoBaseConfig;
import org.neo4j.graphalgo.config.RelationshipWeightConfig;
import org.neo4j.graphalgo.config.WriteConfig;

@Configuration("DegreeCentralityConfigImpl")
@ValueClass
public interface DegreeCentralityConfig extends AlgoBaseConfig, RelationshipWeightConfig, WriteConfig {

    String DEFAULT_SCORE_PROPERTY = "degree";

    @Configuration.Ignore
    @Value.Default
    default boolean isWeighted() {
        return StringUtils.isNotEmpty(relationshipWeightProperty());
    }

    @Value.Default
    default String writeProperty() {
        return DEFAULT_SCORE_PROPERTY;
    }
}

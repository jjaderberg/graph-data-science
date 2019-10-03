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
package org.neo4j.graphalgo.wcc;

import org.neo4j.graphalgo.api.IdMapping;
import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeLongArray;
import org.neo4j.graphalgo.core.utils.paged.HugeLongLongMap;
import org.neo4j.graphalgo.core.utils.paged.dss.DisjointSetStruct;
import org.neo4j.graphalgo.core.write.PropertyTranslator;

import java.util.stream.LongStream;
import java.util.stream.Stream;

abstract class WccResultProducer {

    private final PropertyTranslator<WccResultProducer> propertyTranslator;

    WccResultProducer(final PropertyTranslator<WccResultProducer> propertyTranslator) {
        this.propertyTranslator = propertyTranslator;
    }

    /**
     * Computes the set id of a given ID.
     *
     * @param p an id
     * @return corresponding set id
     */
    abstract long setIdOf(long p);

    PropertyTranslator<WccResultProducer> getPropertyTranslator() {
        return propertyTranslator;
    }

    /**
     * Computes the result stream based on a given ID mapping by using
     * {@link #setIdOf(long)} to look up the set representative for each node id.
     *
     * @param idMapping mapping between internal ids and Neo4j ids
     * @return tuples of Neo4j ids and their set ids
     */
    Stream<WccBaseProc.StreamResult> resultStream(IdMapping idMapping) {
        return LongStream.range(IdMapping.START_NODE_ID, idMapping.nodeCount())
                .mapToObj(mappedId -> new WccBaseProc.StreamResult(
                        idMapping.toOriginalNodeId(mappedId),
                        setIdOf(mappedId)));
    }

    static final class NonConsecutive extends WccResultProducer {

        private final DisjointSetStruct dss;

        NonConsecutive(final PropertyTranslator<WccResultProducer> translator, final DisjointSetStruct dss) {
            super(translator);
            this.dss = dss;
        }

        @Override
        public long setIdOf(final long p) {
            return dss.setIdOf(p);
        }

    }

    static final class Consecutive extends WccResultProducer {

        // Magic number to estimate the number of communities that need to be mapped into consecutive space
        private static final long MAPPING_SIZE_QUOTIENT = 10L;

        private final HugeLongArray communities;

        Consecutive(
                PropertyTranslator<WccResultProducer> propertyTranslator,
                DisjointSetStruct dss,
                AllocationTracker tracker) {
            super(propertyTranslator);

            long nextConsecutiveId = -1L;

            // TODO is there a better way to set the initial size, e.g. dss.setCount
            HugeLongLongMap setIdToConsecutiveId = new HugeLongLongMap(BitUtil.ceilDiv(
                    dss.size(),
                    MAPPING_SIZE_QUOTIENT), tracker);
            this.communities = HugeLongArray.newArray(dss.size(), tracker);

            for (int nodeId = 0; nodeId < dss.size(); nodeId++) {
                long setId = dss.setIdOf(nodeId);
                long communityId = setIdToConsecutiveId.getOrDefault(setId, -1);
                if (communityId == -1) {
                    setIdToConsecutiveId.addTo(setId, ++nextConsecutiveId);
                    communityId = nextConsecutiveId;
                }
                communities.set(nodeId, communityId);
            }
        }

        @Override
        public long setIdOf(final long p) {
            return communities.get(p);
        }
    }

    /**
     * Responsible for writing back the set ids to Neo4j.
     */
    static final class NonSeedingTranslator implements PropertyTranslator.OfLong<WccResultProducer> {

        public static final PropertyTranslator<WccResultProducer> INSTANCE = new NonSeedingTranslator();

        private NonSeedingTranslator() {}

        @Override
        public long toLong(final WccResultProducer data, final long nodeId) {
            return data.setIdOf(nodeId);
        }
    }
}
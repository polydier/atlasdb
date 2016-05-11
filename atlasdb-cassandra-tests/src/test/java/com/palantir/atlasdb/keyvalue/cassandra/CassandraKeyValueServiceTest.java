/**
 * Copyright 2016 Palantir Technologies
 * <p>
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://opensource.org/licenses/BSD-3-Clause
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.keyvalue.cassandra;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfigManager;
import com.palantir.atlasdb.encoding.PtBytes;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.api.Value;

public class CassandraKeyValueServiceTest {

    private static final String ROW = "row1";
    private static final Cell CELL = Cell.create(PtBytes.toBytes(ROW), PtBytes.toBytes("column1"));
    private KeyValueService keyValueService;
    private final AtomicLong timestamp = new AtomicLong();
    private final Random random = new Random();

    @Before
    public void setupKVS() {
        keyValueService = createKvs();
    }

    private CassandraKeyValueService createKvs() {
        return CassandraKeyValueService.create(
                CassandraKeyValueServiceConfigManager.createSimpleManager(CassandraTestSuite.CASSANDRA_KVS_CONFIG));
    }

    @Test
    public void testCreateTableCaseInsensitive() {
        TableReference table1 = TableReference.createFromFullyQualifiedName("ns.tAbLe");
        TableReference table2 = TableReference.createFromFullyQualifiedName("ns.table");
        TableReference table3 = TableReference.createFromFullyQualifiedName("ns.TABle");
        keyValueService.createTable(table1, AtlasDbConstants.GENERIC_TABLE_METADATA);
        keyValueService.createTable(table2, AtlasDbConstants.GENERIC_TABLE_METADATA);
        keyValueService.createTable(table3, AtlasDbConstants.GENERIC_TABLE_METADATA);
        Set<TableReference> allTables = keyValueService.getAllTableNames();
        Preconditions.checkArgument(allTables.contains(table1));
        Preconditions.checkArgument(!allTables.contains(table2));
        Preconditions.checkArgument(!allTables.contains(table3));
    }

    @Test
    public void shouldHandleCreatingMultipleAtlasClientsConcurrentlySafely() {
        List results = new ArrayList<>();
        IntStream.range(0, 10).forEachOrdered(i -> {
            List<CassandraKeyValueService> kvses = IntStream.range(0, 100)
                    .mapToObj(j -> createKvs())
                    .collect(toList());

            TableReference table = TableReference.createFromFullyQualifiedName("ns.testtable_" + i);

            kvses.parallelStream().forEach(kvs -> {
                createTable(kvs, table);
                writeValue(kvs, table);
            });

            Set<Value> reads = kvses.stream()
                    .map(kvs -> getValues(kvs, table))
                    .collect(toSet());

            results.add(reads);
        });

        System.out.println(results.stream().map(Object::toString).collect(joining("\n")));
    }

    private Value getValues(CassandraKeyValueService kvs, TableReference table) {
        return kvs.get(table, ImmutableMap.of(CELL, Long.MAX_VALUE)).get(CELL);
    }

    private void writeValue(CassandraKeyValueService kvs, TableReference table) {
        kvs.put(table, ImmutableMap.of(CELL, PtBytes.toBytes(randomPositiveLong())), timestamp());
    }

    private long randomPositiveLong() {
        long value = random.nextLong();
        if (value > 0) {
            return value;
        }

        return randomPositiveLong();
    }

    private long timestamp() {
        return timestamp.incrementAndGet();
    }

    private void createTable(CassandraKeyValueService kvs, TableReference table) {
        System.out.println("CREATING TABLE: " + table);
        kvs.createTable(table, AtlasDbConstants.GENERIC_TABLE_METADATA);
    }

}

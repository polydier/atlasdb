/**
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.cassandra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.stream.LongStream;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.CfDef;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.collect.ConcurrentHashMultiset;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.cassandra.ImmutableCassandraKeyValueServiceConfig;
import com.palantir.docker.compose.DockerComposition;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.HealthCheck;
import com.palantir.docker.compose.connection.waiting.SuccessOrFailure;

public class CassandraBugRepro {
    private CassandraClientPool clientPool;

    public static final int THRIFT_PORT_NUMBER = 9160;

    @ClassRule
    public static final DockerComposition composition = DockerComposition.of("src/test/resources/docker-compose.yml")
            .waitingForHostNetworkedPort(THRIFT_PORT_NUMBER, toBeOpen())
            .saveLogsTo("container-logs")
            .build();

    static InetSocketAddress CASSANDRA_THRIFT_ADDRESS;

    static CassandraKeyValueServiceConfig CASSANDRA_KVS_CONFIG;

    @BeforeClass
    public static void waitUntilCassandraIsUp() throws IOException, InterruptedException {
        DockerPort port = composition.hostNetworkedPort(THRIFT_PORT_NUMBER);
        CASSANDRA_THRIFT_ADDRESS = new InetSocketAddress(port.getIp(), port.getExternalPort());

        CASSANDRA_KVS_CONFIG = ImmutableCassandraKeyValueServiceConfig.builder()
                .addServers(CASSANDRA_THRIFT_ADDRESS)
                .poolSize(20)
                .keyspace("atlasdb")
                .ssl(false)
                .replicationFactor(1)
                .mutationBatchCount(10000)
                .mutationBatchSizeBytes(10000000)
                .fetchBatchCount(1000)
                .safetyDisabled(false)
                .autoRefreshNodes(false)
                .build();

    }

    @Before public void setup() throws TException {
        clientPool = new CassandraClientPool(CASSANDRA_KVS_CONFIG);
        CassandraVerifier.ensureKeyspaceExistsAndIsUpToDate(clientPool, CASSANDRA_KVS_CONFIG);
    }

    @Test public void asynchronous() {
        long runs = 32;
        ConcurrentHashMultiset<String> ids = ConcurrentHashMultiset.create();

        LongStream.range(0, runs).parallel().forEach(i -> {
            clientPool.runWithRetry(client -> {
                Optional<String> possibleId = createTable(client, "asyncTest");
                possibleId.ifPresent(id -> ids.add(id, 1));
                return null;
            });
        });

        System.out.println(ids.elementSet());

        assertThat(ids.elementSet(), hasSize(1));
    }

    private Optional<String> createTable(Cassandra.Client client, String table) {
        try {
            String result = client.system_add_column_family(new CfDef("atlasdb", table));
            return Optional.of(result);
        } catch (TException e) {
            return Optional.empty();
        }
    }

    private static HealthCheck<DockerPort> toBeOpen() {
        return port -> SuccessOrFailure.fromBoolean(port.isListeningNow(), "" + "" + port + " was not open");
    }
}

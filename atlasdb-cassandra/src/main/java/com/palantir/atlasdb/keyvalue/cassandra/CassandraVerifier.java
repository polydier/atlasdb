/**
 * Copyright 2015 Palantir Technologies
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

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Cassandra.Client;
import org.apache.cassandra.thrift.CfDef;
import org.apache.cassandra.thrift.EndpointDetails;
import org.apache.cassandra.thrift.InvalidRequestException;
import org.apache.cassandra.thrift.KsDef;
import org.apache.cassandra.thrift.SchemaDisagreementException;
import org.apache.cassandra.thrift.TokenRange;
import org.apache.commons.lang.Validate;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.keyvalue.api.TableReference;
import com.palantir.atlasdb.keyvalue.cassandra.CassandraClientFactory.ClientCreationFailedException;
import com.palantir.common.base.FunctionCheckedException;
import com.palantir.common.collect.Maps2;

public class CassandraVerifier {

    private static final Logger log = LoggerFactory.getLogger(CassandraVerifier.class);

    static void validateCassandraSetup(Cassandra.Client client, KsDef ks, boolean freshInstance, int desiredRf)
            throws InvalidRequestException, SchemaDisagreementException, TException {

        // check for test keyspace and create it if it doesn't exist
        List<String> existingKeyspaces = Lists.transform(client.describe_keyspaces(), KsDef::getName);
        if (!existingKeyspaces.contains(CassandraConstants.SIMPLE_RF_TEST_KEYSPACE)) {
            client.system_add_keyspace(
                    new KsDef(CassandraConstants.SIMPLE_RF_TEST_KEYSPACE, CassandraConstants.SIMPLE_STRATEGY, ImmutableList.<CfDef>of())
                    .setStrategy_options(ImmutableMap.of(CassandraConstants.REPLICATION_FACTOR_OPTION, "1")));
        }

        // populate topology information using the test keyspace
        List<TokenRange> ring = client.describe_ring(CassandraConstants.SIMPLE_RF_TEST_KEYSPACE);
        Set<String> hosts = Sets.newHashSet();
        Multimap<String, String> dataCenterToRack = HashMultimap.create();
        for (TokenRange tokenRange : ring) {
            for (EndpointDetails details : tokenRange.getEndpoint_details()) {
                dataCenterToRack.put(details.datacenter, details.rack);
                hosts.add(details.host);
            }
        }

        if (freshInstance) {
            ks.setStrategy_options(Maps2.createConstantValueMap(dataCenterToRack.keySet(), String.valueOf(desiredRf)));
        }

        CassandraVerifier.checkDatacenters(dataCenterToRack, hosts, desiredRf);
        CassandraVerifier.checkReplicationStrategy(ks, dataCenterToRack.keySet(), desiredRf);
    }

    static void checkDatacenters(Multimap<String, String> dataCenterToRack, Set<String> hosts, int desiredRf)
            throws InvalidRequestException, TException {
        if (dataCenterToRack.size() == 1) {
            String dc = dataCenterToRack.keySet().iterator().next();
            String rack = dataCenterToRack.values().iterator().next();
            if (dc.equals(CassandraConstants.DEFAULT_DC) && rack.equals(CassandraConstants.DEFAULT_RACK) && desiredRf > 1) {
                // We don't allow greater than RF=1 because they didn't set up their network.
                throw new RuntimeException("The cassandra cluster is not set up to be datacenter and rack aware.  " +
                        "Please set this up before running with a replication factor higher than 1.");

            }
            if (dataCenterToRack.values().size() < desiredRf && hosts.size() > desiredRf) {
                throw new RuntimeException("The cassandra cluster only has one DC, " +
                        "and is set up with less racks than the desired number of replicas, " +
                        "and there are more hosts than the replication factor. " +
                        "It is very likely that your rack configuration is incorrect and replicas would not be placed correctly for the failure tolerance you want.");
            }
            if (hosts.size() < desiredRf) {
                throw new RuntimeException(String.format("The cassandra cluster only has %d nodes, and the desired replication " +
                        "factor of %d is greater than that.  Your replication factor is likely incorrect for your setup.",
                        hosts.size(), desiredRf));
            }
        }
    }

    static void sanityCheckTableName(TableReference tableRef) {
        String tableName = tableRef.getQualifiedName();
        Validate.isTrue(!(tableName.startsWith("_") && tableName.contains("."))
                || AtlasDbConstants.hiddenTables.contains(tableRef)
                || tableName.startsWith(AtlasDbConstants.NAMESPACE_PREFIX), "invalid tableName: " + tableName);
    }

    // We don't have an established strategy for configuring and validating multi-datacenter deployments,
    // so in those cases log the error and continue instead of throwing it
    static void logErrorOrThrow(String errorMessage, int numDcs) {
        String safetyMessage = " This would have normally resulted in Palantir exiting, however this "
                + "check was disabled because you have multiple datacenters in your cassandra cluster.";
        if (numDcs > 1) {
            log.error(errorMessage + safetyMessage);
        } else {
            throw new IllegalStateException(errorMessage);
        }
    }

    static void validatePartitioner(Cassandra.Client client, CassandraKeyValueServiceConfig config) throws TException {
        String partitioner = client.describe_partitioner();
        if (!CassandraConstants.ALLOWED_PARTITIONERS.contains(partitioner)) {
            String errorMessage = String.format("Invalid partitioner. Allowed: %s, but partitioner is: %s",
                        CassandraConstants.ALLOWED_PARTITIONERS, partitioner);
            throw new RuntimeException(errorMessage);
        }
    }

    static void ensureKeyspaceExistsAndIsUpToDate(CassandraClientPool clientPool, CassandraKeyValueServiceConfig config)
            throws InvalidRequestException, TException, SchemaDisagreementException {
        try {
            clientPool.run(new FunctionCheckedException<Cassandra.Client, Void, TException>() {
                @Override
                public Void apply(Cassandra.Client client) throws TException {
                    KsDef ks = client.describe_keyspace(config.keyspace());
                    CassandraVerifier.validateCassandraSetup(client, ks,
                            false, config.replicationFactor());
                    return null;
                }
            });
        } catch (ClientCreationFailedException e) { // fresh instance
            for (InetSocketAddress host : config.servers()) { // try until we find a server that works
                try {
                    Client client = CassandraClientFactory.getClientInternal(
                            host, config.ssl(), config.socketTimeoutMillis(), config.socketQueryTimeoutMillis());
                    KsDef ks = new KsDef(config.keyspace(), CassandraConstants.NETWORK_STRATEGY, ImmutableList.<CfDef>of());
                    CassandraVerifier.validateCassandraSetup(client, ks, true, config.replicationFactor());

                    ks.setDurable_writes(true);
                    client.system_add_keyspace(ks);
                    CassandraKeyValueServices.waitForSchemaVersions(client, "(adding the initial empty keyspace)", config.schemaMutationTimeoutMillis());

                    // if we got this far, we're done
                    return;
                } catch (Exception f) {
                    throw new TException(f);
                }
            }
        }
    }

    // Disallow SimpleStrategy (i.e. only allow NetworkTopologyStrategy)
    // We need to worry about users altering replication levels without proper repairs/maintenance.
    static void checkReplicationStrategy(KsDef ks, Set<String> dcs, int desiredRf) {
        if (CassandraConstants.SIMPLE_STRATEGY.equals(ks.getStrategy_class())) {
            String errorMessage = "This cassandra cluster is running using the simple partitioning strategy.  " +
                    "This partitioner is not rack aware and is not intended for use on prod.  " +
                    "This will have to be fixed by manually configuring the network partitioner " +
                    "and running the appropriate repairs.";
            throw new RuntimeException(errorMessage);
        }
        
        Map<String, String> strategyOptions = Maps.newHashMap(ks.getStrategy_options());
        for (String dc : dcs) {
            String so = strategyOptions.get(dc);
            if (so == null) {
                logErrorOrThrow("The datacenter for this cassandra cluster is invalid. " +
                        " failed dc: " + dc +
                        "  strategyOptions: " + strategyOptions, dcs.size());
                continue;
            }
            int currentRF = Integer.parseInt(so);
            if (currentRF != desiredRf) {
                throw new UnsupportedOperationException("Your current Cassandra keyspace (" + ks.getName() +
                        ") has a replication factor not matching your Atlas Cassandra configuration. " +
                        "Change them to match, but be mindful of what steps you'll need to " +
                        "take to correctly repair or cleanup existing data in your cluster.");
            }
        }
    }
}

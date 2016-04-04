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

package com.palantir.atlasdb.timelock;

import java.io.InputStream;
import java.util.function.Function;

import org.junit.ClassRule;
import org.junit.Test;

import com.google.common.base.Optional;
import com.palantir.atlasdb.config.AtlasDbConfig;
import com.palantir.atlasdb.factory.TransactionManagers;
import com.palantir.atlasdb.table.description.Schema;
import com.palantir.atlasdb.table.description.TableDefinition;
import com.palantir.atlasdb.table.description.ValueType;
import com.palantir.atlasdb.transaction.api.TransactionManager;
import com.palantir.docker.compose.DockerComposition;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.HealthCheck;

public class AtlasWithRemoteTimelockServerTest {
    public static final int TIMELOCK_SERVER_PORT = 3828;
//    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

    @ClassRule
    public static DockerComposition composition = DockerComposition.of("atlasdb-timelock-server/timelock-ete/docker-compose.yml")
            .waitingForService("timelock1", toBePingable())
            .waitingForService("timelock2", toBePingable())
            .waitingForService("timelock3", toBePingable())
            .saveLogsTo("atlasdb-timelock-server/timelock-ete/container-logs")
            .build();

    private static HealthCheck toBePingable() {
        return container -> container.portMappedInternallyTo(TIMELOCK_SERVER_PORT).isHttpResponding(onPingEndpoint());
    }

    private static Function<DockerPort, String> onPingEndpoint() {
        return port -> "http://" + port.getIp() + ":" + port.getExternalPort() + "/leader/ping";
    }

    @Test public void shouldBeAbleToWrite50rows() throws Exception {
        TransactionManager transactionManager = TransactionManagers.create(
                config(),
                Optional.absent(),
                schema(),
                service -> {},
                false
        );

    }

    private AtlasDbConfig config() {
        InputStream file = this.getClass().getResourceAsStream("/test-atlas-config.yml");
//        try {
            return null;
//            return OBJECT_MAPPER.readValue(file, AtlasDbConfig.class);
//        } catch (IOException e) {
//            throw propagate(e);
//        }
    }

    private Schema schema() {
        Schema schema = new Schema();

        schema.addTableDefinition("blobs", new TableDefinition() {{
            rowName();
            rowComponent("id", ValueType.FIXED_LONG);
            columns();
            column("data", "d", ValueType.BLOB);
        }});

        return schema;
    }
}

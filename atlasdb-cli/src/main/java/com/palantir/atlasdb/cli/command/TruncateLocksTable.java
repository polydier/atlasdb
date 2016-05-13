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
package com.palantir.atlasdb.cli.command;

import com.palantir.atlasdb.cassandra.CassandraKeyValueServiceConfig;
import com.palantir.atlasdb.keyvalue.cassandra.CassandraKeyValueServices;
import com.palantir.atlasdb.spi.KeyValueServiceConfig;

import io.airlift.airline.Command;

@Command(name = "truncate-locks-table", description = "Truncate a potentially inconsistent _locks table")
public class TruncateLocksTable extends AbstractCommand {

    @Override
    public Integer call() throws Exception {
        KeyValueServiceConfig kvsConfig = getServicesConfigModule().provideAtlasDbConfig().keyValueService();
        if(!(kvsConfig instanceof CassandraKeyValueServiceConfig)) {
            System.err.printf("Error: Backing key value service must be of type cassandra, but yours is %s\n",
                    kvsConfig.type());
            return 1;
        }
        CassandraKeyValueServiceConfig ckvsConfig = (CassandraKeyValueServiceConfig) kvsConfig;

        CassandraKeyValueServices.truncateLocksTable(ckvsConfig);
        return 0;
    }

}

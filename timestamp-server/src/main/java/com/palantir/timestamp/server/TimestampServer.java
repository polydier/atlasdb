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
package com.palantir.timestamp.server;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.palantir.atlasdb.client.TextDelegateDecoder;
import com.palantir.atlasdb.keyvalue.leveldb.impl.LevelDbBoundStore;
import com.palantir.atlasdb.keyvalue.leveldb.impl.LevelDbKeyValueService;
import com.palantir.common.base.Throwables;
import com.palantir.common.concurrent.PTExecutors;
import com.palantir.leader.PaxosLeaderElectionService;
import com.palantir.leader.PingableLeader;
import com.palantir.leader.proxy.AwaitingLeadershipProxy;
import com.palantir.lock.RemoteLockService;
import com.palantir.lock.impl.LockServiceImpl;
import com.palantir.paxos.PaxosAcceptor;
import com.palantir.paxos.PaxosAcceptorImpl;
import com.palantir.paxos.PaxosLearner;
import com.palantir.paxos.PaxosLearnerImpl;
import com.palantir.paxos.PaxosProposer;
import com.palantir.paxos.PaxosProposerImpl;
import com.palantir.timestamp.InMemoryTimestampService;
import com.palantir.timestamp.PersistentTimestampService;
import com.palantir.timestamp.RateLimitedTimestampService;
import com.palantir.timestamp.TimestampService;
import com.palantir.timestamp.server.config.TimestampServerConfiguration;
import com.palantir.timestamp.server.config.TimestampServerConfiguration.ServerType;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;
import jersey.repackaged.com.google.common.collect.Lists;

public class TimestampServer extends Application<TimestampServerConfiguration> {

    public static void main(String[] args) throws Exception {
        new TimestampServer().run(args);
    }

    private final ExecutorService executor = PTExecutors.newCachedThreadPool();

    private <T> List<T> getRemoteServices(List<String> uris, Class<T> iFace) {
    	ObjectMapper mapper = new ObjectMapper();
        List<T> ret = Lists.newArrayList();
        for (String uri : uris) {
            T service = Feign.builder()
                    .decoder(new TextDelegateDecoder(new JacksonDecoder()))
                    .encoder(new JacksonEncoder(mapper))
                    .contract(new JAXRSContract())
                    .target(iFace, uri);
            ret.add(service);
        }
        return ret;
    }

    @Override
    public void run(TimestampServerConfiguration configuration, Environment environment) throws Exception {
    	PaxosLearner learner = PaxosLearnerImpl.newLearner(configuration.learnerLogDir);
    	PaxosAcceptor acceptor = PaxosAcceptorImpl.newAcceptor(configuration.acceptorLogDir);
        environment.jersey().register(acceptor);
        environment.jersey().register(learner);

        List<PaxosLearner> learners = getRemoteServices(configuration.servers, PaxosLearner.class);
        learners.set(configuration.localIndex, learner);
        List<PaxosAcceptor> acceptors = getRemoteServices(configuration.servers, PaxosAcceptor.class);
        acceptors.set(configuration.localIndex, acceptor);

        List<PingableLeader> otherLeaders = getRemoteServices(configuration.servers, PingableLeader.class);
        otherLeaders.remove(configuration.localIndex);

        PaxosProposer proposer = PaxosProposerImpl.newProposer(
        		learner,
        		acceptors,
        		learners,
        		configuration.quorumSize,
        		executor);
        PaxosLeaderElectionService leader = new PaxosLeaderElectionService(
                proposer,
                learner,
                otherLeaders,
                acceptors,
                learners,
                executor,
                1000,
                1000,
                5000);
        environment.jersey().register(leader);
        environment.jersey().register(createTimestampService(leader, configuration));
        environment.jersey().register(createLockService(leader));
        environment.jersey().register(new NotCurrentLeaderExceptionMapper());
    }

    private RemoteLockService createLockService(PaxosLeaderElectionService leader) {
        RemoteLockService lock = AwaitingLeadershipProxy.newProxyInstance(RemoteLockService.class, new Supplier<RemoteLockService>() {
            @Override
            public RemoteLockService get() {
                return LockServiceImpl.create();
            }
        }, leader);
        return lock;
    }

    private TimestampService createTimestampService(PaxosLeaderElectionService leader, final TimestampServerConfiguration config) {
        if (config.serverType == ServerType.LEVELDB) {
            Preconditions.checkArgument(config.servers.size() == 1, "only one server allowed for LevelDB");
        }
        TimestampService timestamp = AwaitingLeadershipProxy.newProxyInstance(TimestampService.class, new Supplier<TimestampService>() {
            @Override
            public TimestampService get() {
                if (config.serverType == ServerType.LEVELDB) {
                    try {
                        return PersistentTimestampService.create(LevelDbBoundStore.create(LevelDbKeyValueService.create(new File(config.levelDbDir))));
                    } catch (IOException e) {
                        throw Throwables.throwUncheckedException(e);
                    }
                }
                return new RateLimitedTimestampService(new InMemoryTimestampService(), 0L);
            }
        }, leader);
        return timestamp;
    }
}

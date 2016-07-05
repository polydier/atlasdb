/*
 * Copyright 2016 Palantir Technologies
 * ​
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * ​
 * http://opensource.org/licenses/BSD-3-Clause
 * ​
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.paxos;

import static com.google.common.collect.ImmutableList.copyOf;

import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import com.google.common.base.Defaults;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

public class Paxos {
    private final PaxosProposer proposer;
    private final PaxosLearner knowledge;
    private final List<PaxosAcceptor> acceptors;
    private final List<PaxosLearner> learners;

    final ExecutorService executor;

    public Paxos(PaxosProposer proposer, PaxosLearner knowledge, List<PaxosAcceptor> acceptors, List<PaxosLearner> learners, ExecutorService executorService) {
        this.proposer = proposer;
        this.knowledge = knowledge;
        this.acceptors = acceptors;
        this.learners = learners;

        this.executor = executorService;
    }

    public PaxosValue getGreatestLearnedLocalValue() {
        return knowledge.getGreatestLearnedValue();
    }

    public String getProposerUuid() {
        return proposer.getUUID();
    }

    public void propose(PaxosKey key, @Nullable byte[] proposalValue) throws PaxosRoundFailureException {
        proposer.propose(key, proposalValue);
    }

    /**
     * Queries all other learners for unknown learned values
     *
     * @param numPeersToQuery number of peer learners to query for updates
     * @returns true if new state was learned, otherwise false
     */
    public boolean updateLearnedStateFromPeers(PaxosValue greatestLearned) {
        final long nextToLearnSeq = greatestLearned != null ? greatestLearned.getRound().seq() + 1 : Defaults.defaultValue(long.class);
        List<PaxosUpdate> updates = PaxosQuorumChecker.<PaxosLearner, PaxosUpdate>collectQuorumResponses(
                ImmutableList.copyOf(learners),
                new Function<PaxosLearner, PaxosUpdate>() {
                    @Override
                    @Nullable
                    public PaxosUpdate apply(@Nullable PaxosLearner learner) {
                        return new PaxosUpdate(
                                copyOf(learner.getLearnedValuesSince(nextToLearnSeq)));
                    }
                },
                proposer.getQuorumSize(),
                executor,
                PaxosQuorumChecker.DEFAULT_REMOTE_REQUESTS_TIMEOUT_IN_SECONDS);

        // learn the state accumulated from peers
        boolean learned = false;
        for (PaxosUpdate update : updates) {
            ImmutableCollection<PaxosValue> values = update.getValues();
            for (PaxosValue value : values) {
                PaxosValue currentLearnedValue = knowledge.getLearnedValue(value.getRound().seq());
                if (currentLearnedValue == null) {
                    knowledge.learn(value.getRound().seq(), value);
                    learned = true;
                }
            }
        }

        return learned;
    }

    public int getQuorumSize() {
        return proposer.getQuorumSize();
    }

    public List<PaxosAcceptor> getAcceptors() {
        return acceptors;
    }
}

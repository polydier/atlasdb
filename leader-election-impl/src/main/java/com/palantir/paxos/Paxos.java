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

import java.util.List;

import com.palantir.paxos.PaxosAcceptor;
import com.palantir.paxos.PaxosLearner;
import com.palantir.paxos.PaxosProposer;

public class Paxos {
    private final PaxosProposer proposer;
    private final PaxosLearner knowledge;
    private final List<PaxosAcceptor> acceptors;
    private final List<PaxosLearner> learners;

    public Paxos(PaxosProposer proposer, PaxosLearner knowledge, List<PaxosAcceptor> acceptors, List<PaxosLearner> learners) {
        this.proposer = proposer;
        this.knowledge = knowledge;
        this.acceptors = acceptors;
        this.learners = learners;
    }

    public PaxosProposer getProposer() {
        return proposer;
    }

    public PaxosLearner getKnowledge() {
        return knowledge;
    }

    public List<PaxosAcceptor> getAcceptors() {
        return acceptors;
    }

    public List<PaxosLearner> getLearners() {
        return learners;
    }
}

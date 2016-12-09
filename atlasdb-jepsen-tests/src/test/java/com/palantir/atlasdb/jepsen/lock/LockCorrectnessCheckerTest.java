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
package com.palantir.atlasdb.jepsen.lock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.palantir.atlasdb.jepsen.CheckerResult;
import com.palantir.atlasdb.jepsen.events.Event;
import com.palantir.atlasdb.jepsen.events.ImmutableInvokeEvent;
import com.palantir.atlasdb.jepsen.events.ImmutableOkEvent;
import com.palantir.atlasdb.jepsen.events.RequestType;

public class LockCorrectnessCheckerTest {
    private final long SUCCESS = 1L;
    private final String LOCK1 = "lock_1";

    @Test
    public void shouldSucceedOnNoEvents() {
        CheckerResult result = runLockCorrectnessChecker();

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void shouldSucceedWhenNoRefreshes(){
        long time = 0;
        Event event1 = createInvokeEvent(time++, 0, RequestType.LOCK, LOCK1);
        Event event2 = createInvokeEvent(time++, 1, RequestType.LOCK, LOCK1);
        Event event3 = createOkEvent(time++, 1, SUCCESS, RequestType.LOCK, LOCK1);
        Event event4 = createOkEvent(time++, 0, SUCCESS, RequestType.LOCK, LOCK1);

        CheckerResult result = runLockCorrectnessChecker(event1, event2, event3, event4);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void shouldSucceedWhenNoLocks(){
        long time = 0;
        Event event1 = createInvokeEvent(time++, 0, RequestType.REFRESH, LOCK1);
        Event event2 = createOkEvent(time++, 0, SUCCESS, RequestType.REFRESH, LOCK1);
        Event event3 = createInvokeEvent(time++, 1, RequestType.UNLOCK, LOCK1);
        Event event4 = createOkEvent(time++, 1, SUCCESS, RequestType.UNLOCK, LOCK1);

        CheckerResult result = runLockCorrectnessChecker(event1, event2, event3, event4);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void shouldFailWhenLockIsGrantedWhenNotFree(){
        long time = 0;
        Event event1 = createInvokeEvent(time++, 0, RequestType.LOCK, LOCK1);
        Event event2 = createOkEvent(time++, 0, SUCCESS, RequestType.LOCK, LOCK1);
        Event event3 = createInvokeEvent(time++, 1, RequestType.LOCK, LOCK1);
        Event event4 = createOkEvent(time++, 1, SUCCESS, RequestType.LOCK, LOCK1);
        Event event5 = createInvokeEvent(time++, 0, RequestType.REFRESH, LOCK1);
        Event event6 = createOkEvent(time++, 0, SUCCESS, RequestType.REFRESH, LOCK1);

        CheckerResult result = runLockCorrectnessChecker(event1, event2, event3, event4, event5, event6);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly(event3, event4);
    }

    @Test
    public void shouldSucceedWhenThereIsASmallWindow(){
        long time = 0;
        Event event1 = createInvokeEvent(0, 0, RequestType.LOCK, LOCK1);
        Event event2 = createOkEvent(1, 0, SUCCESS, RequestType.LOCK, LOCK1);
        Event event3 = createInvokeEvent(2, 1, RequestType.LOCK, LOCK1);
        Event event4 = createInvokeEvent(2, 2, RequestType.LOCK, LOCK1);
        Event event5 = createInvokeEvent(3, 0, RequestType.REFRESH, LOCK1);
        Event event6 = createOkEvent(3, 0, SUCCESS, RequestType.REFRESH, LOCK1);
        Event event7 = createOkEvent(3, 2, SUCCESS, RequestType.LOCK, LOCK1);
//        Event event6 = createInvokeEvent(time++, 0, RequestType.LOCK, LOCK1);
//        Event event7 = createOkEvent(time++, 0, SUCCESS, RequestType.LOCK, LOCK1);
        Event event8 = createOkEvent(4, 1, SUCCESS, RequestType.LOCK, LOCK1);
        Event event9 = createInvokeEvent(5, 2, RequestType.REFRESH, LOCK1);
        Event eventA = createOkEvent(6, 2, SUCCESS, RequestType.REFRESH, LOCK1);

        CheckerResult result = runLockCorrectnessChecker(event1, event2, event3, event4, event5,event6, event7, event8, event9, eventA );

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    public void shouldSucceedForIntervalEdgeCases(){
        long time = 0;
        Event event1 = createInvokeEvent(time++, 0, RequestType.LOCK, LOCK1);
        Event event2 = createOkEvent(time, 0, SUCCESS, RequestType.LOCK, LOCK1);
        Event event3 = createInvokeEvent(time++, 1, RequestType.LOCK, LOCK1);
        Event event4 = createOkEvent(time++, 1, SUCCESS, RequestType.LOCK, LOCK1);
        Event event5 = createInvokeEvent(time++, 2, RequestType.LOCK, LOCK1);
        Event event6 = createOkEvent(time, 2, SUCCESS, RequestType.LOCK, LOCK1);
        Event event7 = createInvokeEvent(time++, 0, RequestType.REFRESH, LOCK1);
        Event event8 = createOkEvent(time++, 0, SUCCESS, RequestType.REFRESH, LOCK1);

        CheckerResult result = runLockCorrectnessChecker(event1, event2, event3, event4, event5, event6, event7, event8);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    private ImmutableInvokeEvent createInvokeEvent(long time, int process, RequestType requestType, String resourceName) {
        return ImmutableInvokeEvent.builder()
                .time(time)
                .process(process)
                .requestType(requestType)
                .resourceName(resourceName)
                .build();
    }

    private ImmutableOkEvent createOkEvent(long time, int process, long value, RequestType requestType, String resourceName) {
        return ImmutableOkEvent.builder()
                .time(time)
                .process(process)
                .value(value)
                .requestType(requestType)
                .resourceName(resourceName)
                .build();
    }

    private static CheckerResult runLockCorrectnessChecker(Event... events) {
        LockCorrectnessChecker lockCorrectnessChecker = new LockCorrectnessChecker();
        return lockCorrectnessChecker.check(ImmutableList.copyOf(events));
    }
}
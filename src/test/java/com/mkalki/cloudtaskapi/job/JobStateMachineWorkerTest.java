package com.mkalki.cloudtaskapi.job;

import com.mkalki.cloudtaskapi.job.exception.IllegalJobTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.mkalki.cloudtaskapi.job.JobStatus.DEAD;
import static com.mkalki.cloudtaskapi.job.JobStatus.PENDING;
import static com.mkalki.cloudtaskapi.job.JobStatus.QUEUED;
import static com.mkalki.cloudtaskapi.job.JobStatus.RUNNING;
import static com.mkalki.cloudtaskapi.job.JobStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The worker-side methods of the state machine: claim and the fenced result. */
class JobStateMachineWorkerTest {

    private static final Instant T0 = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-12-01T10:00:30Z");

    private final JobRepository repository = mock(JobRepository.class);
    private final JobStateMachine stateMachine = new JobStateMachine(repository);
    private final UUID id = UUID.randomUUID();

    @Test
    void claimRunsOneConditionalUpdateFromQueuedToRunning() {
        when(repository.claimIfQueued(id, QUEUED, RUNNING, "w1", T0, T1)).thenReturn(1);

        assertThat(stateMachine.claim(id, "w1", T0, T1)).isTrue();

        verify(repository).claimIfQueued(id, QUEUED, RUNNING, "w1", T0, T1);
    }

    @Test
    void claimReturnsFalseWhenTheRowIsNotQueued() {
        when(repository.claimIfQueued(id, QUEUED, RUNNING, "w1", T0, T1)).thenReturn(0);

        assertThat(stateMachine.claim(id, "w1", T0, T1)).isFalse();
    }

    @Test
    void aSuccessIsWrittenWithTheFenceAndNoError() {
        when(repository.updateStatusIfOwned(id, RUNNING, SUCCEEDED, 2, "w1", null, T1, T1)).thenReturn(1);

        assertThat(stateMachine.transitionIfOwned(id, 2, "w1", SUCCEEDED, null, T1)).isTrue();
    }

    @Test
    void aFailureIsWrittenWithTheFenceAndTheError() {
        when(repository.updateStatusIfOwned(id, RUNNING, DEAD, 1, "w1", "boom", T1, T1)).thenReturn(1);

        assertThat(stateMachine.transitionIfOwned(id, 1, "w1", DEAD, "boom", T1)).isTrue();
    }

    @Test
    void aFencedResultMatchesNoRowAndReturnsFalse() {
        when(repository.updateStatusIfOwned(id, RUNNING, SUCCEEDED, 1, "old-worker", null, T1, T1)).thenReturn(0);

        assertThat(stateMachine.transitionIfOwned(id, 1, "old-worker", SUCCEEDED, null, T1)).isFalse();
    }

    @Test
    void aRetryMoveIsLegalAndLeavesFinishedAtEmpty() {
        when(repository.updateStatusIfOwned(id, RUNNING, PENDING, 1, "w1", "again", T1, null)).thenReturn(1);

        assertThat(stateMachine.transitionIfOwned(id, 1, "w1", PENDING, "again", T1)).isTrue();
    }

    @Test
    void anIllegalTargetIsRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> stateMachine.transitionIfOwned(id, 1, "w1", QUEUED, null, T1))
                .isInstanceOf(IllegalJobTransitionException.class);

        verifyNoInteractions(repository);
    }
}

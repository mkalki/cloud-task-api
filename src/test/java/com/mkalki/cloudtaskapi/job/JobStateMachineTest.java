package com.mkalki.cloudtaskapi.job;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mkalki.cloudtaskapi.job.exception.IllegalJobTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.mkalki.cloudtaskapi.job.JobStatus.BLOCKED;
import static com.mkalki.cloudtaskapi.job.JobStatus.CANCELLED;
import static com.mkalki.cloudtaskapi.job.JobStatus.DEAD;
import static com.mkalki.cloudtaskapi.job.JobStatus.PENDING;
import static com.mkalki.cloudtaskapi.job.JobStatus.QUEUED;
import static com.mkalki.cloudtaskapi.job.JobStatus.RUNNING;
import static com.mkalki.cloudtaskapi.job.JobStatus.SUCCEEDED;
import static com.mkalki.cloudtaskapi.job.JobStatus.UPSTREAM_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Checks every one of the 8 x 8 status pairs against the table in PRD section 8.1. */
class JobStateMachineTest {

    private static final Instant T0 = Instant.parse("2026-12-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-12-01T10:05:00Z");

    /** Written out by hand, not read from the production table, so a typo in either shows up. */
    private static final Set<List<JobStatus>> LEGAL = Set.of(
            List.of(BLOCKED, PENDING),
            List.of(BLOCKED, UPSTREAM_FAILED),
            List.of(BLOCKED, CANCELLED),
            List.of(PENDING, QUEUED),
            List.of(PENDING, CANCELLED),
            List.of(QUEUED, RUNNING),
            List.of(QUEUED, CANCELLED),
            List.of(RUNNING, SUCCEEDED),
            List.of(RUNNING, PENDING),
            List.of(RUNNING, DEAD),
            List.of(RUNNING, CANCELLED),
            List.of(DEAD, PENDING),
            List.of(UPSTREAM_FAILED, BLOCKED)
    );

    private final JobRepository repository = mock(JobRepository.class);
    private final JobStateMachine stateMachine = new JobStateMachine(repository);

    static Stream<List<JobStatus>> legalPairs() {
        return LEGAL.stream();
    }

    static Stream<List<JobStatus>> illegalPairs() {
        return Arrays.stream(JobStatus.values())
                .flatMap(from -> Arrays.stream(JobStatus.values()).map(to -> List.of(from, to)))
                .filter(pair -> !LEGAL.contains(pair));
    }

    @Test
    void tableCoversAllSixtyFourPairs() {
        assertThat(legalPairs().count() + illegalPairs().count()).isEqualTo(64);
        assertThat(legalPairs().count()).isEqualTo(13);
    }

    @ParameterizedTest(name = "{0} is legal")
    @MethodSource("legalPairs")
    void legalTransitionChangesStatus(List<JobStatus> pair) {
        Job job = jobIn(pair.get(0));

        assertThat(stateMachine.canTransition(pair.get(0), pair.get(1))).isTrue();
        stateMachine.transition(job, pair.get(1), T1);

        assertThat(job.getStatus()).isEqualTo(pair.get(1));
        assertThat(job.getUpdatedAt()).isEqualTo(T1);
    }

    @ParameterizedTest(name = "{0} is illegal")
    @MethodSource("illegalPairs")
    void illegalTransitionThrowsAndLeavesJobUntouched(List<JobStatus> pair) {
        Job job = jobIn(pair.get(0));

        assertThat(stateMachine.canTransition(pair.get(0), pair.get(1))).isFalse();
        assertThatThrownBy(() -> stateMachine.transition(job, pair.get(1), T1))
                .isInstanceOf(IllegalJobTransitionException.class)
                .hasMessageContaining(pair.get(0) + " -> " + pair.get(1));

        assertThat(job.getStatus()).isEqualTo(pair.get(0));
        assertThat(job.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void enteringTerminalStatusSetsFinishedAt() {
        Job job = jobIn(PENDING);

        stateMachine.transition(job, CANCELLED, T1);

        assertThat(job.getFinishedAt()).isEqualTo(T1);
    }

    @Test
    void leavingTerminalStatusClearsFinishedAt() {
        Job job = jobIn(DEAD);
        job.setFinishedAt(T0);

        stateMachine.transition(job, PENDING, T1);

        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void movingBetweenActiveStatusesLeavesFinishedAtEmpty() {
        Job job = jobIn(PENDING);

        stateMachine.transition(job, QUEUED, T1);

        assertThat(job.getFinishedAt()).isNull();
    }

    @Test
    void succeededAndCancelledAreFinal() {
        for (JobStatus to : JobStatus.values()) {
            assertThat(stateMachine.canTransition(SUCCEEDED, to)).isFalse();
            assertThat(stateMachine.canTransition(CANCELLED, to)).isFalse();
        }
    }

    @Test
    void everyNonTerminalStatusCanBeCancelledExceptThroughNothingElse() {
        for (JobStatus from : new JobStatus[]{BLOCKED, PENDING, QUEUED, RUNNING}) {
            assertThat(stateMachine.canTransition(from, CANCELLED)).isTrue();
        }
        for (JobStatus from : new JobStatus[]{SUCCEEDED, DEAD, UPSTREAM_FAILED, CANCELLED}) {
            assertThat(stateMachine.canTransition(from, CANCELLED)).isFalse();
        }
    }

    @Test
    void activeStatusesAreTheNonTerminalOnes() {
        assertThat(JobStatus.activeStatuses())
                .containsExactlyInAnyOrder(BLOCKED, PENDING, QUEUED, RUNNING);
        for (JobStatus status : JobStatus.values()) {
            assertThat(JobStatus.activeStatuses().contains(status)).isEqualTo(!status.isTerminal());
        }
    }

    @Test
    void jobHasNoPublicStatusSetter() {
        boolean publicSetter = Arrays.stream(Job.class.getMethods())
                .map(Method::getName)
                .anyMatch(name -> name.equals("setStatus"));

        assertThat(publicSetter)
                .as("Only JobStateMachine may change status, so Job.setStatus must not be public")
                .isFalse();
    }

    @Test
    void transitionIfRunsOneConditionalUpdateAndSetsFinishedAtForTerminalStatuses() {
        UUID id = UUID.randomUUID();
        when(repository.updateStatusIfCurrent(id, RUNNING, CANCELLED, T1, T1)).thenReturn(1);

        assertThat(stateMachine.transitionIf(id, RUNNING, CANCELLED, T1)).isTrue();

        verify(repository).updateStatusIfCurrent(id, RUNNING, CANCELLED, T1, T1);
    }

    @Test
    void transitionIfClearsFinishedAtForNonTerminalStatuses() {
        UUID id = UUID.randomUUID();
        when(repository.updateStatusIfCurrent(id, DEAD, PENDING, T1, null)).thenReturn(1);

        assertThat(stateMachine.transitionIf(id, DEAD, PENDING, T1)).isTrue();
    }

    @Test
    void transitionIfReturnsFalseWhenAnotherWriterChangedTheStatusFirst() {
        UUID id = UUID.randomUUID();
        when(repository.updateStatusIfCurrent(any(), any(), any(), any(), any())).thenReturn(0);

        assertThat(stateMachine.transitionIf(id, QUEUED, CANCELLED, T1)).isFalse();
    }

    @Test
    void transitionIfRejectsIllegalMovesWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> stateMachine.transitionIf(UUID.randomUUID(), SUCCEEDED, CANCELLED, T1))
                .isInstanceOf(IllegalJobTransitionException.class);

        verifyNoInteractions(repository);
    }

    private static Job jobIn(JobStatus status) {
        ValidatedJob request = new ValidatedJob(
                "demo.work", JsonNodeFactory.instance.objectNode(), 5, T0, 3, 300);
        Job job = Job.newPending(UUID.randomUUID(), 1L, request, null, T0);
        job.setStatus(status);
        return job;
    }
}

package com.mkalki.cloudtaskapi.scheduler;

import com.mkalki.cloudtaskapi.job.JobRepository;
import com.mkalki.cloudtaskapi.job.JobStateMachine;
import com.mkalki.cloudtaskapi.job.JobStatus;
import com.mkalki.cloudtaskapi.queue.QueueService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPromoterTest {

    private static final Instant NOW = Instant.parse("2026-12-01T10:00:00Z");

    private final QueueService queue = mock(QueueService.class);
    private final JobRepository repository = mock(JobRepository.class);
    private final JobStateMachine stateMachine = mock(JobStateMachine.class);
    private final JobPromoter promoter = new JobPromoter(
            queue, repository, stateMachine,
            new SchedulerProperties(true, Duration.ofSeconds(1), 100, Duration.ofSeconds(60), 500),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aFailureOnOneJobDoesNotStopTheOthers() {
        UUID broken = UUID.randomUUID();
        UUID fine = UUID.randomUUID();
        when(queue.promoteDue(NOW.toEpochMilli(), 100)).thenReturn(List.of(broken, fine));
        when(stateMachine.transitionIf(eq(broken), any(), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(stateMachine.transitionIf(fine, JobStatus.PENDING, JobStatus.QUEUED, NOW)).thenReturn(true);

        assertThat(promoter.promoteDue()).containsExactly(fine);

        verify(queue, never()).remove(any());
    }

    @Test
    void aCancelledJobIsRemovedFromRedis() {
        UUID id = UUID.randomUUID();
        when(queue.promoteDue(NOW.toEpochMilli(), 100)).thenReturn(List.of(id));
        when(stateMachine.transitionIf(id, JobStatus.PENDING, JobStatus.QUEUED, NOW)).thenReturn(false);
        when(repository.findStatusById(id)).thenReturn(Optional.of(JobStatus.CANCELLED));

        assertThat(promoter.promoteDue()).isEmpty();

        verify(queue).remove(id);
    }

    @Test
    void aJobWithoutARowIsRemovedFromRedis() {
        UUID id = UUID.randomUUID();
        when(queue.promoteDue(NOW.toEpochMilli(), 100)).thenReturn(List.of(id));
        when(stateMachine.transitionIf(id, JobStatus.PENDING, JobStatus.QUEUED, NOW)).thenReturn(false);
        when(repository.findStatusById(id)).thenReturn(Optional.empty());

        promoter.promoteDue();

        verify(queue).remove(id);
    }

    @Test
    void anAlreadyQueuedJobStaysInRedis() {
        UUID id = UUID.randomUUID();
        when(queue.promoteDue(NOW.toEpochMilli(), 100)).thenReturn(List.of(id));
        when(stateMachine.transitionIf(id, JobStatus.PENDING, JobStatus.QUEUED, NOW)).thenReturn(false);
        when(repository.findStatusById(id)).thenReturn(Optional.of(JobStatus.QUEUED));

        assertThat(promoter.promoteDue()).isEmpty();

        verify(queue, never()).remove(any());
    }
}

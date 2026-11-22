package com.mkalki.cloudtaskapi.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Starts the promote loop and the reconciler. Kept apart from the logic classes so tests can
 * switch the timers off (cloudtask.scheduler.enabled=false) and call the logic directly.
 * A failure is logged and the next tick tries again: Redis being down must not kill the loop.
 */
@Component
@Profile("scheduler")
@ConditionalOnProperty(prefix = "cloudtask.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerTriggers {

    private static final Logger log = LoggerFactory.getLogger(SchedulerTriggers.class);

    private final JobPromoter promoter;
    private final QueueReconciler reconciler;

    public SchedulerTriggers(JobPromoter promoter, QueueReconciler reconciler) {
        this.promoter = promoter;
        this.reconciler = reconciler;
    }

    @Scheduled(fixedDelayString = "${cloudtask.scheduler.poll-interval:PT1S}")
    void promote() {
        try {
            promoter.promoteDue();
        } catch (RuntimeException e) {
            log.warn("promote run failed, will retry: {}", e.toString());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void reconcileOnStartup() {
        reconcile();
    }

    @Scheduled(
            initialDelayString = "${cloudtask.scheduler.reconcile-interval:PT60S}",
            fixedDelayString = "${cloudtask.scheduler.reconcile-interval:PT60S}")
    void reconcilePeriodically() {
        reconcile();
    }

    private void reconcile() {
        try {
            reconciler.reconcile();
        } catch (RuntimeException e) {
            log.warn("reconcile run failed, will retry: {}", e.toString());
        }
    }
}

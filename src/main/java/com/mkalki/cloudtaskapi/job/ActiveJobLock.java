package com.mkalki.cloudtaskapi.job;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Per-user lock around the active-job cap check.
 *
 * <p>Without it, under READ COMMITTED, several submits from one user at {@code limit - 1}
 * can all count {@code limit - 1} before any of them commits, and all insert. The lock is a
 * PostgreSQL transaction-level advisory lock: a second submit from the same user waits at
 * this line until the first transaction commits or rolls back, then counts the real number.
 * PostgreSQL releases the lock itself when the transaction ends, so a crash cannot leak it.
 */
@Component
public class ActiveJobLock {

    /**
     * First key of {@code pg_advisory_xact_lock(int, int)}. It marks the lock as
     * "active-job cap" so other advisory locks added later cannot collide with it.
     */
    static final int ACTIVE_JOB_CAP_NAMESPACE = 1;

    private final JdbcTemplate jdbc;

    public ActiveJobLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Blocks until this transaction holds the lock for {@code ownerId}. The second key is the
     * user id folded to an int; two users sharing a key only wait for each other, which is safe.
     */
    public void lockFor(Long ownerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // Outside a transaction the lock would be released as soon as the statement ends.
            throw new IllegalStateException("The active-job lock must be taken inside a transaction");
        }
        jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> { },
                ACTIVE_JOB_CAP_NAMESPACE, Long.hashCode(ownerId));
    }
}

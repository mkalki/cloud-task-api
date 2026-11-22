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
     * Mixed into the lock key so this lock cannot collide with another advisory lock that
     * uses a plain id as its key. Any fixed 64-bit constant works; this one is arbitrary.
     */
    static final long ACTIVE_JOB_CAP_NAMESPACE = 0x6A0B_CA9E_5EED_0001L;

    private final JdbcTemplate jdbc;

    public ActiveJobLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Blocks until this transaction holds the lock for {@code ownerId}.
     */
    public void lockFor(Long ownerId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // Outside a transaction the lock would be released as soon as the statement ends.
            throw new IllegalStateException("The active-job lock must be taken inside a transaction");
        }
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> { }, lockKey(ownerId));
    }

    /**
     * {@code users.id} is a BIGINT, so the single-bigint form {@code pg_advisory_xact_lock(bigint)}
     * is used: the whole id fits, and ids above 2^31 cannot overflow. (The two-int form would
     * force the id into 32 bits.) XOR with a constant is a bijection on 64-bit values, so two
     * different users never get the same key, and the key differs from the bare id.
     */
    static long lockKey(long ownerId) {
        return ownerId ^ ACTIVE_JOB_CAP_NAMESPACE;
    }
}

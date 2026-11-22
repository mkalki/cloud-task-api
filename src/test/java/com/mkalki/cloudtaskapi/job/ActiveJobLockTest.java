package com.mkalki.cloudtaskapi.job;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Why the lock key is a single bigint: users.id is BIGINT, so ids can exceed 2^31. */
class ActiveJobLockTest {

    private static final long[] IDS = {
            1L, 2L, 255L, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, 1L << 32, (1L << 32) + 1,
            3_000_000_000L, Long.MAX_VALUE - 1, Long.MAX_VALUE
    };

    @Test
    void differentOwnerIdsNeverShareAKeyEvenAboveTwoToThe31() {
        Set<Long> keys = new HashSet<>();
        for (long id : IDS) {
            keys.add(ActiveJobLock.lockKey(id));
        }

        assertThat(keys).hasSameSizeAs(IDS);
    }

    @Test
    void idsThatCollideWhenFoldedToAnIntStayDistinct() {
        // Long.hashCode(4) == Long.hashCode((1L << 32) + 5) == 4: a 32-bit key would merge them.
        long small = 4L;
        long large = (1L << 32) + 5;
        assertThat(Long.hashCode(small)).isEqualTo(Long.hashCode(large));

        assertThat(ActiveJobLock.lockKey(small)).isNotEqualTo(ActiveJobLock.lockKey(large));
    }

    @Test
    void theKeyIsMixedWithTheNamespaceAndReversible() {
        for (long id : IDS) {
            long key = ActiveJobLock.lockKey(id);
            assertThat(key).isNotEqualTo(id);
            assertThat(key ^ ActiveJobLock.ACTIVE_JOB_CAP_NAMESPACE).isEqualTo(id);
        }
    }
}

package com.platform.security.session;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** The PD-02 check's own rules: off means trust the token; a live answer is reused only for its TTL; a dead one never. */
class SessionLivenessTests {

    /** Answers every liveness query with {@code answer} and counts the queries; no database. */
    private static final class FakeJdbc extends NamedParameterJdbcTemplate {
        private final AtomicInteger queries = new AtomicInteger();
        private boolean answer;

        FakeJdbc(boolean answer) {
            super(new DriverManagerDataSource());
            this.answer = answer;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, SqlParameterSource params, Class<T> type) {
            queries.incrementAndGet();
            return (T) Boolean.valueOf(answer);
        }
    }

    private final AtomicLong clock = new AtomicLong();

    private SessionLiveness liveness(FakeJdbc jdbc, boolean enabled, Duration ttl) {
        return new SessionLiveness(new SessionLiveness.Settings(enabled, ttl), jdbc, clock::get);
    }

    @Test
    void switchedOffItTrustsTheTokenAndAsksNothing() {
        FakeJdbc jdbc = new FakeJdbc(false);
        assertThat(liveness(jdbc, false, Duration.ofSeconds(10)).isLive(UUID.randomUUID(), UUID.randomUUID())).isTrue();
        assertThat(jdbc.queries).hasValue(0);
    }

    @Test
    void aLiveAnswerIsReusedForItsTtlThenAskedAgain() {
        FakeJdbc jdbc = new FakeJdbc(true);
        SessionLiveness check = liveness(jdbc, true, Duration.ofSeconds(10));
        UUID account = UUID.randomUUID();
        UUID family = UUID.randomUUID();

        assertThat(check.isLive(account, family)).isTrue();
        clock.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(check.isLive(account, family)).isTrue();
        assertThat(jdbc.queries).hasValue(1);

        clock.addAndGet(Duration.ofSeconds(2).toNanos());
        jdbc.answer = false;
        assertThat(check.isLive(account, family)).as("asked again once the answer is stale").isFalse();
        assertThat(jdbc.queries).hasValue(2);
    }

    @Test
    void anEndedSignInIsNeverRemembered() {
        FakeJdbc jdbc = new FakeJdbc(false);
        SessionLiveness check = liveness(jdbc, true, Duration.ofSeconds(10));
        UUID account = UUID.randomUUID();
        UUID family = UUID.randomUUID();

        assertThat(check.isLive(account, family)).isFalse();
        assertThat(check.isLive(account, family)).isFalse();
        assertThat(check.isLive(account, null)).as("a token without a sid: the account's status only").isFalse();
        assertThat(jdbc.queries).hasValue(3);
    }

}

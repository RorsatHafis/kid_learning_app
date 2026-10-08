package com.platform.security.session;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * PD-02 as adopted in Y13: an access token is honoured only while the sign-in it names (its
 * {@code sid}, Y2) is still live and its account still active. Signing out, signing a device
 * out, changing the password, a password reset, reuse detection and suspension therefore take
 * effect on the next request instead of up to 15 minutes later. One indexed query per sign-in,
 * remembered for {@link Settings#cacheTtl()} so a busy page doesn't ask on every request.
 * Tokens without a {@code sid} (issued before Y2) are checked for the account's status only.
 */
@Component
public class SessionLiveness {

    /**
     * @param enabled  check every request (true) or trust the token until it expires (false: the
     *                 pre-Y13 behaviour, at most the access-token lifetime)
     * @param cacheTtl how long one answer is reused (0 = ask every time; at most 1 minute)
     */
    @ConfigurationProperties(prefix = "platform.security.session-check")
    public record Settings(boolean enabled, Duration cacheTtl) {
        public Settings {
            ConfigurationContract.require(cacheTtl != null && !cacheTtl.isNegative()
                            && cacheTtl.compareTo(Duration.ofMinutes(1)) <= 0,
                    "SESSION_CHECK_CACHE_TTL must be between 0s and 1m, was " + cacheTtl);
        }
    }

    private static final int MAX_REMEMBERED = 10_000;

    private final Settings settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final LongSupplier nanoClock;
    private final Map<String, Long> liveUntil = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_REMEMBERED;
        }
    };

    @org.springframework.beans.factory.annotation.Autowired
    public SessionLiveness(Settings settings, NamedParameterJdbcTemplate jdbc) {
        this(settings, jdbc, System::nanoTime);
    }

    SessionLiveness(Settings settings, NamedParameterJdbcTemplate jdbc, LongSupplier nanoClock) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.nanoClock = nanoClock;
    }

    /** True while the token's sign-in may act: its account active and (when named) its sign-in live. */
    public boolean isLive(UUID accountId, UUID sessionFamilyId) {
        if (!settings.enabled()) {
            return true;
        }
        String key = accountId + "/" + sessionFamilyId;
        long now = nanoClock.getAsLong();
        synchronized (liveUntil) {
            Long until = liveUntil.get(key);
            if (until != null && until - now > 0) {
                return true;
            }
        }
        MapSqlParameterSource params = new MapSqlParameterSource("account", accountId).addValue("family", sessionFamilyId);
        Boolean live = sessionFamilyId == null
                ? jdbc.queryForObject("select exists (select 1 from accounts where id = :account and status = 'ACTIVE')",
                        params, Boolean.class)
                : jdbc.queryForObject("""
                        select exists (select 1 from account_sessions s join accounts a on a.id = s.account_id
                                       where s.family_id = :family and s.account_id = :account and s.status = 'ACTIVE'
                                         and s.expires_at > now() and a.status = 'ACTIVE')
                        """, params, Boolean.class);
        boolean answer = Boolean.TRUE.equals(live);
        synchronized (liveUntil) {
            if (answer && !settings.cacheTtl().isZero()) {
                liveUntil.put(key, now + settings.cacheTtl().toNanos());
            } else {
                liveUntil.remove(key);
            }
        }
        return answer;
    }

}

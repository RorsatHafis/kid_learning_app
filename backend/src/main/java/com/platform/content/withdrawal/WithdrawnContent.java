package com.platform.content.withdrawal;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * "Has this been taken down?" (Y16), asked wherever content reaches a child. An activity version is
 * withdrawn when it is, or when any question version in it is. One indexed query each.
 */
@Component
public class WithdrawnContent {

    private final NamedParameterJdbcTemplate jdbc;

    public WithdrawnContent(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean activityVersion(UUID activityVersionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from content_withdrawals w where w.restored_at is null
                               and (w.version_id = :v or w.version_id in
                                    (select i.question_version_id from activity_items i where i.activity_version_id = :v)))
                """, Map.of("v", activityVersionId), Boolean.class));
    }

    public boolean questionVersion(UUID questionVersionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from content_withdrawals where version_id = :v and restored_at is null)",
                Map.of("v", questionVersionId), Boolean.class));
    }

}

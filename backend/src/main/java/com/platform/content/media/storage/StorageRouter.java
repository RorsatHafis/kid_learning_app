package com.platform.content.media.storage;

import com.platform.content.media.MediaStorage;
import com.platform.common.web.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * M11: which storage a file is in, and where a new upload goes.
 *
 * <ul>
 *   <li>A file is read from the storage it was written to ({@code content_assets.storage_connection_id}; null: hosted).</li>
 *   <li>A new upload goes to the author's school's own storage when the author works at exactly one active school and
 *       that school has chosen its own storage; otherwise (platform content, several schools, none) Bookeyond-hosted.</li>
 * </ul>
 * Connections are opened lazily and kept (they never change: a new connection is a new row).
 */
@Service
public class StorageRouter {

    private final MediaStorage hosted;
    private final StorageConnectionRepository connections;
    private final StorageSeal seal;
    private final StorageProperties properties;
    private final JdbcTemplate jdbc;
    private final Map<UUID, S3StorageProvider> open = new ConcurrentHashMap<>();

    public StorageRouter(MediaStorage hosted, StorageConnectionRepository connections, StorageSeal seal, StorageProperties properties,
                         JdbcTemplate jdbc) {
        this.hosted = hosted;
        this.connections = connections;
        this.seal = seal;
        this.properties = properties;
        this.jdbc = jdbc;
    }

    /** The storage behind a connection id (null: Bookeyond-hosted). */
    public StorageProvider provider(UUID connectionId) {
        if (connectionId == null) {
            return hosted;
        }
        return open.computeIfAbsent(connectionId, id -> {
            StorageConnection connection = connections.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("StorageConnection", id));
            return S3StorageProvider.of(connection, seal.open(connection.getAccessKeySealed(), connection.sealContext()),
                    properties.allowPrivateEndpoints());
        });
    }

    /** Where an upload by {@code ownerAccountId} goes now (null: Bookeyond-hosted). */
    public UUID uploadTarget(UUID ownerAccountId) {
        if (ownerAccountId == null) {
            return null;
        }
        List<java.util.Map<String, Object>> schools = jdbc.queryForList("""
                SELECT DISTINCT s.id, s.storage_connection_id FROM schools s
                WHERE s.status = 'ACTIVE' AND (s.principal_account_id = ?
                   OR s.id IN (SELECT st.school_id FROM school_staff st WHERE st.account_id = ? AND st.status = 'ACTIVE'))
                """, ownerAccountId, ownerAccountId);
        if (schools.size() != 1) {
            return null;
        }
        Object connection = schools.getFirst().get("storage_connection_id");
        return connection == null ? null : (UUID) connection;
    }

    /** Forgets an opened connection (after a failed check, it is opened again next time). */
    public void forget(UUID connectionId) {
        S3StorageProvider closed = open.remove(connectionId);
        if (closed != null) {
            closed.close();
        }
    }
}

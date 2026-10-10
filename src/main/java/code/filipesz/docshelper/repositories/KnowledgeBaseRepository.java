package code.filipesz.docshelper.repositories;

import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class KnowledgeBaseRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeBaseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Document> findChunksByFiles(List<String> files, int limit) {
        return jdbc.query("""
                SELECT content, metadata ->> 'file_name' AS file_name
                FROM vector_store
                WHERE metadata ->> 'file_name' IN (:files)
                LIMIT :limit
                """, Map.of("files", files, "limit", limit),
                (rs, rowNum) -> new Document(rs.getString("content"),
                        Map.of("file_name", rs.getString("file_name"))));
    }

    public List<String> findFileNames() {
        return jdbc.queryForList("""
                SELECT DISTINCT metadata ->> 'file_name'
                FROM vector_store
                WHERE metadata ->> 'file_name' IS NOT NULL
                ORDER BY 1
                """, Map.of(), String.class);
    }

    public boolean containsAllFiles(List<String> files) {
        List<String> existing = jdbc.queryForList("""
                SELECT DISTINCT metadata ->> 'file_name'
                FROM vector_store
                WHERE metadata ->> 'file_name' IN (:files)
                """, Map.of("files", files), String.class);
        return existing.size() == files.size();
    }

    public void deleteFile(String fileName) {
        jdbc.update("DELETE FROM vector_store WHERE metadata ->> 'file_name' = :fileName",
                Map.of("fileName", fileName));
    }
}

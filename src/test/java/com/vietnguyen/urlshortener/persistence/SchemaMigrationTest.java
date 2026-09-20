package com.vietnguyen.urlshortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.vietnguyen.urlshortener.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void initialMigrationCreatesRequiredTablesAndIndexes() {
    List<String> tables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'public'
              AND table_name IN ('links', 'click_events')
            ORDER BY table_name
            """,
            String.class);

    assertThat(tables).containsExactly("click_events", "links");
    assertThat(indexExists("links_short_code_key")).isTrue();
    assertThat(indexExists("idx_click_events_link_occurred_at")).isTrue();
  }

  private boolean indexExists(String indexName) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = ?
            """,
            Integer.class,
            indexName);
    return count != null && count == 1;
  }
}

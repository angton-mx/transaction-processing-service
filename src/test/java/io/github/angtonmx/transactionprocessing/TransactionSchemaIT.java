package io.github.angtonmx.transactionprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransactionSchemaIT {

    private static final String TABLE_NAME = "transactions";

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:17.10-alpine3.23");

    @DynamicPropertySource
    static void configurePostgreSql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void transactionsTableExists() {
        assertTransactionsTableExists();
    }

    @Test
    void transactionsColumnsMatchContract() {
        assertTransactionsTableExists();

        List<ColumnMetadata> columns = jdbcTemplate().query("""
                SELECT column_name,
                       data_type,
                       character_maximum_length,
                       is_nullable,
                       column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                ORDER BY ordinal_position
                """, (resultSet, rowNumber) -> new ColumnMetadata(
                resultSet.getString("column_name"),
                resultSet.getString("data_type"),
                resultSet.getObject("character_maximum_length", Integer.class),
                "YES".equals(resultSet.getString("is_nullable")),
                resultSet.getString("column_default")), TABLE_NAME);

        assertThat(columns)
                .extracting(ColumnMetadata::name, ColumnMetadata::dataType,
                        ColumnMetadata::maximumLength)
                .containsExactlyInAnyOrder(
                        tuple("id", "uuid", null),
                        tuple("account_id", "text", null),
                        tuple("type", "text", null),
                        tuple("amount", "numeric", null),
                        tuple("currency", "character varying", 3),
                        tuple("description", "text", null),
                        tuple("status", "text", null),
                        tuple("provider_status", "text", null),
                        tuple("provider_transaction_id", "text", null),
                        tuple("balance_after", "numeric", null),
                        tuple("provider_executed_at", "timestamp with time zone", null),
                        tuple("provider_code", "text", null),
                        tuple("provider_message", "text", null),
                        tuple("error_message", "text", null),
                        tuple("created_at", "timestamp with time zone", null));

        assertThat(columns)
                .extracting(ColumnMetadata::name, ColumnMetadata::nullable)
                .containsExactlyInAnyOrder(
                        tuple("id", false),
                        tuple("account_id", false),
                        tuple("type", false),
                        tuple("amount", false),
                        tuple("currency", false),
                        tuple("description", true),
                        tuple("status", false),
                        tuple("provider_status", true),
                        tuple("provider_transaction_id", true),
                        tuple("balance_after", true),
                        tuple("provider_executed_at", true),
                        tuple("provider_code", true),
                        tuple("provider_message", true),
                        tuple("error_message", true),
                        tuple("created_at", false));

        assertThat(columns)
                .filteredOn(column -> !"created_at".equals(column.name()))
                .extracting(ColumnMetadata::defaultValue)
                .containsOnlyNulls();

        assertThat(columns)
                .filteredOn(column -> "created_at".equals(column.name()))
                .singleElement()
                .extracting(ColumnMetadata::defaultValue)
                .asString()
                .containsIgnoringCase("CURRENT_TIMESTAMP");
    }

    @Test
    void transactionsCheckConstraintsMatchContract() {
        assertTransactionsTableExists();

        List<String> constraintNames = jdbcTemplate().queryForList("""
                SELECT constraint_definition.conname
                FROM pg_catalog.pg_constraint constraint_definition
                JOIN pg_catalog.pg_class constrained_table
                  ON constrained_table.oid = constraint_definition.conrelid
                JOIN pg_catalog.pg_namespace table_schema
                  ON table_schema.oid = constrained_table.relnamespace
                WHERE table_schema.nspname = 'public'
                  AND constrained_table.relname = ?
                  AND constraint_definition.contype = 'c'
                """, String.class, TABLE_NAME);

        assertThat(constraintNames).containsExactlyInAnyOrder(
                "chk_transactions_account_id",
                "chk_transactions_type",
                "chk_transactions_amount",
                "chk_transactions_debit_limit",
                "chk_transactions_currency",
                "chk_transactions_status",
                "chk_transactions_provider_status",
                "chk_transactions_result");
    }

    @Test
    void transactionsIndexesMatchContract() {
        assertTransactionsTableExists();

        Map<String, IndexMetadata> indexes = loadIndexes();

        assertThat(indexes).hasSize(4);
        assertThat(indexes.values())
                .filteredOn(IndexMetadata::primary)
                .singleElement()
                .satisfies(index -> {
                    assertThat(index.unique()).isTrue();
                    assertThat(index.columns()).containsExactly("id");
                    assertThat(index.descending()).containsExactly(false);
                    assertThat(index.predicate()).isNull();
                });

        assertThat(indexes.get("idx_transactions_created"))
                .isNotNull()
                .satisfies(index -> {
                    assertThat(index.primary()).isFalse();
                    assertThat(index.unique()).isFalse();
                    assertThat(index.columns()).containsExactly("created_at", "id");
                    assertThat(index.descending()).containsExactly(true, true);
                    assertThat(index.predicate()).isNull();
                });

        assertThat(indexes.get("idx_transactions_account_created"))
                .isNotNull()
                .satisfies(index -> {
                    assertThat(index.primary()).isFalse();
                    assertThat(index.unique()).isFalse();
                    assertThat(index.columns()).containsExactly("account_id", "created_at", "id");
                    assertThat(index.descending()).containsExactly(false, true, true);
                    assertThat(index.predicate()).isNull();
                });

        assertThat(indexes.get("uq_transactions_provider_id"))
                .isNotNull()
                .satisfies(index -> {
                    assertThat(index.primary()).isFalse();
                    assertThat(index.unique()).isTrue();
                    assertThat(index.columns()).containsExactly("provider_transaction_id");
                    assertThat(index.descending()).containsExactly(false);
                    assertThat(normalizeExpression(index.predicate()))
                            .isEqualTo("provider_transaction_idisnotnull");
                });
    }

    @Test
    void transactionsDoNotHaveTypeOrStatusIndexes() {
        assertTransactionsTableExists();

        assertThat(loadIndexes().values())
                .flatExtracting(IndexMetadata::columns)
                .doesNotContain("type", "status");
    }

    private void assertTransactionsTableExists() {
        Integer tableCount = jdbcTemplate().queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """, Integer.class, TABLE_NAME);

        assertThat(tableCount)
                .as("transactions table must exist before its metadata can be verified")
                .isEqualTo(1);
    }

    private Map<String, IndexMetadata> loadIndexes() {
        List<IndexColumnMetadata> indexColumns = jdbcTemplate().query("""
                SELECT index_relation.relname AS index_name,
                       index_definition.indisunique AS is_unique,
                       index_definition.indisprimary AS is_primary,
                       pg_get_expr(index_definition.indpred, index_definition.indrelid) AS predicate,
                       indexed_column.position::integer AS column_position,
                       table_column.attname AS column_name,
                       pg_index_column_has_property(
                           index_definition.indexrelid,
                           indexed_column.position::integer,
                           'desc'
                       ) AS is_descending
                FROM pg_catalog.pg_index index_definition
                JOIN pg_catalog.pg_class indexed_table
                  ON indexed_table.oid = index_definition.indrelid
                JOIN pg_catalog.pg_namespace table_schema
                  ON table_schema.oid = indexed_table.relnamespace
                JOIN pg_catalog.pg_class index_relation
                  ON index_relation.oid = index_definition.indexrelid
                CROSS JOIN LATERAL unnest(index_definition.indkey)
                  WITH ORDINALITY AS indexed_column(attribute_number, position)
                JOIN pg_catalog.pg_attribute table_column
                  ON table_column.attrelid = indexed_table.oid
                 AND table_column.attnum = indexed_column.attribute_number
                WHERE table_schema.nspname = 'public'
                  AND indexed_table.relname = ?
                ORDER BY index_relation.relname, indexed_column.position
                """, (resultSet, rowNumber) -> new IndexColumnMetadata(
                resultSet.getString("index_name"),
                resultSet.getBoolean("is_unique"),
                resultSet.getBoolean("is_primary"),
                resultSet.getString("predicate"),
                resultSet.getInt("column_position"),
                resultSet.getString("column_name"),
                resultSet.getBoolean("is_descending")), TABLE_NAME);

        Map<String, List<IndexColumnMetadata>> columnsByIndex = new LinkedHashMap<>();
        for (IndexColumnMetadata column : indexColumns) {
            columnsByIndex.computeIfAbsent(column.indexName(), ignored -> new java.util.ArrayList<>())
                    .add(column);
        }

        Map<String, IndexMetadata> indexes = new LinkedHashMap<>();
        columnsByIndex.forEach((name, columns) -> {
            IndexColumnMetadata firstColumn = columns.getFirst();
            indexes.put(name, new IndexMetadata(
                    firstColumn.unique(),
                    firstColumn.primary(),
                    firstColumn.predicate(),
                    columns.stream().map(IndexColumnMetadata::columnName).toList(),
                    columns.stream().map(IndexColumnMetadata::descending).toList()));
        });
        return indexes;
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    private String normalizeExpression(String expression) {
        return expression == null
                ? null
                : expression.replaceAll("[()\\s]", "").toLowerCase(Locale.ROOT);
    }

    private record ColumnMetadata(
            String name,
            String dataType,
            Integer maximumLength,
            boolean nullable,
            String defaultValue) {
    }

    private record IndexColumnMetadata(
            String indexName,
            boolean unique,
            boolean primary,
            String predicate,
            int position,
            String columnName,
            boolean descending) {
    }

    private record IndexMetadata(
            boolean unique,
            boolean primary,
            String predicate,
            List<String> columns,
            List<Boolean> descending) {
    }
}

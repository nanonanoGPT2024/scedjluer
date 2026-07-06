package co.id.mcs.dika.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import co.id.mcs.ptdika.MadMachine.Repository.Column;
import co.id.mcs.ptdika.MadMachine.Repository.Table;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class JdbcUtil {

    /**
     * Performs a true bulk insert using a single INSERT INTO ... VALUES (), (), ...
     * statement.
     * Uses reflection to extract table and column names from @Table and @Column
     * annotations.
     * 
     * @param <T>          The entity type
     * @param jdbcTemplate The NamedParameterJdbcTemplate to use
     * @param entities     The list of entities to insert
     * @param clazz        The class of the entity
     */
    public static <T> void bulkInsert(NamedParameterJdbcTemplate jdbcTemplate, List<T> entities, Class<T> clazz) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        Table tableAnn = clazz.getAnnotation(Table.class);
        String tableName = (tableAnn != null) ? tableAnn.name() : clazz.getSimpleName().toLowerCase();

        List<Field> fields = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            Column colAnn = field.getAnnotation(Column.class);
            if (colAnn != null) {
                field.setAccessible(true);
                fields.add(field);
                columnNames.add(colAnn.name());
            }
        }

        if (fields.isEmpty()) {
            log.error("No @Column fields found for class {}", clazz.getName());
            return;
        }

        // Batch processing to avoid parameter limits (PostgreSQL typically 65535)
        int batchSize = 65000 / fields.size();
        if (batchSize < 1)
            batchSize = 1;

        // Also limit batchSize to 500 to keep SQL size and parameters reasonably small
        // for JDBC driver
        if (batchSize > 500) {
            batchSize = 500;
        }

        org.springframework.jdbc.core.JdbcTemplate rawJdbcTemplate = jdbcTemplate.getJdbcTemplate();

        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<T> batch = entities.subList(i, end);

            StringBuilder sql = new StringBuilder("INSERT INTO ")
                    .append(tableName)
                    .append(" (")
                    .append(String.join(", ", columnNames))
                    .append(") VALUES ");

            List<Object> args = new ArrayList<>(batch.size() * fields.size());
            for (int j = 0; j < batch.size(); j++) {
                if (j > 0)
                    sql.append(", ");
                sql.append("(");
                T entity = batch.get(j);
                for (int k = 0; k < fields.size(); k++) {
                    if (k > 0)
                        sql.append(", ");
                    sql.append("?");
                    try {
                        args.add(fields.get(k).get(entity));
                    } catch (IllegalAccessException e) {
                        args.add(null);
                    }
                }
                sql.append(")");
            }

            try {
                rawJdbcTemplate.update(sql.toString(), args.toArray());
            } catch (Exception e) {
                log.error("Failed to perform bulk insert for table {}: {}", tableName, e.getMessage());
                try {
                    StringBuilder debugSql = new StringBuilder(sql.toString());
                    for (Object arg : args) {
                        int qMark = debugSql.indexOf("?");
                        if (qMark == -1)
                            break;
                        String val = "NULL";
                        if (arg != null) {
                            if (arg instanceof String || arg instanceof java.util.Date
                                    || arg instanceof java.util.UUID) {
                                val = "'" + arg.toString().replace("'", "''") + "'";
                            } else {
                                val = arg.toString();
                            }
                        }
                        debugSql.replace(qMark, qMark + 1, val);
                    }
                    log.error("Generated SQL with actual values: {}", debugSql.toString());
                } catch (Exception ex) {
                    log.error("Could not generate debug SQL: {}", ex.getMessage());
                }
                log.error("Exception details: ", e);
                throw e; // Rethrow to allow caller to handle if needed
            }
        }
    }
}

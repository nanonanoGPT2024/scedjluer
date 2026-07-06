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
     * Performs a high-performance bulk insert by generating explicit multi-row
     * positional INSERT INTO table (cols) VALUES (?,?,?), (?,?,?) statements.
     * Bypasses Spring NamedParameter parsing overhead for massive performance gains.
     *
     * @param <T>          The entity type
     * @param jdbcTemplate The NamedParameterJdbcTemplate to extract raw JdbcTemplate from
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

        org.springframework.jdbc.core.JdbcTemplate rawJdbc = jdbcTemplate.getJdbcTemplate();
        
        // 1000 rows chunk size reduces round-trips while staying well within PG parameters limit (65535)
        int chunkSize = 1000;
        String colsPart = String.join(", ", columnNames);
        int numCols = columnNames.size();

        // Build single-row placeholders: (?,?,?,...,?)
        StringBuilder rowPlBuilder = new StringBuilder("(");
        for (int k = 0; k < numCols; k++) {
            if (k > 0) rowPlBuilder.append(",");
            rowPlBuilder.append("?");
        }
        rowPlBuilder.append(")");
        String singleRowPlaceholder = rowPlBuilder.toString();

        for (int i = 0; i < entities.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, entities.size());
            List<T> chunk = entities.subList(i, end);
            int chunkLen = chunk.size();

            StringBuilder sqlBuilder = new StringBuilder("INSERT INTO ")
                    .append(tableName)
                    .append(" (").append(colsPart).append(") VALUES ");

            Object[] params = new Object[chunkLen * numCols];

            for (int r = 0; r < chunkLen; r++) {
                if (r > 0) sqlBuilder.append(",");
                sqlBuilder.append(singleRowPlaceholder);

                T entity = chunk.get(r);
                for (int k = 0; k < numCols; k++) {
                    try {
                        Object value = fields.get(k).get(entity);
                        // Normalize java.util.Date to Timestamp for PostgreSQL
                        if (value instanceof java.util.Date
                                && !(value instanceof java.sql.Date)
                                && !(value instanceof java.sql.Timestamp)) {
                            value = new java.sql.Timestamp(((java.util.Date) value).getTime());
                        }
                        params[r * numCols + k] = value;
                    } catch (IllegalAccessException e) {
                        params[r * numCols + k] = null;
                    }
                }
            }

            try {
                rawJdbc.update(sqlBuilder.toString(), params);
            } catch (Exception e) {
                log.error("Failed to perform bulk insert for table {}: {}", tableName, e.getMessage());
                log.error("Exception details: ", e);
                throw e;
            }
        }
    }
}

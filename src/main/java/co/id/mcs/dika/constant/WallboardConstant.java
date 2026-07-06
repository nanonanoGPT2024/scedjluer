package co.id.mcs.dika.constant;

public class WallboardConstant {

    public static final String GET_STATUS_SUMMARY = """
            SELECT
                COALESCE(SUM(CASE WHEN status_agent = 1 THEN 1 ELSE 0 END), 0) AS total_online,
                COALESCE(SUM(CASE WHEN status_agent = 0 THEN 1 ELSE 0 END), 0) AS total_offline
            FROM wallboard
            """;

    public static final String GET_BEST_SPV = """
            SELECT
                supervisor AS fullname,
                SUM(agree) AS total_agree,
                MAX(storage_name::text) AS storage_name,
                NULL AS first_agree_time
            FROM wallboard
            WHERE supervisor_id IS NOT NULL AND supervisor IS NOT NULL
            GROUP BY supervisor_id, supervisor
            ORDER BY total_agree DESC, MIN(agree_date) ASC
            LIMIT 3;
            """;

    public static final String GET_LOW_SPV = """
            WITH ranked_spv AS (
                SELECT
                    supervisor_id,
                    supervisor AS fullname,
                    SUM(agree) AS total_agree,
                    MAX(storage_name::text) AS storage_name,
                    MAX(agree_date) AS agree_date,
                    ROW_NUMBER() OVER (ORDER BY SUM(agree) ASC, MAX(agree_date) DESC) AS rank_order
                FROM wallboard
                WHERE supervisor_id IS NOT NULL
                  AND supervisor NOT IN ('SPV','SIGIT', 'SPV_FU')
                GROUP BY supervisor_id, supervisor
            )
            SELECT
                fullname,
                total_agree,
                storage_name,
                NULL AS first_agree_time
            FROM ranked_spv
            WHERE rank_order <= 3
            ORDER BY rank_order DESC;
            """;

    public static final String GET_BEST_AGENT = """
            SELECT
                agent_fullname AS fullname,
                agree AS total_agree,
                storage_name::text,
                NULL AS first_agree_time
            FROM wallboard
            WHERE agent_fullname IS NOT NULL
            ORDER BY total_agree DESC, agree_date ASC
            LIMIT 3;
            """;

    public static final String GET_LOW_AGENT = """
            SELECT
                agent_fullname AS fullname,
                agree AS total_agree,
                storage_name::text,
                NULL AS first_agree_time
            FROM wallboard
            WHERE agent_fullname IS NOT NULL
            ORDER BY total_agree ASC, agree_date DESC
            LIMIT 3;
            """;

    public static final String RESET_INSERT = """
            INSERT INTO wallboard (
                id, agent_id, status_agent, call_time, total_call, success_call, agree,
                call_date, calltime_answer, supervisor_id, extension, supervisor, supervisor_fullname, agent_fullname, agent_username, storage_name, id_manager, app_reference_id)
            VALUES (
                :id,
                :agentId,
                0, 0, 0, 0, 0, CURRENT_DATE, 0, :supervisorId, :extension, :supervisor, :supervisorFullname, :agentFullname, :agentUsername, :storageName, :idManager, :appReferenceId)
            """;
}

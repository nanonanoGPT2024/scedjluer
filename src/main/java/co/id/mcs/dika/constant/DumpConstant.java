package co.id.mcs.dika.constant;

public class DumpConstant {

    /**
     * Query export dump — export seluruh data order_data ke CSV.
     * Disesuaikan dengan schema PostgreSQL project ini.
     *
     * Tabel utama : order_data (od)
     * Join : data_customer_profile (cp)
     * : master_campaign (mc)
     * : master_campaign_product (mcp)
     * : master_status_result (msr)
     * : order_data_details (odd)
     * : master_segment_card (msc1, msc2)
     * : oauth.agent (a)
     */
    public static final String DUMP_QUERY = """
            SELECT
                'nocase' AS c1, 'custname' AS c2, 'cis' AS c3, 'social_number' AS c4, 'dob' AS c5, 'campaignname' AS c6, 'published' AS c7, 'campaign_status' AS c8,
                'productname' AS c9, 'producttype' AS c10, 'distribute_datespv' AS c11, 'distribute_datetsr' AS c12, 'last_status' AS c13, 'last_reason' AS c14,
                'last_status_date' AS c15, 'upload_date' AS c16, 'begin_date' AS c17, 'expire_date' AS c18, 'sellercode' AS c19, 'nik' AS c20, 'agree_date' AS c21,
                'leadername' AS c22, 'block_status' AS c23, 'pickup_status' AS c24, 'pickup_reason' AS c25, 'pickup_resultdate' AS c26, 'ms_code' AS c27, 'ms_name' AS c28,
                'datainfo' AS c29, 'status_qa' AS c30, 'tgl_approve_qa' AS c31, 'tenor' AS c32, 'volume' AS c33, 'pku_date' AS c34, 'lead_id' AS c35,
                'supp_fullname' AS c36, 'Addon_Supplement' AS c37, 'City_Domisili' AS c38
            UNION ALL
            SELECT * FROM (
            SELECT
                od.case_id::TEXT                                                                AS no_case,
                cp.name                                                                         AS custname,
                od.cust_no                                                                      AS cis,
                '-'                                                                             AS social_number,
                COALESCE(TO_CHAR(cp.tanggal_lahir, 'YYYY-MM-DD HH24:MI:SS'), '0000-00-00 00:00:00')                AS dob,
                mc.campaign_code                                                                AS campaignname,
                CASE
                    WHEN mc.is_published = TRUE THEN '1'
                    ELSE '-'
                END                                                                             AS published,
                CASE
                    WHEN mc.is_active = TRUE THEN 'Active'
                    ELSE 'Inactive'
                END                                                                             AS campaign_status,
                mcp.product                                                                     AS productname,
                mct.type                                                                        AS producttype,
                COALESCE(TO_CHAR(od.distribusi_date,     'YYYY-MM-DD HH24:MI:SS'), '0000-00-00 00:00:00')           AS distribute_datespv,
                COALESCE(TO_CHAR(od.distribusi_date_spv, 'YYYY-MM-DD HH24:MI:SS'), '0000-00-00 00:00:00')           AS distribute_datetsr,
                COALESCE(msr.name, '-')                                                         AS last_status,
                COALESCE(mr.name, '-')                                                          AS last_reason,
                COALESCE(TO_CHAR(od.status_date, 'YYYY-MM-DD HH24:MI:SS'), '-')                 AS last_status_date,
                TO_CHAR(od.created_date, 'YYYY-MM-DD HH24:MI:SS')                               AS upload_date,
                TO_CHAR(mc.begin_date, 'YYYY-MM-DD HH24:MI:SS')                                 AS begin_date,
                TO_CHAR(mc.end_date,   'YYYY-MM-DD HH24:MI:SS')                                 AS expire_date,
                COALESCE(od.agent_username, '-')                                                AS sellercode,
                COALESCE(od.nik, '-')                                                           AS nik,
                CASE
                    WHEN msr.name = 'AGREE'
                        THEN TO_CHAR(od.agree_date, 'YYYY-MM-DD HH24:MI:SS')
                    ELSE '-'
                END                                                                             AS agree_date,
                COALESCE(od.pic_fullname, '-')                                                  AS leadername,
                CASE
                    WHEN od.business_category IS NULL OR od.business_category = 0
                        THEN 'Blocked'
                    ELSE 'Unblocked'
                END                                                                             AS block_status,
                COALESCE(od.status_ms, '-')                                                     AS pickup_status,
                COALESCE(od.reason_ms, '-')                                                     AS pickup_reason,
                COALESCE(TO_CHAR(od.pickup_result_date, 'YYYY-MM-DD HH24:MI:SS'), '-')          AS pickup_resultdate,
                COALESCE(od.ms_code_update, '-')                                                AS ms_code,
                COALESCE(od.ms_name_update, '-')                                                AS ms_name,
                COALESCE(od.penawaran, '-')                                                     AS datainfo,
                COALESCE(od.status_qc, '-')                                                     AS status_qa,
                COALESCE(TO_CHAR(od.tanggal_nilai, 'YYYY-MM-DD HH24:MI:SS'), '-')               AS tgl_approve_qa,
                COALESCE(od.prem_cred_line, '-')                                                AS tenor,
                '-'                                                                             AS volume,
                COALESCE(TO_CHAR(odd.pickup_date_remark, 'YYYY-MM-DD HH24:MI:SS'), '-')         AS pku_date,
                COALESCE(od.lead_id, '-')                                                       AS lead_id,
                COALESCE(odd.supp_name_1, '-')                                                  AS supp_fullname,
                COALESCE(odd.supp_name_2, '-')                                                  AS Addon_Supplement,
                COALESCE(odd.kota_remark, '-')                                                  AS City_Domisili
            FROM order_data od
            JOIN data_customer_profile cp         ON cp.id          = od.customer_id
            JOIN master_campaign mc               ON mc.id          = od.campaign_id
            JOIN master_campaign_product mcp      ON mcp.id         = mc.id_product
            JOIN master_campaign_type mct         ON mct.id         = mc.id_type
            LEFT JOIN master_status_result msr    ON msr.id         = od.status_result_id
            LEFT JOIN master_reason mr            ON mr.id          = od.reason_id
            LEFT JOIN order_data_details odd      ON odd.order_id   = od.id
            WHERE mc.begin_date <= CURRENT_DATE
            AND (:pAppReferenceId::VARCHAR IS NULL OR od.app_reference_id = :pAppReferenceId and status_date is not null)
            ORDER BY od.case_id limit 10
            ) sub
            """;

    private DumpConstant() {
        // utility class — no instantiation
    }
}

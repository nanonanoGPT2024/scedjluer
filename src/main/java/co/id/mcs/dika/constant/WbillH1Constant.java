package co.id.mcs.dika.constant;

public class WbillH1Constant {

    /**
     * Query export wbill H1 — data AGREE Credit Card dengan pickup_date antara
     * CURRENT_DATE+1 hingga CURRENT_DATE+3 (pickup esok hingga 3 hari ke depan).
     * Disesuaikan dengan schema PostgreSQL project ini.
     *
     * Tabel utama : order_data (od)
     * Join : data_customer_profile (cp)
     * : master_campaign (mc)
     * : master_campaign_product (mcp) — filter product = 'Credit Card'
     * : master_status_result (msr) — filter name = 'AGREE'
     * : order_data_details (odd)
     * : master_segment_card (msc1, msc2)
     * : customer_phone pivot — Phone1..Phone5
     */
    public static final String WBILL_H1_QUERY = """
            WITH phone_pivot AS (
                SELECT
                    customer_id,
                    MAX(CASE WHEN phone_type = 'PRIMARY' THEN phone_number END) AS ph1,
                    MAX(CASE WHEN phone_type = 'SECONDARY' THEN phone_number END) AS ph2,
                    MAX(CASE WHEN phone_type = 'PHONE' THEN phone_number END) AS ph3,
                    MAX(CASE WHEN phone_type = 'SMS' THEN phone_number END) AS ph4,
                    MAX(CASE WHEN phone_type = 'OTHER 5' THEN phone_number END) AS ph5
                FROM customer_phone
                WHERE phone_type IN ('PRIMARY','SECONDARY','PHONE','SMS','OTHER 5')
                GROUP BY customer_id
            )
            SELECT
                'NoCase' AS c1, 'CIS' AS c2, 'NAME' AS c3, 'DOB' AS c4, 'Gender' AS c5, 'Address_Domisili' AS c6, 'Post_Code_Domisili' AS c7, 'City_Domisili' AS c8,
                'Phone_Number_1_Home' AS c9, 'Phone_Number_2_Office' AS c10, 'Phone_Number_3_Mobile' AS c11, 'Phone_Number_4_Mobile' AS c12,
                'Phone_Number_5_Mobile' AS c13, 'ID' AS c14, 'Program_Campaign' AS c15, 'Credit_Card_Type' AS c16, 'Username' AS c17, 'TM_Name' AS c18, 'TM_Code' AS c19,
                'Agree_Date' AS c20, 'Agree_Time' AS c21, 'Upload_Date' AS c22, 'TGL_PU' AS c23, 'Barcode' AS c24, 'Branch' AS c25, 'PU_Time' AS c26, 'Remarks' AS c27,
                'Phone1' AS c28, 'Phone2' AS c29, 'Phone3' AS c30, 'Phone4' AS c31, 'Phone5' AS c32, 'HP1' AS c33, 'HP2' AS c34, 'HomePhone1' AS c35, 'HomePhone2' AS c36, 'OfficePhone1' AS c37,
                'Area' AS c38, 'Nama_Supplement_1' AS c39, 'Hubungan' AS c40, 'Nama_Supplement_2' AS c41, 'Hubungan_2' AS c42, 'Credit_Card_Type_2' AS c43
            UNION ALL
            SELECT * FROM (
                SELECT
                    od.case_id::TEXT                                                                AS NoCase,
                od.cust_no                                                                      AS CIS,
                cp.name                                                                         AS NAME,
                COALESCE(TO_CHAR(cp.tanggal_lahir, 'YYYY-MM-DD'), '0000-00-00')                AS DOB,
                CASE cp.jenis_kelamin
                    WHEN 'LAKI'      THEN 'M'
                    WHEN 'PEREMPUAN' THEN 'F'
                    ELSE '-'
                END                                                                             AS Gender,
                TRIM(CONCAT_WS(' ',
                    NULLIF(odd.alamat_remark, ''),
                    CASE WHEN odd.rt_rw_remark    <> '' AND odd.rt_rw_remark    IS NOT NULL
                         THEN CONCAT('RT/RW ', odd.rt_rw_remark)   END,
                    CASE WHEN odd.kelurahan_remark <> '' AND odd.kelurahan_remark IS NOT NULL
                         THEN CONCAT(', ', odd.kelurahan_remark)   END,
                    CASE WHEN odd.kecamatan_remark <> '' AND odd.kecamatan_remark IS NOT NULL
                         THEN CONCAT(', ', odd.kecamatan_remark)   END,
                    CASE WHEN odd.kode_pos_remark  <> '' AND odd.kode_pos_remark  IS NOT NULL
                         THEN CONCAT(', ', odd.kode_pos_remark)    END,
                    CASE WHEN odd.kota_remark      <> '' AND odd.kota_remark      IS NOT NULL
                         THEN CONCAT(', ', odd.kota_remark)        END
                ))                                                                              AS Address_Domisili,
                COALESCE(NULLIF(odd.kode_pos_remark, ''), '-')                                  AS Post_Code_Domisili,
                COALESCE(NULLIF(odd.kota_remark,     ''), '-')                                  AS City_Domisili,
                COALESCE(pp.ph1, '-')                                                           AS Phone_Number_1_Home,
                COALESCE(pp.ph2, '-')                                                           AS Phone_Number_2_Office,
                COALESCE(NULLIF(odd.phone_wa_remark, ''), '-')                                  AS Phone_Number_3_Mobile,
                COALESCE(pp.ph3, '-')                                                           AS Phone_Number_4_Mobile,
                COALESCE(pp.ph4, '-')                                                           AS Phone_Number_5_Mobile,
                od.cust_no                                                                      AS ID,
                mc.campaign_code                                                                AS Program_Campaign,
                COALESCE(msc1.card_name, '-')                                                   AS Credit_Card_Type,
                od.agent_username                                                               AS Username,
                od.agent_fullname                                                               AS TM_Name,
                od.nik                                                                          AS TM_Code,
                TO_CHAR(od.agree_date, 'YYYY-MM-DD')                                            AS Agree_Date,
                TO_CHAR(od.agree_date, 'HH24:MI:SS')                                            AS Agree_Time,
                TO_CHAR(od.created_date, 'YYYY-MM-DD')                                          AS Upload_Date,
                TO_CHAR(odd.pickup_date_remark, 'YYYY-MM-DD')                                   AS Tgl_PU,
                CONCAT(
                    CASE WHEN mcp.product = 'Credit Card' THEN 'CC'
                         WHEN mcp.product = 'Smartcash'   THEN 'SC'
                         ELSE ''
                    END,
                    od.case_id::TEXT,
                    CASE WHEN mcp.product = 'Credit Card' THEN '11'
                         WHEN mcp.product = 'Smartcash'   THEN '01'
                         ELSE ''
                    END,
                    TO_CHAR(CURRENT_DATE, 'DDMMYY')
                )                                                                               AS Barcode,
                '-'                                                                             AS Branch,
                TO_CHAR(
                    NULLIF(odd.pickup_time_remark, 0) * INTERVAL '1 second',
                    'HH24:MI:SS'
                )                                                                               AS PU_Time,
                COALESCE(NULLIF(odd.remark_for_ms, ''), '-')                                    AS Remarks,
                COALESCE(pp.ph1, '-')                                                           AS Phone1,
                COALESCE(pp.ph2, '-')                                                           AS Phone2,
                COALESCE(pp.ph3, '-')                                                           AS Phone3,
                COALESCE(pp.ph4, '-')                                                           AS Phone4,
                COALESCE(pp.ph5, '-')                                                           AS Phone5,
                COALESCE(NULLIF(odd.handphone_1,   ''), '-')                                    AS HP1,
                COALESCE(NULLIF(odd.handphone_2,   ''), '-')                                    AS HP2,
                COALESCE(NULLIF(odd.landline_phone, ''), '-')                                   AS HomePhone1,
                COALESCE(NULLIF(odd.handphone,      ''), '-')                                   AS HomePhone2,
                COALESCE(NULLIF(odd.office_phone,   ''), '-')                                   AS OfficePhone1,
                '-'                                                                             AS Area,
                COALESCE(NULLIF(odd.supp_name_1, ''), '-')                                      AS Nama_Supplement_1,
                COALESCE(NULLIF(odd.hubungan_supp_1, ''), '-')                                  AS Hubungan,
                COALESCE(NULLIF(odd.supp_name_2, ''), '-')                                      AS Nama_Supplement_2,
                COALESCE(NULLIF(odd.hubungan_supp_2, ''), '-')                                  AS Hubungan_2,
                COALESCE(msc2.card_name, '-')                                                   AS Credit_Card_Type_2
            FROM order_data od
            INNER JOIN master_status_result msr    ON msr.id         = od.status_result_id AND msr.name = 'AGREE'
            INNER JOIN data_customer_profile cp    ON cp.id          = od.customer_id
            INNER JOIN master_campaign mc          ON mc.id          = od.campaign_id
            INNER JOIN master_campaign_product mcp ON mcp.id         = mc.id_product AND mcp.product = 'Credit Card'
            LEFT JOIN order_data_details odd       ON odd.order_id   = od.id
            LEFT JOIN master_segment_card msc1     ON msc1.id        = odd.card_segment_1
            LEFT JOIN master_segment_card msc2     ON msc2.id        = odd.card_segment_2
            LEFT JOIN phone_pivot pp               ON pp.customer_id = od.customer_id
            WHERE 1=1
              AND odd.pickup_date_remark >= CURRENT_DATE + INTERVAL '1 day'
              AND odd.pickup_date_remark  < CURRENT_DATE + INTERVAL '4 day'
              AND (:pAppReferenceId::VARCHAR IS NULL OR od.app_reference_id = :pAppReferenceId)
            ORDER BY od.agree_date ASC
            ) sub
            """;

    private WbillH1Constant() {
        // utility class — no instantiation
    }
}

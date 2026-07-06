-- Active: 1783312416614@@127.0.0.1@5433@58K_tellus@public
select * from mapper;

select * from master_campaign;

select * from master_kota_upload;

-- drop database 58K_tellus;

-- DROP DATABASE "58K_tellus";

select * from order_data;

select * from data_customer_profile;

select * from customer_address;

select * from customer_phone;

select * from master_campaign;

select * from master_campaign_product;

select * from log_upload;

-- Membuat View untuk mempermudah pengecekan (Sudah dibuat di DB)
CREATE OR REPLACE VIEW vw_check_upload AS
SELECT
    od.case_id,
    od.cust_no,
    mc.campaign_code,
    cp.name AS customer_name,
    cph.phone_number,
    cph.phone_type,
    cph.is_primary AS primary_phone,
    ca.alamat_lengkap,
    ca.address_type,
    lu.nama_file AS uploaded_file,
    lu.upload_date,
    od.created_date AS order_created_date
FROM
    order_data od
    INNER JOIN data_customer_profile cp ON od.customer_id = cp.id
    INNER JOIN master_campaign mc ON od.campaign_id = mc.id
    LEFT JOIN log_upload lu ON od.log_upload::uuid = lu.id
    LEFT JOIN customer_phone cph ON cp.id = cph.customer_id
    LEFT JOIN customer_address ca ON cp.id = ca.customer_id;

-- Cukup panggil query ini untuk memverifikasi data masuk:
SELECT DISTINCT case_id FROM vw_check_upload;

select * from leads;

INSERT INTO
    leads (
        id,
        source_id,
        cust_no,
        phone,
        customer_name,
        created_at,
        mock_call,
        campaign_id,
        status,
        app_reference_id
    )
SELECT
    gen_random_uuid (),
    od.id,
    od.case_id,
    cph.phone_number,
    cp.name,
    now(),
    false,
,
    od.campaign_id,
    'NEW',
    od.app_reference_id
FROM
    order_data od
    LEFT JOIN data_customer_profile cp ON od.customer_id = cp.id
    LEFT JOIN customer_phone cph ON od.customer_id = cph.customer_id;

-- 1. CARA UTAMA: Mengosongkan seluruh data transaksi melalui Stored Procedure (Sudah diperbaiki)
CALL truncate_transaction_data ();

TRUNCATE TABLE leads, customer_phone CASCADE;

-- 3. Query Pembuatan Index (Sudah dijalankan di database)
-- CREATE INDEX IF NOT EXISTS idx_order_data_log_upload ON order_data(log_upload);

-- 4. Query untuk mengecek daftar index yang aktif pada tabel order_data
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE
    schemaname = 'public'
    AND tablename = 'order_data';

CREATE INDEX IF NOT EXISTS idx_order_data_log_upload ON order_data (log_upload);

-- 5. Query untuk mengecek processlist (query aktif yang sedang berjalan di PostgreSQL)
SELECT
    pid,
    usename AS db_user,
    client_addr AS client_ip,
    application_name,
    backend_start,
    query_start,
    age (
        clock_timestamp(),
        query_start
    ) AS duration,
    state,
    query
FROM pg_stat_activity
WHERE
    state != 'idle'
ORDER BY duration DESC;

-- Cara menghentikan/kill query yang hang/berjalan terlalu lama:
-- SELECT pg_terminate_backend(pid_yang_mau_di_kill);
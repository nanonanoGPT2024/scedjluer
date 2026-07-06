package co.id.mcs.dika.model;

import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import co.id.mcs.ptdika.MadMachine.Repository.Column;
import co.id.mcs.ptdika.MadMachine.Repository.PrimaryKeyJdbc;
import co.id.mcs.ptdika.MadMachine.Repository.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "order_data", schema = "")
public class OrderData {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "customer_id")
    private UUID customerId; // UUID

    @Column(name = "campaign_id")
    private UUID campaignId; // UUID

    @Column(name = "agent_id")
    private UUID agentId; // UUID

    @Column(name = "status_result_id")
    private UUID statusResultId;

    @Column(name = "reason_id")
    private UUID reasonId;

    @Column(name = "cust_no")
    private String custNo;

    @Column(name = "prem_cred_line")
    private String premCredLine;

    @Column(name = "created_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @Column(name = "update_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateDate;

    @Column(name = "program")
    private String program;

    @Column(name = "penawaran")
    private String penawaran;

    @Column(name = "promo")
    private String promo;

    @Column(name = "business_category")
    private Integer businessCategory;

    @Column(name = "id_pic")
    private UUID idPic;

    @Column(name = "last_activity_id")
    private UUID lastActivityId; // UUID

    @Column(name = "last_id_activity_qc")
    private UUID lastIdActivityQc;

    @Column(name = "json_qc_last_score")
    private Object jsonQcLastScore; // json in DB

    @Column(name = "tanggal_nilai")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date tanggalNilai;

    @Column(name = "qc_username")
    private String qcUsername;

    @Column(name = "id_qc")
    private UUID idQc;

    @Column(name = "id_manager")
    private UUID idManager;

    @Column(name = "log_upload")
    private String logUpload;

    @Column(name = "distribusi_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date distribusiDate;

    @Column(name = "distribusi_date_spv")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date distribusiDateSpv;

    @Column(name = "flag_call")
    private Boolean flagCall;

    @Column(name = "rf1")
    private String rf1;

    @Column(name = "rf2")
    private String rf2;

    @Column(name = "rf3")
    private String rf3;

    @Column(name = "status_ms")
    private String statusMs;

    @Column(name = "reason_ms")
    private String reasonMs;

    @Column(name = "kota_tm")
    private String kotaTm;

    @Column(name = "lead_id")
    private String leadId;

    @Column(name = "ms_code")
    private String msCode;

    @Column(name = "data_pend_doc")
    private String dataPendDoc;

    @Column(name = "pickup_result_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickupResultDate;

    @Column(name = "ms_code_update")
    private String msCodeUpdate;

    @Column(name = "ms_name_update")
    private String msNameUpdate;

    @Column(name = "pickup_result_date_ms")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date pickupResultDateMs;

    @Column(name = "status_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date statusDate;

    @Column(name = "flaging_bucket_qc")
    private Integer flagingBucketQc;

    @Column(name = "rff_app_id")
    private String rffAppId;

    @Column(name = "agree_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date agreeDate;

    @Column(name = "status_id")
    private UUID statusId;

    @Column(name = "status_call_id")
    private UUID statusCallId;

    @Column(name = "camp_is_active")
    private Boolean campIsActive;

    @Column(name = "camp_is_published")
    private Boolean campIsPublished;

    @Column(name = "flag_suplement")
    private Boolean flagSuplement;

    @Column(name = "remark")
    private String remark;

    @Column(name = "flag_vip")
    private Boolean flagVip;

    @Column(name = "flag_reload")
    private Boolean flagReload;

    @Column(name = "status_qc")
    private String statusQc;

    @Column(name = "agent_fullname")
    private String agentFullname;

    @Column(name = "agent_username")
    private String agentUsername;

    @Column(name = "pic_fullname")
    private String picFullname;

    @Column(name = "pic_username")
    private String picUsername;

    @Column(name = "qc_fullname")
    private String qcFullname;

    @Column(name = "is_publish")
    private Boolean isPublish;

    @Column(name = "flag_return")
    private Boolean flagReturn;

    @Column(name = "case_id")
    private Long caseId;
 
    @Column(name = "nik")
    private String nik;

    @Column(name = "app_reference_id")
    private String appReferenceId;

    @Column(name = "last_call")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastCall;

    @Column(name = "last_hangup_code")
    private String lastHangupCode;

    @Column(name = "last_hangup_cause")
    private String lastHangupCause;
}



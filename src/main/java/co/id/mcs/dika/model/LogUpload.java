package co.id.mcs.dika.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.Date;
import java.util.UUID;

import co.id.mcs.ptdika.MadMachine.Repository.Column;
import co.id.mcs.ptdika.MadMachine.Repository.PrimaryKeyJdbc;
import co.id.mcs.ptdika.MadMachine.Repository.Table;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "log_upload", schema = "")
public class LogUpload {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "nama_file")
    private String namaFile;

    @Column(name = "total_data")
    private Integer totalData;

    @Column(name = "success")
    private Integer success;

    @Column(name = "duplicate")
    private Integer duplicate;

    @Column(name = "failed_upload")
    private Integer failedUpload;

    @Column(name = "upload_by")
    private UUID uploadBy; // UUID

    @Column(name = "upload_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date uploadDate;

    @Column(name = "campaign_code")
    private String campaignCode;

    @Column(name = "error")
    private Integer error;

    @Column(name = "app_reference_id")
    private String appReferenceId;
}



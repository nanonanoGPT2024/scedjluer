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
@Table(name = "printing", schema = "")
public class Printing {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id;

    @Column(name = "no_case")
    private Long noCase;

    @Column(name = "cis")
    private String cis;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "status_pick_up_ms")
    private String statusPickUpMs;

    @Column(name = "status_gagal_pu")
    private String statusGagalPu;

    @Column(name = "pu_ms_date")
    private String puMsDate;

    @Column(name = "ms_code")
    private String msCode;

    @Column(name = "ms_name")
    private String msName;

    @Column(name = "date_upload")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date dateUpload;
}


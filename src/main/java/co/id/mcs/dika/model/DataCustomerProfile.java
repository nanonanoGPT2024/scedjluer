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
@Table(name = "data_customer_profile", schema = "")
public class DataCustomerProfile {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "cust_no")
    private String custNo;

    @Column(name = "name")
    private String name;

    @Column(name = "no_ktp_kitas")
    private String noKtpKitas;

    @Column(name = "tanggal_lahir")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date tanggalLahir;

    @Column(name = "tempat_lahir")
    private String tempatLahir;

    @Column(name = "jenis_kelamin")
    private String jenisKelamin; // Enum in DB

    @Column(name = "no_npwp")
    private String noNpwp;

    @Column(name = "email")
    private String email;

    @Column(name = "telp_hp_utama")
    private String telpHpUtama;

    // @Column(name = "expired")
    // @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    // private Date expired;

    @Column(name = "reff_app_id")
    private String reffAppId;

    @Column(name = "agent_bank")
    private String agentBank;

    @Column(name = "nama_suplement")
    private String namaSuplement;

    @Column(name = "app_reference_id")
    private String appReferenceId;
}



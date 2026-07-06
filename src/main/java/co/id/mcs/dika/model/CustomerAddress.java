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
@Table(name = "customer_address", schema = "")
public class CustomerAddress {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "customer_id")
    private UUID customerId; // UUID

    @Column(name = "address_type")
    private String addressType; // Enum in DB, String here for simplicity

    @Column(name = "alamat_lengkap")
    private String alamatLengkap;

    @Column(name = "rt_rw")
    private String rtRw;

    @Column(name = "kelurahan")
    private String kelurahan;

    @Column(name = "kecamatan")
    private String kecamatan;

    @Column(name = "kota")
    private String kota;

    @Column(name = "kode_pos")
    private String kodePos;

    @Column(name = "created_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @Column(name = "update_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateAt;

    @Column(name = "created_by")
    private Integer createdBy;
}



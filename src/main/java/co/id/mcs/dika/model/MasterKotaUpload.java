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
@Table(name = "master_kota_upload", schema = "")
public class MasterKotaUpload {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id;

    @Column(name = "kota")
    private String kota;

    @Column(name = "is_description")
    private Integer isDescription;

    @Column(name = "id_product")
    private UUID idProduct;

    @Column(name = "date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date date;
}

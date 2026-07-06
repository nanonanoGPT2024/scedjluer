package co.id.mcs.dika.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.UUID;

import co.id.mcs.ptdika.MadMachine.Repository.Column;
import co.id.mcs.ptdika.MadMachine.Repository.PrimaryKeyJdbc;
import co.id.mcs.ptdika.MadMachine.Repository.Table;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@ToString
@Table(name = "mapper", schema = "")
public class Mapper {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "mapper")
    private String mapper;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "file")
    private String file;

    @Column(name = "type_id")
    private UUID typeId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "extrak_date_mode")
    private Integer extrakDateMode;

    @Column(name = "firstname")
    private String firstname;

    @Column(name = "app_reference_id")
    private String appReferenceId;
}



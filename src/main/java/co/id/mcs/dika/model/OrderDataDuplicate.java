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
@Table(name = "order_data_duplicate", schema = "")
public class OrderDataDuplicate {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id;

    @Column(name = "cust_no")
    private String custNo;

    @Column(name = "name")
    private String name;

    @Column(name = "campaign_code")
    private String campaignCode;

    @Column(name = "created_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "error")
    private String error;
}



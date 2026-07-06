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
@Table(name = "master_campaign", schema = "")
public class MasterCampaign {

    @PrimaryKeyJdbc
    @Column(name = "id")
    private UUID id; // UUID

    @Column(name = "campaign_code")
    private String campaignCode;

    @Column(name = "description")
    private String description;

    @Column(name = "begin_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date beginDate;

    @Column(name = "end_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date endDate;

    @Column(name = "campaign_origin")
    private String campaignOrigin;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_published")
    private Boolean isPublished;

    @Column(name = "created_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @Column(name = "updated_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedDate;

    @Column(name = "id_type")
    private UUID idType;

    @Column(name = "id_product")
    private UUID idProduct;

    @Column(name = "app_reference_id")
    private String appReferenceId;
}

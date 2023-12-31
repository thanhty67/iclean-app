package iclean.code.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import iclean.code.data.enumjava.OptionProcessReportEnum;
import iclean.code.data.enumjava.ReportStatusEnum;
import iclean.code.utils.Utils;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer"})
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Integer reportId;

    private String detail;

    private String solution;

    @Column(name = "report_status")
    private ReportStatusEnum reportStatus;

    @Column(name = "option_process")
    private OptionProcessReportEnum option;

    private Double refund;

    @Column(name = "refund_money")
    private Double refundMoney = 0D;

    @Column(name = "refund_point")
    private Double refundPoint = 0D;

    @Column(name = "penalty_money")
    private Double penaltyMoney = 0D;

    @Column(name = "create_at")
    private LocalDateTime createAt = Utils.getLocalDateTimeNow();

    @Column(name = "process_at")
    private LocalDateTime processAt;

    @OneToOne
    @JsonIgnoreProperties("report")
    @JoinColumn(name = "booking_detail_id")
    private BookingDetail bookingDetail;

    @OneToMany(mappedBy = "report")
    @JsonIgnoreProperties("report")
    @JsonIgnore
    private List<ReportAttachment> reportAttachments;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "report_type_id")
    private ReportType reportType;
}

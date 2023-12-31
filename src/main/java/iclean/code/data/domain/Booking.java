package iclean.code.data.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import iclean.code.data.enumjava.BookingStatusEnum;
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
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Integer bookingId;

    private String bookingCode = Utils.generateRandomCode();

    private String location;

    @Column(name = "location_description")
    private String locationDescription;

    private Double longitude;

    private Double latitude;

    @Column(name = "order_date")
    private LocalDateTime orderDate = Utils.getLocalDateTimeNow();

    @Column(name = "accept_date")
    private LocalDateTime acceptDate;

    @Column(name = "rj_reason_description")
    private String rjReasonDescription;

    @Column(name = "request_count")
    private Integer requestCount;

    @Column(name = "total_price")
    private Double totalPrice;

    @Column(name = "total_price_actual")
    private Double totalPriceActual;

    private Double point;

    @Column(name = "using_point")
    private Boolean usingPoint = false;

    @Column(name = "option_json")
    private String option;

    @Column(name = "auto_assign")
    private Boolean autoAssign;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Column(name = "booking_status")
    private BookingStatusEnum bookingStatus;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "renter_id")
    private User renter;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "manager_id")
    private User manager;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "rejection_reason_id")
    private RejectionReason rejectionReason;

    @OneToMany(mappedBy = "booking")
    @JsonIgnoreProperties("booking")
    @JsonIgnore
    private List<BookingDetail> bookingDetails;

}
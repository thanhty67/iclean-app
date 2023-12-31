package iclean.code.data.dto.response.bookingdetail;

import lombok.Data;

import javax.persistence.Column;
import java.time.LocalDateTime;

@Data
public class GetBookingDetailResponse {
    private Integer bookingDetailId;
    private String renterName;
    private String bookingCode;
    private LocalDateTime orderDate;
    private Integer serviceId;
    private Integer serviceUnitId;
    private String serviceName;
    private String serviceIcon;
    private String workDate;
    private String note;
    private String workTime;
    private String value;
    private Double equivalent;
    private Double price;
    private Double refundMoney;
    private Double refundPoint;
    private Double penaltyMoney;
    private String status;
    private Double latitude;
    private Double longitude;
    private Boolean reported;
}
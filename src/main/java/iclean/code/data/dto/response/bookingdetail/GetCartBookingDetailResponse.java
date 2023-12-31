package iclean.code.data.dto.response.bookingdetail;

import lombok.Data;

@Data
public class GetCartBookingDetailResponse {
    private Integer cartItemId;
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
}

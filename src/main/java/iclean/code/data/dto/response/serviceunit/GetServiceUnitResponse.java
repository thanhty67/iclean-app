package iclean.code.data.dto.response.serviceunit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GetServiceUnitResponse {
    private Integer serviceUnitId;
    private Integer unitId;
    private String unitDetail;
    private Double unitValue;
    private Double defaultPrice;
    private Double helperCommission;
    private Boolean isDeleted;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
}

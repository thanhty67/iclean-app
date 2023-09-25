package iclean.code.data.dto.request.booking;

import lombok.*;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import javax.persistence.Column;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AddBookingRequest {

    @NotNull(message = "Kinh độ không được để trống")
    private double longitude;

    @NotNull(message = "Vĩ độ không được để trống")
    private double latitude;

    @NotNull(message = "Vị trí không được để trống")
    @NotBlank(message = "Vị trí không được để trống")
    private String location;

    @Length(max = 200, message = "Tối đa 200 từ")
    private String locationDescription;

    @Range(min = 1, max = 8, message = "Giờ làm không được lớn hơn 8 tiếng")
    private double workHour;

    @Range(min = 1, max = 8, message = "Giờ làm thực tế không được lớn hơn 8 tiếng")
    private double workHourActual;

    @Range(min = 1000, message = "Giá tiền phải lớn hơn 1000 VNĐ")
    private double totalPrice;

    @Range(min = 1, message = "Mã nhân viên phải lớn hơn 1")
    private int staffId;

    @Range(min = 1, message = "Mã khách hàng phải lớn hơn 1")
    private int renterId;

    @Range(min = 1, message = "Mã công việc phải lớn hơn 1")
    private int jobId;

}
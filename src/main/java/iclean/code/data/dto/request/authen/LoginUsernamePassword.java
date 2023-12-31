package iclean.code.data.dto.request.authen;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginUsernamePassword {
    @NotNull(message = "Tên đăng nhập là bắt buộc")
    @NotBlank(message = "Tên đăng nhập không được bỏ trống")
    @Schema(example = "nhatlinh")
    private String username;

    @NotNull(message = "Mật khẩu bắt buộc")
    @NotBlank(message = "Mật khẩu không được bỏ trống")
    @Schema(example = "$2y$10$PjxrokalgyXaABBMQhmbJeO9z87cXDmnrJedmOBELlP7dImfhFwe6")
    private String password;

}

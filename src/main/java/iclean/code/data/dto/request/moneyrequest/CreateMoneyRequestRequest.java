package iclean.code.data.dto.request.moneyrequest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateMoneyRequestRequest {
    private String userPhoneNumber;
    private Double balance;
    private String moneyRequestType;
}

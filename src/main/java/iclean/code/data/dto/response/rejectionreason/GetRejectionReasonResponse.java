package iclean.code.data.dto.response.rejectionreason;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetRejectionReasonResponse {
    private Integer rejectionReasonId;
    private String rejectionReasonContent;
}

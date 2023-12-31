package iclean.code.function.rejectionreason.controller;

import iclean.code.data.dto.common.ResponseObject;
import iclean.code.function.rejectionreason.service.RejectReasonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/rejection-reason")
@Tag(name = "Rejection Reason API")
@PreAuthorize("hasAuthority('admin')")
public class RejectReasonController {
    @Autowired
    private RejectReasonService rejectReasonService;

    @GetMapping
    @Operation(summary = "Get all rejection reasons", description = "Return all rejection reasons information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New Employees Information"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Login please"),
            @ApiResponse(responseCode = "403", description = "Forbidden - You don't have permission to access on this api"),
            @ApiResponse(responseCode = "400", description = "Bad request - Missing some field required")
    })
    @PreAuthorize("hasAnyAuthority('admin', 'manager')")
    public ResponseEntity<ResponseObject> getRejectionReasons() {
        return rejectReasonService.getRejectionReasons();
    }
}

package iclean.code.function.common.service;

import iclean.code.data.dto.request.security.ValidateOTPRequest;

public interface TwilioOTPService {
    public String sendAndGetOTPToken(String phoneNumberDto);
    public boolean validateOTP(ValidateOTPRequest validateOTPRequest);
}

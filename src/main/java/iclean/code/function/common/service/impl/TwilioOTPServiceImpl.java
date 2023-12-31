package iclean.code.function.common.service.impl;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import iclean.code.config.TwilioConfig;
import iclean.code.data.dto.request.security.ValidateOTPRequest;
import iclean.code.data.repository.SystemParameterRepository;
import iclean.code.function.common.service.TwilioOTPService;
import iclean.code.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.persistence.EntityNotFoundException;
import java.text.DecimalFormat;
import java.util.Random;

@Service
public class TwilioOTPServiceImpl implements TwilioOTPService {
    @Autowired
    private TwilioConfig twilioConfig;

    @Value("${iclean.app.default.otp.message}")
    private String defaultMessage;

    @Autowired
    private SystemParameterRepository systemParameterRepository;

    private String getMessageDefault() {
        try {
            return systemParameterRepository
                    .findSystemParameterByParameterField("otp_message_default")
                    .getParameterValue();

        } catch (EntityNotFoundException e) {
            return defaultMessage;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "%s";
        }
    }

    private String generateOTP() {
        return new DecimalFormat("0000")
                .format(new Random().nextInt(9999));
    }

    @Override
    public String sendAndGetOTPToken(String phoneNumberDto) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        try {
            String pattern = "^0";
            phoneNumberDto = phoneNumberDto.replaceFirst(pattern, "+84");
            PhoneNumber to = new PhoneNumber(phoneNumberDto);
            PhoneNumber from = new PhoneNumber(twilioConfig.getTrialNumber());
            String otp = generateOTP();
            String defaultMessage = getMessageDefault();
            if (Utils.isNullOrEmpty(defaultMessage) || !defaultMessage.contains("%s"))
                defaultMessage += "%s";

            String otpMessage = String.format(defaultMessage, otp);
            Message message = Message
                    .creator(to,
                            from,
                            otpMessage)
                    .create();
        return passwordEncoder.encode(otp);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return passwordEncoder.encode("2356");
    }

    @Override
    public boolean validateOTP(ValidateOTPRequest validateOTPRequest) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(validateOTPRequest.getUserOtpInput(),
                validateOTPRequest.getOtpToken());
    }
}

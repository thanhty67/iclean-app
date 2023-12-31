package iclean.code.function.serviceprice.service.impl;

import iclean.code.data.domain.ServiceUnit;
import iclean.code.data.domain.ServicePrice;
import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.serviceprice.GetServicePriceRequest;
import iclean.code.data.dto.request.serviceprice.GetServicePriceRequestDto;
import iclean.code.data.repository.ServiceUnitRepository;
import iclean.code.data.repository.ServicePriceRepository;
import iclean.code.exception.NotFoundException;
import iclean.code.function.serviceprice.service.ServicePriceService;
import iclean.code.utils.Utils;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Log4j2
public class ServicePriceServiceImpl implements ServicePriceService {

    @Autowired
    private ServicePriceRepository servicePriceRepository;

    @Autowired
    private ServiceUnitRepository serviceUnitRepository;

    @Autowired
    private ModelMapper modelMapper;
    @Value("${iclean.app.default.start.time}")
    private String stringStartTimeDefault;
    @Value("${iclean.app.default.end.time}")
    private String stringEndTimeDefault;

    @Override
    public ResponseEntity<ResponseObject> getServicePriceActive(GetServicePriceRequest request) {
        try {
            Double totalPrice = getServicePrice(request);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Not implement yet!", totalPrice));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public Double getServicePrice(GetServicePriceRequest request) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            // Parse the string to LocalTime
            LocalTime startTimeDefault = LocalTime.parse(stringStartTimeDefault, formatter);
            LocalTime endTimeDefault = LocalTime.parse(stringEndTimeDefault, formatter);

            GetServicePriceRequestDto requestDto = modelMapper.map(request, GetServicePriceRequestDto.class);
            ServiceUnit serviceUnit = findServiceUnitById(requestDto.getServiceUnitId());
            LocalTime startTime = requestDto.getStartTime();
            Double hour = serviceUnit.getUnit().getUnitValue();
            LocalTime endTime = Utils.plusLocalTime(startTime, hour);
            double totalPrice = 0;
            List<ServicePrice> servicePrices = servicePriceRepository.findByServiceUnitId(serviceUnit.getServiceUnitId());
            if (!servicePrices.isEmpty()) {
                int i = 0;
                do {
                    if (i >= servicePrices.size()) {
                        break;
                    }
                    ServicePrice servicePrice = servicePrices.get(i);

                    if (Utils.isAfterOrEqual(startTime, servicePrice.getStartTime())
                            && Utils.isBeforeOrEqual(endTime, servicePrice.getEndTime())) {
                        double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPrice += servicePrice.getPrice() * hour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        continue;
                    }

                    if (Utils.isBeforeOrEqual(startTime, startTimeDefault)) {
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        if (numberHour >= 4) {
                            break;
                        }
                        totalPrice += servicePrice.getPrice() * numberHour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, startTimeDefault) && Utils.isBeforeOrEqual(startTime, endTimeDefault)) { //
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPrice += serviceUnit.getDefaultPrice() * numberHour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, endTimeDefault)) { //
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPrice += servicePrice.getPrice() * numberHour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, servicePrice.getEndTime())) {
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        if (numberHour >= 4) {
                            break;
                        }
                        totalPrice += servicePrice.getPrice() * numberHour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }
                    Double numberHour = Utils.minusLocalTime(startTime, servicePrice.getStartTime());
                    totalPrice += serviceUnit.getDefaultPrice() * numberHour;
                    startTime = Utils.plusLocalTime(startTime, numberHour);

                } while (startTime.isBefore(endTime) && startTime.plusSeconds(20).isBefore(endTime));
            } else {
                totalPrice = serviceUnit.getDefaultPrice() * serviceUnit.getUnit().getUnitValue();
            }
            return totalPrice;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    @Override
    public Double getServiceHelperPrice(GetServicePriceRequest request) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            // Parse the string to LocalTime
            LocalTime startTimeDefault = LocalTime.parse(stringStartTimeDefault, formatter);
            LocalTime endTimeDefault = LocalTime.parse(stringEndTimeDefault, formatter);

            GetServicePriceRequestDto requestDto = modelMapper.map(request, GetServicePriceRequestDto.class);
            ServiceUnit serviceUnit = findServiceUnitById(requestDto.getServiceUnitId());
            LocalTime startTime = requestDto.getStartTime();
            Double hour = serviceUnit.getUnit().getUnitValue();
            LocalTime endTime = Utils.plusLocalTime(startTime, hour);

            double totalPriceHelper = 0;
            List<ServicePrice> servicePrices = servicePriceRepository.findByServiceUnitId(serviceUnit.getServiceUnitId());
            if (!servicePrices.isEmpty()) {
                int i = 0;
//               do {
//                    if (i >= servicePrices.size()) {
//                        break;
//                    }
//                    ServicePrice servicePrice = servicePrices.get(i);
//                    if (Utils.isBeforeOrEqual(servicePrice.getEndTime(), startTime)) {
//                        i++;
//                        continue;
//                    } else if (Utils.isBeforeOrEqual(servicePrice.getStartTime(), startTime)
//                            && Utils.isBeforeOrEqual(servicePrice.getEndTime(), endTime)) {
//                        Double numberHour = Utils.minusLocalTime(startTime, servicePrice.getEndTime());
//                        totalPriceHelper += servicePrice.getPrice() * numberHour * servicePrice.getEmployeeCommission() / 100;
//                        startTime = Utils.plusLocalTime(startTime, numberHour);
//                        i++;
//                        continue;
//                    } else if (Utils.isBeforeOrEqual(servicePrice.getStartTime(), startTime)
//                            && Utils.isAfterOrEqual(servicePrice.getEndTime(), endTime)) {
//                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
//                        totalPriceHelper += servicePrice.getPrice() * numberHour * servicePrice.getEmployeeCommission() / 100;
//                        startTime = Utils.plusLocalTime(startTime, numberHour);
//                        continue;
//                    }
//                    Double numberHour = Utils.minusLocalTime(startTime, servicePrice.getStartTime());
//                    totalPriceHelper += serviceUnit.getDefaultPrice() * numberHour * serviceUnit.getHelperCommission() / 100;
//                    startTime = Utils.plusLocalTime(startTime, numberHour);
//
//                } while (startTime.isBefore(endTime) && startTime.plusSeconds(20).isBefore(endTime));
                do {
                    if (i >= servicePrices.size()) {
                        break;
                    }
                    ServicePrice servicePrice = servicePrices.get(i);

                    if (Utils.isAfterOrEqual(startTime, servicePrice.getStartTime())
                            && Utils.isBeforeOrEqual(endTime, servicePrice.getEndTime())) {
                        double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPriceHelper += servicePrice.getPrice() * numberHour * servicePrice.getEmployeeCommission() / 100;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        continue;
                    }

                    if (Utils.isBeforeOrEqual(startTime, startTimeDefault)) {
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        if (numberHour >= 4) {
                            break;
                        }
                        totalPriceHelper += servicePrice.getPrice() * numberHour * servicePrice.getEmployeeCommission() / 100;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, startTimeDefault) && Utils.isBeforeOrEqual(startTime, endTimeDefault)) { //
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPriceHelper += serviceUnit.getDefaultPrice() * numberHour * serviceUnit.getHelperCommission() / 100;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, endTimeDefault)) { //
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        totalPriceHelper += servicePrice.getPrice() * numberHour;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }

                    if (Utils.isAfterOrEqual(startTime, servicePrice.getEndTime())) {
                        Double numberHour = Utils.minusLocalTime(startTime, endTime);
                        if (numberHour >= 4) {
                            break;
                        }
                        totalPriceHelper += servicePrice.getPrice() * numberHour * servicePrice.getEmployeeCommission() / 100;
                        startTime = Utils.plusLocalTime(startTime, numberHour);
                        i++;
                        continue;
                    }
                    Double numberHour = Utils.minusLocalTime(startTime, servicePrice.getStartTime());
                    totalPriceHelper += serviceUnit.getDefaultPrice() * numberHour * serviceUnit.getHelperCommission() / 100;
                    startTime = Utils.plusLocalTime(startTime, numberHour);

                } while (startTime.isBefore(endTime) && startTime.plusSeconds(20).isBefore(endTime));

            } else {
                totalPriceHelper = serviceUnit.getDefaultPrice() * serviceUnit.getUnit().getUnitValue() * serviceUnit.getHelperCommission() / 100;
            }
            return totalPriceHelper;
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
    }

    private ServiceUnit findServiceUnitById(int id) {
        return serviceUnitRepository.findById(id).orElseThrow(() -> new NotFoundException(String.format("Service Unit ID %s is not found", id)));
    }
}

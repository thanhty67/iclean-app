package iclean.code.function.bookingdetail.service.impl;

import iclean.code.config.MessageVariable;
import iclean.code.config.SystemParameterField;
import iclean.code.data.domain.*;
import iclean.code.data.dto.common.Position;
import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.authen.NotificationRequestDto;
import iclean.code.data.dto.request.booking.CreateBookingHelperRequest;
import iclean.code.data.dto.request.booking.QRCodeValidate;
import iclean.code.data.dto.request.bookingdetail.HelperChoiceRequest;
import iclean.code.data.dto.request.bookingdetail.ResendBookingDetailRequest;
import iclean.code.data.dto.request.security.ValidateOTPRequest;
import iclean.code.data.dto.request.serviceprice.GetServicePriceRequest;
import iclean.code.data.dto.request.transaction.TransactionRequest;
import iclean.code.data.dto.request.workschedule.DateTimeRange;
import iclean.code.data.dto.response.PageResponseObject;
import iclean.code.data.dto.response.booking.GetBookingResponseForHelper;
import iclean.code.data.dto.response.booking.GetTransactionBookingResponse;
import iclean.code.data.dto.response.bookingdetail.*;
import iclean.code.data.dto.response.bookingdetailhelper.GetBookingDetailHelperResponse;
import iclean.code.data.dto.response.bookingdetailhelper.GetHelpersResponse;
import iclean.code.data.dto.response.feedback.GetFeedbackResponse;
import iclean.code.data.dto.response.feedback.PointFeedbackOfHelper;
import iclean.code.data.dto.response.service.PriceService;
import iclean.code.data.enumjava.*;
import iclean.code.data.repository.*;
import iclean.code.exception.BadRequestException;
import iclean.code.exception.NotFoundException;
import iclean.code.exception.UserNotHavePermissionException;
import iclean.code.function.bookingdetail.service.BookingDetailService;
import iclean.code.function.common.service.FirebaseRealtimeDatabaseService;
import iclean.code.function.feedback.service.FeedbackService;
import iclean.code.function.serviceprice.service.ServicePriceService;
import iclean.code.function.common.service.FCMService;
import iclean.code.function.common.service.GoogleMapService;
import iclean.code.function.common.service.QRCodeService;
import iclean.code.utils.Utils;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
public class BookingDetailServiceImpl implements BookingDetailService {
    @Autowired
    private FCMService fcmService;

    @Value("${iclean.app.max.distance.length}")
    private Double maxDistance;

    @Value("${iclean.app.max.late.minutes}")
    private long maxLateMinutes;
    @Value("${iclean.app.max.soon.minutes}")
    private long maxSoonMinutes;

    @Value("${iclean.app.max.update.and.cancel.minutes}")
    private long maxUpdateMinutes;

    @Value("${iclean.app.money.to.point}")
    private long moneyToPoint;

    @Value("${iclean.app.max.end.time.minutes}")
    private long maxEndTimeMinutes;

    @Autowired
    private HelperInformationRepository helperInformationRepository;

    @Autowired
    private QRCodeService qrCodeService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRegistrationRepository serviceRegistrationRepository;

    @Autowired
    private BookingDetailStatusHistoryRepository bookingDetailStatusHistoryRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ServiceUnitRepository serviceUnitRepository;

    @Autowired
    private BookingDetailRepository bookingDetailRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FirebaseRealtimeDatabaseService firebaseRealtimeDatabaseService;

    @Value("${iclean.app.max.distance.length}")
    private Double delayMinutes;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private BookingDetailHelperRepository bookingDetailHelperRepository;

    @Autowired
    private SystemParameterRepository systemParameterRepository;
    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private ServicePriceService servicePriceService;
    @Autowired
    GoogleMapService googleMapService;
    @Autowired
    private ModelMapper modelMapper;

    @Override
    public ResponseEntity<ResponseObject> cancelBookingDetail(Integer renterId, Integer detailId) {
        try {
            BookingDetail bookingDetail = findById(detailId);
            isPermission(renterId, bookingDetail);
            List<Integer> userIds;
            List<BookingDetailHelper> bookingDetailHelpers;
            switch (bookingDetail.getBookingDetailStatus()) {
                case WAITING:
                    String checkTime = checkInTime(LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart()),
                            bookingDetail.getServiceUnit().getService().getServiceName());
                    if (!Utils.isNullOrEmpty(checkTime)) {
                        throw new BadRequestException(String.format(checkTime, bookingDetail.getServiceUnit().getService().getServiceName()));
                    }
                    bookingDetailHelpers = bookingDetailHelperRepository.findByBookingDetailIdAndActive(detailId, BookingDetailHelperStatusEnum.ACTIVE);
                    if (!bookingDetailHelpers.isEmpty()) {
                        userIds = bookingDetailHelpers
                                .stream()
                                .map(element -> element
                                        .getServiceRegistration()
                                        .getHelperInformation()
                                        .getUser().getUserId()).collect(Collectors.toList());
                        NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
                        notificationRequestDto.setBody(String.format(MessageVariable.RENTER_CANCEL_BOOKING,
                                bookingDetail.getBooking().getBookingCode()));
                        notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                        sendNotificationForUser(notificationRequestDto, userIds);
                    }
                case APPROVED:
                case NOT_YET:
                    bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.CANCEL_BY_RENTER);
                    bookingDetailRepository.save(bookingDetail);
                    NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
                    notificationRequestDto.setBody(String.format(MessageVariable.RENTER_CANCEL_BOOKING_TITLE,
                            bookingDetail.getBooking().getBookingCode()));
                    notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                    sendNotificationForUser(notificationRequestDto, renterId);
                    break;
                default:
                    throw new BadRequestException(String.format(MessageVariable.CANNOT_CANCEL_BOOKING, bookingDetail.getBooking().getBookingCode()));
            }
            int bookingId = bookingDetail.getBooking().getBookingId();
            String bookingCode = bookingDetail.getBooking().getBookingCode();

            Transaction transaction = transactionRepository.findByBookingIdAndWalletTypeAndTransactionTypeAndUserId(
                    bookingId,
                    WalletTypeEnum.POINT,
                    TransactionTypeEnum.WITHDRAW,
                    renterId);
            if (transaction != null) {
                createTransaction(new TransactionRequest(
                        transaction.getAmount(),
                        String.format(MessageVariable.REFUND_POINT_CANCEL_BOOKING, bookingCode),
                        renterId,
                        TransactionTypeEnum.DEPOSIT.name(),
                        WalletTypeEnum.POINT.name(),
                        bookingId));
            }

            Transaction transactionMoney = transactionRepository.findByBookingIdAndWalletTypeAndTransactionTypeAndUserId(
                    bookingId,
                    WalletTypeEnum.MONEY,
                    TransactionTypeEnum.WITHDRAW,
                    renterId);
            if (transactionMoney != null) {
                createTransaction(new TransactionRequest(
                        transactionMoney.getAmount(),
                        String.format(MessageVariable.REFUND_REJECT_BOOKING, bookingCode),
                        renterId,
                        TransactionTypeEnum.DEPOSIT.name(),
                        WalletTypeEnum.MONEY.name(),
                        bookingId));
            }
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Cancel a service of a booking successful!", null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    @Transactional(rollbackFor = BadRequestException.class)
    public ResponseEntity<ResponseObject> cancelBookingDetailByHelper(Integer helperId, Integer detailId) {
        try {
            BookingDetail bookingDetail = findById(detailId);
            findByHelperId(helperId, bookingDetail.getBookingDetailId());
            isPermissionForHelper(helperId, bookingDetail);
            Booking booking = bookingDetail.getBooking();
            NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
            switch (bookingDetail.getBookingDetailStatus()) {
                case WAITING:
                    String checkTime = checkInTime(LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart()),
                            bookingDetail.getServiceUnit().getService().getServiceName());
                    if (!Utils.isNullOrEmpty(checkTime)) {
                        throw new BadRequestException(String.format(checkTime, bookingDetail.getServiceUnit().getService().getServiceName()));
                    }
                    notificationRequestDto.setBody(String.format(MessageVariable.HELPER_CANCEL_BOOKING,
                            booking.getBookingCode()));
                    notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                    sendNotificationForUser(notificationRequestDto, booking.getRenter().getUserId());
                    bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.CANCEL_BY_HELPER);
                    bookingDetailRepository.save(bookingDetail);
                    break;
                case APPROVED:
                    Optional<BookingDetailHelper> bookingDetailHelper = bookingDetailHelperRepository.findByBookingDetailIdAndHelperId(detailId, helperId);
                    bookingDetailHelper.ifPresent(detailHelper -> bookingDetailHelperRepository.delete(detailHelper));
                    notificationRequestDto.setBody(String.format(MessageVariable.HELPER_CANCEL_BOOKING,
                            booking.getBookingCode()));
                    notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                    sendNotificationForUser(notificationRequestDto, booking.getRenter().getUserId());
                    bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.CANCEL_BY_HELPER);
                    bookingDetailRepository.save(bookingDetail);
                    break;
                default:
                    throw new BadRequestException(String.format(MessageVariable.CANNOT_CANCEL_BOOKING, booking.getBookingCode()));
            }
            int bookingId = bookingDetail.getBooking().getBookingId();
            String bookingCode = bookingDetail.getBooking().getBookingCode();
            int renterId = booking.getRenter().getUserId();

            Transaction transaction = transactionRepository.findByBookingIdAndWalletTypeAndTransactionTypeAndUserId(
                    bookingId,
                    WalletTypeEnum.POINT,
                    TransactionTypeEnum.WITHDRAW,
                    renterId);
            if (transaction != null) {
                createTransaction(new TransactionRequest(
                        transaction.getAmount(),
                        String.format(MessageVariable.REFUND_POINT_CANCEL_BOOKING, bookingCode),
                        renterId,
                        TransactionTypeEnum.DEPOSIT.name(),
                        WalletTypeEnum.POINT.name(),
                        bookingId));
            }

            Transaction transactionMoney = transactionRepository.findByBookingIdAndWalletTypeAndTransactionTypeAndUserId(
                    bookingId,
                    WalletTypeEnum.MONEY,
                    TransactionTypeEnum.WITHDRAW,
                    renterId);
            if (transactionMoney != null) {
                createTransaction(new TransactionRequest(
                        transactionMoney.getAmount(),
                        String.format(MessageVariable.REFUND_REJECT_BOOKING, bookingCode),
                        renterId,
                        TransactionTypeEnum.DEPOSIT.name(),
                        WalletTypeEnum.MONEY.name(),
                        bookingId));
            }
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Cancel a service of a booking successful!", null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> updateBookingDetail(int bookingDetailId, Integer renterId, UpdateBookingDetailRequest request) {
        try {
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            Booking booking = bookingDetail.getBooking();
            Double priceDetail = servicePriceService
                    .getServicePrice(new GetServicePriceRequest(bookingDetail.getServiceUnit().getServiceUnitId(),
                            request.getStartTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            Double priceHelper = servicePriceService
                    .getServiceHelperPrice(new GetServicePriceRequest(bookingDetail.getServiceUnit().getServiceUnitId(),
                            request.getStartTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            switch (bookingDetail.getBookingDetailStatus()) {
                case WAITING:
                    long difference = Utils.minusLocalDateTime(Utils.getLocalDateTimeNow(),
                            LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart()));
                    if (Utils.isSoonMinutes(difference, getMaxUpdateMinutes())) {
                        throw new BadRequestException("Its too late to update the booking!");
                    }
                    BookingDetailHelper bookingDetailHelper = findBookingDetailHelperByBookingDetailId(
                            bookingDetail.getBookingDetailId());
                    NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
                    notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                    notificationRequestDto.setBody(String.format(MessageVariable.RENTER_CHANGE_BOOKING,
                            booking.getBookingCode()));
                    sendNotificationForUser(notificationRequestDto, bookingDetailHelper
                            .getServiceRegistration()
                            .getHelperInformation().getUser().getUserId());
                case ON_CART:
                case APPROVED:
                case NOT_YET:
                case REJECTED:
                    bookingDetail.setNote(request.getNote());
                    bookingDetail.setWorkDate(request.getStartTime().toLocalDate());
                    bookingDetail.setWorkStart(request.getStartTime().toLocalTime());
                    break;
                default:
                    throw new BadRequestException("Cannot update a this booking!");
            }
            double price;
            price = booking.getTotalPrice() - bookingDetail.getPriceDetail();
            booking.setTotalPrice(price);
            booking.setTotalPriceActual(price);
            bookingRepository.save(booking);

            bookingDetail.setPriceDetail(priceDetail);
            bookingDetail.setPriceHelper(priceHelper);
            bookingDetail.setPriceHelperDefault(priceHelper);
            bookingDetailRepository.save(bookingDetail);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Create Booking Successfully!", null));

        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> validateBookingToStart(Integer userId, Integer detailId, QRCodeValidate request) {
        try {
            BookingDetail bookingDetail = findBookingDetail(detailId);
            isPermissionForHelper(userId, bookingDetail);
            ValidateOTPRequest validateOTPRequest = new ValidateOTPRequest(request.getQrCode(), bookingDetail.getQrCode());
            boolean check = qrCodeService.validateQRCode(validateOTPRequest);
            String onTimeToWork = checkInTimeToWork(LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart()));
            if (!Utils.isNullOrEmpty(onTimeToWork)) {
                throw new BadRequestException(onTimeToWork);
            }
            if (check) {
                bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.IN_PROCESS);
                bookingDetail.setQrCode(null);
                BookingDetailStatusHistory bookingDetailStatusHistory = new BookingDetailStatusHistory();
                bookingDetailStatusHistory.setBookingDetailStatus(BookingDetailStatusEnum.IN_PROCESS);
                bookingDetailStatusHistory.setBookingDetail(bookingDetail);
                bookingDetailStatusHistoryRepository.save(bookingDetailStatusHistory);
                bookingDetailRepository.save(bookingDetail);
                updateBookingIfSameStatusBookingDetail(bookingDetail.getBooking());
                firebaseRealtimeDatabaseService.sendMessage(bookingDetail.getBookingDetailId().toString(),
                        request.getQrCode());
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ResponseObject(HttpStatus.OK.toString(),
                                "Validate booking successful, helper can start to do this service", null));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                "Invalid QR Code, cannot do this service", null));
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> generateQrCode(Integer renterId, Integer detailId) {
        try {
            BookingDetail bookingDetail = findBookingDetailByStatus(detailId, BookingDetailStatusEnum.WAITING);
            isPermission(renterId, bookingDetail);
            String onTimeToWork = checkInTimeToWork(LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart()));

            if (!Utils.isNullOrEmpty(onTimeToWork)) {
                throw new BadRequestException(onTimeToWork);
            }
            String qrCode = qrCodeService.generateCodeValue();
            QRCodeResponse response = new QRCodeResponse();
            response.setValue(qrCode);
            String hashQrCode = qrCodeService.hashQrCode(qrCode);
            bookingDetail.setQrCode(hashQrCode);
            bookingDetailRepository.save(bookingDetail);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Generate qr code successful!", response));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    private List<BookingDetailHelper> checkOverlapBooking(BookingDetail bookingDetail,
                                                          List<BookingDetailHelper> bookingDetailHelpers) {
        List<BookingDetailHelper> overlappingHelpers = new ArrayList<>();
        LocalDateTime start = LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart());
        LocalDateTime end = Utils.plusLocalDateTime(start, bookingDetail.getServiceUnit().getUnit().getUnitValue() + getDelayMinutes() / 60);
        DateTimeRange dateTimeRange = new DateTimeRange(start, end);
        for (BookingDetailHelper helper : bookingDetailHelpers) {
            LocalDateTime helperStart = LocalDateTime.of(helper.getBookingDetail().getWorkDate(), helper.getBookingDetail().getWorkStart());
            LocalDateTime helperEnd = Utils.plusLocalDateTime(helperStart, helper.getBookingDetail().getServiceUnit().getUnit().getUnitValue() + getDelayMinutes() / 60);
            DateTimeRange helperDateTimeRange = new DateTimeRange(helperStart, helperEnd);

            if (Utils.hasOverlapTime(dateTimeRange, helperDateTimeRange)) {
                overlappingHelpers.add(helper);
            }
        }
        return overlappingHelpers;
    }

    @Override
    public ResponseEntity<ResponseObject> getHelpersInformation(Integer renterId, Integer bookingDetailId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            isPermission(renterId, bookingDetail);
            Integer serviceId = bookingDetail.getServiceUnit().getService().getServiceId();
            List<BookingDetailHelper> bookingDetailHelpers = bookingDetailHelperRepository.findByBookingDetailId(bookingDetailId);

            List<Integer> helperIds = bookingDetailHelpers
                    .stream()
                    .map(element -> element.getServiceRegistration().getHelperInformation().getUser().getUserId())
                    .collect(Collectors.toList());

            List<BookingDetailHelper> bookingDetailHelpersAlreadyWork = bookingDetailHelperRepository
                    .findAlreadyWork(helperIds, List.of(BookingDetailStatusEnum.WAITING,
                            BookingDetailStatusEnum.IN_PROCESS), BookingDetailHelperStatusEnum.ACTIVE);
            List<BookingDetailHelper> overLapDetailHelper = checkOverlapBooking(bookingDetail, bookingDetailHelpersAlreadyWork);

            List<BookingDetailHelper> filteredHelpers = bookingDetailHelpers.stream()
                    .filter(helper -> overLapDetailHelper.stream()
                            .noneMatch(overlapHelper -> overlapHelper.getServiceRegistration().getHelperInformation().getHelperInformationId()
                                    .equals(helper.getServiceRegistration().getHelperInformation().getHelperInformationId())))
                    .collect(Collectors.toList());

            List<GetHelpersResponse> dtoList = filteredHelpers
                    .stream()
                    .map(bookingDetailHelper -> {
                                GetHelpersResponse getHelpersResponse = new GetHelpersResponse();
                                User helper = bookingDetailHelper.getServiceRegistration().getHelperInformation().getUser();
                                PointFeedbackOfHelper pointFeedbackOfHelper = feedbackService
                                        .getDetailOfHelperFunction(bookingDetailHelper.getServiceRegistration().getHelperInformation().getUser().getUserId(),
                                                bookingDetail.getServiceUnit().getServiceUnitId());
                                getHelpersResponse.setServiceId(serviceId);
                                getHelpersResponse.setHelperId(helper.getUserId());
                                getHelpersResponse.setHelperName(helper.getFullName());
                                getHelpersResponse.setHelperAvatar(helper.getAvatar());
                                getHelpersResponse.setRate(pointFeedbackOfHelper.getRate());
                                getHelpersResponse.setNumberOfFeedback(pointFeedbackOfHelper.getNumberOfFeedback());
                                getHelpersResponse.setPhoneNumber(helper.getPhoneNumber());
                                return getHelpersResponse;
                            }
                    )
                    .collect(Collectors.toList());
            GetBookingDetailHelperResponse response = modelMapper.map(bookingDetail, GetBookingDetailHelperResponse.class);
            response.setHelpers(dtoList);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Get Helpers For Booking", response));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getBookingsAround(Integer userId) {
        try {
            LocalDateTime currentDate = Utils.getLocalDateTimeNow();
            Address address;
            HelperInformation helperInformation = helperInformationRepository.findByUserId(userId);
            if (helperInformation.getLockDateExpired() != null && helperInformation.getLockDateExpired().isAfter(currentDate.toLocalDate())) {
                ResponseEntity.status(HttpStatus.OK)
                        .body(new ResponseObject(HttpStatus.OK.toString(),
                                "Your account already lock because too many report!", List.of()));
            }
            List<Address> addresses = addressRepository.findByUserIdAnAndIsDefault(userId);
            if (!addresses.isEmpty()) {
                address = addresses.get(0);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                MessageVariable.NEED_ADD_LOCATION, null));
            }
            List<BookingDetail> bookings;

            // Tìm tất cả những booking ko có thằng này nhận cũng như đặt và đã đăng ki
            bookings = bookingDetailRepository.findBookingDetailByStatusAndNoUserIdNoEmployee(BookingDetailStatusEnum.APPROVED, userId,
                    ServiceHelperStatusEnum.ACTIVE);

            // Loại bỏ những booking quá xa
            List<BookingDetail> detailsWithoutTooFar = null;
            if (address != null) {
                detailsWithoutTooFar = googleMapService.checkDistance(bookings,
                        new Position(address.getLongitude(), address.getLatitude()), getMaxDistance());
            }
            //Tìm tất cả những booking mà thằng này đã được nhận đi làm
            List<BookingDetail> bookingDetails = bookingDetailRepository.findByHelperIdAndBookingStatus(userId, BookingDetailStatusEnum.WAITING);
            List<Integer> bookingIds = bookingDetails
                    .stream()
                    .map(BookingDetail::getBookingDetailId).collect(Collectors.toList());

            //Loại bỏ các booking bị chồng chéo thời gian
            List<BookingDetail> notOverlapBookings = null;
            if (detailsWithoutTooFar != null) {
                notOverlapBookings = getBookingDetailNotOverlapTime(userId, detailsWithoutTooFar);
            }

            List<GetBookingResponseForHelper> dtoList = null;
            if (notOverlapBookings != null) {
                dtoList = notOverlapBookings
                        .stream()
                        .map(booking -> {
                                    GetBookingResponseForHelper responseForHelper = modelMapper.map(booking, GetBookingResponseForHelper.class);
                                    responseForHelper.setServiceUnitId(booking.getServiceUnit().getServiceUnitId());
                                    if (booking.getWorkStart() != null) {
                                        responseForHelper.setWorkStart(booking.getWorkStart().format(DateTimeFormatter.ofPattern("HH:mm")));
                                    }
                                    responseForHelper.setValue(booking.getServiceUnit().getUnit().getUnitDetail());
                                    responseForHelper.setEquivalent(booking.getServiceUnit().getUnit().getUnitValue());
                                    responseForHelper.setLatitude(booking.getBooking().getLatitude());
                                    responseForHelper.setLongitude(booking.getBooking().getLongitude());
                                    responseForHelper.setRenterName(booking.getBooking().getRenter().getFullName());
                                    responseForHelper.setServiceName(booking.getServiceUnit().getService().getServiceName());
                                    responseForHelper.setServiceImages(booking.getServiceUnit().getService().getServiceImage());
                                    responseForHelper.setAmount(booking.getPriceHelper());
                                    responseForHelper.setLocationDescription(booking.getBooking().getLocationDescription());
                                    if (bookingIds.contains(booking.getBookingDetailId())) {
                                        responseForHelper.setIsApplied(true);
                                        responseForHelper.setNoteMessage(String.format(MessageVariable.DUPLICATE_BOOKING, booking.getBooking().getBookingCode()));
                                    }
                                    responseForHelper.setDistance(googleMapService.calculateDistance(new Position(booking.getBooking().getLongitude(), booking.getBooking().getLatitude())
                                            , new Position(address.getLongitude(), address.getLatitude())));
                                    return responseForHelper;
                                }
                        )
                        .collect(Collectors.toList());
            }


            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Booking History Response!", dtoList));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> acceptBookingForHelper(CreateBookingHelperRequest request, Integer userId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(request.getBookingDetailId());
            if (!bookingDetail.getBookingDetailStatus().equals(BookingDetailStatusEnum.APPROVED)) {
                throw new BadRequestException(MessageVariable.CANNOT_ACCEPT_BOOKING_NOT_APPROVED);
            }
            Booking booking = bookingDetail.getBooking();
            if (Objects.equals(bookingDetail.getBooking().getRenter().getUserId(), userId)) {
                throw new UserNotHavePermissionException(MessageVariable.HELPER_CANNOT_ACCEPT_THERE_BOOKING);
            }
            ServiceRegistration serviceRegistration = serviceRegistrationRepository
                    .findByServiceIdAndUserId(bookingDetail.getServiceUnit().getService().getServiceId(), userId,
                            ServiceHelperStatusEnum.ACTIVE);
            if (serviceRegistration == null) {
                throw new BadRequestException(MessageVariable.HELPER_NOT_HAVE_PERMISSION_TO_DO_SERVICE);
            }
            List<Address> addressHelper = addressRepository.findByUserIdAnAndIsDefault(userId);
            Address address;
            if (!addressHelper.isEmpty()) address = addressHelper.get(0);
            else throw new BadRequestException(MessageVariable.NEED_ADD_LOCATION);
            Double distance = googleMapService.checkDistance(new Position(booking.getLongitude(), booking.getLatitude()),
                    new Position(address.getLongitude(), address.getLatitude()));
            if (distance > getMaxDistance()) {
                throw new BadRequestException(String.format(MessageVariable.TOO_FAR, booking.getBookingCode()));
            }

            List<BookingDetail> bookingDetails = getBookingDetailNotOverlapTime(userId, List.of(bookingDetail));
            if (bookingDetails.isEmpty()) {
                throw new BadRequestException(MessageVariable.ALREADY_HAVE_BOOKING_AT_THIS_TIME);
            }
            // if the first helper accept booking notification to renter
            if (bookingDetail.getBookingDetailHelpers().size() == 0) {
                NotificationRequestDto notification = new NotificationRequestDto();
                notification.setTarget(booking.getRenter().getDeviceTokens()
                        .stream()
                        .map(DeviceToken::getFcmToken)
                        .collect(Collectors.toList()));
                notification.setTitle(String.format(MessageVariable.TITLE_APP));
                notification.setBody(String.format(MessageVariable.ORDER_HAVE_HELPER,
                        bookingDetail.getServiceUnit().getService().getServiceName(),
                        booking.getBookingCode()));
                if (!notification.getTarget().isEmpty()) {
                    fcmService.sendPnsToTopic(notification);
                }
            }
            BookingDetailHelper bookingDetailHelper = new BookingDetailHelper();
            bookingDetailHelper.setBookingDetail(bookingDetail);
            bookingDetailHelper.setServiceRegistration(serviceRegistration);
            bookingDetailHelper.setBookingDetailHelperStatus(BookingDetailHelperStatusEnum.INACTIVE);
            bookingDetailHelperRepository.save(bookingDetailHelper);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Accept a booking successful", null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getBookingDetails(Integer userId, List<String> statuses, Boolean isHelper,
                                                            Pageable pageable) {
        try {
            Page<BookingDetail> bookingDetails;
//            Sort order = Sort.by(Sort.Order.desc("booking.orderDate"));
//            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);
            List<BookingDetailStatusEnum> bookingDetailStatusEnums = null;
            if (!(statuses == null || statuses.isEmpty())) {
                bookingDetailStatusEnums = statuses
                        .stream()
                        .map(element -> BookingDetailStatusEnum.valueOf(element.toUpperCase()))
                        .collect(Collectors.toList());
            }
            String roleUser = userRepository.findByUserId(userId).getRole().getTitle().toUpperCase();
            if (Utils.isNullOrEmpty(roleUser))
                throw new UserNotHavePermissionException("User do not have permission to do this action");
            RoleEnum roleEnum = RoleEnum.valueOf(roleUser);
            switch (roleEnum) {
                case EMPLOYEE:
                    if (isHelper) {
                        bookingDetails = !(statuses == null || statuses.isEmpty())
                                ? bookingDetailRepository.findByHelperId(userId, bookingDetailStatusEnums, BookingDetailStatusEnum.ON_CART,
                                List.of(BookingDetailHelperStatusEnum.ACTIVE), pageable)
                                : bookingDetailRepository.findByHelperId(userId, BookingDetailStatusEnum.ON_CART, pageable);
                    } else {
                        bookingDetails = !(statuses == null || statuses.isEmpty())
                                ? bookingDetailRepository.findByRenterId(userId, bookingDetailStatusEnums, BookingDetailStatusEnum.ON_CART, pageable)
                                : bookingDetailRepository.findByRenterId(userId, BookingDetailStatusEnum.ON_CART, pageable);
                    }

                    break;
                case RENTER:
                    bookingDetails = !(statuses == null || statuses.isEmpty())
                            ? bookingDetailRepository.findByRenterId(userId, bookingDetailStatusEnums, BookingDetailStatusEnum.ON_CART, pageable)
                            : bookingDetailRepository.findByRenterId(userId, BookingDetailStatusEnum.ON_CART, pageable);

                    break;
                case MANAGER:
                case ADMIN:
                    bookingDetails = !(statuses == null || statuses.isEmpty())
                            ? bookingDetailRepository.findAllByBookingStatus(bookingDetailStatusEnums, BookingDetailStatusEnum.ON_CART, pageable)
                            : bookingDetailRepository.findAllBooking(BookingDetailStatusEnum.ON_CART, pageable);

                    break;
                default:
                    throw new UserNotHavePermissionException("User do not have permission to do this action");
            }
            PageResponseObject pageResponseObject = getResponseObjectResponseEntity(bookingDetails, isHelper);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Booking History Response!", pageResponseObject));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getBookingDetail(Integer renterId, Integer bookingDetailId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            isPermission(renterId, bookingDetail);
            GetBookingDetailDetailResponse response = modelMapper.map(bookingDetail, GetBookingDetailDetailResponse.class);
            if (bookingDetail.getBooking().getBookingCode() != null)
                response.setBookingCode(bookingDetail.getBooking().getBookingCode());
            if (bookingDetail.getBooking().getRejectionReason() != null && bookingDetail.getBooking().getRjReasonDescription() != null) {
                response.setRejectionReasonContent(bookingDetail.getBooking().getRejectionReason().getRejectionContent());
                response.setRejectionReasonDescription(bookingDetail.getBooking().getRjReasonDescription());
            }
            response.setReported(false);
            if (Objects.nonNull(bookingDetail.getReport())) {
                response.setReported(true);
                response.setRefundMoney(bookingDetail.getReport().getRefundMoney());
                response.setRefundPoint(bookingDetail.getReport().getRefundPoint());
                response.setPenaltyMoney(bookingDetail.getReport().getPenaltyMoney());
            }
            response.setServiceId(bookingDetail.getServiceUnit().getService().getServiceId());
            response.setServiceUnitId(bookingDetail.getServiceUnit().getServiceUnitId());
            response.setServiceIcon(bookingDetail.getServiceUnit().getService().getServiceImage());
            response.setServiceName(bookingDetail.getServiceUnit().getService().getServiceName());
            response.setValue(bookingDetail.getServiceUnit().getUnit().getUnitDetail());
            response.setEquivalent(bookingDetail.getServiceUnit().getUnit().getUnitValue());
            response.setPrice(bookingDetail.getPriceDetail());
            response.setCurrentStatus(bookingDetail.getBookingDetailStatus().name());
            GetAddressResponseBooking addressResponseBooking = modelMapper.map(bookingDetail.getBooking(), GetAddressResponseBooking.class);
            GetHelpersResponse getHelpersResponse = null;
            BookingDetailHelper bookingDetailHelper = null;
            List<BookingDetailHelper> bookingDetailHelpers = bookingDetailHelperRepository.findByBookingDetailIdAndActive(bookingDetailId, BookingDetailHelperStatusEnum.ACTIVE);
            if (!bookingDetailHelpers.isEmpty()) {
                bookingDetailHelper = bookingDetailHelpers.get(0);
            }
            if (bookingDetailHelper != null) {
                getHelpersResponse = new GetHelpersResponse();
                User helper = bookingDetailHelper.getServiceRegistration().getHelperInformation().getUser();
                PointFeedbackOfHelper pointFeedbackOfHelper = feedbackService
                        .getDetailOfHelperFunction(bookingDetailHelper.getServiceRegistration().getHelperInformation().getUser().getUserId(),
                                bookingDetail.getServiceUnit().getServiceUnitId());
                getHelpersResponse.setServiceId(bookingDetail.getServiceUnit().getService().getServiceId());
                getHelpersResponse.setHelperId(helper.getUserId());
                getHelpersResponse.setHelperName(helper.getFullName());
                getHelpersResponse.setHelperAvatar(helper.getAvatar());
                getHelpersResponse.setRate(pointFeedbackOfHelper.getRate());
                getHelpersResponse.setNumberOfFeedback(pointFeedbackOfHelper.getNumberOfFeedback());
                getHelpersResponse.setPhoneNumber(helper.getPhoneNumber());
            }

            GetTransactionBookingResponse transactionBookingResponse = new GetTransactionBookingResponse();
            Transaction transactionMoney = transactionRepository
                    .findByBookingIdAndWalletTypeAndTransactionTypeAndUserId(bookingDetail.getBooking().getBookingId(),
                            WalletTypeEnum.MONEY, TransactionTypeEnum.WITHDRAW, renterId);
            List<PriceService> priceServices;
            if (transactionMoney != null) {
                priceServices = bookingDetail.getBooking().getBookingDetails()
                        .stream()
                        .map(element -> {
                            PriceService priceService = new PriceService();
                            priceService.setServiceName(element.getServiceUnit().getService().getServiceName());
                            priceService.setPrice(element.getPriceDetail());
                            return priceService;
                        })
                        .collect(Collectors.toList());
                transactionBookingResponse.setStatus(TransactionBookingStatusEnum.PAID.name());
                transactionBookingResponse.setTransactionCode(transactionMoney.getTransactionCode());
            } else {
                priceServices = bookingDetail.getBooking().getBookingDetails()
                        .stream()
                        .map(element -> {
                            PriceService priceService = new PriceService();
                            priceService.setServiceName(element.getServiceUnit().getService().getServiceName());
                            priceService.setPrice(element.getPriceDetail());
                            return priceService;
                        })
                        .collect(Collectors.toList());
                transactionBookingResponse.setStatus(TransactionBookingStatusEnum.UNPAID.name());
            }
            transactionBookingResponse.setTotalPrice(bookingDetail.getBooking().getTotalPrice());
            transactionBookingResponse.setTotalPriceActual(bookingDetail.getBooking().getTotalPriceActual());
            transactionBookingResponse.setDiscount(bookingDetail.getBooking().getTotalPrice() - bookingDetail.getBooking().getTotalPriceActual());
            transactionBookingResponse.setServicePrice(priceServices);
            GetFeedbackResponse feedback = null;
            if (bookingDetail.getFeedback() != null && !bookingDetail.getFeedback().isEmpty()) {
                feedback = modelMapper.map(bookingDetail, GetFeedbackResponse.class);
            }

            response.setAddress(addressResponseBooking);
            response.setHelper(getHelpersResponse);
            response.setTransaction(transactionBookingResponse);
            response.setFeedback(feedback);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Booking Detail Detail!", response));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> chooseHelperForBooking(Integer renterId, Integer bookingDetailId, HelperChoiceRequest request) {
        try {
            User helper = findAccount(request.getHelperId());
            if (!RoleEnum.EMPLOYEE.name().equalsIgnoreCase(helper.getRole().getTitle())) {
                throw new BadRequestException(MessageVariable.CANNOT_CHOOSE_RENTER);
            }
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            isPermission(renterId, bookingDetail);
            Optional<BookingDetailHelper> bookingDetailHelper =
                    bookingDetailHelperRepository.findByBookingDetailIdAndHelperId(bookingDetailId, request.getHelperId());
            if (bookingDetailHelper.isEmpty()) throw new BadRequestException(MessageVariable.NOT_FOUND_HELPER);
            List<BookingDetailHelper> bookingDetailHelpers = bookingDetailHelperRepository.findAlreadyWork(
                    List.of(request.getHelperId()), List.of(BookingDetailStatusEnum.WAITING, BookingDetailStatusEnum.IN_PROCESS),
                    BookingDetailHelperStatusEnum.ACTIVE);
            List<BookingDetailHelper> overLapBookings = checkOverlapBooking(bookingDetail, bookingDetailHelpers);
            if (overLapBookings.isEmpty()) {
                BookingDetailHelper bookingDetailHelperUpdate = bookingDetailHelper.get();
                bookingDetailHelperUpdate.setBookingDetailHelperStatus(BookingDetailHelperStatusEnum.ACTIVE);
                bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.WAITING);
                NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
                notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
                notificationRequestDto.setBody(String.format(MessageVariable.RENTER_CHOOSE_YOU,
                        bookingDetail.getBooking().getBookingCode()));
                sendNotificationForUser(notificationRequestDto,
                        bookingDetailHelperUpdate.getServiceRegistration().getHelperInformation().getUser().getUserId());
                Notification notification = new Notification();
                notification.setIsHelper(true);
                notification.setTitle(notificationRequestDto.getTitle());
                notification.setContent(notificationRequestDto.getBody());
                notification.setUser(bookingDetailHelperUpdate.getServiceRegistration()
                        .getHelperInformation().getUser());
                BookingDetailStatusHistory bookingDetailStatusHistory = new BookingDetailStatusHistory();
                bookingDetailStatusHistory.setBookingDetail(bookingDetail);
                bookingDetailStatusHistory.setBookingDetailStatus(BookingDetailStatusEnum.WAITING);
                notificationRepository.save(notification);
                bookingDetailStatusHistoryRepository.save(bookingDetailStatusHistory);
                bookingDetailHelperRepository.save(bookingDetailHelperUpdate);
                bookingDetailRepository.save(bookingDetail);
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ResponseObject(HttpStatus.OK.toString(),
                                "Choose Helper Successful!", null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            MessageVariable.HELPER_ALREADY_HAVE_BOOKING, null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> resendBookingDetail(Integer renterId, ResendBookingDetailRequest request, Integer bookingDetailId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            Booking booking = modelMapper.map(bookingDetail.getBooking(), Booking.class);
            BookingDetailStatusHistory bookingDetailStatusHistory = new BookingDetailStatusHistory();
            Double priceDetail = servicePriceService
                    .getServicePrice(new GetServicePriceRequest(bookingDetail.getServiceUnit().getServiceUnitId(),
                            request.getStartTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            Double priceHelper = servicePriceService
                    .getServiceHelperPrice(new GetServicePriceRequest(bookingDetail.getServiceUnit().getServiceUnitId(),
                            request.getStartTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            booking.setRenter(findAccount(renterId));
            booking.setTotalPrice(0.0);
            booking.setBookingStatus(BookingStatusEnum.NOT_YET);
            bookingDetailStatusHistory.setBookingDetailStatus(BookingDetailStatusEnum.NOT_YET);

            List<Address> addresses = addressRepository.findByUserIdAnAndIsDefault(renterId);
            Address addressDefault = null;
            if (!addresses.isEmpty()) {
                addressDefault = addresses.get(0);
            }
            if (addressDefault != null && ObjectUtils.anyNull(booking.getLongitude(),
                    booking.getLatitude(),
                    booking.getLocation(),
                    booking.getLocationDescription())) {
                booking.setLongitude(addressDefault.getLongitude());
                booking.setLatitude(addressDefault.getLatitude());
                booking.setLocation(addressDefault.getLocationName());
                booking.setLocationDescription(addressDefault.getDescription());
            }
            double price = booking.getTotalPrice();
            BookingDetail bookingDetailCreate = modelMapper.map(bookingDetail, BookingDetail.class);
            ServiceUnit requestServiceUnit = findServiceUnitById(bookingDetail.getServiceUnit().getServiceUnitId());
            bookingDetailCreate.setServiceUnit(requestServiceUnit);
            bookingDetailCreate.setNote(request.getNote());
            price = price + priceDetail;
            booking.setTotalPrice(price);
            booking.setTotalPriceActual(price);
            bookingRepository.save(booking);

            bookingDetail.setBooking(booking);
            bookingDetail.setPriceDetail(priceDetail);
            bookingDetail.setPriceHelper(priceHelper);
            bookingDetail.setPriceHelperDefault(priceHelper);
            bookingDetail.setWorkStart(request.getStartTime().toLocalTime());
            bookingDetail.setWorkDate(request.getStartTime().toLocalDate());
            bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.NOT_YET);
            bookingDetail.setNote(request.getNote());
            bookingDetailRepository.save(bookingDetail);
            bookingDetailStatusHistory.setBookingDetail(bookingDetail);
            bookingDetailStatusHistoryRepository.save(bookingDetailStatusHistory);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Create Booking Successfully!", null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> checkoutBookingDetail(Integer id, Integer helperId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(id);
            if (bookingDetail.getBookingDetailStatus() != BookingDetailStatusEnum.IN_PROCESS) {
                throw new BadRequestException(MessageVariable.CANNOT_CHECK_OUT);
            }
            isPermissionForHelper(helperId, bookingDetail);
            LocalDateTime startDateTime = LocalDateTime.of(bookingDetail.getWorkDate(), bookingDetail.getWorkStart());
            String checkValue = checkInTimeToEndWork(startDateTime);
            if (!Utils.isNullOrEmpty(checkValue)) {
                throw new BadRequestException(checkValue);
            }
            bookingDetail.setBookingDetailStatus(BookingDetailStatusEnum.FINISHED);
            bookingDetail.setWorkEnd(Utils.getLocalDateTimeNow().toLocalTime());
            BookingDetailStatusHistory bookingDetailStatusHistory = new BookingDetailStatusHistory();
            bookingDetailStatusHistory.setBookingDetailStatus(BookingDetailStatusEnum.FINISHED);
            bookingDetailStatusHistory.setBookingDetail(bookingDetail);
            double point = bookingDetail.getPriceDetail() / getMoneyToPoint();
            createTransaction(new TransactionRequest(
                    point,
                    String.format(MessageVariable.GET_POINT),
                    bookingDetail.getBooking().getRenter().getUserId(),
                    TransactionTypeEnum.DEPOSIT.name(),
                    WalletTypeEnum.POINT.name(),
                    bookingDetail.getBooking().getBookingId()
            ));
            NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
            notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
            notificationRequestDto.setBody(String.format(MessageVariable.COMPLETE_BOOKING,
                    bookingDetail.getServiceUnit().getService().getServiceName(),
                    bookingDetail.getBooking().getBookingCode()));
            firebaseRealtimeDatabaseService.sendNotification(bookingDetail.getBooking().getRenter().getPhoneNumber(),
                    bookingDetail.getBookingDetailId().toString(),
                    String.format(String.format(MessageVariable.COMPLETE_BOOKING,
                            bookingDetail.getServiceUnit().getService().getServiceName(),
                            bookingDetail.getBooking().getBookingCode())));
            sendMessage(notificationRequestDto, bookingDetail.getBooking().getRenter());
            bookingDetailStatusHistoryRepository.save(bookingDetailStatusHistory);
            bookingDetailRepository.save(bookingDetail);
            updateBookingIfSameStatusBookingDetail(bookingDetail.getBooking());
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Checkout booking successful!", null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getBookingDetailByHelper(Integer helperId, Integer bookingDetailId) {
        try {
            BookingDetail bookingDetail = findBookingDetail(bookingDetailId);
            isPermissionForHelper(helperId, bookingDetail);
            GetBookingDetailDetailForHelperResponse response = modelMapper.map(bookingDetail, GetBookingDetailDetailForHelperResponse.class);
            if (bookingDetail.getBooking().getBookingCode() != null)
                response.setBookingCode(bookingDetail.getBooking().getBookingCode());
            if (bookingDetail.getBooking().getRejectionReason() != null && bookingDetail.getBooking().getRjReasonDescription() != null) {
                response.setRejectionReasonContent(bookingDetail.getBooking().getRejectionReason().getRejectionContent());
                response.setRejectionReasonDescription(bookingDetail.getBooking().getRjReasonDescription());
            }
            response.setWorkStart(bookingDetail.getWorkStart().format(DateTimeFormatter.ofPattern("HH:mm")));
            response.setServiceId(bookingDetail.getServiceUnit().getService().getServiceId());
            response.setServiceUnitId(bookingDetail.getServiceUnit().getServiceUnitId());
            response.setServiceIcon(bookingDetail.getServiceUnit().getService().getServiceImage());
            response.setServiceName(bookingDetail.getServiceUnit().getService().getServiceName());
            response.setValue(bookingDetail.getServiceUnit().getUnit().getUnitDetail());
            response.setEquivalent(bookingDetail.getServiceUnit().getUnit().getUnitValue());
            response.setNote(bookingDetail.getNote());
            response.setIsReported(bookingDetail.getReport() != null);
            if (Objects.nonNull(bookingDetail.getReport())) {
                response.setRefundMoney(bookingDetail.getReport().getRefundMoney());
                response.setRefundPoint(bookingDetail.getReport().getRefundPoint());
                response.setPenaltyMoney(bookingDetail.getReport().getPenaltyMoney());
            }
            response.setPrice(bookingDetail.getPriceHelperDefault());
            response.setCurrentStatus(bookingDetail.getBookingDetailStatus().name());
            GetAddressResponseBooking addressResponseBooking = modelMapper.map(bookingDetail.getBooking(), GetAddressResponseBooking.class);
            GetRenterResponse getRenterResponse = null;
            User renter = bookingDetail.getBooking().getRenter();
            if (renter != null) {
                getRenterResponse = new GetRenterResponse();
                getRenterResponse.setRenterId(renter.getUserId());
                getRenterResponse.setRenterName(renter.getFullName());
                getRenterResponse.setRenterAvatar(renter.getAvatar());
                getRenterResponse.setPhoneNumber(renter.getPhoneNumber());
            }
            GetFeedbackResponse feedback = null;
            if (bookingDetail.getFeedback() != null && !bookingDetail.getFeedback().isEmpty()) {
                feedback = modelMapper.map(bookingDetail, GetFeedbackResponse.class);
            }

            response.setAddress(addressResponseBooking);
            response.setRenter(getRenterResponse);
            response.setFeedback(feedback);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Booking Detail Detail!", response));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getCurrentBookingDetail(Integer helperId) {
        try {
            Address address = null;
            List<BookingDetail> bookingDetails = bookingDetailRepository.findCurrentByHelperId(helperId, BookingDetailStatusEnum.IN_PROCESS, BookingDetailHelperStatusEnum.ACTIVE);
            BookingDetail bookingDetail = null;
            GetBookingResponseForHelper response = new GetBookingResponseForHelper();
            if (bookingDetails != null && !bookingDetails.isEmpty()) {
                bookingDetail = bookingDetails.get(0);
                List<Address> addresses = addressRepository.findByUserIdAnAndIsDefault(bookingDetail.getBooking().getRenter().getUserId());
                if (!addresses.isEmpty()) {
                    address = addresses.get(0);
                } else {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                    MessageVariable.NEED_ADD_LOCATION, null));
                }
                response.setNote(bookingDetail.getNote());
                response.setBookingDetailId(bookingDetail.getBookingDetailId());
                response.setServiceUnitId(bookingDetail.getServiceUnit().getServiceUnitId());
                response.setEquivalent(bookingDetail.getServiceUnit().getUnit().getUnitValue());
                response.setValue(bookingDetail.getServiceUnit().getUnit().getUnitDetail());
                response.setRenterName(bookingDetail.getBooking().getRenter().getFullName());
                response.setServiceName(bookingDetail.getServiceUnit().getService().getServiceName());
                response.setServiceImages(bookingDetail.getServiceUnit().getService().getServiceImage());
                response.setWorkDate(Utils.getLocalDateAsString(bookingDetail.getWorkDate()));
                response.setWorkStart(Utils.getLocalTimeAsString(bookingDetail.getWorkStart()));
                response.setAmount(bookingDetail.getPriceHelper());
                response.setLocationDescription(bookingDetail.getBooking().getLocationDescription());
                response.setLongitude(bookingDetail.getBooking().getLongitude());
                response.setLatitude(bookingDetail.getBooking().getLatitude());
                response.setIsApplied(true);
                response.setDistance(googleMapService.calculateDistance(new Position(bookingDetail.getBooking().getLongitude(), bookingDetail.getBooking().getLatitude())
                        , new Position(address.getLongitude(), address.getLatitude())));
            }

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Booking Detail Detail!", response));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(), null));
            }
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(), null));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                            "Something wrong occur!", null));
        }
    }

    private void updateBookingIfSameStatusBookingDetail(Booking booking) {
        try {
            List<BookingDetail> bookingDetails = booking.getBookingDetails();
            List<BookingDetailStatusEnum> bookingDetailStatusEnums =
                    bookingDetails
                            .stream()
                            .map(BookingDetail::getBookingDetailStatus)
                            .collect(Collectors.toList());
            List<BookingDetailStatusEnum> distinct = bookingDetailStatusEnums.stream().distinct().collect(Collectors.toList());
            if (distinct.size() == 1) {
                switch (distinct.get(0)) {
                    case ON_CART:
                        booking.setBookingStatus(BookingStatusEnum.ON_CART);
                        break;
                    case NOT_YET:
                        booking.setBookingStatus(BookingStatusEnum.NOT_YET);
                        break;
                    case REJECTED:
                        booking.setBookingStatus(BookingStatusEnum.REJECTED);
                        break;
                    case APPROVED:
                    case WAITING:
                    case IN_PROCESS:
                        booking.setBookingStatus(BookingStatusEnum.APPROVED);
                        break;
                    case FINISHED:
                        booking.setBookingStatus(BookingStatusEnum.FINISHED);
                        break;
                    case CANCEL_BY_RENTER:
                    case CANCEL_BY_HELPER:
                    case CANCEL_BY_SYSTEM:
                        booking.setBookingStatus(BookingStatusEnum.CANCELED);
                        break;
                }
            } else {
                if (distinct.contains(BookingDetailStatusEnum.FINISHED) &&
                        distinct.stream().allMatch(status ->
                                status == BookingDetailStatusEnum.CANCEL_BY_RENTER ||
                                        status == BookingDetailStatusEnum.CANCEL_BY_HELPER ||
                                        status == BookingDetailStatusEnum.CANCEL_BY_SYSTEM
                        )) {
                    booking.setBookingStatus(BookingStatusEnum.FINISHED);
                } else if (distinct.contains(BookingDetailStatusEnum.NOT_YET) &&
                        distinct.stream().allMatch(status ->
                                status == BookingDetailStatusEnum.CANCEL_BY_RENTER ||
                                        status == BookingDetailStatusEnum.CANCEL_BY_HELPER ||
                                        status == BookingDetailStatusEnum.CANCEL_BY_SYSTEM
                        )) {
                    booking.setBookingStatus(BookingStatusEnum.NOT_YET);
                } else if (distinct.contains(BookingDetailStatusEnum.REJECTED)) {
                    booking.setBookingStatus(BookingStatusEnum.REJECTED);
                } else if (distinct.contains(BookingDetailStatusEnum.NOT_YET)) {
                    booking.setBookingStatus(BookingStatusEnum.NOT_YET);
                } else if (distinct.contains(BookingDetailStatusEnum.ON_CART)) {
                    booking.setBookingStatus(BookingStatusEnum.ON_CART);
                } else if (distinct.stream().allMatch(status ->
                        status == BookingDetailStatusEnum.CANCEL_BY_RENTER ||
                                status == BookingDetailStatusEnum.CANCEL_BY_HELPER ||
                                status == BookingDetailStatusEnum.CANCEL_BY_SYSTEM
                )) {
                    booking.setBookingStatus(BookingStatusEnum.CANCELED);
                }
                booking.setBookingStatus(BookingStatusEnum.APPROVED);
            }
            bookingRepository.save(booking);
        } catch (Exception e) {
            log.error(e.toString());
        }
    }

    private ServiceUnit findServiceUnitById(Integer id) {
        return serviceUnitRepository.findById(id).orElseThrow(()
                -> new NotFoundException(String.format("Service Unit %s ID is not exist!", id)));
    }

    private void sendNotificationForUser(NotificationRequestDto request, Integer userId) {
        List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserId(userId);
        request.setTarget(deviceTokens
                .stream()
                .map(DeviceToken::getFcmToken)
                .collect(Collectors.toList()));
        if (!request.getTarget().isEmpty()) {
            fcmService.sendPnsToTopic(request);
        }
    }

    private void isPermissionForHelper(Integer userId, BookingDetail bookingDetail) throws UserNotHavePermissionException {
        List<BookingDetailHelper> helpers = bookingDetailHelperRepository.findByBookingDetailIdAndActive(bookingDetail.getBookingDetailId(),
                BookingDetailHelperStatusEnum.ACTIVE);
        if (!helpers.isEmpty()) {
            User helper = helpers.get(0).getServiceRegistration().getHelperInformation().getUser();
            if (!Objects.equals(helper.getUserId(), userId))
                throw new UserNotHavePermissionException("User do not have permission to do this action");
        }
    }

    private void sendNotificationForUser(NotificationRequestDto request, List<Integer> userIds) {
        for (Integer userId :
                userIds) {
            List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserId(userId);
            request.setTarget(deviceTokens
                    .stream()
                    .map(DeviceToken::getFcmToken)
                    .collect(Collectors.toList()));
            if (!request.getTarget().isEmpty()) {
                fcmService.sendPnsToTopic(request);
            }
        }
    }

    private List<BookingDetail> getBookingDetailNotOverlapTime(Integer helperId, List<BookingDetail> bookingDetails) {
        List<BookingDetailHelper> bookingDetailHelpers = bookingDetailHelperRepository.findByHelperIdAndIsActive(helperId,
                BookingDetailHelperStatusEnum.ACTIVE, List.of(BookingDetailStatusEnum.WAITING, BookingDetailStatusEnum.IN_PROCESS));
        List<BookingDetail> workBookingCurrents = bookingDetailHelpers
                .stream()
                .map(BookingDetailHelper::getBookingDetail)
                .collect(Collectors.toList());
        return bookingDetails.stream()
                .filter(detail -> workBookingCurrents.stream()
                        .noneMatch(workDetail -> checkInTime(workDetail, detail)))
                .collect(Collectors.toList());
    }

    private boolean checkInTime(BookingDetail newBooking, BookingDetail oldBooking) {
        LocalDateTime newBookingStartTime = LocalDateTime.of(newBooking.getWorkDate(), newBooking.getWorkStart());
        LocalDateTime newBookingEndTime = Utils.plusLocalDateTime(newBookingStartTime, newBooking.getServiceUnit().getUnit().getUnitValue() + (getDelayMinutes() / 60));
        if (Objects.equals(newBooking.getBooking().getBookingId(), oldBooking.getBooking().getBookingId())) {
            newBookingEndTime = Utils.plusLocalDateTime(newBookingStartTime, newBooking.getServiceUnit().getUnit().getUnitValue());
        }
        LocalDateTime oldBookingStartTime = LocalDateTime.of(oldBooking.getWorkDate(), oldBooking.getWorkStart());
        LocalDateTime oldBookingEndTime = Utils.plusLocalDateTime(oldBookingStartTime, oldBooking.getServiceUnit().getUnit().getUnitValue());
        return Utils.hasOverlapTime(new DateTimeRange(newBookingStartTime, newBookingEndTime),
                new DateTimeRange(oldBookingStartTime, oldBookingEndTime));
    }

    private PageResponseObject getResponseObjectResponseEntity(Page<BookingDetail> bookingDetails, Boolean ishleper) {
        List<GetBookingDetailResponse> dtoList = bookingDetails
                .stream()
                .map(detail -> {
                            GetBookingDetailResponse response = modelMapper.map(detail, GetBookingDetailResponse.class);
                            response.setStatus(detail.getBookingDetailStatus().name());
                            response.setReported(false);
                            if (Objects.nonNull(detail.getReport())) {
                                response.setReported(true);
                                response.setRefundMoney(detail.getReport().getRefundMoney());
                                response.setRefundPoint(detail.getReport().getRefundPoint());
                                response.setPenaltyMoney(detail.getReport().getPenaltyMoney());
                            }
                            if (ishleper) {
                                response.setPrice(detail.getPriceHelperDefault());
                            } else {
                                response.setPrice(detail.getPriceDetail());
                            }
                            response.setRenterName(detail.getBooking().getRenter().getFullName());
                            response.setLongitude(detail.getBooking().getLongitude());
                            response.setLatitude(detail.getBooking().getLatitude());
                            return response;
                        }
                )
                .collect(Collectors.toList());
        return Utils.convertToPageResponse(bookingDetails, dtoList);
    }

    private String checkInTime(LocalDateTime startDateTime, String serviceName) {
        LocalDateTime current = Utils.getLocalDateTimeNow();
        long difference = Utils.minusLocalDateTime(startDateTime,
                current);
        if (difference >= 0 && Utils.isLateMinutes(difference, getMaxUpdateMinutes())) {
            return String.format(MessageVariable.TOO_LATE_TO_UPDATE, serviceName);
        }
        return null;
    }

    private String checkInTimeToWork(LocalDateTime startDateTime) {
        LocalDateTime current = Utils.getLocalDateTimeNow();
        long difference = Utils.minusLocalDateTime(startDateTime,
                current);
        if (Math.abs(difference) >= 0 && Utils.isLateMinutes(difference, getMaxUpdateMinutes())) {
            return MessageVariable.NOT_ON_TIME_TO_START;
        }
        return null;
    }

    private String checkInTimeToEndWork(LocalDateTime startDateTime) {
        LocalDateTime current = Utils.getLocalDateTimeNow();
        long difference = Utils.minusLocalDateTime(startDateTime,
                current);
        if (Math.abs(difference) >= 0 && Utils.isLateMinutes(difference, getDifferEndMinutes())) {
            return MessageVariable.NOT_ON_TIME_TO_END_WORK;
        }
        return null;
    }

    private boolean isPermission(Integer userId, BookingDetail booking) throws UserNotHavePermissionException {
        if (!Objects.equals(booking.getBooking().getRenter().getUserId(), userId))
            throw new UserNotHavePermissionException("User do not have permission to do this action");
        return true;
    }

    private BookingDetailHelper findBookingDetailHelperByBookingDetailId(Integer bookingDetailId) throws BadRequestException {
        List<BookingDetailHelper> data = bookingDetailHelperRepository.findByBookingDetailIdAndActive(bookingDetailId, BookingDetailHelperStatusEnum.ACTIVE);
        if (data.isEmpty()) {
            throw new NotFoundException("The booking not have helper yet!");
        }
        if (data.size() > 1) {
            throw new BadRequestException("The booking have more than 1 helper!");
        }
        return data.get(0);
    }

    private BookingDetailHelper findByHelperId(int id, int bookingDetailId) throws UserNotHavePermissionException {
        return bookingDetailHelperRepository.findByHelperId(id, bookingDetailId).orElseThrow(() ->
                new UserNotHavePermissionException("Helper do not to have permission to action this booking!"));
    }

    private BookingDetail findById(int id) {
        return bookingDetailRepository.findById(id).orElseThrow(() ->
                new NotFoundException("Booking detail is not exist!"));
    }

    private BookingDetail findBookingDetailByStatus(Integer detailId, BookingDetailStatusEnum statusEnum) {
        return bookingDetailRepository.findByBookingDetailIdAndBookingStatus(detailId, statusEnum)
                .orElseThrow(() -> new NotFoundException("Booking Detail with status waiting is not found"));
    }

    private long getMaxUpdateMinutes() {
        SystemParameter systemParameter = systemParameterRepository.findSystemParameterByParameterField(SystemParameterField.MAX_UPDATE_MINUTES);
        try {
            return Long.parseLong(systemParameter.getParameterValue());
        } catch (Exception e) {
            return maxUpdateMinutes;
        }
    }

    private long getMoneyToPoint() {
        SystemParameter systemParameter = systemParameterRepository.findSystemParameterByParameterField(SystemParameterField.MONEY_TO_POINT);
        try {
            return Long.parseLong(systemParameter.getParameterValue());
        } catch (Exception e) {
            return moneyToPoint;
        }
    }

    private void sendMessage(NotificationRequestDto notificationRequestDto, User user) {
        List<DeviceToken> deviceTokens = deviceTokenRepository.findByUserId(user.getUserId());
        if (!deviceTokens.isEmpty()) {
            notificationRequestDto.setTarget(convertToListFcmToken(deviceTokens));
            fcmService.sendPnsToTopic(notificationRequestDto);
        }
    }

    private List<String> convertToListFcmToken(List<DeviceToken> deviceTokens) {
        return deviceTokens.stream()
                .map(DeviceToken::getFcmToken)
                .collect(Collectors.toList());
    }

    public boolean createTransaction(TransactionRequest request) {
        User user = findAccount(request.getUserId());
        Booking booking = findBookingById(request.getBookingId());
        if (request.getBookingId() != null) {
            request.setNote(String.format(request.getNote(), booking.getBookingCode()));
        }
        NotificationRequestDto notificationRequestDto = new NotificationRequestDto();
        notificationRequestDto.setTitle(MessageVariable.TITLE_APP);
        Wallet wallet = walletRepository.getWalletByUserIdAndType(request.getUserId(),
                WalletTypeEnum.valueOf(request.getWalletType().toUpperCase()));
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUser(user);
            wallet.setBalance(0D);
            wallet.setWalletTypeEnum(WalletTypeEnum.valueOf(request.getWalletType().toUpperCase()));
        }

        wallet.setUpdateAt(Utils.getLocalDateTimeNow());
        TransactionTypeEnum transactionTypeEnum = TransactionTypeEnum.valueOf(request.getTransactionType().toUpperCase());
        switch (transactionTypeEnum) {
            case DEPOSIT:
                wallet.setBalance(wallet.getBalance() + request.getBalance());
                notificationRequestDto.setBody(request.getNote());
                sendMessage(notificationRequestDto, user);
                break;
            case TRANSFER:
                break;
            case WITHDRAW:
                if (wallet.getBalance() < request.getBalance()) {
                    return false;
                }
                wallet.setBalance(wallet.getBalance() - request.getBalance());
                notificationRequestDto.setBody(request.getNote());
                sendMessage(notificationRequestDto, user);
                break;
        }
        Wallet walletUpdate = walletRepository.save(wallet);
        Transaction transaction = mappingForCreate(request);
        transaction.setWallet(walletUpdate);
        transactionRepository.save(transaction);
        return true;
    }

    private Transaction mappingForCreate(TransactionRequest request) {
        Transaction transaction = modelMapper.map(request, Transaction.class);
        Booking booking = findBookingById(request.getBookingId());
        transaction.setAmount(request.getBalance());
        transaction.setBooking(booking);
        transaction.setTransactionCode(Utils.generateRandomCode());
        transaction.setCreateAt(Utils.getLocalDateTimeNow());
        transaction.setTransactionStatusEnum(TransactionStatusEnum.SUCCESS);
        transaction.setTransactionTypeEnum(TransactionTypeEnum.valueOf(request.getTransactionType().toUpperCase()));
        return transaction;
    }

    private long getDifferEndMinutes() {
        SystemParameter systemParameter = systemParameterRepository.findSystemParameterByParameterField(SystemParameterField.MAX_END_TIME_MINUTES);
        try {
            return Long.parseLong(systemParameter.getParameterValue());
        } catch (Exception e) {
            return maxEndTimeMinutes;
        }
    }

    private BookingDetail findBookingDetail(int id) {
        return bookingDetailRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Booking Detail ID %s is not exist!", id)));
    }

    private Booking findBookingById(int id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Booking ID %s is not exist!", id)));
    }

    private User findAccount(int userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User is not exist"));
    }

    private Double getDelayMinutes() {
        SystemParameter systemParameter = systemParameterRepository.findSystemParameterByParameterField(SystemParameterField.DELAY_MINUTES);
        try {
            return Double.parseDouble(systemParameter.getParameterValue());
        } catch (Exception e) {
            return delayMinutes;
        }
    }

    private Double getMaxDistance() {
        SystemParameter systemParameter = systemParameterRepository.findSystemParameterByParameterField(SystemParameterField.MAX_DISTANCE);
        try {
            return Double.parseDouble(systemParameter.getParameterValue());
        } catch (Exception e) {
            return maxDistance;
        }
    }
}

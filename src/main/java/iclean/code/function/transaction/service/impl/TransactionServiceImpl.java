package iclean.code.function.transaction.service.impl;

import iclean.code.data.domain.Booking;
import iclean.code.data.domain.User;
import iclean.code.data.domain.Transaction;
import iclean.code.data.domain.Wallet;
import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.transaction.TransactionRequest;
import iclean.code.data.dto.response.PageResponseObject;
import iclean.code.data.dto.response.service.PriceService;
import iclean.code.data.dto.response.transaction.GetTransactionDetailResponse;
import iclean.code.data.dto.response.transaction.GetTransactionResponse;
import iclean.code.data.enumjava.RoleEnum;
import iclean.code.data.enumjava.TransactionStatusEnum;
import iclean.code.data.enumjava.TransactionTypeEnum;
import iclean.code.data.enumjava.WalletTypeEnum;
import iclean.code.data.repository.UserRepository;
import iclean.code.data.repository.TransactionRepository;
import iclean.code.data.repository.WalletRepository;
import iclean.code.exception.BadRequestException;
import iclean.code.exception.NotFoundException;
import iclean.code.function.transaction.service.TransactionService;
import iclean.code.utils.Utils;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Log4j2
public class TransactionServiceImpl implements TransactionService {
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private ModelMapper modelMapper;

    @Override
    public ResponseEntity<ResponseObject> getTransactions(Integer userId, String walletType, Pageable pageable) {
        try {
            Sort order = Sort.by(Sort.Order.desc("createAt"));
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), order);
            Page<Transaction> transactions = transactionRepository
                    .findByUserUserId(userId, WalletTypeEnum.valueOf(walletType.toUpperCase()), pageable);
            List<GetTransactionResponse> responses = transactions
                    .stream()
                    .map(transaction -> modelMapper.map(transaction, GetTransactionResponse.class))
                    .collect(Collectors.toList());

            PageResponseObject pageResponseObject = Utils.convertToPageResponse(transactions, responses);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Transaction List",
                            pageResponseObject));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Internal System Error",
                            null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getTransaction(Integer id,
                                                         Integer userId) {
        try {
            Transaction transaction = findWalletHistoryById(id);
            GetTransactionDetailResponse responses = modelMapper.map(transaction, GetTransactionDetailResponse.class);
            List<PriceService> priceServices = new ArrayList<>();
            if (transaction.getBooking() != null) {
                Booking booking = transaction.getBooking();
                priceServices = booking.getBookingDetails()
                        .stream()
                        .map(element -> {
                            PriceService priceService = new PriceService();
                            priceService.setServiceName(element.getServiceUnit().getService().getServiceName());
                            priceService.setPrice(element.getPriceDetail());
                            return priceService;
                        })
                        .collect(Collectors.toList());
            }
            responses.setPriceServices(priceServices);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Transaction Detail Information",
                            responses));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(),
                                null));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Internal System Error",
                            null));
        }
    }

    private Transaction mappingForCreate(TransactionRequest request) {
        Transaction transaction = modelMapper.map(request, Transaction.class);
        transaction.setAmount(request.getBalance());
        transaction.setTransactionCode(Utils.generateRandomCode());
        transaction.setCreateAt(Utils.getLocalDateTimeNow());
        transaction.setTransactionStatusEnum(TransactionStatusEnum.SUCCESS);
        transaction.setTransactionTypeEnum(TransactionTypeEnum.valueOf(request.getTransactionType().toUpperCase()));
        return transaction;
    }

    @Override
    public ResponseEntity<ResponseObject> createTransaction(TransactionRequest request) {
        try {
            boolean check = createTransactionService(request);

            if (check) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(new ResponseObject(HttpStatus.OK.toString(),
                                "Create new Transaction Successful",
                                null));
            }
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Create new Transaction Fail",
                            null));

        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof BadRequestException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString(),
                                e.getMessage(),
                                null));
            }
            if (e instanceof NotFoundException)
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(),
                                e.getMessage(),
                                null));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Internal System Error",
                            null));
        }
    }

    @Override
    public boolean createTransactionService(TransactionRequest request) throws BadRequestException {
        User user = findUserById(request.getUserId());
        if (!Objects.equals(user.getRole().getTitle().toUpperCase(), RoleEnum.EMPLOYEE.name()) &&
                !user.getRole().getTitle().toUpperCase().equals(RoleEnum.RENTER.name())) {
            throw new BadRequestException("This user cannot have this information");
        }
        Wallet wallet = walletRepository.getWalletByUserIdAndType(request.getUserId(),
                WalletTypeEnum.valueOf(request.getWalletType().toUpperCase()));
        if (Objects.isNull(wallet)) {
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
                break;
            case TRANSFER:
                break;
            case WITHDRAW:
                if (wallet.getBalance() < request.getBalance()) {
                    throw new BadRequestException("The balance of user is less than the request balance");
                }
                wallet.setBalance(wallet.getBalance() - request.getBalance());
                break;
        }
        Wallet walletUpdate = walletRepository.save(wallet);
        Transaction transaction = mappingForCreate(request);
        transaction.setWallet(walletUpdate);
        transactionRepository.save(transaction);
        return true;
    }

    private User findUserById(Integer id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("User ID: %s is not exist", id)));
    }

    private Transaction findWalletHistoryById(Integer id) {
        return transactionRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Wallet History ID: %s is not exist", id)));
    }
}

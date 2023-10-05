package iclean.code.function.wallethistory.service.impl;

import iclean.code.data.domain.User;
import iclean.code.data.domain.WalletHistory;
import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.wallethistory.CreateWalletHistoryRequestDTO;
import iclean.code.data.dto.request.wallethistory.UpdateWalletHistoryRequestDTO;
import iclean.code.data.dto.response.wallethistory.GetWalletHistoryDetailResponseDto;
import iclean.code.data.dto.response.wallethistory.GetWalletHistoryResponseDTO;
import iclean.code.data.repository.UserRepository;
import iclean.code.data.repository.WalletHistoryRepository;
import iclean.code.exception.NotFoundException;
import iclean.code.exception.UserNotHavePermissionException;
import iclean.code.function.wallethistory.service.WalletHistoryService;
import iclean.code.utils.Utils;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Log4j2
public class WalletHistoryServiceImpl implements WalletHistoryService {
    @Autowired
    private WalletHistoryRepository walletHistoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ModelMapper modelMapper;
    @Override
    public ResponseEntity<ResponseObject> getWalletHistories(Integer userId) {
        try {
            List<WalletHistory> walletHistories = walletHistoryRepository.findByUserUserId(userId);
            List<GetWalletHistoryResponseDTO> responses = walletHistories
                    .stream()
                    .map(walletHistory -> modelMapper.map(walletHistory, GetWalletHistoryResponseDTO.class))
                    .collect(Collectors.toList());

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Wallet History List",
                            responses));
        } catch (Exception e) {
            log.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseObject(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Internal System Error",
                            null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> getWalletHistory(Integer id,
                                                           Integer userId) {
        try {
            WalletHistory walletHistory = findWalletHistoryById(id);
            if (!Objects.equals(userId, walletHistory.getUser().getUserId()))
                throw new UserNotHavePermissionException();

            GetWalletHistoryDetailResponseDto responses = modelMapper.map(walletHistory, GetWalletHistoryDetailResponseDto.class);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Wallet History Information",
                            responses));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(),
                                null));
            }
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

    @Override
    public ResponseEntity<ResponseObject> createWalletHistory(CreateWalletHistoryRequestDTO request,
                                                              Integer userId) {
        try {
            WalletHistory walletHistory = modelMapper.map(request, WalletHistory.class);
            User user = findUserById(userId);
            walletHistory.setUser(user);
            walletHistory.setCreateAt(Utils.getDateTimeNow());
            walletHistoryRepository.save(walletHistory);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Create new Wallet History Successful",
                            null));
        } catch (Exception e) {
            log.error(e.getMessage());
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
    public ResponseEntity<ResponseObject> updateWalletHistory(Integer id,
                                                              UpdateWalletHistoryRequestDTO request,
                                                              Integer userId) {
        try {
            WalletHistory walletHistory = findWalletHistoryById(id);
            if (!Objects.equals(walletHistory.getUser().getUserId(), userId))
                throw new UserNotHavePermissionException();

            modelMapper.map(request, walletHistory);
            walletHistoryRepository.save(walletHistory);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Update Wallet History Successful",
                            null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof UserNotHavePermissionException) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
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
    public ResponseEntity<ResponseObject> deleteWalletHistory(Integer id,
                                                              Integer userId) {
        try {
            WalletHistory walletHistory = findWalletHistoryById(id);
            if (!Objects.equals(walletHistory.getUser().getUserId(), userId))
                throw new UserNotHavePermissionException();

            walletHistoryRepository.delete(walletHistory);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(),
                            "Delete Wallet History Successful",
                            null));
        } catch (Exception e) {
            log.error(e.getMessage());
            if (e instanceof UserNotHavePermissionException)
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseObject(HttpStatus.FORBIDDEN.toString(),
                                e.getMessage(),
                                null));

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

    private User findUserById(Integer id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("User ID: %s is not exist", id)));
    }

    private WalletHistory findWalletHistoryById(Integer id) {
        return walletHistoryRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(String.format("Wallet History ID: %s is not exist", id)));
    }
}

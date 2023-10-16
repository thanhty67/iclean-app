package iclean.code.function.bookingstatus.service.impl;

import iclean.code.data.domain.BookingStatus;
import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.bookingstatus.AddBookingStatusRequest;
import iclean.code.data.dto.request.bookingstatus.UpdateBookingStatusRequest;
import iclean.code.data.dto.response.PageResponseObject;
import iclean.code.data.dto.response.address.GetAddressResponseDetailDto;
import iclean.code.data.dto.response.booking.GetBookingResponse;
import iclean.code.data.dto.response.bookingstatus.GetBookingStatusDTO;
import iclean.code.data.repository.BookingStatusRepository;
import iclean.code.exception.NotFoundException;
import iclean.code.function.bookingstatus.service.BookingStatusService;
import iclean.code.utils.Utils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BookingStatusServiceImpl implements BookingStatusService {

    @Autowired
    private BookingStatusRepository bookingStatusRepository;

    @Autowired
    private ModelMapper modelMapper;
    @Override
    public ResponseEntity<ResponseObject> getAllBookingStatus() {
        if (bookingStatusRepository.findAll().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(), "All BookingStatus", "BookingStatus list is empty"));
        }
        List<BookingStatus> bookingStatus= bookingStatusRepository.findAll();
        GetBookingStatusDTO response = modelMapper.map(bookingStatus, GetBookingStatusDTO.class);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ResponseObject(HttpStatus.ACCEPTED.toString(), "All BookingStatus", response));
    }

    @Override
    public ResponseEntity<ResponseObject> getBookingStatusById(int statusId) {
        try {
            BookingStatus bookingStatus =findBookingStatus(statusId);
            GetBookingStatusDTO response = modelMapper.map(bookingStatus, GetBookingStatusDTO.class);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString(), "BookingStatus", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString()
                            , "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> addBookingStatus(AddBookingStatusRequest newStatus) {
        try {
            BookingStatus bookingStatus = modelMapper.map(newStatus, BookingStatus.class);
            bookingStatus.setTitleStatus(newStatus.getTitleStatus());

            bookingStatusRepository.save(bookingStatus);
            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString()
                            , "Create BookingStatus Successfully!", null));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString()
                            , "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> updateBookingStatus(int bookingStatusId, UpdateBookingStatusRequest newStatus) {
        try {
            BookingStatus bookingStatusForUpdate = findBookingStatus(bookingStatusId);
            BookingStatus bookingStatus = modelMapper.map(bookingStatusForUpdate, BookingStatus.class);

            bookingStatus.setTitleStatus(newStatus.getTitleStatus());
            bookingStatusRepository.save(bookingStatus);

            return ResponseEntity.status(HttpStatus.OK)
                    .body(new ResponseObject(HttpStatus.OK.toString()
                            , "Update BookingStatus Successfully!", null));

        } catch (Exception e) {
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString()
                                , "Something wrong occur!", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString()
                            , "Something wrong occur!", null));
        }
    }

    @Override
    public ResponseEntity<ResponseObject> deleteBookingStatus(int statusId) {
        try {
            Optional<BookingStatus> optionalBookingStatus = bookingStatusRepository.findById(statusId);
            if (optionalBookingStatus.isEmpty())
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString(), "BookingStatus is not exist", null));

            BookingStatus statusToDelete = optionalBookingStatus.get();
            bookingStatusRepository.delete(statusToDelete);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ResponseObject(HttpStatus.ACCEPTED.toString()
                            , "Delete BookingStatus Successfully!", null));

        } catch (Exception e) {
            if (e instanceof NotFoundException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ResponseObject(HttpStatus.NOT_FOUND.toString()
                                , "Something wrong occur!", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ResponseObject(HttpStatus.BAD_REQUEST.toString()
                            , "Something wrong occur!", null));
        }
    }

    private BookingStatus findBookingStatus(int statusId) {
        return bookingStatusRepository.findById(statusId)
                .orElseThrow(() -> new NotFoundException("Booking status is not exist"));
    }
}

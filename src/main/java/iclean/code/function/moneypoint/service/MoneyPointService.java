package iclean.code.function.moneypoint.service;

import iclean.code.data.dto.common.ResponseObject;
import iclean.code.data.dto.request.moneypoint.CreateMoneyPoint;
import iclean.code.data.dto.request.moneypoint.UpdateMoneyPoint;
import org.springframework.http.ResponseEntity;

public interface MoneyPointService {
    ResponseEntity<ResponseObject> getAllMoneyPoint();

    ResponseEntity<ResponseObject> getMoneyPointById(int moneyPointId);

    ResponseEntity<ResponseObject> getMoneyPointByUserId(int userId);

    ResponseEntity<ResponseObject> addNewMoneyPoint(CreateMoneyPoint moneyPoint);

    ResponseEntity<ResponseObject> updateMoneyPointByUserId(int userId, UpdateMoneyPoint moneyPoint);

    ResponseEntity<ResponseObject> deleteMoneyPoint(int moneyPointById);
}
package iclean.code.function.common.service;

public interface FirebaseRealtimeDatabaseService {
    void sendMessage(String key, String message);
    void sendNotification(String userId, String bookingDetailId, String message);
}

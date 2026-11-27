package vn.huce.service;

import org.springframework.stereotype.Service;
import vn.huce.repository.NotificationRepository;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void notifyManagers(String message) {
        repository.notifyManagers(message);
    }

    public void notifyUser(long userId, String message) {
        repository.notifyUser(userId, message);
    }

    public void notifySection(long sectionId, String message) {
        repository.notifySection(sectionId, message);
    }

    public void notifyStaffProfile(long staffId, String message) {
        repository.notifyStaffProfile(staffId, message);
    }

    public void notifyScopedStaff(Object unitId, Object roomId, String message) {
        repository.notifyScopedStaff(unitId, roomId, message);
    }
}

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
}

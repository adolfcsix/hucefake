package vn.huce.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.repository.RuntimeModeRepository;

@Component
public class RuntimeMode implements ApplicationRunner {
    private final RuntimeModeRepository repository;
    private final BCryptPasswordEncoder bcrypt;
    private final boolean demo;

    public RuntimeMode(RuntimeModeRepository repository, BCryptPasswordEncoder bcrypt,
                       @Value("${huce.demo}") boolean demo) {
        this.repository = repository;
        this.bcrypt = bcrypt;
        this.demo = demo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repository.lock();
        List<Boolean> saved = repository.savedMode();
        if (!saved.isEmpty() && saved.getFirst() != demo) {
            throw new IllegalStateException("Database thuộc chế độ khác.");
        }
        if (!demo && repository.hasDemoSeed()) {
            throw new IllegalStateException("Không dùng database demo cho dữ liệu riêng.");
        }
        if (!saved.isEmpty()) {
            return;
        }
        if (demo) {
            if (repository.hasExistingData()) {
                throw new IllegalStateException("Không bật demo trên database đã có dữ liệu.");
            }
            repository.loadDemoCatalog();
            repository.seedDemoAccountsAndSchedules(bcrypt.encode("Fm05Demo@2026"));
        }
        repository.saveMode(demo);
    }
}

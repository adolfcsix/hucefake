package vn.huce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.huce.Accounts.Actor;
import vn.huce.exception.ApiFailure;
import vn.huce.model.IdempotencyRecord;
import vn.huce.repository.MutationRepository;
import vn.huce.validation.Inputs;

@Service
public class MutationService {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-zA-Z0-9._:-]{1,128}");
    private final MutationRepository repository;
    private final ObjectMapper json;

    MutationService(MutationRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.json = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Transactional
    public Object write(Actor actor, HttpServletRequest request, Object body, Supplier<Object> operation) {
        repository.lock();
        String key = request.getHeader("Idempotency-Key");
        if (key == null) {
            return operation.get();
        }
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw Inputs.invalid("Idempotency-Key không hợp lệ.");
        }

        String fingerprint = fingerprint(actor, request, body);
        repository.deleteExpired();
        IdempotencyRecord prior = repository.find(actor.id(), key);
        if (prior != null) {
            if (!fingerprint.equals(prior.fingerprint())) {
                throw new ApiFailure(409, "DUPLICATE_REQUEST",
                        "Mã chống lặp đã được dùng cho một yêu cầu khác.");
            }
            return decode(prior.responseJson());
        }

        Object result = operation.get();
        repository.save(actor.id(), key, fingerprint, encode(result));
        return result;
    }

    private String fingerprint(Actor actor, HttpServletRequest request, Object body) {
        // Preserve field order and canonical JSON for records written by earlier releases.
        List<Object> data = Arrays.asList(request.getMethod(), request.getRequestURI(),
                actor.role(), actor.unitId(), actor.staffId(), request.getParameterMap(), body);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encode(data).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private Object decode(String value) {
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }
}

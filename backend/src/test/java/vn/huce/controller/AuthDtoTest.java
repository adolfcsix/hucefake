package vn.huce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.huce.dto.AuthModeResponse;
import vn.huce.dto.CsrfResponse;
import vn.huce.dto.DemoLoginRequest;
import vn.huce.dto.LoginRequest;

import static org.junit.jupiter.api.Assertions.*;

class AuthDtoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loginRequestAcceptsExistingJsonWithoutExposingPassword() throws Exception {
        LoginRequest request = mapper.readValue("{\"username\":\"staff\",\"password\":\"secret\"}", LoginRequest.class);
        assertEquals("staff", request.username());
        assertEquals("secret", request.password());
        assertFalse(mapper.writeValueAsString(request).contains("secret"));
        assertFalse(request.toString().contains("secret"));
    }

    @Test
    void responseFieldsKeepExistingContract() throws Exception {
        assertEquals(mapper.readTree("{\"demo\":true}"),
                mapper.valueToTree(new AuthModeResponse(true)));
        assertEquals(mapper.readTree("{\"header\":\"X-CSRF-Token\",\"token\":\"value\"}"),
                mapper.valueToTree(new CsrfResponse("X-CSRF-Token", "value")));
        assertEquals("STAFF", mapper.readValue("{\"role\":\"STAFF\"}", DemoLoginRequest.class).role());
    }
}

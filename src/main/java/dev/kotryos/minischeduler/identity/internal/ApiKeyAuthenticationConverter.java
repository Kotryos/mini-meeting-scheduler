package dev.kotryos.minischeduler.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class ApiKeyAuthenticationConverter implements AuthenticationConverter {

    static final String API_KEY_HEADER = "X-API-Key";

    @Override
    public Authentication convert(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return new PreAuthenticatedAuthenticationToken(apiKey, apiKey);
    }
}

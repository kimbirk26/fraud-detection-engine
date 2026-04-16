package com.kim.fraudengine.infrastructure.security;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class InternalAuthenticationRunner {

    public static final String INTERNAL_PROCESSING_AUTHORITY = "transactions:process:internal";

    public <T> T runAs(String principalName, List<String> authorities, Supplier<T> action) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext internalContext = SecurityContextHolder.createEmptyContext();

        var authentication =
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedRequestPrincipal(principalName, null),
                        null,
                        authorities.stream()
                                .map(
                                        org.springframework.security.core.authority
                                                        .SimpleGrantedAuthority
                                                ::new)
                                .toList());
        internalContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(internalContext);

        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
            SecurityContextHolder.setContext(previousContext);
        }
    }
}

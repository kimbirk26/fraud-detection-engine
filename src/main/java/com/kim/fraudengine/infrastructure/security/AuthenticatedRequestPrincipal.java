package com.kim.fraudengine.infrastructure.security;


import java.security.Principal;

public record AuthenticatedRequestPrincipal(String username,
                                            String customerId) implements Principal, CustomerScopedPrincipal {

    @Override
    public String getName() {
        return username;
    }
}

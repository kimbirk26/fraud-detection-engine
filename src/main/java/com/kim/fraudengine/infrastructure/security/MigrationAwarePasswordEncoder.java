package com.kim.fraudengine.infrastructure.security;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class MigrationAwarePasswordEncoder implements PasswordEncoder {

    private static final String ARGON2_ID = "argon2";

    private final PasswordEncoder bcryptEncoder;
    private final DelegatingPasswordEncoder delegatingEncoder;

    public MigrationAwarePasswordEncoder() {
        this(new BCryptPasswordEncoder(12), Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
    }

    MigrationAwarePasswordEncoder(PasswordEncoder bcryptEncoder, PasswordEncoder argon2Encoder) {
        this.bcryptEncoder = bcryptEncoder;
        this.delegatingEncoder =
                new DelegatingPasswordEncoder(
                        ARGON2_ID, Map.of(ARGON2_ID, argon2Encoder, "bcrypt", bcryptEncoder));
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegatingEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (isLegacyUnprefixedHash(encodedPassword)) {
            return bcryptEncoder.matches(rawPassword, encodedPassword);
        }
        return delegatingEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        if (isLegacyUnprefixedHash(encodedPassword)) {
            return true;
        }
        return delegatingEncoder.upgradeEncoding(encodedPassword);
    }

    private boolean isLegacyUnprefixedHash(String encodedPassword) {
        return encodedPassword != null && !encodedPassword.startsWith("{");
    }
}

package com.kim.fraudengine.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    // Test RSA key pair (2048-bit, local/test use only)
    private static final String PRIVATE_KEY =
            """
            -----BEGIN PRIVATE KEY-----
            MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDc4ZhhOUfvdZEn
            iR9NeCyG0WJjVibDZL5LHjHOMFUj4CAjI+c9zWlQkU3FObjY1TlkilSYIKxADm1K
            8aKiu6YNQdikcCHSrPdrpkiHrwYKLJUevHW8S213fVY14iNiwCfMag9rTuAtuCM/
            bn8iOKagHCPuaN8cQFGmnQ4HsI3X9LSest0VbwwS1GLRk7FjgnfSuWsZ807COmH9
            Ao2WMITKqTbOyGEKQZg7Gaycc5E+VRI7t9u+ySiLT7z74C0U4bm177+fC5mMJ0Fi
            A6kPigheYVurr63UYLvK6LlszBSsawq2ZQBSykztfyCcnxV4JRlC0wPqoWCFoAh2
            EFw5MBURAgMBAAECggEABPLcqZXlG557nl6GkVISaGQU20695b3fxZGI2G+cx3wQ
            d1afM/0djHa07/d+y3pnyxUr3FVLX0Z9UUpgAwy9h/haT8EknmO6SdVMNIzJxdpt
            i6GDcv+bKSwG7/psIcQbIClO7KsCznmS5Ejs3JFDb1pFvakPh9Yy6mxmryVrpaws
            asElFuIOVR7OrPvyoyexh0R3d23G2caBg7JphB8bP8qSB+B7U0mhotsNKMLlCiU8
            ik4p6zxbnYALH64ZSwubl45NxGCfxu9PnulLsMhP3HNpIfMjege/gWsTRieNntQo
            DgX4uYkI5Oqj2wdM2EFV4Q+GzpP9o2orfAKsoRQKQQKBgQD6AioMdRGfS8kOgm6h
            zKRkXjaY6yoZawf+d4NljhYcoiktJD6C99+X4dtYnMl+ULuc56P8d2/qxGvPfxxL
            UnOuoIP2UOH3UWsSc9QKr0yRU8V2jIMyTTt83pmbmMNbTx3bI9woXHBzBWgA9jdB
            awVp3vcpDJvni2UIemwmLp9SkwKBgQDiLLtFCLHrojSC9tCUkuui+qeFbuQd6++d
            nKGL9kqVacrTKf5CDhXS00axwFoz2LWw2S91/swjKpIrVgiziBELLQRJX9MTSQ6D
            koF1WYisEmhHikIRX2vl/SIYCN2T/KSb/KtJx0PBKEdgwQAZOaVEicGXY4q+7bXv
            PaAhN0wMSwKBgDeGIYaQvXAuaaHCUAW5KE1uKxv9JmVswuK98j2st7Z2QUTYRtXZ
            bRwTOh7M+2cFURWA0IeykvWF2BfGOCd2UWDYH1amEflWaLw5Yz9YPV4NR86TWFPk
            mTCbU2weGkz+HjhcF3oTRZoV+ko0ZIMv3IztyuCf/0QGTlL6tWgpdJLnAoGAV1jr
            IegPvm6wVPu45ggvlIu08qU22A9sRLRe90yw1S88M3z+QshpyTfrD26351oEIT5f
            Q/SZJeOk+7OIPL2Jx4UlKKknPUVzo4CLGqTvUXTybN6KUWdGplWyOxIBcMubTtqv
            1BhupERH/KfMv/ExUFzbNPoudTocjz5/fxR5/C0CgYA/Seq9l1SqI0OL/r76hgTL
            XgCH2pVx+8N/Jfup4p/Rz05VrJAEwjIGxd2D+w2KdAz5S27ipNvoQ+LE5rZWEqHi
            MrZNVWixZ8IhcuQbXjn07ORPvO+PBO0U0TyB1kLOCqcZpjrWx3eg5yEMtEP5nZ27
            +7RniPrtdWTAiSbyhZmjSw==
            -----END PRIVATE KEY-----""";

    private static final String PUBLIC_KEY =
            """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3OGYYTlH73WRJ4kfTXgs
            htFiY1Ymw2S+Sx4xzjBVI+AgIyPnPc1pUJFNxTm42NU5ZIpUmCCsQA5tSvGiorum
            DUHYpHAh0qz3a6ZIh68GCiyVHrx1vEttd31WNeIjYsAnzGoPa07gLbgjP25/Ijim
            oBwj7mjfHEBRpp0OB7CN1/S0nrLdFW8MEtRi0ZOxY4J30rlrGfNOwjph/QKNljCE
            yqk2zshhCkGYOxmsnHORPlUSO7fbvskoi0+8++AtFOG5te+/nwuZjCdBYgOpD4oI
            XmFbq6+t1GC7yui5bMwUrGsKtmUAUspM7X8gnJ8VeCUZQtMD6qFghaAIdhBcOTAV
            EQIDAQAB
            -----END PUBLIC KEY-----""";

    // A different RSA key pair (used to verify wrong-key rejection)
    private static final String OTHER_PRIVATE_KEY =
            """
            -----BEGIN PRIVATE KEY-----
            MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDB1A8tH3eoPXTn
            LOIJ4H/qeEEqURHyIEfw9ZRiWeKjl4ii4Ij9KK1afqRXLd9kRBWY0bmpqyq/IprF
            uTpC2d7Z0o93xKJgd6aV4wMLVCOS25Q2JR6txJ8wvDsres9fh6WcuLOQYfwjstHs
            GqSIC6HqNe7kltpjzcr7GaJIwVyJXNf07y7xaWDzd9l0C8CPXETiqRZmrPvG5Fjd
            T+xw8JkrWGivPS9L8QpIAq9JTg5hR3gsjP3g2jBnjRJQ4CnpNUlwDavV1ZDwQ2E9
            tzh/yx1eY6MDC4rmCfjo8mr//HArnye/XStifcVKJJXbzR0HWCQzd0OQxTU9y5uy
            3cEPgbwlAgMBAAECggEAG3mNgCosejTh8A346YkA4NHUfOTafB2OzQ1mj+1pVqKZ
            e+sDiA0ey4vVjEUEnDAZdpzN6krIx6imUz4vD8BV2j8CVqoREUcMnkT8TrwJwdyt
            vOIUn/GV1iDsazygjgxKkbVNwLre9Il5v7PEHEO32xzzhMQMZ2bJrb/DtPfxv8eh
            2CMvsUicIsB3soYHRjLG8H4M8I/CFMrjLZDQGAfHfmjWmVaHJRj817Wpdv5yOnit
            MSkSr01ga6RISA+bxetxbUaOs9YaYkg90WwqTDnj+inuDn63Uz5o10aKQ6bX14EY
            OnXdQODd9gxaV+oN0lRtVVWcqRUchHw4lfF8/uD6YQKBgQDq/y79/K+cCoC0PBZE
            guYRcDyN08WrJfUoSxVvwAGb7g7oBA4CI+OEXE3KeOb/+xYjVAEquWFVS4Z2AlBo
            cjKMDx/rNDfb8PgSM2BlfwndoRee9lT9nXgqJsTx0UCymOXNyiqPgmtum5sWuNXL
            Iao2mHO3JnCoAknduoJNJTO2oQKBgQDTJu0Cyp+lqfW0+KPRPFWKtNueYBwvvpuD
            UCrGaT8xpemxVHcKDw/HD4Ym4ptI7t2S056y7CrM/GJu9zqPZkOCqzPteErbYXoq
            xwRqQsUde/mbTn3bmGyMQ7+L3/cCbBjvKHxvjnimOmb042TzL6SjCQfjj0+/QMqN
            oxfSdD5LBQKBgCsdVonS6oU+iA6JV6yKN5vLc9CxofqcpDYYUH6IQ8NQEfwPgmJy
            IzQNm1gihn9AmfcxWmV0TZ9QlALiuc3v5cY8oCaPFhCMTXdJZc45WJ4JCERp/X3q
            fjl9k3SqZ6xc0QzIorZhv0Qz2Gh60P7L1mbd4Z0guFqa0OKVbYKp6KsBAoGAe8we
            ubxCzcZQIMKGiW5uWNygxsJixDtkwiCGc73RJzK3SRjUkjkybutTJAlIMgKaNjOM
            oCqHDZgLvjOFSf4TJtFpqJkWinkkP2Bf3k03dInzVnM2p0E+ox41d7TgBbOLCu0Z
            x+4oHo2vP3TzAwGz7UKmNW0YvHEoUNKGbQVqYGECgYAIklcBGRtEyEoNuCfNMZAO
            Lo7d7rI0dnE8UiVZiXoG2X5+pGhosVKDZ34yi+LnXjQNNPUaxEbGxDaPBkE7xEWL
            yjfVkHOjDRbZQhHPUb6GUoCT5e7WVaMBp/ocLPBuv0y7m7T3NgiGq3Y3pSenu/oU
            7DlDaXELAj1gmvNhAP4ahg==
            -----END PRIVATE KEY-----""";

    private static final String OTHER_PUBLIC_KEY =
            """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwdQPLR93qD105yziCeB/
            6nhBKlER8iBH8PWUYlnio5eIouCI/SitWn6kVy3fZEQVmNG5qasqvyKaxbk6Qtne
            2dKPd8SiYHemleMDC1QjktuUNiUercSfMLw7K3rPX4elnLizkGH8I7LR7BqkiAuh
            6jXu5JbaY83K+xmiSMFciVzX9O8u8Wlg83fZdAvAj1xE4qkWZqz7xuRY3U/scPCZ
            K1horz0vS/EKSAKvSU4OYUd4LIz94NowZ40SUOAp6TVJcA2r1dWQ8ENhPbc4f8sd
            XmOjAwuK5gn46PJq//xwK58nv10rYn3FSiSV280dB1gkM3dDkMU1Pcubst3BD4G8
            JQIDAQAB
            -----END PUBLIC KEY-----""";

    @Test
    void tokens_are_valid_for_expected_issuer_and_audience() {
        JwtService jwtService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = jwtService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(jwtService.isTokenValid(token, "analyst")).isTrue();
    }

    @Test
    void tokens_from_another_issuer_are_rejected() {
        JwtService issuingService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "other-service",
                        "fraud-detection-engine-api-test");
        JwtService validatingService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = issuingService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(validatingService.isTokenValid(token, "analyst")).isFalse();
    }

    @Test
    void tokens_for_another_audience_are_rejected() {
        JwtService issuingService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "some-other-audience");
        JwtService validatingService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = issuingService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(validatingService.isTokenValid(token, "analyst")).isFalse();
    }

    @Test
    void tokens_signed_with_different_key_are_rejected() {
        JwtService issuingService =
                new JwtService(
                        OTHER_PRIVATE_KEY,
                        OTHER_PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");
        JwtService validatingService =
                new JwtService(
                        PRIVATE_KEY,
                        PUBLIC_KEY,
                        60,
                        "fraud-detection-engine-test",
                        "fraud-detection-engine-api-test");

        String token = issuingService.generateToken("analyst", List.of("alerts:read:all"));

        assertThat(validatingService.isTokenValid(token, "analyst")).isFalse();
    }
}

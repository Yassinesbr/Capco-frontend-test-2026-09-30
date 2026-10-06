package com.kyc.identityaccess.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root for the Identity and Access service. */
@SpringBootApplication(scanBasePackages = "com.kyc")
public class IdentityAccessApplication {

    /** Starts the backend process. */
    public static void main(final String[] args) {
        SpringApplication.run(IdentityAccessApplication.class, args);
    }
}

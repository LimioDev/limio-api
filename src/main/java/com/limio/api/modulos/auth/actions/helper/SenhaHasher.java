package com.limio.api.modulos.auth.actions.helper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SenhaHasher {

    private final PasswordEncoder passwordEncoder;

    public SenhaHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String hash(String senhaEmTexto) {
        return passwordEncoder.encode(senhaEmTexto);
    }
}

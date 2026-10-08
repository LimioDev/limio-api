package com.limio.api.modulos.auth.actions.helper;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SenhaHasher {

    private final PasswordEncoder passwordEncoder;

    // Hash de uma senha qualquer, só pra gastar um BCrypt quando não há conta pra conferir.
    private final String hashFicticio;

    public SenhaHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.hashFicticio = passwordEncoder.encode("hash-ficticio-anti-enumeracao");
    }

    public String hash(String senhaEmTexto) {
        return passwordEncoder.encode(senhaEmTexto);
    }

    public boolean confere(String senhaEmTexto, String senhaHash) {
        return passwordEncoder.matches(senhaEmTexto, senhaHash);
    }

    /**
     * Mesmo custo de {@link #confere} quando o e-mail não tem conta: sem isso a
     * resposta de e-mail inexistente sairia mais rápido que a de senha errada e
     * o tempo de resposta revelaria quais e-mails estão cadastrados.
     */
    public void simularConferencia(String senhaEmTexto) {
        passwordEncoder.matches(senhaEmTexto, hashFicticio);
    }
}

package com.limio.api.modulos.moderacao.enums;

/**
 * Veredito do administrador sobre uma denúncia (ETI-116). {@code PROCEDENTE} sempre vem com uma sanção;
 * {@code IMPROCEDENTE} fecha a denúncia sem sanção ("Nenhuma" no painel).
 */
public enum VereditoModeracao {
    PROCEDENTE,
    IMPROCEDENTE
}

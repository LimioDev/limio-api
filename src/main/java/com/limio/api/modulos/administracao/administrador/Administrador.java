package com.limio.api.modulos.administracao.administrador;

import com.limio.api.comum.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Administrador do painel web (ETI-117). Ator próprio, nunca um {@code Usuario} de {@code modulos.auth}: o id dele é
 * o {@code sub} do token de administrador, lido como
 * {@link com.limio.api.comum.seguranca.AdministradorAutenticado}.
 *
 * <p>O login (e-mail e senha) é do card da entrada do admin (US60). Até lá a linha entra por INSERT manual (V42).
 */
@Entity
@Table(name = "administrador")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Administrador extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    /** Quem chama já gerou o hash da senha. Não persiste. */
    public static Administrador cadastrar(String nome, String email, String senhaHash) {
        Administrador administrador = new Administrador();
        administrador.nome = nome;
        administrador.email = email;
        administrador.senhaHash = senhaHash;
        return administrador;
    }
}

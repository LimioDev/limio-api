package com.limio.api.modulos.auth;

import java.time.Instant;
import java.time.LocalDate;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.auth.enums.PapelUsuario;
import com.limio.api.modulos.auth.enums.StatusConta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario extends BaseEntity {

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "telefone", nullable = false, length = 11)
    private String telefone;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "cidade_uf", nullable = false)
    private String cidadeUf;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel_ativo", nullable = false, length = 20)
    private PapelUsuario papelAtivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conta", nullable = false, length = 30)
    private StatusConta statusConta;

    @Column(name = "telefone_confirmado_em")
    private Instant telefoneConfirmadoEm;

    @Column(name = "email_confirmado_em")
    private Instant emailConfirmadoEm;
}

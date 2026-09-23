package com.limio.api.modulos.auth;

import com.limio.api.comum.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de CPF que teve conta excluída — usado só pra bloquear
 * recadastro dentro da janela de cooldown (evita burlar cotas). Criado pelo
 * futuro caso de uso de exclusão de conta; lido aqui só na hora do cadastro.
 * {@code criadoEm} (de {@link BaseEntity}) marca o instante da exclusão.
 */
@Entity
@Table(name = "cpf_bloqueado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CpfBloqueado extends BaseEntity {

    @Column(name = "cpf", nullable = false, length = 11)
    private String cpf;
}

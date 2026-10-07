package com.limio.api.modulos.auth.cpfbloqueado;

import java.time.Instant;

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
 * {@code cpfHmac} guarda HMAC-SHA256 do CPF ({@link
 * com.limio.api.modulos.auth.actions.helper.CpfHasher}), nunca o valor em
 * texto puro — a tabela só precisa conferir igualdade.
 * {@code liberadoEm} existe pra liberação antecipada do cooldown (ex.:
 * decisão administrativa) — enquanto nulo, vale o cooldown padrão a partir
 * de {@code bloqueadoEm}.
 */
@Entity
@Table(name = "cpf_bloqueado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CpfBloqueado extends BaseEntity {

    @Column(name = "cpf_hmac", nullable = false, length = 64)
    private String cpfHmac;

    @Column(name = "bloqueado_em", nullable = false)
    private Instant bloqueadoEm;

    @Column(name = "liberado_em")
    private Instant liberadoEm;
}

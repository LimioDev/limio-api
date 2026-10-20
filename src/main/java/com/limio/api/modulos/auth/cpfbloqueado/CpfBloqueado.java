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
 * recadastro dentro da janela de cooldown (evita burlar cotas). Criado por
 * {@code EncerrarContaUseCase} (UC10); lido aqui só na hora do cadastro.
 * {@code cpfHmac} guarda HMAC-SHA256 do CPF ({@link
 * com.limio.api.modulos.auth.actions.helper.CpfHasher}), nunca o valor em
 * texto puro — a tabela só precisa conferir igualdade.
 *
 * {@code liberadoEm} é sempre gravado na criação ({@code bloqueadoEm} + 12
 * meses, ver {@code CpfBloqueadoService.bloquear}) — a checagem
 * ({@code estaBloqueado}) é só "{@code liberadoEm} no futuro". Liberação
 * antecipada (ex.: decisão administrativa) é mover {@code liberadoEm} pra
 * uma data no passado, não nular o campo: {@code liberadoEm} nulo faz a
 * comparação SQL (<code>liberado_em &gt; :agora</code>) dar
 * desconhecido/falso, ou seja, libera na hora — não é "cooldown padrão"
 * como este campo já significou antes do TICKET-0034.
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

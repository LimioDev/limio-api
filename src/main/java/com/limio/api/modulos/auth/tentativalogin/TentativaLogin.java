package com.limio.api.modulos.auth.tentativalogin;

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
 * Uma tentativa de login que não deu certo (senha errada, e-mail inexistente
 * ou conta encerrada). É gravada antes de conferir a senha — tentativas
 * simultâneas já contam umas pras outras — e apagada se a senha conferir.
 *
 * Chave é o e-mail (o gravado na conta, quando ela existe), não o usuário:
 * o rate limit vale também pra e-mail que não existe e não revela quais
 * contas existem. {@code criadoEm} (de {@link BaseEntity}) é o instante da
 * tentativa. Não está no DER: criada no TICKET-0032 pra contar as tentativas.
 */
@Entity
@Table(name = "tentativa_login")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TentativaLogin extends BaseEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "ip", length = 45)
    private String ip;
}

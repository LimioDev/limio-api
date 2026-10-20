package com.limio.api.modulos.auth.usuario;

import java.time.Instant;
import java.time.LocalDate;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.comum.seguranca.enums.PapelUsuario;
import com.limio.api.modulos.auth.excecao.ContaSuspensaException;
import com.limio.api.modulos.auth.excecao.SessaoInvalidaException;
import com.limio.api.modulos.auth.usuario.enums.StatusConta;

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

    /** Nulo numa conta encerrada (UC10 apaga, não só mascara) — {@code uk_usuario_cpf} aceita vários NULL. */
    @Column(name = "cpf", unique = true, length = 11)
    private String cpf;

    /** Nulo numa conta encerrada (UC10 apaga). */
    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    /** Nulo numa conta encerrada (UC10 apaga). */
    @Column(name = "cidade_uf")
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

    @Column(name = "anonimizado_em")
    private Instant anonimizadoEm;

    /**
     * Conta encerrada pelo titular (UC10): o encerramento anonimiza os dados, então
     * {@code anonimizadoEm} preenchido é o que marca a conta como encerrada — o
     * {@link StatusConta} do diagrama não tem valor próprio pra isso.
     */
    public boolean isEncerrada() {
        return anonimizadoEm != null;
    }

    /** Suspensa por moderação: não autentica, não renova sessão, não troca de papel. */
    public boolean isSuspensa() {
        return switch (statusConta) {
            case ATIVA, PENDENTE_VERIFICACAO -> false; // UC05: login pode acontecer antes de confirmar o e-mail
            case BLOQUEADA -> true;
        };
    }

    /**
     * Guarda de quem já tem sessão (renovar, trocar papel): conta encerrada
     * derruba a sessão, suspensa responde como suspensa. O login não usa —
     * lá conta encerrada responde igual a e-mail inexistente.
     */
    public void exigirAtiva() {
        if (isEncerrada()) {
            throw new SessaoInvalidaException();
        }
        if (isSuspensa()) {
            throw new ContaSuspensaException();
        }
    }

    /**
     * UC10 (encerrar conta, LGPD): apaga cpf/dataNascimento/cidadeUf; anonimiza
     * nomeCompleto/email/telefone (e-mail e telefone ficam livres pra um
     * cadastro novo, já que {@code uk_usuario_email} não libera com só mascarar
     * o valor original). Não mexe em {@code senhaHash} (login já recusa a conta
     * encerrada antes de olhar a senha) nem em {@code papelAtivo}/{@code statusConta}
     * (encerrada é só {@code anonimizadoEm} preenchido — {@link #isEncerrada()}).
     * Quem chama ainda precisa gravar {@link com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueado}
     * com o CPF original, antes de chamar isto.
     */
    public void encerrar(Instant agora) {
        this.nomeCompleto = "Conta encerrada";
        this.email = "encerrada+" + getId() + "@anonimizado.limio";
        this.telefone = "00000000000";
        this.cpf = null;
        this.dataNascimento = null;
        this.cidadeUf = null;
        this.anonimizadoEm = agora;
    }
}

package com.limio.api.modulos.perfil.perfilprestador.janelaatendimento;

import java.time.LocalTime;

import com.limio.api.comum.base.BaseEntity;
import com.limio.api.modulos.perfil.perfilprestador.PerfilPrestador;
import com.limio.api.modulos.perfil.perfilprestador.janelaatendimento.enums.DiaSemana;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Horário em que o Prestador atende, num dia da semana. Subentidade de {@link PerfilPrestador}: sem ciclo
 * de vida próprio, criada e apagada só pelo perfil (cascade + orphanRemoval). Início antes do fim é
 * validado no record de entrada e garantido no banco ({@code ck_janela_atendimento_intervalo}).
 */
@Entity
@Table(name = "janela_atendimento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JanelaAtendimento extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_prestador_id", nullable = false)
    private PerfilPrestador perfilPrestador;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;
}

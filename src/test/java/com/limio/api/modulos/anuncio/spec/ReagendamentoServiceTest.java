package com.limio.api.modulos.anuncio.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.anuncio.enums.StatusReagendamento;
import com.limio.api.modulos.anuncio.reagendamento.Reagendamento;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoRepository;
import com.limio.api.modulos.anuncio.reagendamento.ReagendamentoService;

/** O encerramento "sem efeito" das propostas pendentes vive num ponto só (ETI-99). */
@ExtendWith(MockitoExtension.class)
class ReagendamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T15:00:00Z");
    private static final UUID ANUNCIO_ID = UUID.randomUUID();

    @Mock
    private ReagendamentoRepository repository;

    private ReagendamentoService service;

    @BeforeEach
    void montar() {
        service = new ReagendamentoService(repository);
    }

    private static Reagendamento pendente() {
        return Reagendamento.propor(ANUNCIO_ID, UUID.randomUUID(), AGORA.plus(Duration.ofDays(3)),
                AGORA.plus(Duration.ofDays(5)), "Imprevisto", AGORA.plus(Duration.ofHours(24)), false);
    }

    @Test
    void dadasPropostasPendentes_quandoEncerraSemEfeito_entaoCadaUmaFicaSemEfeitoESalva() {
        Reagendamento a = pendente();
        Reagendamento b = pendente();
        when(repository.findByAnuncioIdInAndStatus(List.of(ANUNCIO_ID), StatusReagendamento.PENDENTE))
                .thenReturn(List.of(a, b));

        service.encerrarPendentesSemEfeito(List.of(ANUNCIO_ID), AGORA);

        assertThat(a.getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        assertThat(b.getStatus()).isEqualTo(StatusReagendamento.SEM_EFEITO);
        assertThat(a.getRespondidoEm()).isEqualTo(AGORA);
        // BaseEntity compara por id (nulo aqui): captura pra conferir as instâncias salvas, não a igualdade.
        ArgumentCaptor<Reagendamento> salvos = ArgumentCaptor.forClass(Reagendamento.class);
        verify(repository, times(2)).save(salvos.capture());
        assertThat(salvos.getAllValues()).containsExactly(a, b);
        assertThat(salvos.getAllValues().get(0)).isSameAs(a);
        assertThat(salvos.getAllValues().get(1)).isSameAs(b);
    }

    @Test
    void semPropostaPendente_quandoEncerraSemEfeito_entaoNaoGravaNada() {
        when(repository.findByAnuncioIdInAndStatus(List.of(ANUNCIO_ID), StatusReagendamento.PENDENTE))
                .thenReturn(List.of());

        service.encerrarPendentesSemEfeito(List.of(ANUNCIO_ID), AGORA);

        verify(repository, never()).save(any());
    }

    @Test
    void semAnuncios_quandoEncerraSemEfeito_entaoNemConsultaOBanco() {
        service.encerrarPendentesSemEfeito(List.of(), AGORA);

        verify(repository, never()).findByAnuncioIdInAndStatus(any(), any());
    }
}

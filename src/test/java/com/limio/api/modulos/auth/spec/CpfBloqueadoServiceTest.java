package com.limio.api.modulos.auth.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.modulos.auth.actions.helper.CpfHasher;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueado;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoRepository;
import com.limio.api.modulos.auth.cpfbloqueado.CpfBloqueadoService;

@ExtendWith(MockitoExtension.class)
class CpfBloqueadoServiceTest {

    private static final String CPF = "52998224725";
    private static final String CPF_HMAC = "hmac-fake";

    @Mock
    private CpfBloqueadoRepository repository;

    @Mock
    private CpfHasher cpfHasher;

    private CpfBloqueadoService service;

    @BeforeEach
    void montarService() {
        service = new CpfBloqueadoService(repository, cpfHasher);
    }

    @Test
    void estaBloqueadoDelegaParaORepositorioComOHashDoCpf() {
        Instant agora = Instant.now();
        when(cpfHasher.hash(CPF)).thenReturn(CPF_HMAC);
        when(repository.existsByCpfHmacAndLiberadoEmAfter(CPF_HMAC, agora)).thenReturn(true);

        assertThat(service.estaBloqueado(CPF, agora)).isTrue();
    }

    @Test
    void bloquearGravaBloqueadoEmAgoraELiberadoEm12MesesDepois() {
        Instant agora = Instant.now();
        when(cpfHasher.hash(CPF)).thenReturn(CPF_HMAC);
        when(repository.save(any(CpfBloqueado.class))).thenAnswer(chamada -> chamada.getArgument(0));

        service.bloquear(CPF, agora);

        ArgumentCaptor<CpfBloqueado> captura = ArgumentCaptor.forClass(CpfBloqueado.class);
        verify(repository).save(captura.capture());
        CpfBloqueado salvo = captura.getValue();
        assertThat(salvo.getCpfHmac()).isEqualTo(CPF_HMAC);
        assertThat(salvo.getBloqueadoEm()).isEqualTo(agora);
        assertThat(salvo.getLiberadoEm()).isAfter(agora.plus(360, ChronoUnit.DAYS));
        assertThat(salvo.getLiberadoEm()).isBefore(agora.plus(370, ChronoUnit.DAYS));
    }

    @Test
    void limparLiberadosDelegaParaORepositorio() {
        Instant agora = Instant.now();

        service.limparLiberados(agora);

        verify(repository).apagarLiberadosAntesDe(eq(agora));
    }
}

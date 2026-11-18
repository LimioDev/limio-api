package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.actions.service.ConsumoCotaPrestadorService;
import com.limio.api.modulos.perfil.actions.usecase.ConsumirCotaDoMesUseCase;
import com.limio.api.modulos.perfil.actions.usecase.EstornarCotaDoMesUseCase;

@ExtendWith(MockitoExtension.class)
class ConsumoCotaPrestadorServiceTest {

    private static final UUID PRESTADOR_ID = UUID.randomUUID();
    private static final Instant QUANDO = Instant.parse("2026-10-29T15:00:00Z");

    @Mock
    private ConsumirCotaDoMesUseCase consumirCotaDoMesUseCase;

    @Mock
    private EstornarCotaDoMesUseCase estornarCotaDoMesUseCase;

    @Test
    void dadoEstorno_quandoPortaChamada_entaoDelegaAoUseCaseEDevolveOResultado() {
        when(estornarCotaDoMesUseCase.executar(PRESTADOR_ID, QUANDO)).thenReturn(true);
        ConsumoCotaPrestadorService service =
                new ConsumoCotaPrestadorService(consumirCotaDoMesUseCase, estornarCotaDoMesUseCase);

        assertThat(service.estornarEscolha(PRESTADOR_ID, QUANDO)).isTrue();

        verify(estornarCotaDoMesUseCase).executar(PRESTADOR_ID, QUANDO);
    }

    @Test
    void portaDeEstornoExigeTransacaoDoChamador() throws NoSuchMethodException {
        Method estornar =
                ConsumoCotaPrestadorService.class.getMethod("estornarEscolha", UUID.class, Instant.class);

        assertThat(estornar.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}

package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.limio.api.comum.midia.ArmazenamentoService;
import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.midia.Midia;
import com.limio.api.comum.midia.MidiaService;
import com.limio.api.modulos.perfil.actions.usecase.ConsultarFotoPerfilUseCase;

@ExtendWith(MockitoExtension.class)
class ConsultarFotoPerfilUseCaseTest {

    private static final UUID USUARIO_ID = UUID.randomUUID();

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    private ConsultarFotoPerfilUseCase useCase;

    @BeforeEach
    void montarUseCase() {
        useCase = new ConsultarFotoPerfilUseCase(midiaService, armazenamentoService);
    }

    @Test
    void dadoFotoAtiva_quandoConsulta_entaoDevolveLinkTemporarioDaChaveGuardada() {
        Midia foto = Midia.fotoPerfil(USUARIO_ID, "fotos-perfil/atual.jpg", "image/jpeg", 10, 10, 100);
        LinkTemporario link = new LinkTemporario("https://bucket/assinado", Instant.now());
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.of(foto));
        when(armazenamentoService.gerarLink("fotos-perfil/atual.jpg")).thenReturn(link);

        assertThat(useCase.executar(USUARIO_ID)).contains(link);
    }

    @Test
    void dadoUsuarioSemFoto_quandoConsulta_entaoVazioSemGerarLink() {
        when(midiaService.buscarFotoPerfilAtiva(USUARIO_ID)).thenReturn(Optional.empty());

        assertThat(useCase.executar(USUARIO_ID)).isEmpty();
        verifyNoInteractions(armazenamentoService);
    }
}

package com.limio.api.modulos.perfil.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import com.limio.api.comum.midia.excecao.ArmazenamentoIndisponivelException;
import com.limio.api.modulos.perfil.actions.service.ConsultaFotoPerfilService;

@ExtendWith(MockitoExtension.class)
class ConsultaFotoPerfilServiceTest {

    private static final UUID COM_FOTO = UUID.randomUUID();
    private static final UUID SEM_FOTO = UUID.randomUUID();
    private static final UUID STORAGE_FALHA = UUID.randomUUID();

    @Mock
    private MidiaService midiaService;

    @Mock
    private ArmazenamentoService armazenamentoService;

    private ConsultaFotoPerfilService service;

    @BeforeEach
    void montarService() {
        service = new ConsultaFotoPerfilService(midiaService, armazenamentoService);
    }

    private static Midia foto(UUID usuarioId, String chave) {
        return Midia.fotoPerfil(usuarioId, chave, "image/jpeg", 10, 10, 100);
    }

    @Test
    void dadoVariosUsuarios_quandoBuscaFotosEmLote_entaoUmaConsultaDeMidiaEOLinkSoDeQuemTemFoto() {
        Instant expiraEm = Instant.now();
        List<UUID> ids = List.of(COM_FOTO, SEM_FOTO);
        when(midiaService.buscarFotosPerfilAtivas(ids)).thenReturn(Map.of(COM_FOTO, foto(COM_FOTO, "fotos-perfil/a.jpg")));
        when(armazenamentoService.gerarLink("fotos-perfil/a.jpg"))
                .thenReturn(new LinkTemporario("https://bucket/a", expiraEm));

        Map<UUID, LinkTemporario> fotos = service.buscarFotosEmLote(ids);

        assertThat(fotos).containsOnlyKeys(COM_FOTO).containsEntry(COM_FOTO, new LinkTemporario("https://bucket/a", expiraEm));
        verify(midiaService).buscarFotosPerfilAtivas(ids);
    }

    @Test
    void dadoStorageIndisponivelParaUm_quandoBuscaFotosEmLote_entaoSoEsseFicaSemFoto() {
        Instant expiraEm = Instant.now();
        List<UUID> ids = List.of(COM_FOTO, STORAGE_FALHA);
        when(midiaService.buscarFotosPerfilAtivas(ids)).thenReturn(Map.of(
                COM_FOTO, foto(COM_FOTO, "fotos-perfil/a.jpg"),
                STORAGE_FALHA, foto(STORAGE_FALHA, "fotos-perfil/b.jpg")));
        when(armazenamentoService.gerarLink("fotos-perfil/a.jpg"))
                .thenReturn(new LinkTemporario("https://bucket/a", expiraEm));
        when(armazenamentoService.gerarLink("fotos-perfil/b.jpg")).thenThrow(new ArmazenamentoIndisponivelException());

        assertThat(service.buscarFotosEmLote(ids)).containsOnlyKeys(COM_FOTO);
    }

    @Test
    void dadaListaVazia_quandoBuscaFotosEmLote_entaoMapaVazioSemConsultar() {
        assertThat(service.buscarFotosEmLote(List.of())).isEmpty();
        verifyNoInteractions(midiaService, armazenamentoService);
    }

    @Test
    void dadoNinguemComFoto_quandoBuscaFotosEmLote_entaoMapaVazioSemGerarLink() {
        when(midiaService.buscarFotosPerfilAtivas(anyCollection())).thenReturn(Map.of());

        assertThat(service.buscarFotosEmLote(List.of(SEM_FOTO))).isEmpty();
        verifyNoInteractions(armazenamentoService);
    }
}

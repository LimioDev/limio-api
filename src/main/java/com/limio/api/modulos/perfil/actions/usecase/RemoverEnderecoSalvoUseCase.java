package com.limio.api.modulos.perfil.actions.usecase;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.modulos.perfil.empregador.PerfilEmpregador;
import com.limio.api.modulos.perfil.empregador.PerfilEmpregadorService;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvo;
import com.limio.api.modulos.perfil.endereco.EnderecoSalvoService;

@Service
public class RemoverEnderecoSalvoUseCase {

    private final PerfilEmpregadorService perfilEmpregadorService;
    private final EnderecoSalvoService enderecoSalvoService;

    public RemoverEnderecoSalvoUseCase(PerfilEmpregadorService perfilEmpregadorService,
            EnderecoSalvoService enderecoSalvoService) {
        this.perfilEmpregadorService = perfilEmpregadorService;
        this.enderecoSalvoService = enderecoSalvoService;
    }

    @Transactional
    public void executar(UUID usuarioId, UUID enderecoId) {
        PerfilEmpregador perfil = perfilEmpregadorService.garantirPerfilParaAtualizar(usuarioId);
        EnderecoSalvo endereco = enderecoSalvoService.buscarEnderecoAtivoOuFalhar(enderecoId, perfil.getId());
        // TODO(ENDERECO_EM_USO): o card pede recusar a remoção (409) de endereço usado em anúncio não terminal.
        // Hoje não dá: o Anuncio (V13) guarda só o snapshot do endereço, sem endereco_salvo_id, então não há como
        // saber quem usa este endereço. Fazer quando o anuncio referenciar o endereço salvo: uma porta em
        // comum/portas (padrão de ConsultaDadosUsuario) consultada aqui, antes de remover(). Ver TICKET-0050.
        endereco.remover(Instant.now());
        enderecoSalvoService.salvar(endereco);
    }
}

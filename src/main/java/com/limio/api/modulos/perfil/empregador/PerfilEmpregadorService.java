package com.limio.api.modulos.perfil.empregador;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.limio.api.comum.base.BaseService;
import com.limio.api.comum.portas.ConsultaDadosUsuario;
import com.limio.api.comum.portas.DadosUsuario;
import com.limio.api.modulos.perfil.excecao.PerfilEmpregadorNaoEncontradoException;

@Component
public class PerfilEmpregadorService extends BaseService<PerfilEmpregador, UUID, PerfilEmpregadorRepository> {

    private final ConsultaDadosUsuario consultaDadosUsuario;

    public PerfilEmpregadorService(PerfilEmpregadorRepository repository,
            ConsultaDadosUsuario consultaDadosUsuario) {
        super(repository);
        this.consultaDadosUsuario = consultaDadosUsuario;
    }

    public MeuPerfilEmpregador garantirPerfil(UUID usuarioId) {
        DadosUsuario dados = consultaDadosUsuario.buscarPorId(usuarioId)
                .orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
        PerfilEmpregador perfil = buscarOuCriar(usuarioId,
                PerfilEmpregador.nomeExibicaoInicialDe(dados.nomeCompleto()));
        return new MeuPerfilEmpregador(perfil, dados);
    }

    public PerfilEmpregador garantirPerfilParaAtualizar(UUID usuarioId) {
        PerfilEmpregador perfil = garantirPerfil(usuarioId).perfil();
        return repository.findByIdParaAtualizar(perfil.getId())
                .orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
    }

    public PerfilEmpregador buscarPerfilExistente(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId).orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
    }

    public PerfilEmpregador travarPerfilExistente(UUID perfilId) {
        return repository.findByIdParaAtualizar(perfilId).orElseThrow(PerfilEmpregadorNaoEncontradoException::new);
    }

    /** Sem criar: vazio é o usuário que nunca abriu o perfil de empregador. */
    public Optional<PerfilEmpregador> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    /**
     * Nome de exibição sem criar o perfil: quem nunca abriu o perfil de Empregador aparece com o nome inicial que
     * ganharia ao abrir. Vazio se a conta não existe ou foi encerrada.
     */
    public Optional<String> nomeExibicaoSemCriar(UUID usuarioId) {
        return consultaDadosUsuario.buscarPorId(usuarioId)
                .map(dados -> repository.findByUsuarioId(usuarioId)
                        .map(PerfilEmpregador::getNomeExibicao)
                        .orElseGet(() -> PerfilEmpregador.nomeExibicaoInicialDe(dados.nomeCompleto())));
    }

    /**
     * Perfis de vários usuários numa consulta, sem criar nada. Quem nunca abriu o perfil volta com um
     * {@link PerfilEmpregador} transitório (nome inicial, sem avaliações, não persistido); conta
     * inexistente ou encerrada fica de fora do mapa.
     */
    public Map<UUID, PerfilEmpregador> buscarPerfisOuIniciais(Collection<UUID> usuarioIds) {
        Map<UUID, PerfilEmpregador> perfis = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return perfis;
        }
        repository.findByUsuarioIdIn(usuarioIds).forEach(perfil -> perfis.put(perfil.getUsuarioId(), perfil));
        usuarioIds.stream().distinct().filter(id -> !perfis.containsKey(id)).forEach(id ->
                consultaDadosUsuario.buscarPorId(id).ifPresent(dados -> perfis.put(id, PerfilEmpregador.builder()
                        .usuarioId(id)
                        .nomeExibicao(PerfilEmpregador.nomeExibicaoInicialDe(dados.nomeCompleto()))
                        .build())));
        return perfis;
    }

    private PerfilEmpregador buscarOuCriar(UUID usuarioId, String nomeInicial) {
        return repository.findByUsuarioId(usuarioId).orElseGet(() -> {
            repository.inserirSeAusente(usuarioId, nomeInicial);
            return repository.findByUsuarioId(usuarioId).orElseThrow();
        });
    }
}

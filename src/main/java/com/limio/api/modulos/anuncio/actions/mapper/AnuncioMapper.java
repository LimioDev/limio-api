package com.limio.api.modulos.anuncio.actions.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.limio.api.comum.midia.LinkTemporario;
import com.limio.api.comum.portas.EnderecoInformado;
import com.limio.api.modulos.anuncio.Anuncio;
import com.limio.api.modulos.anuncio.CandidatoResumido;
import com.limio.api.modulos.anuncio.EnderecoDoAnuncio;
import com.limio.api.modulos.anuncio.FotoDoAnuncio;
import com.limio.api.modulos.anuncio.MeuAnuncio;
import com.limio.api.modulos.anuncio.records.CandidatoResumidoResponse;
import com.limio.api.modulos.anuncio.records.CategoriaAnuncioResponse;
import com.limio.api.modulos.anuncio.records.CriarAnuncioRequest;
import com.limio.api.modulos.anuncio.records.CriarAnuncioResponse;
import com.limio.api.modulos.anuncio.records.EnderecoAvulsoRequest;
import com.limio.api.modulos.anuncio.records.FotoAnuncioResponse;
import com.limio.api.modulos.anuncio.records.MeuAnuncioResponse;

@Component
public class AnuncioMapper {

    /** Só o que vem da tela; dono, status, retrato do endereço e prazo são do {@code PublicarAnuncioUseCase}. */
    public Anuncio toEntity(CriarAnuncioRequest request) {
        return Anuncio.builder()
                .categoriaId(request.categoriaId())
                .descricao(request.descricao())
                .dataHoraPretendida(request.dataHoraPretendida())
                .tamanhoLocal(request.tamanhoLocal())
                .numeroPessoas(request.numeroPessoas())
                .observacoes(request.observacoes())
                .build();
    }

    /** O request já garante que veio exatamente um dos dois. */
    public EnderecoDoAnuncio toEndereco(CriarAnuncioRequest request) {
        if (request.enderecoSalvoId() != null) {
            return new EnderecoDoAnuncio.Salvo(request.enderecoSalvoId());
        }
        EnderecoAvulsoRequest avulso = request.enderecoAvulso();
        return new EnderecoDoAnuncio.Avulso(new EnderecoInformado(avulso.cep(), avulso.rua(), avulso.numero(),
                avulso.complemento(), avulso.bairroRegiao(), avulso.cidade(), avulso.uf()));
    }

    public CriarAnuncioResponse toResponse(Anuncio anuncio) {
        return new CriarAnuncioResponse(anuncio.getId(), anuncio.getStatus());
    }

    public List<FotoAnuncioResponse> toResponse(List<FotoDoAnuncio> fotos) {
        return fotos.stream().map(AnuncioMapper::toResponse).toList();
    }

    public MeuAnuncioResponse toResponse(MeuAnuncio meuAnuncio) {
        Anuncio anuncio = meuAnuncio.anuncio();
        return new MeuAnuncioResponse(
                anuncio.getId(),
                anuncio.getStatus(),
                anuncio.getDescricao(),
                new CategoriaAnuncioResponse(meuAnuncio.categoria().id(), meuAnuncio.categoria().nome(),
                        meuAnuncio.categoria().grupo()),
                anuncio.getDataHoraPretendida(),
                anuncio.getBairroRegiaoSnapshot(),
                anuncio.getUfSnapshot(),
                anuncio.getEnderecoCompletoSnapshot(),
                anuncio.getTamanhoLocal(),
                anuncio.getNumeroPessoas(),
                anuncio.getObservacoes(),
                meuAnuncio.fotos().stream().map(AnuncioMapper::toResponse).toList(),
                meuAnuncio.candidatos(),
                meuAnuncio.primeirosCandidatos().stream().map(AnuncioMapper::toResponse).toList());
    }

    private static FotoAnuncioResponse toResponse(FotoDoAnuncio foto) {
        LinkTemporario link = foto.link();
        return new FotoAnuncioResponse(foto.id(), link == null ? null : link.url(),
                link == null ? null : link.expiraEm());
    }

    private static CandidatoResumidoResponse toResponse(CandidatoResumido candidato) {
        LinkTemporario foto = candidato.foto();
        return new CandidatoResumidoResponse(candidato.iniciais(), foto == null ? null : foto.url(),
                foto == null ? null : foto.expiraEm());
    }
}

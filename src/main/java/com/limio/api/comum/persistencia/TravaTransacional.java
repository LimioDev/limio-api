package com.limio.api.comum.persistencia;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.limio.api.comum.persistencia.enums.EscopoTrava;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Advisory lock do Postgres ({@code pg_advisory_xact_lock}) pra serializar, por chave, operações que
 * leem-e-gravam sem linha pra travar ainda (ex.: "cria se não existe" num toque duplo). O lock é solto
 * no fim da transação de quem chama — por isso {@code MANDATORY}: fora de transação ele seria solto na
 * hora e não protegeria nada.
 *
 * Forma de duas chaves: {@code hashtext(escopo)} separa os {@link EscopoTrava}, {@code hashtext(chave)}
 * separa os valores dentro do escopo. {@code hashtext} é 32 bits: duas chaves do mesmo escopo podem
 * colidir e serializar operações sem relação. Risco aceito — se surgir lentidão esporádica, olhar aqui.
 */
@Component
public class TravaTransacional {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void travar(EscopoTrava escopo, String chave) {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtext(:escopo), hashtext(:chave))")
                .setParameter("escopo", escopo.name())
                .setParameter("chave", chave)
                .getSingleResult();
    }
}

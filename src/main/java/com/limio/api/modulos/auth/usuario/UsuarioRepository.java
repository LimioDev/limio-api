package com.limio.api.modulos.auth.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.limio.api.comum.base.BaseRepository;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends BaseRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Só o id: quem vai travar a conta não pode deixá-la carregada (e possivelmente desatualizada) antes do lock. */
    @Query("select u.id from Usuario u where upper(u.email) = upper(:email)")
    Optional<UUID> findIdByEmailIgnoreCase(@Param("email") String email);

    boolean existsByCpf(String cpf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> findByIdParaAtualizar(@Param("id") UUID id);
}

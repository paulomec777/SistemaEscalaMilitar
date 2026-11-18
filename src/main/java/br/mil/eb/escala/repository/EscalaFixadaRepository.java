package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.EscalaFixada;
import br.mil.eb.escala.model.PapelEscala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EscalaFixadaRepository extends JpaRepository<EscalaFixada, Long> {

    List<EscalaFixada> findByDataBetween(LocalDate inicio, LocalDate fim);

    Optional<EscalaFixada> findByDataAndPapel(LocalDate data, PapelEscala papel);

    List<EscalaFixada> findByMilitarId(Long militarId);
}

package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Missao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MissaoRepository extends JpaRepository<Missao, Long> {

    List<Missao> findByDataBetweenOrderByDataAscHorarioAsc(LocalDate inicio, LocalDate fim);

    List<Missao> findByDataOrderByHorarioAsc(LocalDate data);

    List<Missao> findByUltimaHoraTrueAndDataGreaterThanEqualOrderByDataAscHorarioAsc(LocalDate data);

    List<Missao> findByDataGreaterThanEqualOrderByDataAscHorarioAsc(LocalDate data);

    List<Missao> findTop10ByDataLessThanOrderByDataDescHorarioDesc(LocalDate data);
}

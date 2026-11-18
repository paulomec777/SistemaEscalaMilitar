package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AvisoRepository extends JpaRepository<Aviso, Long> {

    /** Avisos que cruzam o período [inicio, fim]. */
    List<Aviso> findByDataFimGreaterThanEqualAndDataInicioLessThanEqualOrderByDataInicioAsc(LocalDate inicio, LocalDate fim);

    List<Aviso> findAllByOrderByDataInicioDesc();
}

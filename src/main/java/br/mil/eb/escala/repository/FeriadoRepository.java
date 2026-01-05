package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {
    
    // Verifica se existe feriado na data
    boolean existsByData(LocalDate data);
    
    // Busca todos ordenados por data
    List<Feriado> findAllByOrderByDataAsc();
}
package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Militar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MilitarRepository extends JpaRepository<Militar, Long> {

    List<Militar> findByAtivoNaEscalaTrueAndEmServicoExternoFalseOrderByFolgaDesc();
    
    List<Militar> findByAtivoNaEscalaTrue();
    
    List<Militar> findAllByOrderByAtivoNaEscalaDescFolgaDesc();
}
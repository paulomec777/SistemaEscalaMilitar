package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Troca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TrocaRepository extends JpaRepository<Troca, Long> {

    // Trocas que estão valendo nesse dia (a mais recente primeiro)
    List<Troca> findByDataAndDesfeitaFalseOrderByCriadoEmDesc(LocalDate data);

    // Histórico para a tela do ADM
    List<Troca> findTop20ByOrderByCriadoEmDesc();

    List<Troca> findBySaiIdOrEntraId(Long saiId, Long entraId);
}

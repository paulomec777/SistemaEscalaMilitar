package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Militar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilitarRepository extends JpaRepository<Militar, Long> {

    // Lista para o Dashboard (Todos, ordenados por status e folga)
    List<Militar> findAllByOrderByAtivoNaEscalaDescFolgaDesc();

    // Lista para a Escala (Só os aptos)
    List<Militar> findByAtivoNaEscalaTrue();

    // --- O MÉTODO MÁGICO DO NOVO SERVICE ---
    // Busca ativos, que não estão em missão, ordena quem tem MAIS folga primeiro.
    // Se a folga for igual, desempatar por quem tirou serviço há mais tempo (Data Ascendente).
    List<Militar> findByAtivoNaEscalaTrueAndEmServicoExternoFalseOrderByFolgaDescDataUltimoServicoAsc();
}
package br.mil.eb.escala.repository;

import br.mil.eb.escala.model.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FeriadoRepository extends JpaRepository<Feriado, Long> {
    
    // 1. O coração do sistema: verifica se a data é feriado para o "pulo" automático
    boolean existsByData(LocalDate data);
    
    // 2. Busca um feriado específico pela data (útil para edição ou exclusão)
    Optional<Feriado> findByData(LocalDate data);
    
    // 3. Organiza a lista de feriados para o ADM ver do mais próximo ao mais distante
    List<Feriado> findAllByOrderByDataAsc();

    // 4. NOVO: Permite ao ADM pesquisar feriados pelo nome (Ex: "Bandeira")
    List<Feriado> findByDescricaoContainingIgnoreCase(String descricao);
}
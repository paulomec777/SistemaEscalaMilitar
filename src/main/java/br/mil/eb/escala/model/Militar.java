package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "militares")
@Data
@NoArgsConstructor
public class Militar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeGuerra;
    
    @Column(nullable = false)
    private String graduacao; 

    private int folga; 
    private LocalDate dataUltimoServico;

    // NOVO: Agendamento do Serviço Externo
    private LocalDate dataInicioServicoExterno; 
    private LocalDate dataFimServicoExterno; 

    @Column(columnDefinition = "boolean default true")
    private boolean ativoNaEscala; 

    private LocalDate dataInicioAfastamento;
    private LocalDate dataFimAfastamento;
    
    @Enumerated(EnumType.STRING)
    private MotivoInatividade motivoAfastamento;
    
    public boolean estaAptoParaServico(LocalDate dataServico) {
        if (!ativoNaEscala) return false;
        
        // 1. Afastamento normal (Férias, Núpcias, etc)
        if (dataInicioAfastamento != null && dataFimAfastamento != null) {
            if (!dataServico.isBefore(dataInicioAfastamento) && !dataServico.isAfter(dataFimAfastamento)) {
                return false;
            }
        }

        // REGRAS DO SERVIÇO EXTERNO
        if (dataInicioServicoExterno != null && dataFimServicoExterno != null) {
            
            // 2. Durante o Serviço Externo (A partir da data de início até o fim)
            if (!dataServico.isBefore(dataInicioServicoExterno) && !dataServico.isAfter(dataFimServicoExterno)) {
                return false; // Está viajando/em missão
            }

            // 3. Regra das 48h de Descanso (Garante 2 dias inteiros de folga após a missão)
            LocalDate dataLiberacao = dataFimServicoExterno.plusDays(3);
            if (dataServico.isAfter(dataFimServicoExterno) && dataServico.isBefore(dataLiberacao)) {
                return false; // Está nas 48h de descanso obrigatório
            }            
            
        }
        
        return true;
    }

    // Método auxiliar para o Dashboard (HTML) não quebrar ao procurar o status antigo
    public boolean isEmServicoExterno() {
        return this.dataInicioServicoExterno != null;
    }
}
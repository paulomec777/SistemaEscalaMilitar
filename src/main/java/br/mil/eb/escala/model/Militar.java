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

    // Agendamento do Serviço Externo
    private LocalDate dataInicioServicoExterno; 
    private LocalDate dataFimServicoExterno; 

    // Inicialização direta no objeto garante que não venha false por engano
    @Column(columnDefinition = "boolean default true")
    private boolean ativoNaEscala = true; 

    // Controle manual do ADM para impedir o militar
    @Column(columnDefinition = "boolean default true")
    private boolean disponivelManualmente = true;

    // Campo para prescrever o porquê do impedimento
    private String justificativaImpedimento;

    private LocalDate dataInicioAfastamento;
    private LocalDate dataFimAfastamento;
    
    @Enumerated(EnumType.STRING)
    private MotivoInatividade motivoAfastamento;
    
    /**
     * Lógica central de aptidão. 
     * O sistema verifica impedimentos automáticos e a decisão manual do ADM.
     */
    public boolean estaAptoParaServico(LocalDate dataServico) {
        // 1. Verificação básica de atividade e disponibilidade manual (ADM)
        if (!ativoNaEscala || !disponivelManualmente) {
            return false;
        }
        
        // 2. Afastamento normal (Férias, Núpcias, etc)
        if (dataInicioAfastamento != null && dataFimAfastamento != null) {
            if (!dataServico.isBefore(dataInicioAfastamento) && !dataServico.isAfter(dataFimAfastamento)) {
                return false;
            }
        }

        // 3. Regras do Serviço Externo
        if (dataInicioServicoExterno != null && dataFimServicoExterno != null) {
            
            // Durante o Serviço Externo
            if (!dataServico.isBefore(dataInicioServicoExterno) && !dataServico.isAfter(dataFimServicoExterno)) {
                return false; 
            }

            // Regra das 48h de Descanso (3 dias após para garantir 2 dias cheios)
            LocalDate dataLiberacao = dataFimServicoExterno.plusDays(3);
            if (dataServico.isAfter(dataFimServicoExterno) && dataServico.isBefore(dataLiberacao)) {
                return false; 
            }            
        }
        
        return true;
    }

    public boolean isEmServicoExterno() {
        return this.dataInicioServicoExterno != null;
    }

    // Método auxiliar para limpar impedimento manual quando o militar retornar
    public void liberarMilitar() {
        this.disponivelManualmente = true;
        this.justificativaImpedimento = null;
    }

    /**
     * NOVO: Método virtual para mastigar a regra de exibição para o Front-end (Thymeleaf).
     * Como o método chama "getStatus", o HTML pode acessar via "militar.status".
     */
    public String getStatus() {
        if (!ativoNaEscala || !disponivelManualmente) {
            return "AFASTADO";
        }
        if (isEmServicoExterno()) {
            return "EXTERNO";
        }
        return "APTO";
    }
}
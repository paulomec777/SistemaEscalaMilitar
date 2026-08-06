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
    
    // Mudado de Enum para String para aceitar texto livre digitado pelo ADM
    @Column(length = 255)
    private String motivoAfastamento;

    // NOVO CAMPO: Escudo temporário para quem entra de Serviço Pago
    @Column(name = "servico_pago_temporario")
    private boolean servicoPagoTemporario = false;
    
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

            // Regra das 48h: Dia do serviço + 1 dia de folga seguinte (dataFim + 1 dia)
            LocalDate dataLiberacao = dataFimServicoExterno.plusDays(1);
            if (dataServico.isEqual(dataLiberacao)) {
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

    public String getStatus() {
        if (!ativoNaEscala || !disponivelManualmente) {
            return "AFASTADO";
        }
        if (isEmServicoExterno()) {
            return "EXTERNO";
        }
        return "APTO";
    }

    // Getters e Setters do Escudo (Garantia extra de acesso para o motor da escala)
    public boolean isServicoPagoTemporario() {
        return servicoPagoTemporario;
    }

    public void setServicoPagoTemporario(boolean servicoPagoTemporario) {
        this.servicoPagoTemporario = servicoPagoTemporario;
    }
}

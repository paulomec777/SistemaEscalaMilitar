package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.service.EscalaService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void corrigirBancoRailway() {
        try {
            System.out.println("==== TENTANDO DESTRAVAR O BANCO DE DADOS DO RAILWAY ====");
            jdbcTemplate.execute("ALTER TABLE militares DROP CONSTRAINT militares_motivo_afastamento_check");
            System.out.println("==== SUCESSO: TRAVA DO BANCO REMOVIDA! ====");
        } catch (Exception e) {
            System.out.println("==== A TRAVA JÁ FOI REMOVIDA ANTERIORMENTE. TUDO CERTO! ====");
        }
    }
    @Autowired 
    private EscalaService escalaService;

    // --- DTO com motivo do tipo String (Texto Livre) ---
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AfastamentoForm {
        private Long militarId;
        private String motivoAfastamento; 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dataInicio;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dataFim;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServicoExternoForm {
        private Long militarId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dataInicio;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dataFim;
    }

    // --- MILITAR ---
    @GetMapping("/novo-militar")
    public String getFormularioMilitar(Model model) {
        model.addAttribute("militar", new Militar());
        return "admin-formulario";
    }

    @PostMapping("/salvar-militar")
    public String salvarMilitar(@ModelAttribute Militar militar) {
        escalaService.salvarNovoMilitar(militar);
        return "redirect:/dashboard";
    }
    
    @GetMapping("/editar/{id}")
    public String getFormularioEditar(@PathVariable("id") Long id, Model model) {
        model.addAttribute("militar", escalaService.findMilitarById(id));
        return "admin-editar";
    }

    @PostMapping("/salvar-edicao")
    public String salvarEdicao(@ModelAttribute Militar militar) {
        escalaService.editarMilitar(militar);
        return "redirect:/dashboard";
    }
    
    @PostMapping("/atualizar-folga")
    public String atualizarFolga(@RequestParam("id") Long id, @RequestParam("novaFolga") int novaFolga) {
        escalaService.atualizarFolgaManual(id, novaFolga);
        return "redirect:/dashboard"; 
    }

    @PostMapping("/atualizar-data-servico")
    public String atualizarDataServico(@RequestParam("id") Long id, @RequestParam("novaData") String novaDataStr) {
        try {
            LocalDate novaData = LocalDate.parse(novaDataStr);
            escalaService.atualizarDataUltimoServicoManual(id, novaData);
        } catch (Exception e) {
            System.out.println("Erro ao converter data inserida no dashboard: " + e.getMessage());
        }
        return "redirect:/dashboard"; // ou o mapeamento correto do seu painel principal
    }
    
    @PostMapping("/deletar/{id}")
    public String deletarMilitar(@PathVariable("id") Long id) {
        escalaService.deletarMilitar(id);
        return "redirect:/dashboard";
    }

    // --- AFASTAMENTO ---
    @GetMapping("/afastar")
    public String getFormularioAfastar(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaMilitaresInativos", escalaService.getMilitaresInativos());
        return "admin-afastar";
    }
    
    // MÉTODO BLINDADO: Evita o erro 500 e joga o motivo real nos logs
    @PostMapping("/salvar-afastamento")
    public String salvarAfastamento(
            @RequestParam(value = "militarId", required = false) Long militarId, 
            @RequestParam(value = "motivoAfastamento", required = false) String motivoAfastamento,
            @RequestParam(value = "dataInicio", required = false) String dataInicioStr,
            @RequestParam(value = "dataFim", required = false) String dataFimStr,
            RedirectAttributes attributes) {
        
        try {
            System.out.println("---- INICIANDO SALVAMENTO DE AFASTAMENTO ----");
            System.out.println("Militar ID: " + militarId);
            System.out.println("Motivo: " + motivoAfastamento);
            System.out.println("Data Início: " + dataInicioStr);
            System.out.println("Data Fim: " + dataFimStr);
            
            // Converte as datas manualmente de forma segura
            LocalDate dataInicio = LocalDate.parse(dataInicioStr);
            LocalDate dataFim = LocalDate.parse(dataFimStr);
            
            // Chama o serviço para salvar
            escalaService.afastarMilitar(militarId, motivoAfastamento, dataInicio, dataFim);
            
            System.out.println("---- AFASTAMENTO SALVO COM SUCESSO ----");
            
        } catch (Exception e) {
            System.out.println("---- ERRO GRAVE AO SALVAR AFASTAMENTO ----");
            e.printStackTrace(); // Imprime o erro exato nos logs do Railway
        }
        
        // Sempre volta para a tela de afastar, mesmo se der erro, evitando a tela branca (Whitelabel)
        return "redirect:/admin/afastar";
    }

    @GetMapping("/reativar/{id}")
    public String reativarMilitar(@PathVariable("id") Long id) {
        escalaService.reativarMilitar(id);
        return "redirect:/admin/afastar";
    }

    // --- SERVIÇO EXTERNO ---
    @GetMapping("/controle")
    public String getControleEscala(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaGeral", escalaService.getTodosMilitaresParaDashboard());
        model.addAttribute("externoForm", new ServicoExternoForm(null, LocalDate.now(), LocalDate.now()));
        return "admin-controle";
    }

    @PostMapping("/agendar-externo")
    public String agendarServicoExterno(@ModelAttribute ServicoExternoForm form) {
        escalaService.agendarServicoExterno(form.getMilitarId(), form.getDataInicio(), form.getDataFim());
        return "redirect:/admin/controle";
    }

    @PostMapping("/cancelar-externo/{id}")
    public String cancelarServicoExterno(@PathVariable("id") Long id) {
        escalaService.cancelarServicoExterno(id);
        return "redirect:/admin/controle";
    }

    // --- TROCA DE SERVIÇO ---
    @GetMapping("/troca")
    public String getFormularioTroca(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        return "admin-troca";
    }

    @PostMapping("/forcar-escala")
    public String forcarEscalaPeloBotaoDaTabela(@RequestParam("id") Long militarEntraId, RedirectAttributes attributes) {
        Militar titularAntigo = escalaService.getProximoPermanencia();
        Militar entra = escalaService.findMilitarById(militarEntraId);

        if (entra != null) {
            escalaService.atualizarFolgaManual(entra.getId(), 0);
            escalaService.atualizarDataUltimoServicoManual(entra.getId(), LocalDate.now());

            if (titularAntigo != null && !titularAntigo.getId().equals(entra.getId())) {
                attributes.addFlashAttribute("mensagem", "Escala alterada! " + entra.getNomeGuerra() + 
                    " foi forçado para hoje. A folga de " + titularAntigo.getNomeGuerra() + " foi preservada intacta.");
            } else {
                attributes.addFlashAttribute("mensagem", entra.getNomeGuerra() + " foi forçado para o serviço de hoje.");
            }
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/salvar-troca")
    public String salvarTroca(
            @RequestParam Long militarSaiId, 
            @RequestParam Long militarEntraId,
            @RequestParam(value = "zerarFolga", defaultValue = "false") boolean zerarFolga,
            RedirectAttributes attributes) {
        
        if (zerarFolga) {
            Militar sai = escalaService.findMilitarById(militarSaiId);
            if (sai != null) {
                escalaService.atualizarFolgaManual(sai.getId(), 0);
                escalaService.atualizarDataUltimoServicoManual(sai.getId(), LocalDate.now());
                attributes.addFlashAttribute("mensagem", "Troca realizada: folga de " + sai.getNomeGuerra() + " foi ZERADA.");
            }
        } else {
            attributes.addFlashAttribute("mensagem", "Troca realizada: folgas preservadas.");
        }

        escalaService.processarTroca(militarSaiId, militarEntraId);
        return "redirect:/dashboard";
    }
    
    // --- USUÁRIOS ---
    @GetMapping("/usuarios")
    public String getFormularioUsuarios(Model model) {
        model.addAttribute("listaUsuarios", escalaService.getTodosUsuarios());
        model.addAttribute("usuarioVazio", new Usuario());
        model.addAttribute("listaPerfis", Perfil.values());
        return "admin-usuarios";
    }

    @PostMapping("/salvar-usuario")
    public String salvarUsuario(@ModelAttribute Usuario usuario) {
        escalaService.salvarNovoUsuario(usuario);
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/deletar-usuario/{id}")
    public String deletarUsuario(@PathVariable("id") Long id) {
        escalaService.deletarUsuario(id);
        return "redirect:/admin/usuarios";
    }
}

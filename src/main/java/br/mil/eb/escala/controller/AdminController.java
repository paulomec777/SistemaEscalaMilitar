package br.mil.eb.escala.controller;

import br.mil.eb.escala.exception.RegraNegocioException;
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
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired 
    private EscalaService escalaService;

    //DTO com motivo do tipo String (Texto Livre)
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

    //MILITAR 
    @GetMapping("/novo-militar")
    public String getFormularioMilitar(Model model) {
        model.addAttribute("militar", new Militar());
        return "admin-formulario";
    }

    @PostMapping("/salvar-militar")
    public String salvarMilitar(@ModelAttribute Militar militar, RedirectAttributes attributes) {
        try {
            escalaService.salvarNovoMilitar(militar);
            attributes.addFlashAttribute("mensagem", militar.getGraduacao() + " " + militar.getNomeGuerra() + " cadastrado na escala.");
            return "redirect:/dashboard";
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/novo-militar";
        }
    }
    
    @GetMapping("/editar/{id}")
    public String getFormularioEditar(@PathVariable("id") Long id, Model model) {
        Militar militar = escalaService.findMilitarById(id);
        if (militar == null) return "redirect:/dashboard";
        model.addAttribute("militar", militar);
        return "admin-editar";
    }

    @PostMapping("/salvar-edicao")
    public String salvarEdicao(@ModelAttribute Militar militar, RedirectAttributes attributes) {
        try {
            escalaService.editarMilitar(militar);
            attributes.addFlashAttribute("mensagem", "Dados do militar atualizados.");
            return "redirect:/dashboard";
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/editar/" + militar.getId();
        }
    }
    
    @PostMapping("/atualizar-folga")
    public String atualizarFolga(@RequestParam("id") Long id, @RequestParam("novaFolga") int novaFolga) {
        escalaService.atualizarFolgaManual(id, novaFolga);
        return "redirect:/dashboard"; 
    }

    @PostMapping("/atualizar-data-servico")
    public String atualizarDataServico(@RequestParam("id") Long id, @RequestParam("novaData") String novaDataStr,
                                       RedirectAttributes attributes) {
        try {
            LocalDate novaData = LocalDate.parse(novaDataStr);
            escalaService.atualizarDataUltimoServicoManual(id, novaData);
            attributes.addFlashAttribute("mensagem", "Data do último serviço atualizada e folga recalculada.");
        } catch (DateTimeParseException e) {
            attributes.addFlashAttribute("mensagem", "Data inválida. Use o calendário para escolher a data.");
        }
        return "redirect:/dashboard"; 
    }
    
    @PostMapping("/deletar/{id}")
    public String deletarMilitar(@PathVariable("id") Long id, RedirectAttributes attributes) {
        escalaService.deletarMilitar(id);
        attributes.addFlashAttribute("mensagem", "Militar excluído do sistema.");
        return "redirect:/dashboard";
    }

    //AFASTAMENTO
    @GetMapping("/afastar")
    public String getFormularioAfastar(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaMilitaresInativos", escalaService.getMilitaresInativos());
        return "admin-afastar";
    }
    
    //Valida tudo no service e mostra o motivo do erro na própria tela
    @PostMapping("/salvar-afastamento")
    public String salvarAfastamento(
            @RequestParam(value = "militarId", required = false) Long militarId, 
            @RequestParam(value = "motivoAfastamento", required = false) String motivoAfastamento,
            @RequestParam(value = "dataInicio", required = false) String dataInicioStr,
            @RequestParam(value = "dataFim", required = false) String dataFimStr,
            RedirectAttributes attributes) {
        
        try {
            LocalDate dataInicio = (dataInicioStr == null || dataInicioStr.isBlank()) ? null : LocalDate.parse(dataInicioStr);
            LocalDate dataFim = (dataFimStr == null || dataFimStr.isBlank()) ? null : LocalDate.parse(dataFimStr);
            escalaService.afastarMilitar(militarId, motivoAfastamento, dataInicio, dataFim);
            attributes.addFlashAttribute("mensagem", "Afastamento registrado.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        } catch (DateTimeParseException e) {
            attributes.addFlashAttribute("erro", "Data inválida. Use o calendário para escolher as datas.");
        }
        
        return "redirect:/admin/afastar";
    }

    @PostMapping("/reativar/{id}")
    public String reativarMilitar(@PathVariable("id") Long id, RedirectAttributes attributes) {
        escalaService.reativarMilitar(id);
        attributes.addFlashAttribute("mensagem", "Militar reativado na escala.");
        return "redirect:/admin/afastar";
    }

    //SERVIÇO EXTERNO
    @GetMapping("/controle")
    public String getControleEscala(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaGeral", escalaService.getTodosMilitaresParaDashboard());
        model.addAttribute("externoForm", new ServicoExternoForm(null, LocalDate.now(), LocalDate.now()));
        return "admin-controle";
    }

    @PostMapping("/agendar-externo")
    public String agendarServicoExterno(@ModelAttribute ServicoExternoForm form, RedirectAttributes attributes) {
        try {
            escalaService.agendarServicoExterno(form.getMilitarId(), form.getDataInicio(), form.getDataFim());
            attributes.addFlashAttribute("mensagem", "Serviço externo lançado.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/controle";
    }

    @PostMapping("/cancelar-externo/{id}")
    public String cancelarServicoExterno(@PathVariable("id") Long id) {
        escalaService.cancelarServicoExterno(id);
        return "redirect:/admin/controle";
    }

    //TROCA DE SERVIÇO (ATUALIZADO PARA AS 3 REGRAS)
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
    //Blindado contra erros caso o HTML mude
   @PostMapping("/salvar-troca")
    public String salvarTroca(
            @RequestParam Long militarSaiId, 
            @RequestParam Long militarEntraId,
            @RequestParam String tipoTroca,
            @RequestParam(value = "funcaoAlvo", defaultValue = "TITULAR") String funcaoAlvo, 
            RedirectAttributes attributes) {
        
        // Executa a lógica de troca com base na regra e no posto selecionado
        try {
            escalaService.processarTroca(militarSaiId, militarEntraId, tipoTroca, funcaoAlvo);
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/admin/troca";
        }
        
        String mensagemRegra = "Troca registrada com sucesso! ";
        if ("PAGO".equals(tipoTroca)) {
            mensagemRegra += "Serviço Pago (Titular arcou com o turno).";
        } else if ("MISSAO".equals(tipoTroca)) {
            mensagemRegra += "Troca registrada por Missão/Necessidade do Serviço.";
        } else if ("PERMUTA".equals(tipoTroca)) {
            mensagemRegra += "Troca registrada por Permuta/Acordo (Folgas invertidas).";
        }

        attributes.addFlashAttribute("mensagem", mensagemRegra);
        return "redirect:/dashboard";
    }
    
    //USUÁRIOS
    @GetMapping("/usuarios")
    public String getFormularioUsuarios(Model model) {
        model.addAttribute("listaUsuarios", escalaService.getTodosUsuarios());
        model.addAttribute("usuarioVazio", new Usuario());
        model.addAttribute("listaPerfis", Perfil.values());
        return "admin-usuarios";
    }

    @PostMapping("/salvar-usuario")
    public String salvarUsuario(@ModelAttribute Usuario usuario, RedirectAttributes attributes) {
        try {
            escalaService.salvarNovoUsuario(usuario);
            attributes.addFlashAttribute("mensagem", "Usuário " + usuario.getLogin() + " criado.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/deletar-usuario/{id}")
    public String deletarUsuario(@PathVariable("id") Long id, Principal principal, RedirectAttributes attributes) {
        try {
            escalaService.deletarUsuario(id, principal.getName());
            attributes.addFlashAttribute("mensagem", "Usuário excluído.");
        } catch (RegraNegocioException e) {
            attributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }
}

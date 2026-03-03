package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.model.Militar;
import br.mil.eb.escala.model.MotivoInatividade;
import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.service.EscalaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired 
    private EscalaService escalaService;

    public record AfastamentoForm(Long militarId, MotivoInatividade motivo, LocalDate dataInicio, LocalDate dataFim) {}
    
    // NOVO DTO para agendar a Missão
    public record ServicoExternoForm(Long militarId, LocalDate dataInicio, LocalDate dataFim) {}

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
    
    @PostMapping("/deletar/{id}")
    public String deletarMilitar(@PathVariable("id") Long id) {
        escalaService.deletarMilitar(id);
        return "redirect:/dashboard";
    }

    // --- AFASTAMENTO ---
    @GetMapping("/afastar")
    public String getFormularioAfastar(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaMotivos", MotivoInatividade.values());
        model.addAttribute("listaMilitaresInativos", escalaService.getMilitaresInativos());
        model.addAttribute("afastamentoForm", new AfastamentoForm(null, null, LocalDate.now(), LocalDate.now())); 
        return "admin-afastar";
    }
    
    @PostMapping("/salvar-afastamento")
    public String salvarAfastamento(@ModelAttribute AfastamentoForm form) {
        escalaService.afastarMilitar(form.militarId(), form.motivo(), form.dataInicio(), form.dataFim());
        return "redirect:/dashboard";
    }

    @GetMapping("/reativar/{id}")
    public String reativarMilitar(@PathVariable("id") Long id) {
        escalaService.reativarMilitar(id);
        return "redirect:/admin/afastar";
    }

    // NOVO: Rota para atualizar a data do último serviço manualmente
    @PostMapping("/atualizar-data-servico")
    public String atualizarDataServico(@RequestParam("id") Long id, @RequestParam("novaData") LocalDate novaData) {
        escalaService.atualizarDataUltimoServicoManual(id, novaData);
        return "redirect:/dashboard";
    }

    // --- SERVIÇO EXTERNO (AGENDAMENTO NOVO) ---
    @GetMapping("/controle")
    public String getControleEscala(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        model.addAttribute("listaGeral", escalaService.getTodosMilitaresParaDashboard());
        model.addAttribute("externoForm", new ServicoExternoForm(null, LocalDate.now(), LocalDate.now()));
        return "admin-controle";
    }

    @PostMapping("/agendar-externo")
    public String agendarServicoExterno(@ModelAttribute ServicoExternoForm form) {
        escalaService.agendarServicoExterno(form.militarId(), form.dataInicio(), form.dataFim());
        return "redirect:/admin/controle";
    }

    @PostMapping("/cancelar-externo/{id}")
    public String cancelarServicoExterno(@PathVariable("id") Long id) {
        escalaService.cancelarServicoExterno(id);
        return "redirect:/admin/controle";
    }

    // --- TROCA, FERIADOS E USUÁRIOS ---
    @GetMapping("/troca")
    public String getFormularioTroca(Model model) {
        model.addAttribute("listaMilitaresAtivos", escalaService.getMilitaresAtivos());
        return "admin-troca";
    }

    @PostMapping("/salvar-troca")
    public String salvarTroca(@RequestParam Long militarSaiId, @RequestParam Long militarEntraId) {
        escalaService.processarTroca(militarSaiId, militarEntraId);
        return "redirect:/dashboard";
    }
    
    @GetMapping("/feriados")
    public String getFormularioFeriados(Model model) {
        model.addAttribute("listaFeriados", escalaService.getTodosFeriados());
        model.addAttribute("feriadoVazio", new Feriado());
        return "admin-feriados";
    }

    @PostMapping("/salvar-feriado")
    public String salvarFeriado(@ModelAttribute Feriado feriado) {
        escalaService.salvarFeriado(feriado);
        return "redirect:/admin/feriados";
    }

    @PostMapping("/deletar-feriado/{id}")
    public String deletarFeriado(@PathVariable("id") Long id) {
        escalaService.deletarFeriado(id);
        return "redirect:/admin/feriados";
    }
    
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
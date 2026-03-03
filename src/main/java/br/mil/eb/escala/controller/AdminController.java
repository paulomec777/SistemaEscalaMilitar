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

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired 
    private EscalaService escalaService;

    // --- RECORD (DTO) PARA AFASTAMENTO (JAVA 21) ---
    // Mantive o Record pois é nativo do Java e ajuda na organização
    public record AfastamentoForm(Long militarId, MotivoInatividade motivo, LocalDate dataInicio, LocalDate dataFim) {}

    // --- MILITAR (Adicionar, Editar, Deletar) ---
    @GetMapping("/novo-militar")
    public String getFormularioMilitar(Model model) {
        model.addAttribute("militar", new Militar());
        return "admin-formulario";
    }

    @PostMapping("/salvar-militar")
    public String salvarMilitar(@ModelAttribute Militar militar) {
        // Removido @Valid e BindingResult para não dar erro de importação
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
        // Removido validação
        escalaService.editarMilitar(militar);
        return "redirect:/dashboard";
    }
    
    // --- NOVA ROTA: ALTERAR FOLGA MANUALMENTE ---
    @PostMapping("/atualizar-folga")
    public String atualizarFolga(@RequestParam("id") Long id, @RequestParam("novaFolga") int novaFolga) {
        escalaService.atualizarFolgaManual(id, novaFolga);
        return "redirect:/dashboard"; // Retorna para o dashboard atualizado
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
        // Passando null nos campos iniciais do record apenas para inicializar
        model.addAttribute("afastamentoForm", new AfastamentoForm(null, null, LocalDate.now(), LocalDate.now())); 
        return "admin-afastar";
    }
    
    @PostMapping("/salvar-afastamento")
    public String salvarAfastamento(@ModelAttribute AfastamentoForm form) {
        escalaService.afastarMilitar(
            form.militarId(), 
            form.motivo(), 
            form.dataInicio(), 
            form.dataFim()
        );
        return "redirect:/dashboard";
    }

    @GetMapping("/reativar/{id}")
    public String reativarMilitar(@PathVariable("id") Long id) {
        escalaService.reativarMilitar(id);
        return "redirect:/admin/afastar";
    }

    // --- MOTOR DA ESCALA ---
    @PostMapping("/avancar-dia")
    public String avancarDia() {
        escalaService.avancarDiaDaEscala();
        return "redirect:/dashboard";
    }

    // --- CONTROLE EXTERNO ---
    @GetMapping("/controle")
    public String getControleEscala(Model model) {
        model.addAttribute("listaMilitares", escalaService.getTodosMilitaresParaDashboard());
        return "admin-controle";
    }

    @PostMapping("/marcar-externo/{id}")
    public String marcarServicoExterno(@PathVariable("id") Long id) {
        escalaService.marcarServicoExterno(id);
        return "redirect:/admin/controle";
    }

    @PostMapping("/limpar-externo/{id}")
    public String limparServicoExterno(@PathVariable("id") Long id) {
        escalaService.limparStatusExterno(id);
        return "redirect:/admin/controle";
    }

    // --- TROCA ---
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
    
    // --- FERIADOS ---
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
        // Removido validação
        escalaService.salvarNovoUsuario(usuario);
        return "redirect:/admin/usuarios";
    }

    @PostMapping("/deletar-usuario/{id}")
    public String deletarUsuario(@PathVariable("id") Long id) {
        escalaService.deletarUsuario(id);
        return "redirect:/admin/usuarios";
    }
}
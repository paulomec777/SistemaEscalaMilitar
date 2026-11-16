package br.mil.eb.escala.controller;

import br.mil.eb.escala.model.Feriado;
import br.mil.eb.escala.repository.FeriadoRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/feriados")
public class FeriadoController {

    @Autowired
    private FeriadoRepository feriadoRepository;

    //Lista todos os feriados cadastrados
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("feriados", feriadoRepository.findAllByOrderByDataAsc());
        
        //Objeto para o formulário de cadastro
        model.addAttribute("feriado", new Feriado()); 
        
        //CORRIGIDO: Agora aponta para o arquivo admin-feriados.html na pasta templates
        return "admin-feriados"; 
    }

    //Salva um novo feriado (Nacional ou do Exército que por vez podem mudar)
    @PostMapping("/salvar")
    public String salvar(@Valid Feriado feriado, BindingResult result, RedirectAttributes attributes) {
        if (result.hasErrors()) {
            //Mantém o usuário na mesma tela em caso de erro de validação
            return "admin-feriados"; 
        }

        //Verifica se a data já está cadastrada para evitar duplicidade
        if (feriadoRepository.existsByData(feriado.getData())) {
            attributes.addFlashAttribute("mensagemErro", "Este feriado já está cadastrado!");
            return "redirect:/admin/feriados";
        }

        feriadoRepository.save(feriado);
        attributes.addFlashAttribute("mensagem", "Feriado cadastrado com sucesso!");
        return "redirect:/admin/feriados";
    }

    //Remove um feriado (caso o expediente volte ao normal)
    @GetMapping("/excluir/{id}")
    public String excluir(@PathVariable("id") Long id, RedirectAttributes attributes) {
        feriadoRepository.deleteById(id);
        attributes.addFlashAttribute("mensagem", "Feriado removido com sucesso!");
        return "redirect:/admin/feriados";
    }
}

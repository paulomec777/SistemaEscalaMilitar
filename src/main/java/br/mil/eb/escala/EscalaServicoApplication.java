package br.mil.eb.escala;

import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct; // Importante para o fuso horário
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // Importante para o agendamento
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling // <--- 1. ISSO ATIVA O AGENDADOR AUTOMÁTICO
public class EscalaServicoApplication implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public static void main(String[] args) {
        SpringApplication.run(EscalaServicoApplication.class, args);
    }

    // <--- 2. ISSO GARANTE O HORÁRIO DE BRASÍLIA
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.out.println("--- Fuso horário definido para: " + TimeZone.getDefault().getID() + " ---");
    }

    @Override
    public void run(String... args) throws Exception {
        Optional<Usuario> admin = usuarioRepository.findByLogin("root");
        
        if (admin.isEmpty()) {
            System.out.println("--- CRIANDO USUÁRIO ADM (root) ---");
            Usuario novoAdmin = new Usuario();
            novoAdmin.setLogin("root");
            novoAdmin.setNomeCompleto("Administrador do Sistema");
            novoAdmin.setSenha(passwordEncoder.encode("root")); 
            novoAdmin.setPerfil(Perfil.ADM);
            usuarioRepository.save(novoAdmin);
            System.out.println("--- USUÁRIO ADM (root) CRIADO COM SUCESSO ---");
        } else {
            System.out.println("--- USUÁRIO ADM (root) JÁ EXISTE ---");
        }
    }
}
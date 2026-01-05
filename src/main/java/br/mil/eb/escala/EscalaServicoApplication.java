package br.mil.eb.escala;

import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@SpringBootApplication
public class EscalaServicoApplication implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public static void main(String[] args) {
        SpringApplication.run(EscalaServicoApplication.class, args);
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
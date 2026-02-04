package br.mil.eb.escala;

import br.mil.eb.escala.model.Perfil;
import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class EscalaServicoApplication implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    public static void main(String[] args) {
        // --- CONFIGURAÇÃO DE EMERGÊNCIA (HARDCODED) ---
        // Isso força o sistema a conectar no Neon mesmo se o Eclipse ignorar o arquivo.properties
        System.setProperty("spring.datasource.url", "jdbc:postgresql://ep-twilight-hill-ahxn609v-pooler.c-3.us-east-1.aws.neon.tech/neondb?user=neondb_owner&password=npg_JjX8OAhMFYV4&sslmode=require&channelBinding=require");
        System.setProperty("spring.datasource.driver-class-name", "org.postgresql.Driver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        System.setProperty("spring.jpa.show-sql", "true");
        System.setProperty("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        // ----------------------------------------------

        SpringApplication.run(EscalaServicoApplication.class, args);
    }

    // Garante horário de Brasília
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        System.out.println("--- Fuso horário definido para: " + TimeZone.getDefault().getID() + " ---");
    }

    // Cria o usuário Admin se não existir
    @Override
    public void run(String... args) throws Exception {
        Optional<Usuario> admin = usuarioRepository.findByLogin("root");
        
        if (admin.isEmpty()) {
            System.out.println("--- CRIANDO USUÁRIO ADM (root) ---");
            Usuario novoAdmin = new Usuario();
            novoAdmin.setLogin("root");
            novoAdmin.setNomeCompleto("Administrador do Sistema");
            novoAdmin.setSenha(passwordEncoder.encode("root")); 
            // ATENÇÃO: Se der erro no "Perfil.ADM", verifique se no seu Enum é ADM ou ADMIN
            novoAdmin.setPerfil(Perfil.ADM); 
            
            // Tentei colocar email null, mas se sua entidade exigir, coloque um email fictício:
            novoAdmin.setEmail("admin@escala.eb.mil.br"); 
            
            usuarioRepository.save(novoAdmin);
            System.out.println("--- USUÁRIO ADM (root) CRIADO COM SUCESSO ---");
        } else {
            System.out.println("--- USUÁRIO ADM (root) JÁ EXISTE ---");
        }
    }
}
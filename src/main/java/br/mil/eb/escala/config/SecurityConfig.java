package br.mil.eb.escala.config;

import br.mil.eb.escala.security.AutenticacaoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // CSS, JS, imagens e vídeos de fundo são públicos
                .requestMatchers("/css/**", "/js/**", "/img/**", "/Img/**", "/videos/**").permitAll()
                // Login é público
                .requestMatchers("/login").permitAll()
                // --- MUDANÇA AQUI ---
                // O Dashboard e a raiz são PÚBLICOS (qualquer um vê)
                .requestMatchers("/", "/dashboard", "/mapa").permitAll()
                // Página de erro precisa abrir para qualquer um (senão vira redirecionamento para o login)
                .requestMatchers("/error", "/favicon.ico").permitAll()
                // O Admin continua restrito
                .requestMatchers("/admin/**").hasRole("ADM")
                // Qualquer outra coisa requer autenticação
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/dashboard") // Ao sair, volta para o painel público
                .permitAll()
            );
            // CSRF fica LIGADO (padrão do Spring Security): os formulários com th:action
            // já enviam o token sozinhos, e isso impede que outro site dispare ações
            // de ADM usando a sessão de quem está logado na intranet.
            
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http, PasswordEncoder passwordEncoder, AutenticacaoService userDetailsService) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        return authBuilder.build();
    }
}
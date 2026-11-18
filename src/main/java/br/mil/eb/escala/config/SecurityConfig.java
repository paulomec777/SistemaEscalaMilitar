package br.mil.eb.escala.config;

import br.mil.eb.escala.security.AutenticacaoService;
import br.mil.eb.escala.security.LimiteDeTentativas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;
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

    // true = painel e mapa abertos sem login (bom na intranet).
    // false = só com login (use se o sistema ficar acessível pela internet). Variável APP_PAINEL_PUBLICO.
    @Value("${app.painel-publico:true}")
    private boolean painelPublico;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LimiteDeTentativas limiteDeTentativas) throws Exception {
        String[] paginasDoPainel = {"/", "/dashboard", "/mapa"};
        http
            .authorizeHttpRequests(authorize -> authorize
                // CSS, JS, imagens e vídeos de fundo são públicos
                .requestMatchers("/css/**", "/js/**", "/img/**", "/Img/**", "/videos/**").permitAll()
                // Login é público
                .requestMatchers("/login").permitAll()
                // Painel e mapa: públicos ou só com login, conforme app.painel-publico
                .requestMatchers(paginasDoPainel).access((auth, ctx) ->
                        new org.springframework.security.authorization.AuthorizationDecision(
                                painelPublico || (auth.get() != null && auth.get().isAuthenticated()
                                        && !(auth.get() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken))))
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
                .successHandler((request, response, authentication) -> {
                    limiteDeTentativas.registrarAcerto(authentication.getName());
                    response.sendRedirect(request.getContextPath() + "/dashboard");
                })
                .failureHandler((request, response, exception) -> {
                    String login = request.getParameter("username");
                    if (!(exception instanceof LockedException)) limiteDeTentativas.registrarErro(login);
                    boolean bloqueado = limiteDeTentativas.estaBloqueado(login);
                    response.sendRedirect(request.getContextPath() + (bloqueado ? "/login?bloqueado" : "/login?error"));
                })
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
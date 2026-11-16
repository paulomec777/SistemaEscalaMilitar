package br.mil.eb.escala.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "usuarios")
@Data
@NoArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String login;

    @Column(nullable = false)
    private String nomeCompleto;

    @Column(nullable = false)
    private String senha;
    
    // Agora o email também é único no banco
    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Garante que não dê erro de NullPointer se o perfil não estiver carregado
        if (this.perfil == null) return Collections.emptyList();
        // O Spring Security gosta que o perfil tenha o prefixo "ROLE_"
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.perfil.name()));
    }

    @Override public String getPassword() { return this.senha; }
    @Override public String getUsername() { return this.login; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
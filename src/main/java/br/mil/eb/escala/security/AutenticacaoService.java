package br.mil.eb.escala.security;

import br.mil.eb.escala.model.Usuario;
import br.mil.eb.escala.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Usuario> usuario = usuarioRepository.findByLogin(username);
        if (usuario.isEmpty()) {
            throw new UsernameNotFoundException("Usuário não encontrado!");
        }
        return usuario.get();
    }
}
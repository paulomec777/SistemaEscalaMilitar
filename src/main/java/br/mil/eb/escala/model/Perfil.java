package br.mil.eb.escala.model;

import org.springframework.security.core.GrantedAuthority;

public enum Perfil implements GrantedAuthority {
    ADM("ROLE_ADM"),
    MILITAR("ROLE_MILITAR");

    private String role;

    Perfil(String role) {
        this.role = role;
    }

    @Override
    public String getAuthority() {
        return this.role;
    }
}
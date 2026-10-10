package com.ruoyi.medical.identity;

import com.ruoyi.framework.security.context.AuthenticationContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

final class LocalAuthenticationProvider extends DaoAuthenticationProvider {
    LocalAuthenticationProvider(LocalAccountDirectory accounts, PasswordEncoder passwords) {
        super(accounts);
        setPasswordEncoder(passwords);
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String password) || !LocalAccountInputs.validPassword(password)) {
            throw new BadCredentialsException("AUTHENTICATION_FAILED");
        }
        AuthenticationContextHolder.setContext(authentication);
        try {
            return super.authenticate(authentication);
        } finally {
            AuthenticationContextHolder.clearContext();
        }
    }
}

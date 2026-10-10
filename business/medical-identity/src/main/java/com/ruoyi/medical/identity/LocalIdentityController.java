package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@ConditionalOnProperty(name = "medical.identity.mode", havingValue = "local")
public final class LocalIdentityController {
    private final LocalAccountDirectory accounts;
    public LocalIdentityController(LocalAccountDirectory accounts) { this.accounts = accounts; }

    @GetMapping("/")
    String home() { return "redirect:/auth/login"; }

    @GetMapping("/auth/login")
    String login() { return accounts.setupRequired() ? "redirect:/auth/setup" : "forward:/index.html"; }

    @GetMapping("/auth/setup")
    String setup() { return accounts.setupRequired() ? "forward:/local-auth/setup.html" : "redirect:/auth/login"; }

    @GetMapping("/auth/csrf")
    @ResponseBody
    ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("header_name", token.getHeaderName(), "parameter_name", token.getParameterName(), "token", token.getToken()));
    }

    @PostMapping("/auth/setup")
    String create(HttpServletRequest request, @RequestParam String username, @RequestParam String password,
            @RequestParam String display_name) {
        accounts.bootstrap(request, username, password, display_name);
        return "redirect:/auth/login";
    }

    @GetMapping("/auth/account")
    @ResponseBody
    ResponseEntity<LocalAccountDirectory.AccountView> account(Authentication authentication) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(accounts.view(authentication));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    void invalid(HttpServletResponse response) throws IOException {
        new IdentityErrorResponseWriter().write(response, HttpServletResponse.SC_BAD_REQUEST, ErrorCode.AUTHENTICATION_FAILED);
    }
}

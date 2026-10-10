package com.ruoyi.medical.identity;

import com.ruoyi.medical.contracts.WireValues;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public final class ApplicationSessionIdentity {
    private final IdentityMode mode;
    private final ObjectProvider<LocalAccountDirectory> local;
    private final ObjectProvider<PlatformIdentityDirectory> directory;
    private final ObjectProvider<PlatformOidcSessions> oidc;

    public ApplicationSessionIdentity(IdentityMode mode, ObjectProvider<LocalAccountDirectory> local,
            ObjectProvider<PlatformIdentityDirectory> directory, ObjectProvider<PlatformOidcSessions> oidc) {
        this.mode = mode; this.local = local; this.directory = directory; this.oidc = oidc;
    }

    public Verified requireCurrent(HttpServletRequest request, Authentication authentication) {
        BusinessSessionProtocol.User user;
        AuthenticatedIdentityBinding binding;
        if (mode.mode().equals("local")) {
            var accounts = local.getObject();
            accounts.requireSession(request, authentication);
            var account = accounts.view(authentication);
            user = new BusinessSessionProtocol.User(account.user_id(), account.username(), account.display_name(), account.platform_role());
            binding = AuthenticatedIdentityBinding.local(account.user_id());
        } else {
            oidc.getObject().requireCurrentSession(request);
            var account = directory.getObject().requireCurrentIdentity();
            var principal = (OidcUser) Objects.requireNonNull(authentication).getPrincipal();
            user = new BusinessSessionProtocol.User(account.userId(), null, principal.getFullName(), null);
            binding = AuthenticatedIdentityBinding.oidc(account.userId(), account.binding());
        }
        return new Verified(Objects.requireNonNull(request.getSession(false)), authentication, user, binding);
    }

    public void lockCurrent(Verified subject) {
        Objects.requireNonNull(subject);
        if (!subject.sessionId.equals(subject.session.getId())) throw new IllegalStateException("SESSION_IDENTITY_CHANGED");
        if (mode.mode().equals("local")) {
            var accounts = local.getObject();
            accounts.lockSession(subject.sessionId, subject.authentication);
            var current = accounts.view(subject.authentication);
            if (!subject.binding.matches(AuthenticatedIdentityBinding.local(current.user_id()))) {
                throw new IllegalStateException("SESSION_IDENTITY_CHANGED");
            }
        } else {
            var account = directory.getObject().lockCurrentIdentity();
            if (!subject.user.user_id().equals(account.userId())) throw new IllegalStateException("SESSION_IDENTITY_CHANGED");
            if (!subject.binding.matches(AuthenticatedIdentityBinding.oidc(account.userId(), account.binding()))) {
                throw new IllegalStateException("SESSION_IDENTITY_CHANGED");
            }
            oidc.getObject().lockCurrentSession(subject.session);
        }
    }

    public static final class Verified {
        private final String sessionId;
        private final BusinessSessionProtocol.User user;
        private final jakarta.servlet.http.HttpSession session;
        private final Authentication authentication;
        private final AuthenticatedIdentityBinding binding;
        private Verified(jakarta.servlet.http.HttpSession session, Authentication authentication, BusinessSessionProtocol.User user,
                AuthenticatedIdentityBinding binding) {
            this.session = Objects.requireNonNull(session);
            this.authentication = Objects.requireNonNull(authentication);
            this.sessionId = session.getId();
            this.user = Objects.requireNonNull(user);
            this.binding = Objects.requireNonNull(binding);
            WireValues.uuid(user.user_id());
            if (!user.user_id().equals(binding.view().user_id())) throw new IllegalStateException("SESSION_IDENTITY_CHANGED");
        }
        public String sessionId() { return sessionId; }
        public BusinessSessionProtocol.User user() { return user; }
        public AuthenticatedIdentityBinding binding() { return binding; }
        @Override public String toString() { return "VerifiedApplicationSession"; }
    }
}

package family.fisa.hangangpay.global.security;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.MERCHANT_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.USER_ID;

import family.fisa.hangangpay.domain.party.entity.PartyType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromSession(request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }

        PartyType role = resolveRole(session);
        if (role == null) {
            return;
        }

        Object principalId = session.getAttribute(resolvePrincipalIdKey(role));
        Object partyId = session.getAttribute(PARTY_ID);
        if (!(principalId instanceof Long) || !(partyId instanceof Long)) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principalId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        authentication.setDetails(Map.of(PARTY_ID, partyId, ROLE, role.name()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private PartyType resolveRole(HttpSession session) {
        Object role = session.getAttribute(ROLE);
        if (!(role instanceof String roleName)) {
            return null;
        }

        try {
            return PartyType.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolvePrincipalIdKey(PartyType role) {
        if (role == PartyType.MERCHANT) {
            return MERCHANT_ID;
        }
        return USER_ID;
    }
}

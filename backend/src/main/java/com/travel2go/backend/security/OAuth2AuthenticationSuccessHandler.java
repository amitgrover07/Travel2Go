package com.travel2go.backend.security;

import com.travel2go.backend.model.User;
import com.travel2go.backend.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        
        // Determine provider (simplified heuristic or from registrationId if passed)
        // Spring Security usually handles this via ClientRegistration, but for simplicity here we check attributes
        // Better approach: use OAuth2AuthorizedClientService, but this is a quick extraction
        String provider = oAuth2User.getAttributes().containsKey("sub") ? "GOOGLE" : "FACEBOOK";
        String providerId = oAuth2User.getAttributes().containsKey("sub") ? oAuth2User.getAttribute("sub") : oAuth2User.getAttribute("id");

        User user = userRepository.findByEmail(email).block();
        if (user != null) {
            if (!provider.equals(user.getProvider()) && "LOCAL".equals(user.getProvider())) {
                // Optionally handle linking accounts
            }
        } else {
            user = User.builder()
                    .email(email)
                    .provider(provider)
                    .providerId(providerId)
                    .roles(Set.of("USER")) // default role
                    .enabled(true)
                    .build();
            user = userRepository.save(user).block();
        }

        String role = user.getRoles().iterator().next();
        String token = jwtUtil.generateToken(user.getEmail(), role);

        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/oauth2/redirect")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}

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
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @org.springframework.beans.factory.annotation.Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        
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
            user.setName(name);
            user.setPicture(picture);
            // Leave roles as they are in the database
            user = userRepository.save(user).block();
        } else {
            user = User.builder()
                    .email(email)
                    .name(name)
                    .picture(picture)
                    .provider(provider)
                    .providerId(providerId)
                    .roles(List.of("USER")) // Grant USER role by default
                    .enabled(true)
                    .build();
            user = userRepository.save(user).block();
        }

        String role = user.getRoles().iterator().next();
        String token = jwtUtil.generateToken(user.getEmail(), role, user.getName(), user.getPicture());

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}

package com.codegym.jira.common.internal.config;

import com.codegym.jira.login.AuthUser;
import com.codegym.jira.login.Role;
import com.codegym.jira.login.internal.UserRepository;
import com.codegym.jira.login.internal.sociallogin.CustomOAuth2UserService;
import com.codegym.jira.login.internal.sociallogin.CustomTokenResponseConverter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@Slf4j
@AllArgsConstructor
//https://stackoverflow.com/questions/72493425/548473
public class SecurityConfig {
    public static final PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private final UserRepository userRepository;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PASSWORD_ENCODER;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return email -> {
            log.debug("Authenticating '{}'", email);
            return new AuthUser(userRepository.getExistedByEmail(email));
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/api/mngr/**").hasAnyRole(Role.ADMIN.name(), Role.MANAGER.name())
                        .requestMatchers(HttpMethod.POST, "/api/users").anonymous()
                        .requestMatchers("/api/**").authenticated()
                )
                .httpBasic(basic -> basic
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.NEVER)
                )
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/view/unauth/**", "/ui/register/**", "/ui/password/**").anonymous()
                        .requestMatchers("/", "/doc", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**", "/static/**").permitAll()
                        .requestMatchers("/ui/admin/**", "/view/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/ui/mngr/**").hasAnyRole(Role.ADMIN.name(), Role.MANAGER.name())
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/view/login")
                        .permitAll()
                        .defaultSuccessUrl("/", true)
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/view/login")
                        .defaultSuccessUrl("/", true)
                        .clientRegistrationRepository(createFilteredClientRegistrationRepository(clientRegistrationRepository))
                        .tokenEndpoint(token -> token
                                .accessTokenResponseClient(accessTokenResponseClient())
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/ui/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient accessTokenResponseClient =
                new DefaultAuthorizationCodeTokenResponseClient();
        OAuth2AccessTokenResponseHttpMessageConverter tokenResponseHttpMessageConverter =
                new OAuth2AccessTokenResponseHttpMessageConverter();
        tokenResponseHttpMessageConverter.setAccessTokenResponseConverter(new CustomTokenResponseConverter());
        RestTemplate restTemplate = new RestTemplate(Arrays.asList(
                new FormHttpMessageConverter(), tokenResponseHttpMessageConverter));
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        accessTokenResponseClient.setRestOperations(restTemplate);
        return accessTokenResponseClient;
    }

    /**
     * Método que crea un ClientRegistrationRepository filtrado excluyendo Facebook
     */
    private ClientRegistrationRepository createFilteredClientRegistrationRepository(
            ClientRegistrationRepository originalRepository) {

        if (!(originalRepository instanceof InMemoryClientRegistrationRepository)) {
            log.warn("ClientRegistrationRepository no es una instancia de InMemoryClientRegistrationRepository");
            return originalRepository;
        }

        InMemoryClientRegistrationRepository inMemoryRepo = (InMemoryClientRegistrationRepository) originalRepository;
        List<ClientRegistration> filteredRegistrations = new ArrayList<>();

        // Filtrar excluyendo Facebook
        for (ClientRegistration registration : inMemoryRepo) {
            String registrationId = registration.getRegistrationId();
            if (!"facebook".equals(registrationId)) {
                filteredRegistrations.add(registration);
                log.debug("Manteniendo proveedor OAuth2: {}", registrationId);
            } else {
                log.info("Deshabilitando proveedor OAuth2: {}", registrationId);
            }
        }

        log.info("Proveedores OAuth2 habilitados: {}",
                filteredRegistrations.stream()
                        .map(ClientRegistration::getRegistrationId)
                        .collect(Collectors.toList()));

        return new InMemoryClientRegistrationRepository(filteredRegistrations);
    }


}

package goojeans.harulog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import goojeans.harulog.filter.ExceptionHandlerFilter;
import goojeans.harulog.filter.JsonAuthenticationFilter;
import goojeans.harulog.filter.JwtAuthenticationFilter;
import goojeans.harulog.user.repository.UserRepository;
import goojeans.harulog.user.service.CustomOAuth2UserService;
import goojeans.harulog.user.service.LoginService;
import goojeans.harulog.user.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final LoginService loginService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;
    private final OAuthLoginFailureHandler oAuthLoginFailureHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final RabbitMQConfig rabbitMQConfig;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors((cors) ->
                        cors.configurationSource(corsConfigurationSource()))
                .headers((headers) ->
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .sessionManagement((sessionManagement) ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests((authorizeHttpRequests) ->
                        authorizeHttpRequests.requestMatchers("/api/admin")
                                .hasRole(UserRole.ADMIN.getRole())
                                .requestMatchers("/","/css/**","/images/**", "/js/**",
                                        "/index.html",
                                        "/**",
                                        "/favicon.ico", "/swagger-ui/**", "/api", "/api/login", "/api/sign-up", "/api/main")
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )
                .oauth2Login((oauth2Login) ->
                        oauth2Login.successHandler(oAuthLoginSuccessHandler)
                                .failureHandler(oAuthLoginFailureHandler)
                                .userInfoEndpoint((userInfoEndPoint) ->
                                        userInfoEndPoint.userService(customOAuth2UserService)
                                )
                );
        http.addFilterAfter(customJsonUsernamePasswordAuthenticationFilter(), LogoutFilter.class);
        http.addFilterBefore(jwtAuthenticationProcessingFilter(), JsonAuthenticationFilter.class);
        http.addFilterBefore(exceptionHandlerFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();

        // TODO: 운영 환경 배포시 strict 하게 설정
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addExposedHeader("*");
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfiguration.setAllowedOrigins(List.of(
                "https://kb38e49779611a.user-app.krampoline.com:3000",
                "https://kb38e49779611a.user-app.krampoline.com:3000/**",
                "https://kb38e49779611a.user-app.krampoline.com:5672",
                "https://kb38e49779611a.user-app.krampoline.com:15672",
                "https://kb38e49779611a.user-app.krampoline.com:61613",
                "https://kb38e49779611a.user-app.krampoline.com:8080",
                "http://rabbitmq.default.svc.cluster.local:61613",
                "http://rabbitmq.default.svc.cluster.local:5672",
                "http://rabbitmq.default.svc.cluster.local:15672",
                "http://frontend.default.svc.cluster.local:3000",
                "http://rabbitmq.default.svc.cluster.local:8080"
        ));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", corsConfiguration);

        return source;
    }


    @Bean
    public AuthenticationManager authenticationManager() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder());
        provider.setUserDetailsService(loginService);
        return new ProviderManager(provider);
    }

    @Bean
    public LoginSuccessHandler loginSuccessHandler() {
        return new LoginSuccessHandler(jwtTokenProvider, userRepository, loginService, rabbitMQConfig);
    }

    @Bean
    public LoginFailureHandler loginFailureHandler() {
        return new LoginFailureHandler();
    }


    @Bean
    public JsonAuthenticationFilter customJsonUsernamePasswordAuthenticationFilter() {
        JsonAuthenticationFilter jsonAuthenticationFilter
                = new JsonAuthenticationFilter(objectMapper);

        jsonAuthenticationFilter.setAuthenticationManager(authenticationManager());
        jsonAuthenticationFilter.setAuthenticationSuccessHandler(loginSuccessHandler());
        jsonAuthenticationFilter.setAuthenticationFailureHandler(loginFailureHandler());

        return jsonAuthenticationFilter;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationProcessingFilter() {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, loginService);
        return jwtAuthenticationFilter;
    }

    @Bean
    public ExceptionHandlerFilter exceptionHandlerFilter() {
        return new ExceptionHandlerFilter();
    }

}

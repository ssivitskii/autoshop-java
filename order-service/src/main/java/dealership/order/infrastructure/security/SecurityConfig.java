package dealership.order.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter) {
        this.jwtAuthConverter = jwtAuthConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/actuator/health"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/orders/stock").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/stock/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/orders/stock/**").hasAnyRole("USER", "MANAGER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/orders/custom").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/orders/custom/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/orders/custom/**").hasAnyRole("USER", "MANAGER", "WAREHOUSE_ADMIN", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/test-drives").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/test-drives/**").hasAnyRole("USER", "MANAGER", "ADMIN")

                        .requestMatchers("/api/v1/cars/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                );

        return http.build();
    }
}

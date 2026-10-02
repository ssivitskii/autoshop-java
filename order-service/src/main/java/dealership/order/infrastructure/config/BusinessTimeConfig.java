package dealership.order.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BusinessTimeConfig {
    @Bean
    Clock businessClock(@Value("${business.time-zone:Europe/Moscow}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}

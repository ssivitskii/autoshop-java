package dealership.order.core.application.port.out;

import java.math.BigDecimal;
import java.util.Map;

public interface CustomConfigurationGateway {
    ConfigurationQuote quote(String carModelId, Map<String, String> selectedVariants);

    record ConfigurationQuote(String carModelId,
                              Map<String, String> selectedVariants,
                              BigDecimal totalPrice) {
        public ConfigurationQuote {
            selectedVariants = Map.copyOf(selectedVariants);
        }
    }
}

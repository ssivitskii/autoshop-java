package dealership.order.core.application.port.out;

import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.UserRole;

import java.util.List;

public interface UserRepository {
    User save(User user);

    User findById(String id);

    List<User> findAll();

    List<User> findByRole(UserRole role);

    void deleteById(String id);
}

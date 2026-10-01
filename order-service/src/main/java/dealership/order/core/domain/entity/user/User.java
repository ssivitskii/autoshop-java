package dealership.order.core.domain.entity.user;

import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.DomainValidationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = "password")
public class User {
    @EqualsAndHashCode.Include
    private final String id;

    private String username;
    private String password;
    private String fullName;
    private String email;
    private String phone;
    private UserRole role;

    public User(String username, String password, String fullName, String email, String phone, UserRole role) {
        this(null, username, password, fullName, email, phone, role);
    }

    public User(String fullName, String email, UserRole role) {
        this(null, fullName, "password", fullName, email, null, role);
    }

    public User(String id, String username, String password, String fullName, String email, String phone, UserRole role) {
        if (fullName == null || fullName.isBlank()) {
            throw new DomainValidationException("Name is null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new DomainValidationException("Email is null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new DomainValidationException("Password is null or blank");
        }
        if (role == null) {
            throw new DomainValidationException("Role is null");
        }
        this.id = (id != null) ? id : UUID.randomUUID().toString();
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
    }
}
package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

    Optional<AuthUserEntity> findByUsernameIgnoreCase(String username);

    Optional<AuthUserEntity> findByEmailIgnoreCase(String email);

    Optional<AuthUserEntity> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);
}

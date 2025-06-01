package com.example.llmagentsystem.repository;

import com.example.llmagentsystem.model.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager; // Useful for managing entities
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test") // Ensure application-test.properties are used
class UserRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager; // Provides a JPA EntityManager for tests

    @Autowired
    private UserRepository userRepository;

    @Test
    void whenSaveUser_thenFindById_returnsUser() {
        UserEntity newUser = new UserEntity();
        newUser.setPlatformId("testUser123");
        newUser.setSourcePlatform("testPlatform");
        newUser.setDisplayName("Test User Display");
        newUser.setEmail("testuser@example.com");
        // UUID is auto-generated, no need to set userId explicitly before persist

        UserEntity savedUser = userRepository.save(newUser);
        entityManager.flush(); // Ensure changes are written to the DB
        entityManager.clear(); // Clear persistence context to ensure fresh load

        Optional<UserEntity> foundUserOpt = userRepository.findById(savedUser.getUserId());

        assertThat(foundUserOpt).isPresent();
        UserEntity foundUser = foundUserOpt.get();
        assertThat(foundUser.getPlatformId()).isEqualTo(newUser.getPlatformId());
        assertThat(foundUser.getEmail()).isEqualTo(newUser.getEmail());
        assertThat(foundUser.getUserId()).isNotNull(); // Ensure ID was generated
    }

    @Test
    void findByPlatformIdAndSourcePlatform_whenUserExists_returnsUser() {
        UserEntity user = new UserEntity();
        user.setPlatformId("platformUser1");
        user.setSourcePlatform("platformA");
        user.setDisplayName("Platform User A");
        entityManager.persistAndFlush(user);

        Optional<UserEntity> foundUserOpt = userRepository.findByPlatformIdAndSourcePlatform("platformUser1", "platformA");

        assertThat(foundUserOpt).isPresent();
        assertThat(foundUserOpt.get().getDisplayName()).isEqualTo("Platform User A");
    }

    @Test
    void findByPlatformIdAndSourcePlatform_whenUserNotExists_returnsEmpty() {
        Optional<UserEntity> foundUserOpt = userRepository.findByPlatformIdAndSourcePlatform("nonExistentUser", "platformX");
        assertThat(foundUserOpt).isEmpty();
    }

    @Test
    void findByEmail_whenUserExists_returnsUser() {
        UserEntity user = new UserEntity();
        user.setEmail("unique.email@example.com");
        user.setPlatformId("userWithEmail");
        user.setSourcePlatform("emailPlatform");
        entityManager.persistAndFlush(user);

        Optional<UserEntity> foundUserOpt = userRepository.findByEmail("unique.email@example.com");

        assertThat(foundUserOpt).isPresent();
        assertThat(foundUserOpt.get().getPlatformId()).isEqualTo("userWithEmail");
    }

    @Test
    void whenDeleteUser_thenFindById_returnsEmpty() {
        UserEntity user = new UserEntity();
        user.setPlatformId("userToDelete");
        user.setSourcePlatform("deletePlatform");
        UserEntity persistedUser = entityManager.persistFlushFind(user); // Persist, flush, and retrieve
        UUID userId = persistedUser.getUserId();

        userRepository.deleteById(userId);
        entityManager.flush();
        entityManager.clear();

        Optional<UserEntity> foundUserOpt = userRepository.findById(userId);
        assertThat(foundUserOpt).isEmpty();
    }
}

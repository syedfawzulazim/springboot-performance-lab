package com.sfazim.order;

import com.sfazim.order.domain.model.User;
import com.sfazim.order.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DatabasePerformanceApplication.class)// or your main config class
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @Rollback(false) // If you want to see actual inserts in DB
    void testCreateAndFindUser() {
        // Arrange
        User user = User.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        // Act
        userRepository.save(user);

        // Assert
        Optional<User> found = userRepository.findByEmail("test@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }
}

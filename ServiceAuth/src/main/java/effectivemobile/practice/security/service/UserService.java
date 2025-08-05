package effectivemobile.practice.security.service;

import effectivemobile.practice.model.db.User;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.repository.UserRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User create(User user) {
        Optional<User> existsUser = userRepository.findByEmail(user.getEmail());

        if (existsUser.isPresent()) {
            if (existsUser.get().isVerify()) {
                throw new EntityExistsException("User already exists with Email: " + user.getEmail());
            } else if (!existsUser.get().isVerify()) {
                throw new AccountNotVerified("Account has not been verified with Email: " + user.getEmail());
            }
        }

        return save(user);
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with Email: " + email));
    }

    public UserDetailsService userDetailsService() {
        return this::findByEmail;
    }
}

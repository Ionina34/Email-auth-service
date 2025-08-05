package effectivemobile.practice.security.service.security;

import effectivemobile.practice.controller.dto.in.SignInUpRequest;
import effectivemobile.practice.controller.dto.out.JwtAuthenticationResponse;
import effectivemobile.practice.model.db.User;
import effectivemobile.practice.security.exception.AccountNotVerified;
import effectivemobile.practice.security.service.UserService;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public AuthenticationService(UserService userService, JwtService jwtService, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public User registerUnconfirmedAccount(SignInUpRequest request) throws EntityExistsException, AccountNotVerified {
        User user = User.builder()
                .email(request.email())
                .isVerify(false)
                .password(passwordEncoder.encode(request.password()))
                .build();

        return userService.create(user);
    }

    public JwtAuthenticationResponse tokenIssuance(String email) {
        User user = userService.findByEmail(email);
        user.setVerify(true);
        userService.save(user);

        return new JwtAuthenticationResponse(
                jwtService.generateToken(user)
        );
    }

    public JwtAuthenticationResponse signIn(SignInUpRequest request) throws EntityNotFoundException {
        User user = userService.findByEmail(request.email());
        if (!user.isVerify()) {
            throw new AccountNotVerified("Account has not been verified with Email: " + user.getEmail());
        }

        UserDetails userDetails = userService
                .userDetailsService()
                .loadUserByUsername(user.getEmail());

        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.email(),
                request.password()
        ));

        String jwt = jwtService.generateToken(userDetails);
        return new JwtAuthenticationResponse(jwt);
    }
}

package TNB.Switch.mapper;

import TNB.Switch.DTO.response.UserResponse;
import TNB.Switch.entity.User;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class UserMapper implements Function<User, UserResponse> {

    @Override
    public UserResponse apply(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getAccountStatus(),
                user.isAdmin()
        );
    }
}
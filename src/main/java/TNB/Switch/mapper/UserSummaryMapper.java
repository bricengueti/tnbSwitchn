package TNB.Switch.mapper;

import TNB.Switch.DTO.response.UserSummaryResponse;
import TNB.Switch.entity.User;
import org.springframework.stereotype.Component;

import java.util.function.Function;
@Component
public class UserSummaryMapper implements Function<User, UserSummaryResponse> {

    @Override
    public UserSummaryResponse apply(User user) {
        if (user == null) {
            return null;
        }

        return new UserSummaryResponse(
                user.getId(),
                user.getPhoneNumber()
        );
    }
}
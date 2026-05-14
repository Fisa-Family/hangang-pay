package family.fisa.hangangpay.domain.user.service;

import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return new UserProfileResponse(user.getNickname(), user.getRegion(), user.getCreatedAt());
    }
}

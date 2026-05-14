package family.fisa.hangangpay.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.domain.user.code.error.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.UserProfileResponse;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    @Test
    @DisplayName("사용자 프로필 응답을 생성한다")
    void getProfile() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 15, 0, 0);
        User user = User.builder().nickname("김한강").region("성동구").build();
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserProfileResponse response = userService.getProfile(1L);

        assertThat(response.name()).isEqualTo("김한강");
        assertThat(response.region()).isEqualTo("성동구");
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 USER_NOT_FOUND 예외를 던진다")
    void getProfileUserNotFound() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }
}

package family.fisa.hangangpay.domain.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(String name, String region, LocalDateTime createdAt) {}

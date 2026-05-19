package family.fisa.hangangpay.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MerchantAccountUpdateRequest(
        @NotBlank String institutionCode,
        @NotBlank @Pattern(regexp = "\\d{8,20}") String accountNumber) {}

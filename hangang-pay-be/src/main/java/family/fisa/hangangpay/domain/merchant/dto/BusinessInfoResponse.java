package family.fisa.hangangpay.domain.merchant.dto;

public record BusinessInfoResponse(
        String businessNumber,
        String merchantName,
        String ownerName,
        String address,
        String businessType) {}

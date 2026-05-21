package family.fisa.hangangpaybank.domain.institution.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InstitutionCode {
    BOK("BoK"),
    WOORI("WR"),
    SHINHAN("SH"),
    HANA("HN");

    private final String code;

    public boolean matches(String institutionCode) {
        return code.equalsIgnoreCase(institutionCode);
    }
}

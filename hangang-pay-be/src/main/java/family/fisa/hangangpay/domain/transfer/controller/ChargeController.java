package family.fisa.hangangpay.domain.transfer.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/charge")
@Tag(name = "Charge", description = "Charge API")
@RequiredArgsConstructor
public class ChargeController {}

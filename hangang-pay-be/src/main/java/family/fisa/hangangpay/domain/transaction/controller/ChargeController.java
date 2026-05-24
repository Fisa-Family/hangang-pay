package family.fisa.hangangpay.domain.transaction.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "충전", description = "충전 API")
@RestController
@RequestMapping("/api/v1/charge")
@RequiredArgsConstructor
public class ChargeController {}

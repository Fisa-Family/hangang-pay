// 기관별 예금 토큰 발행 및 관리를 위한 상업은행 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "./BaseToken.sol";

contract DepositToken is BaseToken {

    // 발행 기관 식별자
    uint256 public institutionId;

    // 기관 정보와 초기 발행량을 설정하는 생성자
    constructor(
        uint256 _institutionId,
        string memory bankName,
        string memory symbol_
    )
        BaseToken(
            string.concat(bankName, " Deposit Token"),
            symbol_
        )
    {
        institutionId = _institutionId;

        _mint(
            msg.sender,
            1_000_000 * 10 ** decimals()
        );
    }
}

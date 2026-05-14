// 중앙은행 디지털화폐 CBDC 발행용 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "./BaseToken.sol";

contract CBDCToken is BaseToken {

    // 토큰 이름과 초기 발행량을 설정하는 생성자
    constructor()
        BaseToken(
            "Central Bank Digital Currency",
            "CBDC"
        )
    {
        _mint(msg.sender, 1_000_000_000 * 10 ** decimals());
    }
}

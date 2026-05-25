// 중앙은행 디지털화폐 CBDC 발행용 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "./BaseToken.sol";

contract CBDCToken is BaseToken {

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    // 토큰 이름과 심볼을 초기화하는 함수
    function initialize(address initialOwner) public initializer {
        __BaseToken_init(
            "Central Bank Digital Currency",
            "CBDC",
            initialOwner
        );
    }
}

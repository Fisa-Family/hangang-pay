// 우리은행 예금토큰
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "./BaseToken.sol";

contract DepositToken is BaseToken {

    constructor()
        BaseToken(
            "Woori Bank Deposit Token",
            "WDT"
        )
    {}
}
// 우리은행 예금토큰
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "./BaseToken.sol";

contract DepositToken is BaseToken {
  /// @custom:oz-upgrades-unsafe-allow constructor
  constructor() {
    _disableInitializers();
  }

  function initialize(address initialOwner) public initializer {
    __BaseToken_init("Woori Bank Deposit Token", "WDT", initialOwner);
  }
}

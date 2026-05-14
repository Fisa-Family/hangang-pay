// Hardhat 기본 제공 ERC20 토큰 예제 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract Token is ERC20 {
    // 컨트랙트 소유자 주소
    address public owner;

    // 초기 토큰을 발행하는 생성자
    constructor() ERC20("My Hardhat Token", "MHT") {
        owner = msg.sender;
        _mint(msg.sender, 1_000_000 * 10 ** decimals());
    }
}

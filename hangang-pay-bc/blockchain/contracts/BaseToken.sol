// 운영자 기반 민트 번 이체 기능을 제공하는 공통 ERC20 토큰 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

abstract contract BaseToken is ERC20 {
    // 컨트랙트 소유자 주소
    address public owner;

    // 운영자 권한 보유 여부 매핑
    mapping(address => bool) public operators;

    // 운영자 권한 변경 이벤트
    event OperatorUpdated(address indexed operator, bool approved);

    // 운영자 강제 이체 이벤트
    event ForceTransfer(
        address indexed operator,
        address indexed from,
        address indexed to,
        uint256 amount
    );

    // 토큰 발행 이벤트
    event Mint(
        address indexed operator,
        address indexed to,
        uint256 amount
    );

    // 토큰 소각 이벤트
    event Burn(
        address indexed operator,
        address indexed from,
        uint256 amount
    );

    // 소유자 전용 접근 제어자
    modifier onlyOwner() {
        require(msg.sender == owner, "caller is not owner");
        _;
    }

    // 운영자 전용 접근 제어자
    modifier onlyOperator() {
        require(operators[msg.sender], "caller is not operator");
        _;
    }

    // 토큰 이름과 심볼을 초기화하는 생성자
    constructor(
        string memory name_,
        string memory symbol_
    ) ERC20(name_, symbol_) {
        owner = msg.sender;
        operators[msg.sender] = true;
    }

    // 운영자 권한 등록 및 해제 함수
    function setOperator(
        address operator,
        bool approved
    ) external onlyOwner {
        require(operator != address(0), "zero address");

        operators[operator] = approved;

        emit OperatorUpdated(operator, approved);
    }

    // 운영자 권한 기반 강제 이체 함수
    function forceTransfer(
        address from,
        address to,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _transfer(from, to, amount);

        emit ForceTransfer(
            msg.sender,
            from,
            to,
            amount
        );

        return true;
    }

    // 지정 주소에 토큰을 발행하는 함수
    function mint(
        address to,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _mint(to, amount);

        emit Mint(
            msg.sender,
            to,
            amount
        );

        return true;
    }

    // 지정 주소에서 토큰을 소각하는 함수
    function burn(
        address from,
        uint256 amount
    ) external onlyOperator returns (bool) {
        _burn(from, amount);

        emit Burn(
            msg.sender,
            from,
            amount
        );

        return true;
    }
}

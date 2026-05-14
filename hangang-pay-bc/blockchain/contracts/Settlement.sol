// 기관 간 예금토큰 이체를 처리하는 정산 컨트랙트
// 예: 우리은행 사용자 -> 신한은행 사용자 송금
// 내부적으로:
// 1. 우리은행 예금토큰 burn
// 2. 기관 준비금(CBDC) 이동
// 3. 신한은행 예금토큰 mint

// SPDX 라이선스 표시
// SPDX-License-Identifier: UNLICENSED

pragma solidity ^0.8.20;

// 예금토큰 인터페이스
// Settlement가 은행 토큰의 burn/mint 호출하기 위해 사용
interface IBankToken {

    // 사용자 토큰 소각
    function burn(address from, uint256 amount) external returns (bool);

    // 사용자 토큰 발행
    function mint(address to, uint256 amount) external returns (bool);
}

// CBDC 인터페이스
// 기관 준비금 이동용
interface ICBDC {

    // Settlement 권한 기반 CBDC 강제 이동
    function forceTransfer(
        address from,
        address to,
        uint256 amount
    ) external returns (bool);
}

contract Settlement {

    // 컨트랙트 관리자
    address public owner;

    // CBDC 컨트랙트 주소
    address public cbdc;

    // 기관ID -> 해당 기관 예금토큰 컨트랙트 주소
    mapping(uint256 => address) public bankToken;

    // 기관ID -> 기관 준비금 지갑 주소
    mapping(uint256 => address) public bankReserveWallet;

    // 가스 절감 및 BE ErrorCode 매핑을 위한 custom error
    error Unauthorized();
    error InvalidCbdcAddress();
    error InvalidInstitutionId();
    error InvalidTokenAddress();
    error InvalidReserveWallet();
    error SameInstitution();
    error InvalidUserAddress();
    error InvalidAmount();
    error BankTokenNotRegistered();
    error BankReserveWalletNotRegistered();
    error DepositTokenBurnFailed();
    error CbdcTransferFailed();
    error DepositTokenMintFailed();

    // 기관 등록 이벤트
    event BankUpdated(
        uint256 indexed institutionId,
        address indexed token,
        address indexed reserveWallet
    );

    // 정산 완료 이벤트
    event Settled(
        uint256 indexed fromInstitutionId,
        uint256 indexed toInstitutionId,
        address indexed fromUser,
        address toUser,
        uint256 amount
    );

    // owner만 실행 가능
    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert Unauthorized();
        }
        _;
    }

    // 배포 시 CBDC 주소 저장
    constructor(address _cbdc) {

        // 0주소 방지
        if (_cbdc == address(0)) {
            revert InvalidCbdcAddress();
        }

        // 배포자를 owner로 지정
        owner = msg.sender;

        // CBDC 컨트랙트 저장
        cbdc = _cbdc;
    }

    // 은행 정보 등록
    // institutionId:
    // 1 -> 한국은행
    // 2 -> 우리은행
    // 3 -> 신한은행
    // 4 -> 하나은행
    function setBank(
        uint256 institutionId,
        address token,
        address reserveWallet
    ) external onlyOwner {

        // 기관ID 검증
        if (institutionId == 0) {
            revert InvalidInstitutionId();
        }

        // 토큰 주소 검증
        if (token == address(0)) {
            revert InvalidTokenAddress();
        }

        // 준비금 지갑 검증
        if (reserveWallet == address(0)) {
            revert InvalidReserveWallet();
        }

        // 기관별 예금토큰 등록
        bankToken[institutionId] = token;

        // 기관별 준비금 지갑 등록
        bankReserveWallet[institutionId] = reserveWallet;

        emit BankUpdated(
            institutionId,
            token,
            reserveWallet
        );
    }

    // 타행 이체 핵심 로직
    function settle(
        uint256 fromInstitutionId, // 보내는 은행
        uint256 toInstitutionId,   // 받는 은행
        address fromUser,          // 보내는 사용자
        address toUser,            // 받는 사용자
        uint256 amount             // 송금 금액
    ) external returns (bool) {

        // 같은 은행이면 안됨
        if (fromInstitutionId == toInstitutionId) {
            revert SameInstitution();
        }

        // 주소 검증
        if (fromUser == address(0) || toUser == address(0)) {
            revert InvalidUserAddress();
        }

        // 금액 검증
        if (amount == 0) {
            revert InvalidAmount();
        }

        // 기관별 예금토큰 조회
        address fromToken = bankToken[fromInstitutionId];
        address toToken = bankToken[toInstitutionId];

        // 기관 준비금 지갑 조회
        address fromReserve = bankReserveWallet[fromInstitutionId];
        address toReserve = bankReserveWallet[toInstitutionId];

        // 등록 여부 확인
        if (fromToken == address(0) || toToken == address(0)) {
            revert BankTokenNotRegistered();
        }

        if (fromReserve == address(0) || toReserve == address(0)) {
            revert BankReserveWalletNotRegistered();
        }

        // 1단계:
        // 보내는 사용자 예금토큰 소각
        if (!IBankToken(fromToken).burn(fromUser, amount)) {
            revert DepositTokenBurnFailed();
        }

        // 2단계:
        // 기관 준비금(CBDC) 이동
        // 보내는 은행 준비금 -> 받는 은행 준비금
        if (!ICBDC(cbdc).forceTransfer(
                fromReserve,
                toReserve,
                amount
            )) {
            revert CbdcTransferFailed();
        }

        // 3단계:
        // 받는 사용자에게 새 예금토큰 발행
        if (!IBankToken(toToken).mint(toUser, amount)) {
            revert DepositTokenMintFailed();
        }

        // 정산 이벤트 기록
        emit Settled(
            fromInstitutionId,
            toInstitutionId,
            fromUser,
            toUser,
            amount
        );

        return true;
    }
}

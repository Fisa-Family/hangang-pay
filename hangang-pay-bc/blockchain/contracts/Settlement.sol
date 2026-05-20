// CBDC reserve 정산 및 우리은행 예금토큰 충전/환불 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

// 예금토큰 인터페이스
// Settlement가 예금토큰 mint/burn 호출을 위해 사용
interface IDepositToken {

    // 사용자에게 예금토큰 발행
    function mint(
        address to,
        uint256 amount
    ) external returns (bool);

    // 사용자 예금토큰 소각
    function burn(
        address from,
        uint256 amount
    ) external returns (bool);
}

// CBDC 인터페이스
// Settlement 내부 reserve 검증용
interface ICBDC {

    // 계정의 CBDC 잔액 조회
    function balanceOf(
        address account
    ) external view returns (uint256);
}

contract Settlement {

    // 우리은행 기관 ID
    uint256 public constant WOORI_BANK_ID = 2;

    // 컨트랙트 관리자
    address public owner;

    // CBDC 컨트랙트 주소
    address public cbdc;

    // 우리은행 예금토큰 컨트랙트 주소
    address public depositToken;

    // 등록된 기관 여부
    mapping(uint256 => bool) public registeredBank;

    // 기관별 CBDC reserve 잔액
    // Settlement 내부 장부 역할
    mapping(uint256 => uint256) public reserveBalance;

    // 전체 reserve 합계
    uint256 public totalReserved;

    // BE ErrorCode 매핑을 위한 custom error
    error Unauthorized();
    error InvalidAddress();
    error InvalidInstitutionId();
    error InvalidAmount();
    error BankNotRegistered();
    error InsufficientReserve();
    error ReserveExceedsLockedCbdc();
    error DepositTokenMintFailed();
    error DepositTokenBurnFailed();

    // 기관 등록 이벤트
    event BankRegistered(
        uint256 indexed institutionId
    );

    // 기관 reserve 설정 이벤트
    event ReserveSet(
        uint256 indexed institutionId,
        uint256 amount
    );

    // 기관 간 reserve 이동 이벤트
    event ReserveMoved(
        uint256 indexed fromInstitutionId,
        uint256 indexed toInstitutionId,
        uint256 amount
    );

    // 충전 이벤트
    event Charged(
        uint256 indexed fromInstitutionId,
        address indexed user,
        uint256 amount
    );

    // 환불 이벤트
    event Refunded(
        uint256 indexed toInstitutionId,
        address indexed user,
        uint256 amount
    );

    // owner만 실행 가능
    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert Unauthorized();
        }

        _;
    }

    // 배포 시 CBDC 및 예금토큰 주소 저장
    constructor(
        address _cbdc,
        address _depositToken
    ) {

        // 주소 검증
        if (
            _cbdc == address(0) ||
            _depositToken == address(0)
        ) {
            revert InvalidAddress();
        }

        // 배포자를 owner로 지정
        owner = msg.sender;

        // CBDC 컨트랙트 저장
        cbdc = _cbdc;

        // 예금토큰 컨트랙트 저장
        depositToken = _depositToken;
    }

    // 기관 등록 함수
    function registerBank(
        uint256 institutionId
    ) external onlyOwner {

        // 기관 ID 검증
        if (institutionId == 0) {
            revert InvalidInstitutionId();
        }

        // 기관 등록
        registeredBank[institutionId] = true;

        emit BankRegistered(
            institutionId
        );
    }

    // 기관별 reserve 설정 함수
    // Settlement가 보유한 CBDC 범위 내에서만 가능
    function setReserve(
        uint256 institutionId,
        uint256 amount
    ) external onlyOwner {

        // 등록 여부 검증
        if (!registeredBank[institutionId]) {
            revert BankNotRegistered();
        }

        // 기존 reserve 조회
        uint256 current = reserveBalance[institutionId];

        // 전체 reserve 합계 갱신
        if (amount > current) {
            totalReserved += amount - current;
        } else {
            totalReserved -= current - amount;
        }

        // 실제 lock된 CBDC 초과 여부 검증
        if (
            totalReserved >
            ICBDC(cbdc).balanceOf(address(this))
        ) {
            revert ReserveExceedsLockedCbdc();
        }

        // reserve 저장
        reserveBalance[institutionId] = amount;

        emit ReserveSet(
            institutionId,
            amount
        );
    }

    // 충전 함수
    // 선택 은행 reserve 차감
    // 우리은행 reserve 증가
    // 사용자에게 우리은행 예금토큰 mint
    function charge(
        uint256 fromInstitutionId,
        address user,
        uint256 amount
    ) external onlyOwner returns (bool) {

        // 기관 등록 여부 검증
        if (!registeredBank[fromInstitutionId]) {
            revert BankNotRegistered();
        }

        // 사용자 주소 검증
        if (user == address(0)) {
            revert InvalidAddress();
        }

        // 금액 검증
        if (amount == 0) {
            revert InvalidAmount();
        }

        // 타행 충전 시 reserve 이동
        if (fromInstitutionId != WOORI_BANK_ID) {

            _moveReserve(
                fromInstitutionId,
                WOORI_BANK_ID,
                amount
            );
        }

        // 우리은행 예금토큰 발행
        if (
            !IDepositToken(depositToken).mint(
                user,
                amount
            )
        ) {
            revert DepositTokenMintFailed();
        }

        emit Charged(
            fromInstitutionId,
            user,
            amount
        );

        return true;
    }

    // 환불 함수
    // 사용자 예금토큰 burn
    // 우리은행 reserve 차감
    // 선택 은행 reserve 증가
    function refund(
        uint256 toInstitutionId,
        address user,
        uint256 amount
    ) external onlyOwner returns (bool) {

        // 기관 등록 여부 검증
        if (!registeredBank[toInstitutionId]) {
            revert BankNotRegistered();
        }

        // 사용자 주소 검증
        if (user == address(0)) {
            revert InvalidAddress();
        }

        // 금액 검증
        if (amount == 0) {
            revert InvalidAmount();
        }

        // 사용자 예금토큰 소각
        if (
            !IDepositToken(depositToken).burn(
                user,
                amount
            )
        ) {
            revert DepositTokenBurnFailed();
        }

        // 타행 환불 시 reserve 이동
        if (toInstitutionId != WOORI_BANK_ID) {

            _moveReserve(
                WOORI_BANK_ID,
                toInstitutionId,
                amount
            );
        }

        emit Refunded(
            toInstitutionId,
            user,
            amount
        );

        return true;
    }

    // 기관 간 reserve 이동 내부 함수
    function _moveReserve(
        uint256 fromInstitutionId,
        uint256 toInstitutionId,
        uint256 amount
    ) internal {

        // reserve 부족 검증
        if (
            reserveBalance[fromInstitutionId] <
            amount
        ) {
            revert InsufficientReserve();
        }

        // reserve 차감
        reserveBalance[fromInstitutionId] -= amount;

        // reserve 증가
        reserveBalance[toInstitutionId] += amount;

        emit ReserveMoved(
            fromInstitutionId,
            toInstitutionId,
            amount
        );
    }
}
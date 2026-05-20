// 지역화폐 결제 정책 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

// 예금토큰 인터페이스
// 지역화폐 정책 컨트랙트가 forceTransfer 호출을 위해 사용
interface IDepositToken {

    // 운영자 권한 기반 강제 이체 함수
    function forceTransfer(
        address from,
        address to,
        uint256 amount
    ) external returns (bool);
}

contract LocalCurrencyPolicy {

    // 컨트랙트 관리자
    address public owner;

    // 우리은행 예금토큰 컨트랙트 주소
    address public depositToken;

    // 지역화폐 총 사용 가능 한도
    uint256 public maxTotalUsage;

    // 누적 사용 금액
    uint256 public totalUsed;

    // 등록된 가맹점 여부
    mapping(address => bool) public merchants;

    // BE ErrorCode 매핑을 위한 custom error
    error Unauthorized();
    error InvalidAddress();
    error InvalidAmount();
    error MerchantNotRegistered();
    error UsageLimitExceeded();
    error TransferFailed();

    // 가맹점 등록 이벤트
    event MerchantUpdated(
        address indexed merchant,
        bool approved
    );

    // 결제 이벤트
    event Paid(
        address indexed from,
        address indexed to,
        uint256 amount
    );

    // 결제 취소 이벤트
    event PaymentCanceled(
        address indexed from,
        address indexed to,
        uint256 amount
    );

    // owner만 실행 가능
    modifier onlyOwner() {
        if (msg.sender != owner) {
            revert Unauthorized();
        }

        _;
    }

    // 배포 시 예금토큰 주소 및 최대 사용 한도 저장
    constructor(
        address _depositToken,
        uint256 _maxTotalUsage
    ) {

        // 주소 검증
        if (_depositToken == address(0)) {
            revert InvalidAddress();
        }

        // 배포자를 owner로 지정
        owner = msg.sender;

        // 예금토큰 컨트랙트 저장
        depositToken = _depositToken;

        // 지역화폐 총 사용 가능 한도 저장
        maxTotalUsage = _maxTotalUsage;
    }

    // 가맹점 등록 및 해제 함수
    function setMerchant(
        address merchant,
        bool approved
    ) external onlyOwner {

        // 주소 검증
        if (merchant == address(0)) {
            revert InvalidAddress();
        }

        // 가맹점 상태 저장
        merchants[merchant] = approved;

        emit MerchantUpdated(
            merchant,
            approved
        );
    }

// 결제 함수
// 사용자 -> 가맹점 방향으로 예금토큰을 이동
function pay(
    address from,
    address to,
    uint256 amount
) external onlyOwner returns (bool) {
    // 주소 검증
    if (
        from == address(0) ||
        to == address(0)
    ) {
        revert InvalidAddress();
    }

    // 금액 검증
    if (amount == 0) {
        revert InvalidAmount();
    }

    // 수취 가맹점 등록 여부 검증
    if (!merchants[to]) {
        revert MerchantNotRegistered();
    }

    // 총 사용 한도 초과 여부 검증
    if (
        totalUsed + amount >
        maxTotalUsage
    ) {
        revert UsageLimitExceeded();
    }

    // 누적 사용 금액 증가
    totalUsed += amount;
    // from -> to 예금토큰 강제 이체
    if (
        !IDepositToken(depositToken).forceTransfer(
            from,
            to,
            amount
        )
    ) {
        revert TransferFailed();
    }

    emit Paid(
        from,
        to,
        amount
    );
    return true;
}


// 결제 취소 함수
// 가맹점 -> 사용자 방향으로 예금토큰을 반환
function cancelPayment(
    address from,
    address to,
    uint256 amount
) external onlyOwner returns (bool) {

    // 주소 검증
    if (
        from == address(0) ||
        to == address(0)
    ) {
        revert InvalidAddress();
    }

    // 금액 검증
    if (amount == 0) {
        revert InvalidAmount();
    }

    // 결제 취소 요청자는 등록된 가맹점이어야 함
    if (!merchants[from]) {
        revert MerchantNotRegistered();
    }

    // 누적 사용 금액 감소
    if (totalUsed >= amount) {
        totalUsed -= amount;
    } else {
        totalUsed = 0;
    }

    // from -> to 예금토큰 강제 이체
    // (가맹점 -> 사용자)
    if (
        !IDepositToken(depositToken).forceTransfer(
            from,
            to,
            amount
        )
    ) {
        revert TransferFailed();
    }
    emit PaymentCanceled(
        from,
        to,
        amount
    );
    return true;

}
}
// 지역화폐 결제 정책 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

// 예금토큰 인터페이스
// 지역화폐 정책 컨트랙트가 forceTransfer 호출을 위해 사용
interface ILocalDepositToken {
  function mint(address to, uint256 amount) external returns (bool);
  function burn(address from, uint256 amount) external returns (bool);

  // 운영자 권한 기반 강제 이체 함수
  function forceTransfer(address from, address to, uint256 amount) external returns (bool);
}

interface ISettlement {
  function registeredBank(uint256 institutionId) external view returns (bool);

  function moveReserve(
    uint256 fromInstitutionId,
    uint256 toInstitutionId,
    uint256 amount
  ) external returns (bool);
}

contract LocalCurrencyPolicy is Initializable, UUPSUpgradeable {
  uint256 public constant WOORI_BANK_ID = 2;

  // 컨트랙트 관리자
  address public owner;

  // 우리은행 예금토큰 컨트랙트 주소
  address public depositToken;
  address public settlement;

  // 지역화폐 총 누적 발행 가능 한도
  uint256 public constant MAX_TOTAL_ISSUANCE = 10_000_000 * 10 ** 18;
  // 누적 지역화폐 발행량
  uint256 public totalIssued;

  // 등록된 가맹점 여부
  mapping(address => bool) public merchants;

  // 처리된 transactionUuid 중복 실행 방지
  mapping(bytes32 => bool) public processedTx;

  // BE ErrorCode 매핑을 위한 custom error
  error Unauthorized();
  error InvalidAddress();
  error InvalidAmount();
  error MerchantNotRegistered();
  error IssuanceLimitExceeded();
  error ReserveMoveFailed();
  error DepositTokenMintFailed();
  error DepositTokenBurnFailed();
  error TransferFailed();
  error BankNotRegistered();
  error AlreadyProcessed();

  // 가맹점 등록 이벤트
  event MerchantUpdated(address indexed merchant, bool approved);

  // 충전 이벤트
  event Charged(uint256 indexed fromInstitutionId, address indexed user, uint256 amount);

  // 환불 이벤트
  event Refunded(uint256 indexed toInstitutionId, address indexed user, uint256 amount);

  // 결제 이벤트
  event Paid(
    bytes32 indexed transactionUuid,
    address indexed from,
    address indexed to,
    uint256 amount
  );

  // 결제 취소 이벤트
  event PaymentCanceled(
    bytes32 indexed transactionUuid,
    address indexed from,
    address indexed to,
    uint256 amount
  );

  // owner만 실행 가능
  modifier onlyOwner() {
    if (msg.sender != owner) revert Unauthorized();
    _;
  }

  /// @custom:oz-upgrades-unsafe-allow constructor
  constructor() {
    _disableInitializers();
  }

  // 배포 시 예금토큰 주소, Settlement 주소 저장
  function initialize(
    address _depositToken,
    address _settlement,
    address initialOwner
  ) public initializer {
    // 주소 검증
    if (_depositToken == address(0) || _settlement == address(0) || initialOwner == address(0)) {
      revert InvalidAddress();
    }

    // 초기 owner 지정
    owner = initialOwner;

    // 예금토큰 컨트랙트 저장
    depositToken = _depositToken;
    settlement = _settlement;
  }

  // 가맹점 등록 및 해제 함수
  function setMerchant(address merchant, bool approved) external onlyOwner {
    // 주소 검증
    if (merchant == address(0)) {
      revert InvalidAddress();
    }

    // 가맹점 상태 저장
    merchants[merchant] = approved;

    emit MerchantUpdated(merchant, approved);
  }

  function charge(
    uint256 fromInstitutionId,
    address user,
    uint256 amount
  ) external onlyOwner returns (bool) {
    if (user == address(0)) revert InvalidAddress();
    if (amount == 0) revert InvalidAmount();
    if (!ISettlement(settlement).registeredBank(fromInstitutionId)) {
      revert BankNotRegistered();
    }
    if (totalIssued + amount > MAX_TOTAL_ISSUANCE) {
      revert IssuanceLimitExceeded();
    }

    if (fromInstitutionId != WOORI_BANK_ID) {
      if (!ISettlement(settlement).moveReserve(fromInstitutionId, WOORI_BANK_ID, amount)) {
        revert ReserveMoveFailed();
      }
    }

    if (!ILocalDepositToken(depositToken).mint(user, amount)) {
      revert DepositTokenMintFailed();
    }

    totalIssued += amount;

    emit Charged(fromInstitutionId, user, amount);
    return true;
  }

  function refund(
    uint256 toInstitutionId,
    address user,
    uint256 amount
  ) external onlyOwner returns (bool) {
    if (user == address(0)) revert InvalidAddress();
    if (amount == 0) revert InvalidAmount();
    if (!ISettlement(settlement).registeredBank(toInstitutionId)) {
      revert BankNotRegistered();
    }
    if (!ILocalDepositToken(depositToken).burn(user, amount)) {
      revert DepositTokenBurnFailed();
    }
    if (toInstitutionId != WOORI_BANK_ID) {
      if (!ISettlement(settlement).moveReserve(WOORI_BANK_ID, toInstitutionId, amount)) {
        revert ReserveMoveFailed();
      }
    }

    emit Refunded(toInstitutionId, user, amount);
    return true;
  }

  // 결제 함수
  // 사용자 -> 가맹점 방향으로 예금토큰을 이동
  function pay(
    bytes32 transactionUuid,
    address from,
    address to,
    uint256 amount
  ) external onlyOwner returns (bool) {
    // 중복 실행 방지
    if (processedTx[transactionUuid]) revert AlreadyProcessed();

    // 주소 검증
    if (from == address(0) || to == address(0)) {
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

    // from -> to 예금토큰 강제 이체
    if (!ILocalDepositToken(depositToken).forceTransfer(from, to, amount)) {
      revert TransferFailed();
    }

    processedTx[transactionUuid] = true;
    emit Paid(transactionUuid, from, to, amount);
    return true;
  }

  // 결제 취소 함수
  // 가맹점 -> 사용자 방향으로 예금토큰을 반환
  function cancelPayment(
    bytes32 transactionUuid,
    address from,
    address to,
    uint256 amount
  ) external onlyOwner returns (bool) {
    // 중복 실행 방지
    if (processedTx[transactionUuid]) revert AlreadyProcessed();

    // 주소 검증
    if (from == address(0) || to == address(0)) {
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

    // from -> to 예금토큰 강제 이체
    // (가맹점 -> 사용자)
    if (!ILocalDepositToken(depositToken).forceTransfer(from, to, amount)) {
      revert TransferFailed();
    }

    processedTx[transactionUuid] = true;
    emit PaymentCanceled(transactionUuid, from, to, amount);
    return true;
  }

  // UUPS 업그레이드는 owner만 허용
  function _authorizeUpgrade(address) internal override onlyOwner {}

  uint256[49] private __gap;

  function versionV5() external pure returns (string memory) {
    return "v5";
  }
}

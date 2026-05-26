// CBDC reserve 정산 인프라 컨트랙트
// SPDX-License-Identifier: UNLICENSED
pragma solidity 0.8.28;

import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

// CBDC 인터페이스
// Settlement 내부 reserve 검증용
interface ICBDC {

    // 계정의 CBDC 잔액 조회
    function balanceOf(
        address account
    ) external view returns (uint256);
}

contract Settlement is Initializable, UUPSUpgradeable {

    // 컨트랙트 관리자
    address public owner;

    // CBDC 컨트랙트 주소
    address public cbdc;

    mapping(address => bool) public operators;

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

    event OperatorUpdated(
        address indexed operator, 
        bool approved
    );

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

    // owner만 실행 가능
    modifier onlyOwner() {
        if (msg.sender != owner) revert Unauthorized();
        _;
    }

    modifier onlyOperator() {
        if (!operators[msg.sender]) revert Unauthorized();
        _;
    }

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    // 배포 시 CBDC 주소 저장
    function initialize(
        address _cbdc,
        address initialOwner
    ) public initializer {
        if (
            _cbdc == address(0) ||
            initialOwner == address(0)
        ) {
            revert InvalidAddress();
        }

        owner = initialOwner;
        cbdc = _cbdc;
        operators[initialOwner] = true;
    }

    function setOperator(
        address operator,
        bool approved
    ) external onlyOwner {
        if (operator == address(0)) revert InvalidAddress();
        operators[operator] = approved;
        emit OperatorUpdated(operator, approved);
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

    // 기관 간 reserve 이동 함수
    function moveReserve(
        uint256 fromInstitutionId,
        uint256 toInstitutionId,
        uint256 amount
    ) external onlyOperator returns (bool) {
        if (amount == 0) revert InvalidAmount();

        if (
            !registeredBank[fromInstitutionId] ||
            !registeredBank[toInstitutionId]
        ) {
            revert BankNotRegistered();
        }

        // reserve 부족 검증
        if (
            reserveBalance[fromInstitutionId] < amount) {
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

        return true;
    }

    // UUPS 업그레이드는 owner만 허용
    function _authorizeUpgrade(
        address
    ) internal override onlyOwner {}

    uint256[50] private __gap;
}

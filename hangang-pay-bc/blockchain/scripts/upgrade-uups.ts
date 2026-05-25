/*
 * 기존 UUPS proxy의 implementation을 업그레이드하는 스크립트.
 *
 * - proxy 주소는 유지된다.
 * - implementation 주소만 새로운 버전으로 교체된다.
 * - 기존 storage/state는 그대로 유지된다.
 *
 * 실행 흐름:
 * 1. deployments/uups-latest.json에서 기존 proxy 주소 조회
 * 2. 새로운 implementation 배포
 * 3. upgradeToAndCall 실행
 * 4. 업그레이드 결과를 deployments/uups-upgrade-latest.json에 저장
 *
 * 사용 예시:
 * CONTRACT_TYPE=LOCAL_CURRENCY
 * npx hardhat run scripts/upgrade-uups.ts --network besu
 *
 * 주의:
 * 업그레이드는 컨트랙트 owner 계정으로만 가능하다.
 */

import hre from "hardhat";
import { readFile, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

type ContractType =
  | "CBDC"
  | "DEPOSIT_TOKEN"
  | "SETTLEMENT"
  | "LOCAL_CURRENCY";

type DeployedContract = {
  institutionCode: string;
  contractType: ContractType;
  proxyAddress: string;
  implementationAddress: string;
  ownerAddress: string;
};

type DeploymentResult = {
  network: string;
  chainId: number;
  deployedAt: string;
  contracts: DeployedContract[];
};

const DEPLOYMENT_RESULT_PATH =
  process.env.DEPLOYMENT_RESULT_PATH ?? "deployments/uups-latest.json";

const UPGRADE_RESULT_PATH =
  process.env.UPGRADE_RESULT_PATH ?? "deployments/uups-upgrade-latest.json";

const CONTRACT_FACTORIES: Record<ContractType, string> = {
  CBDC: "CBDCToken",
  DEPOSIT_TOKEN: "DepositToken",
  SETTLEMENT: "Settlement",
  LOCAL_CURRENCY: "LocalCurrencyPolicy",
};

async function main() {
  const target = process.env.CONTRACT_TYPE as ContractType | undefined;

  if (!target || !(target in CONTRACT_FACTORIES)) {
    throw new Error(
      "CONTRACT_TYPE must be one of CBDC, DEPOSIT_TOKEN, SETTLEMENT, LOCAL_CURRENCY",
    );
  }

  const deployment = await readDeploymentResult();
  const targetContract = deployment.contracts.find(
    (contract) => contract.contractType === target,
  );

  if (!targetContract) {
    throw new Error(`Proxy address not found for ${target}`);
  }

  const signer = await resolveSigner(targetContract.ownerAddress);
  const factoryName = CONTRACT_FACTORIES[target];
  const factory = await hre.ethers.getContractFactory(factoryName, signer);

  const oldImplementation =
    await hre.upgrades.erc1967.getImplementationAddress(
      targetContract.proxyAddress,
    );

  console.log(`Upgrading ${target}`);
  console.log("Proxy:", targetContract.proxyAddress);
  console.log("Old implementation:", oldImplementation);
  console.log("Signer:", await signer.getAddress());

const preparedImplementation = await hre.upgrades.prepareUpgrade(
  targetContract.proxyAddress,
  factory,
  { kind: "uups" },
);

console.log("Prepared implementation:", preparedImplementation);

const proxy = await hre.ethers.getContractAt(
  factoryName,
  targetContract.proxyAddress,
  signer,
);

const upgradeTx = await proxy.upgradeToAndCall(preparedImplementation, "0x");
await upgradeTx.wait();

const newImplementation =
  await hre.upgrades.erc1967.getImplementationAddress(
    targetContract.proxyAddress,
  );

  console.log("New implementation:", newImplementation);
  console.log(`${target} upgrade complete`);

  const result = {
    network: hre.network.name,
    chainId: Number((await hre.ethers.provider.getNetwork()).chainId),
    upgradedAt: new Date().toISOString(),
    contractType: target,
    proxyAddress: targetContract.proxyAddress,
    oldImplementationAddress: oldImplementation,
    newImplementationAddress: newImplementation,
    ownerAddress: targetContract.ownerAddress,
  };

  await writeUpgradeResult(result);
}

async function readDeploymentResult(): Promise<DeploymentResult> {
  const path = resolve(process.cwd(), DEPLOYMENT_RESULT_PATH);
  const content = await readFile(path, "utf8");

  return JSON.parse(content) as DeploymentResult;
}

async function resolveSigner(ownerAddress: string) {
  const signers = await hre.ethers.getSigners();

  const signer = await Promise.all(
    signers.map(async (candidate) => ({
      signer: candidate,
      address: await candidate.getAddress(),
    })),
  ).then((candidates) =>
    candidates.find(
      (candidate) =>
        candidate.address.toLowerCase() === ownerAddress.toLowerCase(),
    ),
  );

  if (!signer) {
    throw new Error(`Owner signer not found: ${ownerAddress}`);
  }

  return signer.signer;
}

async function writeUpgradeResult(result: unknown) {
  const outputPath = resolve(process.cwd(), UPGRADE_RESULT_PATH);

  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`);

  console.log("Upgrade result written:", outputPath);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
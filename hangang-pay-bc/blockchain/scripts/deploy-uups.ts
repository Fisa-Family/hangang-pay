/*
 * UUPS proxy 기반 컨트랙트 초기 배포 스크립트.
 *
 * - CBDCToken
 * - DepositToken
 * - Settlement
 * - LocalCurrencyPolicy
 *
 * 순서로 proxy를 새로 배포하고 초기 설정을 수행한다.
 *
 * 실행 시:
 * - 새로운 proxy / implementation 주소가 생성된다.
 * - 기관 reserve 및 operator 권한을 초기화한다.
 * - 배포 결과를 deployments/uups-latest.json에 저장한다.
 * - Spring 서버에 proxy 주소를 동기화한다.
 *
 * 주의:
 * 이 스크립트는 upgrade가 아니라 재배포이다.
 * 다시 실행하면 새로운 proxy 주소가 생성된다.
 */

import hre from "hardhat";
import { mkdir, writeFile } from "node:fs/promises";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { dirname, resolve } from "node:path";
import { URL } from "node:url";
import type { BaseContract, ContractTransactionResponse } from "ethers";
import "dotenv/config";

type Institution = {
  id: bigint;
  name: string;
};

type ContractType = "CBDC" | "DEPOSIT_TOKEN" | "SETTLEMENT" | "LOCAL_CURRENCY";

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

type DeployedAddresses = {
  proxyAddress: string;
  implementationAddress: string;
};

const TOKEN_DECIMALS = 10n ** 18n;
const INITIAL_LOCKED_CBDC_AMOUNT = 1_000_000_000n * TOKEN_DECIMALS;
const INITIAL_BANK_RESERVE_AMOUNT = 100_000_000n * TOKEN_DECIMALS;
const DEFAULT_DEPLOYMENT_RESULT_PATH = "deployments/uups-latest.json";
const DEFAULT_SPRING_DEPLOYMENT_API_URL =
  "http://localhost:8081/api/v1/internal/blockchain/deployments";
const DEFAULT_BANK_API_KEY = "hangang-pay-local-bank-api-key";

async function main() {
  const [bok, woori] = await hre.ethers.getSigners();
  const network = await hre.ethers.provider.getNetwork();
  const bokAddress = await bok.getAddress();
  const wooriAddress = await woori.getAddress();

  const institutions: Institution[] = [
    { id: 1n, name: "BoK" },
    { id: 2n, name: "Woori Bank" },
    { id: 3n, name: "Shinhan Bank" },
    { id: 4n, name: "Hana Bank" },
  ];

  console.log("Deploying UUPS proxies");
  console.log("BoK owner:", bokAddress);
  console.log("Woori owner:", wooriAddress);

  const cbdcFactory = await hre.ethers.getContractFactory("CBDCToken", bok);
  const cbdc = await hre.upgrades.deployProxy(cbdcFactory, [bokAddress], {
    kind: "uups",
    initializer: "initialize",
    timeout: 600000,
    pollingInterval: 5000,
  });
  await cbdc.waitForDeployment();
  const cbdcDeployment = await logDeployment("CBDCToken", cbdc);

  const depositFactory = await hre.ethers.getContractFactory("DepositToken", woori);
  const depositToken = await hre.upgrades.deployProxy(depositFactory, [wooriAddress], {
    kind: "uups",
    initializer: "initialize",
    timeout: 600000,
    pollingInterval: 5000,
  });
  await depositToken.waitForDeployment();
  const depositTokenDeployment = await logDeployment("DepositToken", depositToken);

  const settlementFactory = await hre.ethers.getContractFactory("Settlement", bok);
  const settlement = await hre.upgrades.deployProxy(
    settlementFactory,
    [cbdcDeployment.proxyAddress, bokAddress],
    { kind: "uups", initializer: "initialize", timeout: 600000, pollingInterval: 5000 },
  );
  await settlement.waitForDeployment();
  const settlementDeployment = await logDeployment("Settlement", settlement);

  const localCurrencyFactory = await hre.ethers.getContractFactory("LocalCurrencyPolicy", bok);
  const localCurrency = await hre.upgrades.deployProxy(
    localCurrencyFactory,
    [depositTokenDeployment.proxyAddress, settlementDeployment.proxyAddress, bokAddress],
    { kind: "uups", initializer: "initialize", timeout: 600000, pollingInterval: 5000 },
  );
  await localCurrency.waitForDeployment();
  const localCurrencyDeployment = await logDeployment("LocalCurrencyPolicy", localCurrency);

  await waitForTx(
    await cbdc.mint(settlementDeployment.proxyAddress, INITIAL_LOCKED_CBDC_AMOUNT),
    "Mint CBDC to Settlement",
  );

  for (const institution of institutions) {
    await waitForTx(await settlement.registerBank(institution.id), `Register ${institution.name}`);
    await waitForTx(
      await settlement.setReserve(institution.id, INITIAL_BANK_RESERVE_AMOUNT),
      `Set reserve for ${institution.name}`,
    );
  }

  await waitForTx(
    await depositToken.connect(woori).setOperator(localCurrencyDeployment.proxyAddress, true),
    "Grant LocalCurrencyPolicy operator on DepositToken",
  );
  await waitForTx(
    await settlement.setOperator(localCurrencyDeployment.proxyAddress, true),
    "Grant LocalCurrencyPolicy operator on Settlement",
  );

  const deploymentResult: DeploymentResult = {
    network: hre.network.name,
    chainId: Number(network.chainId),
    deployedAt: new Date().toISOString(),
    contracts: [
      deploymentContract("BoK", "CBDC", cbdcDeployment, bokAddress),
      deploymentContract("WR", "DEPOSIT_TOKEN", depositTokenDeployment, wooriAddress),
      deploymentContract("BoK", "SETTLEMENT", settlementDeployment, bokAddress),
      deploymentContract("BoK", "LOCAL_CURRENCY", localCurrencyDeployment, bokAddress),
    ],
  };

  await writeDeploymentResult(deploymentResult);
  await syncDeploymentResult(deploymentResult);

  console.log("UUPS proxy deployment complete");
  console.table({
    CBDCToken: cbdcDeployment.proxyAddress,
    DepositToken: depositTokenDeployment.proxyAddress,
    Settlement: settlementDeployment.proxyAddress,
    LocalCurrencyPolicy: localCurrencyDeployment.proxyAddress,
  });
}

async function logDeployment(name: string, contract: BaseContract): Promise<DeployedAddresses> {
  const proxyAddress = await contract.getAddress();
  const implementationAddress = await hre.upgrades.erc1967.getImplementationAddress(proxyAddress);

  console.log(`${name} proxy:`, proxyAddress);
  console.log(`${name} implementation:`, implementationAddress);

  return { proxyAddress, implementationAddress };
}

function deploymentContract(
  institutionCode: string,
  contractType: ContractType,
  addresses: DeployedAddresses,
  ownerAddress: string,
): DeployedContract {
  return {
    institutionCode,
    contractType,
    proxyAddress: addresses.proxyAddress,
    implementationAddress: addresses.implementationAddress,
    ownerAddress,
  };
}

async function waitForTx(tx: ContractTransactionResponse, label: string) {
  const receipt = await tx.wait();

  if (receipt?.status !== 1) {
    throw new Error(`${label} failed`);
  }

  console.log(`${label}:`, tx.hash);
}

async function writeDeploymentResult(deploymentResult: DeploymentResult) {
  const outputPath = resolve(
    process.cwd(),
    process.env.DEPLOYMENT_RESULT_PATH ?? DEFAULT_DEPLOYMENT_RESULT_PATH,
  );

  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(deploymentResult, null, 2)}\n`);

  console.log("Deployment result written:", outputPath);
}

async function syncDeploymentResult(deploymentResult: DeploymentResult) {
  if (process.env.SKIP_SPRING_DEPLOYMENT_SYNC === "true") {
    console.log("Spring deployment sync skipped");
    return;
  }

  const apiUrl = process.env.SPRING_DEPLOYMENT_API_URL ?? DEFAULT_SPRING_DEPLOYMENT_API_URL;
  const responseBody = await postJson(apiUrl, deploymentResult);

  console.log("Spring deployment sync complete:", responseBody);
}

function postJson(urlString: string, body: unknown): Promise<string> {
  const payload = JSON.stringify(body);
  const url = new URL(urlString);
  const client = url.protocol === "https:" ? httpsRequest : httpRequest;

  return new Promise((resolvePromise, rejectPromise) => {
    const request = client(
      url,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(payload),
          "X-Bank-Api-Key": process.env.BANK_API_KEY ?? DEFAULT_BANK_API_KEY,
        },
      },
      (response) => {
        let responseBody = "";

        response.setEncoding("utf8");
        response.on("data", (chunk) => {
          responseBody += chunk;
        });
        response.on("end", () => {
          const statusCode = response.statusCode ?? 0;

          if (statusCode < 200 || statusCode >= 300) {
            rejectPromise(
              new Error(`Spring deployment sync failed: ${statusCode} ${responseBody}`),
            );
            return;
          }

          resolvePromise(responseBody);
        });
      },
    );

    request.on("error", rejectPromise);
    request.write(payload);
    request.end();
  });
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

import { ethers } from "hardhat";

const LOCAL_CURRENCY_ADDRESS =
  process.env.LOCAL_CURRENCY_ADDRESS ?? "0x3Ace09BBA3b8507681146252d3Dd33cD4E2d4F63";
const MERCHANT_WALLET = process.env.MERCHANT_WALLET ?? "";

async function main() {
  if (!MERCHANT_WALLET) {
    console.error(
      "Usage: MERCHANT_WALLET=0x... [LOCAL_CURRENCY_ADDRESS=0x...] npx hardhat run scripts/add-merchant-whitelist.ts --network besu",
    );
    process.exit(1);
  }

  const [bok] = await ethers.getSigners();
  console.log("Signer:", bok.address);

  const lcp = await ethers.getContractAt("LocalCurrencyPolicy", LOCAL_CURRENCY_ADDRESS, bok);

  const before = await lcp.merchants(MERCHANT_WALLET);
  console.log("Merchant whitelisted before:", before);

  const tx = await lcp.setMerchant(MERCHANT_WALLET, true);
  await tx.wait();
  console.log("tx hash:", tx.hash);

  const after = await lcp.merchants(MERCHANT_WALLET);
  console.log("Merchant whitelisted after:", after);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});

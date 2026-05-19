import hre from "hardhat";

const CBDC_ADDRESS = process.env.CBDC_ADDRESS!;

const BOK_PRIVATE_KEY =
  "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";

const BANKS = [
  {
    name: "우리은행",
    address: "0x627306090abaB3A6e1400e9345bC60c78a8BEf57",
  },
  {
    name: "신한은행",
    address: "0xf17f52151EbEF6C7334FAD080c5704D77216b732",
  },
  {
    name: "하나은행",
    address: "0xE9BA79E62a58225065bF24313896CD332dAFCB3C",
  },
];

const AMOUNT_PER_BANK = hre.ethers.parseUnits("100000000", 18); // 1억

async function main() {
  const bok = new hre.ethers.Wallet(BOK_PRIVATE_KEY, hre.ethers.provider);
  const cbdc = await hre.ethers.getContractAt("CBDCToken", CBDC_ADDRESS, bok);

  console.log("BOK CBDC 잔액:", hre.ethers.formatUnits(await cbdc.balanceOf(bok.address), 18));

  for (const bank of BANKS) {
    const tx = await cbdc.transfer(bank.address, AMOUNT_PER_BANK);
    await tx.wait();
    const balance = await cbdc.balanceOf(bank.address);
    console.log(`${bank.name} CBDC 잔액:`, hre.ethers.formatUnits(balance, 18));
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});

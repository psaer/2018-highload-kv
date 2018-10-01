package ru.mail.polis.psaer.stage1.blockchain.service;

import org.jetbrains.annotations.NotNull;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;
import ru.mail.polis.psaer.stage1.blockchain.Utils;
import ru.mail.polis.psaer.stage1.blockchain.dto.FunctionParamDTO;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TransactionService {

    /* BAD practice here */
    private static final String OWNER_ADDRESS = "0xe5c86d4ae9a60fbef571181f172035c47e42487a";
    private static final String OWNER_WALLET_JSON = "{\"version\":3,\"id\":\"20777f66-8be6-449c-9459-d55209d9e92f\",\"address\":\"e5c86d4ae9a60fbef571181f172035c47e42487a\",\"Crypto\":{\"ciphertext\":\"4a3c6909632bb5b114bae088c249ab19d6c93947810443e3b4d2e67027702835\",\"cipherparams\":{\"iv\":\"e6d8d8546c70a148302730706b8cf63e\"},\"cipher\":\"aes-128-ctr\",\"kdf\":\"scrypt\",\"kdfparams\":{\"dklen\":32,\"salt\":\"b8afc497f9c80025893bd3816bc18e4d2f75140f4fae624eab8f96182e065ae4\",\"n\":8192,\"r\":8,\"p\":1},\"mac\":\"ebdb24cd8927da1995e53a6d4fc08dca6e9ca8f00ab0ab40a61bd0c8eb9fa727\"}}";
    private static final String OWNER_JSON_KEY = "StudyStudy";

    private static final String NODE_ADDRESS = "https://ropsten.infura.io/v3/e9db10981c2247a99d48032eec288228";

    private static final String CONTRACT_ADDRESS = "0x4acDa791474d524B29F8a0A712342926D8f1171a";
    private static final BigInteger GAS_PRICE_GWEI = new BigInteger("10000000000");
    private static final BigInteger METHOD_GAS_LIMIT = new BigInteger("100000");

    private static final long SLEEP_DURATION = 1000L;
    private static final int ATTEMPTS = 120;

    private final Web3j web3j;
    private final Utils utils;

    public TransactionService() {
        this.web3j = Web3j.build(new HttpService(NODE_ADDRESS));
        this.utils = new Utils();
    }

    public byte[] makeStateFunction(@NotNull String functionName, @NotNull List<FunctionParamDTO> paramList) throws IOException {
        String encodedFunction = getEncodedFunction(functionName, paramList);

        EthCall response = null;
        try {
            response = this.web3j.ethCall(Transaction.createEthCallTransaction(
                    OWNER_ADDRESS, CONTRACT_ADDRESS, encodedFunction),
                    DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IOException("Problem with getting transaction info");
        }

        /* web3j can return result value only as string, so need to convert value */
        byte[] result = convertStringToHex(response.getValue().replace("0x",""));
        return result;
    }

    public void makeTransactionFunction(@NotNull String functionName, @NotNull List<FunctionParamDTO> paramList) throws IOException {
        RawTransaction rawTransaction = getRawMethodTransaction(functionName, paramList);
        Credentials credentials = getCredentials();
        String signedValue = signAndGetHexValue(rawTransaction, credentials);

        EthSendTransaction transactionResponse = null;
        try {
            transactionResponse = this.web3j.ethSendRawTransaction(signedValue).send();
        } catch (Exception e) {
            throw new IOException("Problem with with sending transaction");
        }

        try {
            waitForTransactionReceipt(transactionResponse.getTransactionHash());
        } catch (TransactionException e) {
            throw new IOException(e);
        }
    }

    private TransactionReceipt waitForTransactionReceipt(@NotNull String transactionHash)
            throws IOException, TransactionException {
        Optional<TransactionReceipt> receiptOptional = this.sendTransactionReceiptRequest(transactionHash);

        for(int i = 0; i < ATTEMPTS; ++i) {
            if (receiptOptional.isPresent()) {
                return (TransactionReceipt)receiptOptional.get();
            }

            try {
                Thread.sleep(SLEEP_DURATION);
            } catch (InterruptedException var8) {
                throw new TransactionException(var8);
            }

            receiptOptional = this.sendTransactionReceiptRequest(transactionHash);
        }

        throw new TransactionException("Transaction receipt was not generated after " + SLEEP_DURATION * (long) ATTEMPTS / 1000L + " seconds for transaction: " + transactionHash);
    }

    private Optional<TransactionReceipt> sendTransactionReceiptRequest(@NotNull String transactionHash) throws IOException, TransactionException {
        EthGetTransactionReceipt transactionReceipt = (EthGetTransactionReceipt)this.web3j.ethGetTransactionReceipt(transactionHash).send();
        if (transactionReceipt.hasError()) {
            throw new TransactionException("Error processing request: " + transactionReceipt.getError().getMessage());
        } else {
            return transactionReceipt.getTransactionReceipt();
        }
    }

    private RawTransaction getRawMethodTransaction(@NotNull String functionName, @NotNull List<FunctionParamDTO> paramList) throws IOException {
        RawTransaction rawTransaction = RawTransaction.createTransaction(
                getNonce(OWNER_ADDRESS),
                GAS_PRICE_GWEI,
                METHOD_GAS_LIMIT,
                CONTRACT_ADDRESS,
                getEncodedFunction(functionName, paramList)
        );

        return rawTransaction;
    }

    private String getEncodedFunction(@NotNull String functionName, @NotNull List<FunctionParamDTO> paramList) throws IOException {
        List inputParams = this.utils.getPreparedFunctionInputs(paramList);
        List outputParams = new ArrayList();

        Function function = new Function(functionName, inputParams, outputParams);
        String encodedFunction = FunctionEncoder.encode(function);

        return encodedFunction;
    }

    private BigInteger getNonce(@NotNull String address) throws IOException {
        EthGetTransactionCount ethGetTransactionCount = null;

        try {
            ethGetTransactionCount = this.web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (Exception e) {
            throw new IOException("Can't get nonce");
        }

        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        return nonce;
    }

    private Credentials getCredentials() throws IOException {
        Credentials credentials = null;

        File ownerJsonFile = this.utils.createOwnerJsonFile();
        Files.write(ownerJsonFile.toPath(), OWNER_WALLET_JSON.getBytes());

        try {
            credentials = WalletUtils.loadCredentials(OWNER_JSON_KEY, ownerJsonFile);
        } catch (CipherException | IOException e) {
            throw new IOException("Can't load user credentials. Wrong password or json file");
        }
        this.utils.removeFile(ownerJsonFile);

        return credentials;
    }

    private static String signAndGetHexValue(@NotNull RawTransaction rawTransaction, @NotNull Credentials credentials){
        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        return hexValue;
    }

    public static byte[] convertStringToHex(@NotNull String hex) {
        ArrayList<Byte> byteArray = new ArrayList<>();

        for (int i = 0; i < hex.length(); i += 2) {
            String str = hex.substring(i, i + 2);

            Character responseChar = (char) Integer.parseInt(str, 16);
            byte[] responseByte = responseChar.toString().getBytes();
            /* Read guide */
            if (responseByte.length > 1 && responseByte[responseByte.length - 2] == -61) {
                byteArray.add(getMagicByte(responseByte[responseByte.length - 1]));
            } else {
                byteArray.add(responseByte[responseByte.length - 1]);
            }
        }

        byte[] result = new byte[byteArray.size()];
        for (int i = 0; i < byteArray.size(); i++) {
            result[i] = byteArray.get(i).byteValue();
        }
        return result;
    }

    private static Byte getMagicByte(@NotNull Byte magicByte) {
        Integer magicNumber = 64;
        return (byte)(magicByte+magicNumber);
    }

}

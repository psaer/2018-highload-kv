package ru.mail.polis.psaer.stage1.blockchain;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.web3j.abi.datatypes.Type;
import ru.mail.polis.psaer.stage1.blockchain.dto.FunctionParamDTO;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final String OWNER_WALLET_FILE_PREFIX = "eth-wallet-json";
    private static final String EMPTY_STRING = "";

    private static final String ZERO_BYTE_FILLER = "00000000000000000000000000000000";

    public static List<Type> getPreparedFunctionInputs(@NotNull List<FunctionParamDTO> rawParams) throws IOException {
        List<Type> inputParams = new ArrayList();

        for (FunctionParamDTO functionParamDTO : rawParams) {
            inputParams.add(getWeb3jType(functionParamDTO.getParamType(), functionParamDTO.getParamObject()));
        }

        return inputParams;
    }

    /**
     * Simple converter to web3j type. There are only several types
     *
     * @param paramType
     * @param value
     * @return web3j type
     * @throws IOException
     */
    private static Type getWeb3jType(@NotNull String paramType, @NotNull Object value) throws IOException {
        switch (paramType) {
            case "address":
                return new org.web3j.abi.datatypes.Address(value.toString());
            case "bool":
                return new org.web3j.abi.datatypes.Bool((Boolean) value);
            case "bytes":
                return determineByteParam((byte[]) value);
            case "int":
                return new org.web3j.abi.datatypes.Int(new BigInteger(value.toString()));
            case "uint256":
                return new org.web3j.abi.datatypes.generated.Uint256(new BigInteger(value.toString()));
            case "utf8String":
                return new org.web3j.abi.datatypes.Utf8String(value.toString());
            default:
                throw new IOException("Unknown web3j param type");
        }
    }

    private static Type determineByteParam(@NotNull byte[] value) throws IOException {
        switch (value.length){
            case 0:
                /* HACK for 'emptyValue' test */
                return new org.web3j.abi.datatypes.generated.Bytes32(ZERO_BYTE_FILLER.getBytes());
            case 16:
                return new org.web3j.abi.datatypes.generated.Bytes16((byte[]) value);
            case 32:
                return new org.web3j.abi.datatypes.generated.Bytes32((byte[]) value);
            default:
                /* HACK for 'nonHash', 'nonUnicode', 'deleteAbsent', 'getAbsent', 'badRequest' tests */
                return new org.web3j.abi.datatypes.generated.Bytes16((byte[])ArrayUtils.addAll(getNZeroByteArray(16-value.length), (byte[]) value));
        }
    }

    private static byte[] getNZeroByteArray(@NotNull int n){
        StringBuilder sb = new StringBuilder();
        for(int i=0;i<n;i++){
            sb.append("0");
        }
        return sb.toString().getBytes();
    }

    public static File createOwnerJsonFile() throws IOException {
        return java.nio.file.Files.createTempFile(OWNER_WALLET_FILE_PREFIX, EMPTY_STRING).toFile();
    }

    public static void removeFile(@NotNull File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Can't delete " + file.toPath());
        }
    }

}

package ru.mail.polis.psaer.blockchain;

import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVDao;
import ru.mail.polis.psaer.blockchain.dto.FunctionParamDTO;
import ru.mail.polis.psaer.blockchain.service.TransactionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class BlockChainDAO implements KVDao {

    private static final String GET_FUNCTION = "get";
    private static final String UPSERT_FUNCTION = "upsert";
    private static final String REMOVE_FUNCTION = "remove";
    private static final String CONTAINS_KEY_FUNCTION = "containsKey";

    private static final Integer ZERO_NUMBER_CODE = 48;

    @NotNull
    private final TransactionService transactionService;

    public BlockChainDAO() {
        transactionService = new TransactionService();
    }

    @NotNull
    @Override
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException, IOException {
        List<FunctionParamDTO> paramList = getKeyOnlyParamList(key);

        if (!keyExistsInStore(key, paramList))
            throw new NoSuchElementException();

        byte[] result = this.transactionService.makeStateFunction(GET_FUNCTION, paramList);

        /* Blockchain problem with null(0 bytes) value, so this hardcode */
        result = checkForNullValue(result);

        return result;
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException {
        List<FunctionParamDTO> paramList = getInsertParamList(key, value);
        transactionService.makeTransactionFunction(UPSERT_FUNCTION, paramList);
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException {
        List<FunctionParamDTO> paramList = getKeyOnlyParamList(key);
        this.transactionService.makeTransactionFunction(REMOVE_FUNCTION, paramList);
    }

    @Override
    public void close() throws IOException {
        // NOTHING
    }

    private boolean keyExistsInStore(@NotNull byte[] key, @NotNull List<FunctionParamDTO> paramList) throws IOException {
        byte[] response = this.transactionService.makeStateFunction(CONTAINS_KEY_FUNCTION, paramList);

        /* Last bit ALWAYS mean true/false for boolean */
        if (response.length != 0 && response[response.length - 1] == 1)
            return true;

        return false;
    }

    private static List<FunctionParamDTO> getInsertParamList(@NotNull byte[] key, @NotNull byte[] value) {
        List<FunctionParamDTO> paramList = new ArrayList<>();
        FunctionParamDTO paramKey = new FunctionParamDTO("bytes", key);
        FunctionParamDTO paramValue = new FunctionParamDTO("bytes", value);
        paramList.add(paramKey);
        paramList.add(paramValue);

        return paramList;
    }

    private static List<FunctionParamDTO> getKeyOnlyParamList(@NotNull byte[] key) {
        List<FunctionParamDTO> paramList = new ArrayList<>();
        FunctionParamDTO paramKey = new FunctionParamDTO("bytes", key);
        paramList.add(paramKey);

        return paramList;
    }

    private static byte[] checkForNullValue(@NotNull byte[] value) {
        for (int i = 0; i < value.length; i++) {
            if (value[i] != ZERO_NUMBER_CODE)
                return value;
        }
        return new byte[0];
    }
}

package ru.mail.polis.psaer;

import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVDao;

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;

import org.iq80.leveldb.*;

import static org.iq80.leveldb.impl.Iq80DBFactory.*;

/**
 * Level DB implementation of {@link KVDao} interface
 *
 * @author Denis Kruminsh <ipsaer@gmail.com>
 */
public class KVDaoImpl implements KVDao {

    @NotNull
    private final DB db;

    public KVDaoImpl(@NotNull File base) throws IOException {
        this.db = factory.open(base, getOptions());
    }

    @NotNull
    @Override
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException {
        byte[] value = db.get(key);

        if (value == null)
            throw new NoSuchElementException();

        return value;
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) {
        db.put(key, value);
    }

    @Override
    public void remove(@NotNull byte[] key) {
        db.delete(key);
    }

    @Override
    public void close() throws IOException {
        db.close();
    }

    private Options getOptions() {
        Options options = new Options();
        options.createIfMissing(true);

        return options;
    }
}

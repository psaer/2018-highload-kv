package ru.mail.polis.psaer;

import org.jetbrains.annotations.NotNull;
import ru.mail.polis.KVDao;

import java.io.*;
import java.util.NoSuchElementException;

import org.iq80.leveldb.*;
import ru.mail.polis.psaer.domain.DaoValue;

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
    public byte[] get(@NotNull byte[] key) throws NoSuchElementException, IOException, DBException {
        DaoValue value = internalGet(key);

        if (value.getState() == DaoValue.State.REMOVED) {
            throw new NoSuchElementException();
        }

        return value.getData();
    }

    @Override
    public void upsert(@NotNull byte[] key, @NotNull byte[] value) throws IOException, DBException {
        value = serialize(new DaoValue(DaoValue.State.PRESENT, System.currentTimeMillis(), value));
        db.put(key, value);
    }

    @Override
    public void remove(@NotNull byte[] key) throws IOException, DBException {
        byte[] value = serialize(new DaoValue(DaoValue.State.REMOVED, System.currentTimeMillis(), new byte[]{}));
        db.put(key, value);
    }

    @Override
    public void close() throws IOException {
        db.close();
    }

    public DaoValue internalGet(@NotNull byte[] key) throws IOException, NoSuchElementException, DBException {
        byte[] data = db.get(key);

        if (data == null)
            throw new NoSuchElementException();

        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);

        try {
            return (DaoValue) is.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Error with deserialization");
        }
    }

    public void internalUpsert(@NotNull byte[] key, @NotNull DaoValue daoValue) throws IOException, DBException {
        byte[] valueToPut = serialize(daoValue);
        db.put(key, valueToPut);
    }

    private byte[] serialize(@NotNull Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);

        return out.toByteArray();
    }

    private Options getOptions() {
        Options options = new Options();
        options.createIfMissing(true);

        return options;
    }
}
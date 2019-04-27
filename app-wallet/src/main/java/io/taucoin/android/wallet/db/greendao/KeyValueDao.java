package io.taucoin.android.wallet.db.greendao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import io.taucoin.android.wallet.db.entity.KeyValue;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.
/** 
 * DAO for table "KEY_VALUE".
*/
public class KeyValueDao extends AbstractDao<KeyValue, Long> {

    public static final String TABLENAME = "KEY_VALUE";

    /**
     * Properties of entity KeyValue.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property PubKey = new Property(1, String.class, "pubKey", false, "PUB_KEY");
        public final static Property PriKey = new Property(2, String.class, "priKey", false, "PRI_KEY");
        public final static Property Address = new Property(3, String.class, "address", false, "ADDRESS");
        public final static Property RawAddress = new Property(4, String.class, "rawAddress", false, "RAW_ADDRESS");
        public final static Property Balance = new Property(5, long.class, "balance", false, "BALANCE");
        public final static Property Power = new Property(6, long.class, "power", false, "POWER");
        public final static Property NickName = new Property(7, String.class, "nickName", false, "NICK_NAME");
        public final static Property MiningState = new Property(8, String.class, "miningState", false, "MINING_STATE");
        public final static Property TransExpiry = new Property(9, long.class, "transExpiry", false, "TRANS_EXPIRY");
    }


    public KeyValueDao(DaoConfig config) {
        super(config);
    }
    
    public KeyValueDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"KEY_VALUE\" (" + //
                "\"_id\" INTEGER PRIMARY KEY ," + // 0: id
                "\"PUB_KEY\" TEXT," + // 1: pubKey
                "\"PRI_KEY\" TEXT," + // 2: priKey
                "\"ADDRESS\" TEXT," + // 3: address
                "\"RAW_ADDRESS\" TEXT," + // 4: rawAddress
                "\"BALANCE\" INTEGER NOT NULL ," + // 5: balance
                "\"POWER\" INTEGER NOT NULL ," + // 6: power
                "\"NICK_NAME\" TEXT," + // 7: nickName
                "\"MINING_STATE\" TEXT," + // 8: miningState
                "\"TRANS_EXPIRY\" INTEGER NOT NULL );"); // 9: transExpiry
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"KEY_VALUE\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, KeyValue entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String pubKey = entity.getPubKey();
        if (pubKey != null) {
            stmt.bindString(2, pubKey);
        }
 
        String priKey = entity.getPriKey();
        if (priKey != null) {
            stmt.bindString(3, priKey);
        }
 
        String address = entity.getAddress();
        if (address != null) {
            stmt.bindString(4, address);
        }
 
        String rawAddress = entity.getRawAddress();
        if (rawAddress != null) {
            stmt.bindString(5, rawAddress);
        }
        stmt.bindLong(6, entity.getBalance());
        stmt.bindLong(7, entity.getPower());
 
        String nickName = entity.getNickName();
        if (nickName != null) {
            stmt.bindString(8, nickName);
        }
 
        String miningState = entity.getMiningState();
        if (miningState != null) {
            stmt.bindString(9, miningState);
        }
        stmt.bindLong(10, entity.getTransExpiry());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, KeyValue entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
 
        String pubKey = entity.getPubKey();
        if (pubKey != null) {
            stmt.bindString(2, pubKey);
        }
 
        String priKey = entity.getPriKey();
        if (priKey != null) {
            stmt.bindString(3, priKey);
        }
 
        String address = entity.getAddress();
        if (address != null) {
            stmt.bindString(4, address);
        }
 
        String rawAddress = entity.getRawAddress();
        if (rawAddress != null) {
            stmt.bindString(5, rawAddress);
        }
        stmt.bindLong(6, entity.getBalance());
        stmt.bindLong(7, entity.getPower());
 
        String nickName = entity.getNickName();
        if (nickName != null) {
            stmt.bindString(8, nickName);
        }
 
        String miningState = entity.getMiningState();
        if (miningState != null) {
            stmt.bindString(9, miningState);
        }
        stmt.bindLong(10, entity.getTransExpiry());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public KeyValue readEntity(Cursor cursor, int offset) {
        KeyValue entity = new KeyValue( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1), // pubKey
            cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2), // priKey
            cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3), // address
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4), // rawAddress
            cursor.getLong(offset + 5), // balance
            cursor.getLong(offset + 6), // power
            cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7), // nickName
            cursor.isNull(offset + 8) ? null : cursor.getString(offset + 8), // miningState
            cursor.getLong(offset + 9) // transExpiry
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, KeyValue entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setPubKey(cursor.isNull(offset + 1) ? null : cursor.getString(offset + 1));
        entity.setPriKey(cursor.isNull(offset + 2) ? null : cursor.getString(offset + 2));
        entity.setAddress(cursor.isNull(offset + 3) ? null : cursor.getString(offset + 3));
        entity.setRawAddress(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setBalance(cursor.getLong(offset + 5));
        entity.setPower(cursor.getLong(offset + 6));
        entity.setNickName(cursor.isNull(offset + 7) ? null : cursor.getString(offset + 7));
        entity.setMiningState(cursor.isNull(offset + 8) ? null : cursor.getString(offset + 8));
        entity.setTransExpiry(cursor.getLong(offset + 9));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(KeyValue entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(KeyValue entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(KeyValue entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}

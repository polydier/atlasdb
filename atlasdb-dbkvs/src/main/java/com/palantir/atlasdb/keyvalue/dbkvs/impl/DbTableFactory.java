package com.palantir.atlasdb.keyvalue.dbkvs.impl;

import java.io.Closeable;

import com.palantir.atlasdb.AtlasSystemPropertyManager;

public interface DbTableFactory extends Closeable {
    DbMetadataTable createMetadata(String tableName, ConnectionSupplier conns);
    DbDdlTable createDdl(String tableName, ConnectionSupplier conns, AtlasSystemPropertyManager systemProperties);
    DbReadTable createRead(String tableName, ConnectionSupplier conns);
    DbWriteTable createWrite(String tableName, ConnectionSupplier conns);
    @Override
    void close();
}

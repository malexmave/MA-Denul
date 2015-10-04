package de.velcommuta.denul.service;

import android.content.ContentValues;

import net.sqlcipher.Cursor;

/**
 * Interface for the DatabaseService class, for use with an IBinder implementation
 */
public interface DatabaseServiceBinder {
    /**
     * Open the secure database with the specified password
     * @param password The password to decrypt the database
     */
    void openDatabase(String password);

    /**
     * Method to check if the database is currently open
     * @return true if yes, false if not
     */
    boolean isDatabaseOpen();


    /**
     * Begin a database transaction
     */
    void beginTransaction();


    /**
     * Commit a database transaction
     */
    void commit();


    /**
     * Roll back a database transaction
     */
    void revert();

    
    /**
     * Insert a ContentValues object into the database
     * @param table Table to insert into
     * @param nullable Nullable columns, as per the insert logic of the database interface
     * @param values The ContentValues object
     * @return The RowID of the inserted record, as per the original APIs
     */
    long insert(String table, String nullable, ContentValues values);

    /**
     * Query the SQLite database. Many of the parameters can be nulled if they should not be used
     * @param table The table to query
     * @param columns The columns to return
     * @param selection Filter to query which rows should be displayed
     * @param selectionArgs Arguments to selection (for "?" wildcards)
     * @param groupBy Grouping clause (excluding the GROUP BY statement)
     * @param having Filtering clause (excluding the HAVING)
     * @param orderBy Ordering clause (excluding the ORDER BY)
     * @return A cursor object that can be used to retrieve the data
     */
    Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy);
}
package dev.morphia.query;


import static java.lang.String.format;

/**
 * This class holds various metrics about the results of an update operation.
 */
public class  UpdateResults {

    /**
     * Creates an UpdateResults
     *
     * @param wr the WriteResult from the driver.
     */
    public UpdateResults() {
    }

    /**
     * @return number inserted; this should be either 0/1.
     */
    public int getInsertedCount() {
        return !getUpdatedExisting() ? getN() : 0;
    }

    /**
     * @return the new _id field if an insert/upsert was performed
     */
    public Object getNewId() {
    	return null;
    }

    /**
     * @return number updated
     */
    public int getUpdatedCount() {
        return getUpdatedExisting() ? getN() : 0;
    }

    /**
     * @return true if updated, false if inserted or none effected
     */
    public boolean getUpdatedExisting() {
    	return false;
    }


    /**
     * @return number of affected documents
     */
    protected int getN() {
    	return 0;
    }

    @Override
    public String toString() {
        return format("UpdateResults{wr=%s}", 0);
    }
}

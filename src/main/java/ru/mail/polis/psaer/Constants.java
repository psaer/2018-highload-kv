package ru.mail.polis.psaer;

public class Constants {

    public static final int STATUS_OK = 200;
    public static final int STATUS_SUCCESS_PUT = 201;
    public static final int STATUS_SUCCESS_DELETE = 202;
    public static final int STATUS_BAD_ARGUMENT = 400;
    public static final int STATUS_NOT_FOUND = 404;
    public static final int STATUS_NOT_ENOUGH_REPLICAS = 504;

    public static final String INTERNAL_ERROR_MESSAGE = "Server problem";

    public static final String EMPTY_KEY = "";

    /* HEADERS */
    public static final String HEADER_VALUE_TIMESTAMP = "Value-timestamp";
    public static final String HEADER_VALUE_SIZE = "Value-size";
    public static final String HEADER_VALUE_REMOVED = "Value-removed";
    public static final String HEADER_REPLICA_REQUEST = "Replica-request";
    public static final String HEADER_REPLICA_REQUEST_FOR_VALUE = "Replica-request-for-value";
}

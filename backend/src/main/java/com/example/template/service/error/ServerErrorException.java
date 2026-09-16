package com.example.template.service.error;

/**
 * Something went wrong on our side. The {@code context} is logged (the reason is
 * never sent to the client) and {@code withSuccess} picks which of the two
 * response body shapes this endpoint uses.
 */
public class ServerErrorException extends RuntimeException {

    private final String context;
    private final boolean withSuccess;

    public ServerErrorException(
        String context,
        Throwable cause,
        boolean withSuccess
    ) {
        super(context + ": " + cause.getMessage(), cause);
        this.context = context;
        this.withSuccess = withSuccess;
    }

    public String context() {
        return context;
    }

    public boolean withSuccess() {
        return withSuccess;
    }
}

/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.DataContracts;

public class TransactionRequest {
    private String sourceMsisdn;
    private String requestCommand;

    public String getSourceMsisdn() {
        return sourceMsisdn;
    }

    public void setSourceMsisdn(String sourceMsisdn) {
        this.sourceMsisdn = sourceMsisdn;
    }

    public String getRequestCommand() {
        return requestCommand;
    }

    public void setRequestCommand(String requestCommand) {
        this.requestCommand = requestCommand;
    }
}

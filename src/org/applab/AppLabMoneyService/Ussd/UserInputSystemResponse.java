/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.Ussd;

public class UserInputSystemResponse {
    private int sequenceNum;
    private String transactionId;
    private long threadId;
    private String userInput;
    private String systemResponse;
    private String responseTitle;
    private String extData1;
    private String extData2;

    public int getSequenceNum() {
        return sequenceNum;
    }

    public void setSequenceNum(int sequenceNum) {
        this.sequenceNum = sequenceNum;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getUserInput() {
        return userInput;
    }

    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }

    public String getSystemResponse() {
        return systemResponse;
    }

    public void setSystemResponse(String systemResponse) {
        this.systemResponse = systemResponse;
    }

    public String getResponseTitle() {
        return responseTitle;
    }

    public void setResponseTitle(String responseTitle) {
        this.responseTitle = responseTitle;
    }

    public String getExtData1() {
        return extData1;
    }

    public void setExtData1(String extData1) {
        this.extData1 = extData1;
    }

    public String getExtData2() {
        return extData2;
    }

    public void setExtData2(String extData2) {
        this.extData2 = extData2;
    }
}

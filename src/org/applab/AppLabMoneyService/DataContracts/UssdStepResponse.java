/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.DataContracts;

/**
 * This class extends the UssdResponse to handle more data required by the Transaction Steps
 */
public class UssdStepResponse extends UssdResponse {

    private static final long serialVersionUID = 1L;

    // OMM: I replicate some fields here for purposes of generating an intuitive Bread Crumb
    private int keywordStepId;
    private String keywordCode;
    private boolean useFixedValue;
    private String fixedValue;
    private boolean hasConfirmedPredefInput;

    public int getKeywordStepId() {
        return keywordStepId;
    }

    public void setKeywordStepId(int keywordStepId) {
        this.keywordStepId = keywordStepId;
    }

    public String getKeywordCode() {
        return keywordCode;
    }

    public void setKeywordCode(String keywordCode) {
        this.keywordCode = keywordCode;
    }

    public boolean isUseFixedValue() {
        return useFixedValue;
    }

    public void setUseFixedValue(boolean useFixedValue) {
        this.useFixedValue = useFixedValue;
    }

    public String getFixedValue() {
        return fixedValue;
    }

    public void setFixedValue(String fixedValue) {
        this.fixedValue = fixedValue;
    }

    public boolean getHasConfirmedPredefInput() {
        return hasConfirmedPredefInput;
    }

    public void setHasConfirmedPredefInput(boolean hasConfirmedPredefInput) {
        this.hasConfirmedPredefInput = hasConfirmedPredefInput;
    }

    /**
     * Strip down the UssdStepResponse to get the base UssdResponse
     * 
     */
    public UssdResponse getBaseUssdResponse() {
        return (UssdResponse)this;
    }
}

/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.DataContracts;

import java.io.Serializable;

public class UssdRequest implements Serializable {
    // Required UID for serialization
    private static final long serialVersionUID = -2761065451328878881L;

    // The transaction ID for the request
    public String transactionId;

    // The requesting MSISDN
    public String msisdn;

    // The input from the user
    public String userInput;
}

/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.DataContracts;

import java.io.Serializable;

public class ChargeInfo implements Serializable {
    // Required UID for serialization
    private static final long serialVersionUID = 8574127985370335316L;

    // The charge amount of a chargeable USSD request
    public double amount;

    // The content type of the chargeable USSD request
    public String contentType;
}

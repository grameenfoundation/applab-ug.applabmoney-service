/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.DataContracts;

import java.io.Serializable;

public class UssdResponse implements Serializable {
    // Required UID for serialization
    private static final long serialVersionUID = 1058864762423086756L;

    // Flag for whether the response is a menu or not.
    public boolean isMenu = true;

    // Flag for whether this is the first menu or not
    public boolean isFirst = false;

    // The details of the response to be sent to the subscriber
    public String responseToSubscriber;

    // The Charging information if the request is chargeable.
    public ChargeInfo chargeableInfo;
}

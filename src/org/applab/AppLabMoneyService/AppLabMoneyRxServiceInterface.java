/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService;

import org.applab.AppLabMoneyService.DataContracts.*;

public interface AppLabMoneyRxServiceInterface {
    TransactionResponse sendTransactionRequest(TransactionRequest request);

    UssdResponse handleUSSDRequest(UssdRequest request);
}

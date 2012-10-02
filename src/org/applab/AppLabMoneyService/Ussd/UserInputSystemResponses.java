/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.Ussd;

import java.util.ArrayList;
import org.applab.AppLabMoneyService.HelperUtils;

public class UserInputSystemResponses extends ArrayList<UserInputSystemResponse> {

    private static final long serialVersionUID = 1L;

    /**
     * Returns the Previous Interaction in the USSD Session
     * 
     * @return
     */
    public UserInputSystemResponse getMostRecentRequestRespose() {
        try {
            UserInputSystemResponse uisrRecent = this.get(0);
            for (int i = 1; i < this.size(); i++) {
                if (this.get(i).getSequenceNum() > this.get(i - 1).getSequenceNum()) {
                    uisrRecent = this.get(i);
                }
            }

            return uisrRecent;
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return null;
        }
    }

    /**
     * Returns the Previous Valid Interaction in the USSD Session
     * 
     * @return
     */
    public UserInputSystemResponse getMostRecentValidRequestRespose() {
        try {
            UserInputSystemResponse uisrRecent = this.get(0);

            // First Perform Ordering in Ascending Order and ignore BACK_MENU
            for (int i = 1; i < this.size(); i++) {
                if (this.get(i).getSequenceNum() > this.get(i - 1).getSequenceNum()) {
                    if (this.get(i).getExtData1() != null && !this.get(i).getExtData1().equalsIgnoreCase("BACK_MENU")) {
                        uisrRecent = this.get(i);
                    }
                }
            }

            return uisrRecent;
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return null;
        }
    }

}

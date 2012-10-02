/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.Ussd;

import java.util.ArrayList;

public class UssdStepMenu extends UssdMenu {
    private int keywordStepId;
    private int keywordStepNum;
    private String keywordCode;
    private String keywordName;
    private String keywordStepName;
    private boolean hasPredefInput;
    private int predefInputId;
    private ArrayList<String> predefInputValues;
    private boolean useFixedValue;
    private String fixedValue;

    public int getKeywordStepId() {
        return keywordStepId;
    }

    public void setKeywordStepId(int keywordStepId) {
        this.keywordStepId = keywordStepId;
    }

    public int getKeywordStepNum() {
        return keywordStepNum;
    }

    public void setKeywordStepNum(int keywordStepNum) {
        this.keywordStepNum = keywordStepNum;
    }

    public String getKeywordCode() {
        return keywordCode;
    }

    public void setKeywordCode(String keywordCode) {
        this.keywordCode = keywordCode;
    }

    public String getKeywordName() {
        return keywordName;
    }

    public void setKeywordName(String keywordName) {
        this.keywordName = keywordName;
    }

    public String getKeywordStepName() {
        return keywordStepName;
    }

    public void setKeywordStepName(String keywordStepName) {
        this.keywordStepName = keywordStepName;
    }

    public boolean getHasPredefInput() {
        return hasPredefInput;
    }

    public void setHasPredefInput(boolean hasPredefInput) {
        this.hasPredefInput = hasPredefInput;
    }

    public int getPredefInputId() {
        return predefInputId;
    }

    public void setPredefInputId(int predefInputId) {
        this.predefInputId = predefInputId;
    }

    public ArrayList<String> getPredefInputValues() {
        return predefInputValues;
    }

    public void setPredefInputValues(ArrayList<String> predefInputValues) {
        this.predefInputValues = predefInputValues;
    }

    public boolean getUseFixedValue() {
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

    // OMM: Override the getMenuStringForDisplay() to avoid the Numbering
    public String getMenuStringForDisplay() {
        String stepDisplay = null;

        // First get the stepDisplay without Numbering
        stepDisplay = getMenuString("\r\n", false, UssdMenu.pageSize, this.page);

        // If it has Predef Values, then append them with Numbers
        if (this.predefInputValues != null && !this.predefInputValues.isEmpty()) {
            stepDisplay = stepDisplay.concat(getPredefMenuString("\r\n", true, UssdMenu.pageSize, this.page));
        }

        return stepDisplay;
    }

    public String getPredefMenuString(String delimeter, Boolean includeIndices, Integer itemsPerPage, Integer page) {
        // Sort items
        // OMM: Sorting handled from DB
        String menuString = "";

        // Add page indicator if necessary
        Double totalPages = super.getTotalNumberOfPages(itemsPerPage);
        if (totalPages > 1) {
            menuString += delimeter + "Page " + page + " of " + totalPages.intValue();
        }

        // Return first numberOfItems items
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = this.predefInputValues.size();
        int itemCounter;
        int itemDisplayIndex = 1;

        for (itemCounter = startIndex; itemCounter < endIndex; itemCounter++) {
            menuString += delimeter + ((includeIndices) ? (itemDisplayIndex) + ". " : "") + this.predefInputValues.get(itemCounter);

            if ((itemDisplayIndex == itemsPerPage) && (itemCounter < endIndex)) {

                // We have more items, so add a "More" link instead of this item
                menuString += delimeter + ((includeIndices) ? ((itemDisplayIndex + 1) + ". ") : "") + "Next Page";
                return menuString;
            }
            itemDisplayIndex++;
        }
        return menuString;
    }
}

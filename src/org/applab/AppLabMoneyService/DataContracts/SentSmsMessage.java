package org.applab.AppLabMoneyService.DataContracts;

import java.util.Date;

public class SentSmsMessage {
    private long messageId;
    private String sourceMsisdn;
    private String destMsisdn;
    private String messageText;
    private Date createdTimestamp;
    
    public long getMessageId() {
        return messageId;
    }
    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }
    public String getSourceMsisdn() {
        return sourceMsisdn;
    }
    public void setSourceMsisdn(String sourceMsisdn) {
        this.sourceMsisdn = sourceMsisdn;
    }
    public String getDestMsisdn() {
        return destMsisdn;
    }
    public void setDestMsisdn(String destMsisdn) {
        this.destMsisdn = destMsisdn;
    }
    public String getMessageText() {
        return messageText;
    }
    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }
    public Date getCreatedTimestamp() {
        return createdTimestamp;
    }
    public void setCreatedTimestamp(Date createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }
}

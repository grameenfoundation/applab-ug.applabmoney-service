/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.Ussd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.applab.AppLabMoneyCore.DatabaseHelper;
import org.applab.AppLabMoneyCore.HelperUtils;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;

import java.io.*;

public class UssdRequestThread {
    private long threadId;
    private String transactionId;
    private String sourceMsisdn;
    private String appModule;
    private UserInputSystemResponses threadRequests;
    private String xmlThreadHistory;
    private String finalTransKeyword;

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getSourceMsisdn() {
        return sourceMsisdn;
    }

    public void setSourceMsisdn(String sourceMsisdn) {
        this.sourceMsisdn = sourceMsisdn;
    }

    public String getAppModule() {
        return appModule;
    }

    public void setAppModule(String appModule) {
        this.appModule = appModule;
    }

    public UserInputSystemResponses getThreadRequests() {
        // If the Collection is null, then instantiate it
        // and return an empty collection
        if (threadRequests != null) {
            return threadRequests;
        }
        else {
            return new UserInputSystemResponses();
        }
    }

    public void setThreadRequests(UserInputSystemResponses threadRequests) {
        this.threadRequests = threadRequests;
    }

    public String getXmlThreadHistory() {
        return xmlThreadHistory;
    }

    public void setXmlThreadHistory(String xmlThreadHistory) {
        this.xmlThreadHistory = xmlThreadHistory;
    }

    public String getFinalTransKeyword() {
        return finalTransKeyword;
    }

    public void setFinalTransKeyword(String finalTransKeyword) {
        this.finalTransKeyword = finalTransKeyword;
    }

    public UssdRequestThread(String transactionId, String sourceMsisdn) {
        setTransactionId(transactionId);
        setSourceMsisdn(sourceMsisdn);
    }

    public UssdRequestThread() {

    }

    public static boolean exists(String transactionId, String sourceMsisdn) {
        Connection cn = null;
        int threadCount = 0;

        try {
            // Otherwise, get the Connection
            cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

            PreparedStatement stm = cn.prepareStatement(String.format(
                    "SELECT COUNT(*) TOTAL_THREADS FROM USSD_REQUESTS WHERE TRANSACTION_ID = '%s' AND SOURCE_MSISDN='%s'", transactionId,
                    sourceMsisdn));

            // Execute the Query
            ResultSet result = stm.executeQuery();

            while (result.next()) {
                threadCount = result.getInt(1);
            }

            if (threadCount > 0) {
                return true;
            }
            else {
                return false;
            }
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return false;
        }
        finally {
            try {
                if (cn != null) {
                    cn.close();
                }
            }
            catch (Exception ex2) {
                HelperUtils.writeToLogFile("Server", "ERR: " + ex2.getMessage() + " TRACE: " + ex2.getStackTrace());
            }
        }
    }

    public static UssdRequestThread getRequestThread(String transactionId, String sourceMsisdn) {
        UssdRequestThread thread = null;
        Connection cn = null;

        // OMM: 2012-02-09T2220: Updated the query to also retrieve FINAL_TRANS_KEYWORD
        try {
            // Otherwise, get the Connection
            cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

            PreparedStatement stm = cn
                    .prepareStatement(String
                            .format("SELECT REQUEST_ID, REQUEST_THREAD, FINAL_TRANS_KEYWORD FROM USSD_REQUESTS WHERE TRANSACTION_ID = '%s' AND SOURCE_MSISDN='%s'",
                                    transactionId, sourceMsisdn));

            // Execute the Query
            ResultSet result = stm.executeQuery();

            while (result.next()) {
                thread = new UssdRequestThread(transactionId, sourceMsisdn);
                thread.threadId = result.getLong(1);
                thread.xmlThreadHistory = result.getString(2);
                thread.finalTransKeyword = result.getString(3);
            }

            // Return the Thread
            return thread;

        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return null;
        }
        finally {
            try {
                if (cn != null) {
                    cn.close();
                }
            }
            catch (Exception ex2) {
                HelperUtils.writeToLogFile("Server", "ERR: " + ex2.getMessage() + " TRACE: " + ex2.getStackTrace());
            }
        }
    }

    public boolean saveThreadChanges() {
        Connection cn = null;
        PreparedStatement stm = null;

        try {
            // Otherwise, get the Connection
            cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

            // If it is new INSERT, otherwise UPDATE
            // OMM: 2012-02-09T0547: Added Final Trans Keyword
            if (HelperUtils.TARGET_DATABASE.equalsIgnoreCase("MYSQL")) {
                if (this.threadId == 0) {
                    stm = cn.prepareStatement(String
                            .format("INSERT INTO USSD_REQUESTS ( REQUEST_ID, REQUEST_TIMESTAMP, TRANSACTION_ID, SOURCE_MSISDN, REQUEST_THREAD, FINAL_TRANS_KEYWORD) VALUES (NULL, SYSDATE(), '%s', '%s', '%s', '%s')",
                                    this.transactionId, this.sourceMsisdn, this.xmlThreadHistory, this.finalTransKeyword));
                }
                else {
                    stm = cn.prepareStatement(String
                            .format("UPDATE USSD_REQUESTS SET REQUEST_TIMESTAMP = SYSDATE(), REQUEST_THREAD = '%s', FINAL_TRANS_KEYWORD = '%s' WHERE REQUEST_ID= %s AND TRANSACTION_ID='%s' AND SOURCE_MSISDN='%s'",
                                    this.xmlThreadHistory, this.finalTransKeyword, Long.toString(this.threadId), this.transactionId,
                                    this.sourceMsisdn));
                }
            }
            else if (HelperUtils.TARGET_DATABASE.equalsIgnoreCase("ORACLE")) {
                if (this.threadId == 0) {
                    stm = cn.prepareStatement(String
                            .format("INSERT INTO USSD_REQUESTS ( REQUEST_ID, REQUEST_TIMESTAMP, TRANSACTION_ID, SOURCE_MSISDN, REQUEST_THREAD, FINAL_TRANS_KEYWORD) VALUES (USSD_REQUEST_ID_SEQ.NEXTVAL, SYSDATE, '%s', '%s', '%s', '%s')",
                                    this.transactionId, this.sourceMsisdn, this.xmlThreadHistory, this.finalTransKeyword));
                }
                else {
                    stm = cn.prepareStatement(String
                            .format("UPDATE USSD_REQUESTS SET REQUEST_TIMESTAMP = SYSDATE, REQUEST_THREAD = '%s', FINAL_TRANS_KEYWORD = '%s' WHERE REQUEST_ID= %s AND TRANSACTION_ID='%s' AND SOURCE_MSISDN='%s'",
                                    this.xmlThreadHistory, this.finalTransKeyword, Long.toString(this.threadId), this.transactionId,
                                    this.sourceMsisdn));
                }
            }
            else { // assume MSSQL or Die
                if (this.threadId == 0) {
                    stm = cn.prepareStatement(String
                            .format("INSERT INTO USSD_REQUESTS (REQUEST_TIMESTAMP, TRANSACTION_ID, SOURCE_MSISDN, REQUEST_THREAD, FINAL_TRANS_KEYWORD) VALUES (CURRDATE(), '%s', '%s', '%s', '%s')",
                                    this.transactionId, this.sourceMsisdn, this.xmlThreadHistory, this.finalTransKeyword));
                }
                else {
                    stm = cn.prepareStatement(String
                            .format("UPDATE USSD_REQUESTS SET REQUEST_TIMESTAMP = CURRDATE(), REQUEST_THREAD = '%s', FINAL_TRANS_KEYWORD = '%s' WHERE REQUEST_ID= %s AND TRANSACTION_ID='%s' AND SOURCE_MSISDN='%s'",
                                    this.xmlThreadHistory, this.finalTransKeyword, Long.toString(this.threadId), this.transactionId,
                                    this.sourceMsisdn));
                }
            }

            // Execute the Query
            int result = stm.executeUpdate();

            if (result > 0) {
                return true;
            }
            else {
                return false;
            }
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return false;
        }
        finally {
            try {
                if (cn != null) {
                    cn.close();
                }
            }
            catch (Exception ex2) {
                HelperUtils.writeToLogFile("Server", "ERR: " + ex2.getMessage() + " TRACE: " + ex2.getStackTrace());
            }
        }
    }

    public String getAllUserInputs() {
        return "all";
    }

    public String getAllSystemResponses() {
        return "You will receive response via SMS";
    }

    public String getPreviousSystemResponse() {
        try {
            UserInputSystemResponse uisrPrev = this.getThreadRequests().getMostRecentRequestRespose();
            if (uisrPrev != null) {
                return uisrPrev.getSystemResponse();
            }
            else {
                return "";
            }
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return "";
        }
    }

    public String getPreviousUserInput() {
        try {
            UserInputSystemResponse uisrPrev = this.getThreadRequests().getMostRecentRequestRespose();
            if (uisrPrev != null) {
                return uisrPrev.getUserInput();
            }
            else {
                return "";
            }
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return "";
        }
    }

    public UserInputSystemResponse getPreviousUserInputSystemResponse() {
        return this.getThreadRequests().getMostRecentRequestRespose();
    }

    /**
     * This function gets a Previous Valid Display to be redisplayed. It is called after a user makes Invalid Selection
     * and when asked to correct chooses Back
     * 
     * @return
     */
    public UserInputSystemResponse getPreviousValidUserInputSystemResponse() {
        return this.getThreadRequests().getMostRecentValidRequestRespose();
    }

    public static String transformRequestThreadToXml(UssdRequestThread requestThread) {

        Element element = null;
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            doc.setXmlVersion("1.0");
            doc.setXmlStandalone(true);

            // Create the Root Element
            Element docElement = doc.createElement("ussdRequestThread");
            docElement.setAttribute("threadId", Long.toString(requestThread.getThreadId()));
            docElement.setAttribute("transId", requestThread.transactionId);
            docElement.setAttribute("srcMsisdn", requestThread.sourceMsisdn);
            docElement.setAttribute("appModule", "applabmoney");
            doc.appendChild(docElement);

            // Now Add the List of Elements in a for each block
            if (requestThread.getThreadRequests() != null) {
                for (UserInputSystemResponse uisr : requestThread.getThreadRequests()) {
                    element = doc.createElement("request");
                    element.setAttribute("seqNum", Integer.toString(uisr.getSequenceNum()));
                    element.setAttribute("userInput", uisr.getUserInput());
                    element.setAttribute("sysResp", uisr.getSystemResponse());
                    element.setAttribute("respTitle", uisr.getResponseTitle());
                    element.setAttribute("extData1", uisr.getExtData1());
                    element.setAttribute("extData2", uisr.getExtData2());

                    // Add it to the DocumentElement
                    docElement.appendChild(element);
                }
            }

            // Now Transform the XML Document to return the String Representation
            // set up a transformer
            TransformerFactory transFactory = TransformerFactory.newInstance();
            Transformer transformer = transFactory.newTransformer();

            // Transformation Options
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            // create string from xml tree
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(doc);
            transformer.transform(source, result);
            String xmlString = sw.toString();

            // return the String
            return xmlString;
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return "Error Generating XML";
        }
    }

    /**
     * Gets an XML String representation of the UssdRequestThread and returns the UssdRequestThread
     */
    public static UssdRequestThread transformXmlToRequestThread(String xmlData) {
        UssdRequestThread thread = null;
        try {
            thread = new UssdRequestThread();

            return thread;
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * Gets an XML String representation of the UssdRequestThread and returns the ThreadRequests
     */
    public static UserInputSystemResponses getThreadRequestsFromXml(String xmlData) {
        UserInputSystemResponses threadRequests = null;
        Element element = null;
        UserInputSystemResponse uisr = null;

        try {
            // First initialize an empty collection
            threadRequests = new UserInputSystemResponses();

            // Check whether the xmlData is empty: return empty collection
            if (xmlData.isEmpty()) {
                return threadRequests;
            }

            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

            // OMM: The xmlData string must be passed to the parser via the InputSource>StringReader
            // Otherwise, it throws a java.malformed exception when read directly by the DocumentBuilder
            Document doc = docBuilder.parse(new InputSource(new StringReader(xmlData)));

            doc.getDocumentElement().normalize();
            NodeList nodeList = doc.getElementsByTagName("request");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    element = (Element)node;
                    uisr = new UserInputSystemResponse();
                    uisr.setExtData1(element.getAttribute("extData1"));
                    uisr.setExtData2(element.getAttribute("extData2"));
                    uisr.setResponseTitle(element.getAttribute("respTitle"));
                    uisr.setSystemResponse(element.getAttribute("sysResp"));
                    uisr.setUserInput(element.getAttribute("userInput"));
                    try {
                        uisr.setSequenceNum(Integer.parseInt(element.getAttribute("seqNum")));
                    }
                    catch (Exception numEx) {
                        HelperUtils.writeToLogFile("Server", "ERR: " + numEx.getMessage() + " TRACE: " + numEx.getStackTrace());
                    }

                    // add it to the collection
                    threadRequests.add(uisr);
                }
            }

            return threadRequests;
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return null;
        }
    }
}

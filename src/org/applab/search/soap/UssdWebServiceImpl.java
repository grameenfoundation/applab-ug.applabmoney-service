/*
 * Copyright (C) 2011 Grameen Foundation
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.applab.search.soap;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.naming.NamingException;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.applab.AppLabMoneyCore.*;
import org.applab.AppLabMoneyService.DataContracts.*;
import org.applab.AppLabMoneyService.Ussd.*;



/**
 * 
 * Service Endpoint implementation class
 * 
 */

@WebService(endpointInterface = "org.applab.search.soap.UssdWebServiceInterface")
public class UssdWebServiceImpl implements UssdWebServiceInterface {

    private static String destMessage;

    @Resource
    private WebServiceContext context;

    public int isCompleted = 0;
    
    @Override
    public UssdResponse handleUSSDRequest(UssdRequest request) {
        UssdProcessingEngine upe = null;
        try {
            // Pick AppLabWebService context
            
         // Push the USSD Request to the USSD Processing Engine
            upe = new UssdProcessingEngine();
            return upe.getResponse(request);
        }
        finally {
            // Destroy the instance of the USSD Processing Engine
            if (upe != null) {
                upe = null;
            }
        }
    }  
    
     
    
    private UssdResponse getUssdResponseFromUssdStepResponse(UssdResponse stepResponse) {
        if(stepResponse == null){
            return null;
        }
        
        try {
        UssdResponse response = new UssdResponse();
        response.chargeableInfo = stepResponse.chargeableInfo;
        response.isFirst = stepResponse.isFirst;
        response.isMenu = stepResponse.isMenu;
        response.responseToSubscriber = stepResponse.responseToSubscriber;
        
        return response;
        }
        catch (Exception ex){
            return null;
        }
        
    }
    
//    private void buildMySqlConnectionString(ServletContext context) {
//        String connString = "jdbc:mysql://localhost/applabmoney?user=root&password=applabug";
//
//        try {
//            if (context != null) {
//                String url = context.getInitParameter("mysql-connection-url");
//                String user = context.getInitParameter("mysql-connection-user");
//                String password = context.getInitParameter("mysql-connection-password");
//
//                connString = String.format("%s?user=%s&password=%s", url, user, password);
//            }
//        }
//        catch (Exception ex) {
//            DatabaseHelper.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
//        }
//
//        // Set both DatabaseHelpers
//        // TODO: Needs Harmonization
//        DatabaseHelper.setMySqlConnectionString(connString);
//        DatabaseHelper.setMySqlConnectionString(connString);
//
//    }

    /**
     * This Procedure Processes data received from direct Web Service Invocation or Build over USSD Session It invokes
     * the Core Transaction Processing Engine
     * 
     * @param receivedData
     */
    public static void processReceivedData(String receivedData) {
        String referenceId = "";
        String sourceMsisdn = "";
        String requestCommand = "";
        String[] requestParams = null;
        String requestKeyword = null;

        // Declare an instance of the TransactionProcessingEngine
        TransactionProcessingEngine tpe = null;

        try {
            // Split the Received Message
            String[] requestParts = receivedData.split("~");

            // Check if it is valid
            if (requestParts.length < 3) {
                DatabaseHelper.writeToLogFile("Server", "Invalid Request: ".concat(receivedData));
                return;
            }

            // Otherwise, Continue
            referenceId = requestParts[0];
            sourceMsisdn = requestParts[1];
            requestCommand = requestParts[2];

            // Check whether the system is in Offline Mode
            if (SystemConfigInfo.getMaintenanceModeFlag() == true) {
                destMessage = "The Service is currently unavailable. Please try your transaction later.";
                TransactionProcessingEngine.logOutBoundMessages(destMessage);
                return;
            }

            // Check whether the Transaction Information is OK
            if (requestCommand == null || requestCommand.isEmpty()) {
                destMessage = "The Request you have entered is Invalid.";
                TransactionProcessingEngine.logOutBoundMessages(destMessage);
                return;
            }

            // Split the Transaction String to get the Transaction Elements like the Keyword
            if (requestCommand.startsWith("TEST")) {
                requestParams = requestCommand.split("*");
            }
            else {
                requestParams = requestCommand.split(" ");
            }

            // Get the requestKeyword
            requestKeyword = requestParams[0];

            // push the Keyword for Processing
            tpe = new TransactionProcessingEngine(referenceId, sourceMsisdn, requestKeyword, requestCommand);
            tpe.processRequestCommand();
        }
        catch (Exception ex) {
            DatabaseHelper.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
        }
    }
    
    @Override
    public SentSmsMessage[] getSentMessages(String destMsisdn, int maxItemCount) {

        try {

            // Set the Database Connection String
            //buildMySqlConnectionString(context);

            ArrayList<SentSmsMessage> destMessages = getSentMessages(destMsisdn);

            if (maxItemCount > 5) {
                maxItemCount = 5;
            }

            if (maxItemCount < 1) {
                maxItemCount = 1;
            }

            if (maxItemCount > destMessages.size()) {
                maxItemCount = destMessages.size();
            }

            List<SentSmsMessage> reducedDestMessages = destMessages.subList(0, maxItemCount);

            updatePulledOutboundMessages(reducedDestMessages);

            return reducedDestMessages.toArray(new SentSmsMessage[reducedDestMessages.size()]);
        }
        catch (SQLException e) {

            return new SentSmsMessage[] {};
        }
    }
    

    /*
     * Procedure to get the sent messages from the database
     */
    
    public static ArrayList<SentSmsMessage> getSentMessages(String destMsisdn)
            throws SQLException {
        Connection cn = null;
        PreparedStatement stm = null;
        StringBuilder sb = null;
        String sqlQuery = "";
        ArrayList<SentSmsMessage> destMessages = null;

        try {
            destMessages = new ArrayList<SentSmsMessage>();
            // If the data fields are empty, return false
            if (destMsisdn.trim().isEmpty()) {
                return destMessages;
            }

            // Otherwise, get the Connection
            cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

            sb = new StringBuilder();
            sb.append("SELECT OUTBOUND_MESSAGE_ID, CREATED_TIMESTAMP, DEST_MSISDN, MESSAGE FROM OUTBOUND_MESSAGES");
            sb.append(" WHERE DEST_MSISDN='%s' AND PULLED_BY_CLIENT_FLG = 0");
            sqlQuery = sb.toString();

            stm = cn.prepareStatement(String.format(sqlQuery, destMsisdn));

            ResultSet result = stm.executeQuery();

            while (result.next()) {
                SentSmsMessage message = new SentSmsMessage();
                message.setMessageId(result.getLong("OUTBOUND_MESSAGE_ID"));
                message.setDestMsisdn((result.getString("DEST_MSISDN") == null) ? "NO-MSISDN" : result.getString("DEST_MSISDN").trim());
                message.setSourceMsisdn(SystemConfigInfo.getSenderNumber());
                message.setCreatedTimestamp((result.getDate("CREATED_TIMESTAMP") == null) ? new Date() : result
                        .getDate("CREATED_TIMESTAMP"));
                message.setMessageText((result.getString("MESSAGE") == null) ? "NO-MESSAGE" : result.getString("MESSAGE").trim());
                destMessages.add(message);
            }

            return destMessages;
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            return destMessages;
        }
        finally {
            if (cn != null) {
                cn.close();
            }

            if (stm != null) {
                stm.close();
            }
        }
    }

    public static boolean updatePulledOutboundMessages(List<SentSmsMessage> reducedDestMessages) {
        Connection cn = null;
        StringBuilder sb = null;
        String sqlQuery = "";
        PreparedStatement stm = null;
        int dbStatusCode = 0;
        try {

            // Do nothing if there is no record
            if (reducedDestMessages.size() <= 0) {
                return true;
            }

            StringBuffer outboundMessageIdsSb = new StringBuffer();
            for (SentSmsMessage ssm : reducedDestMessages) {
                outboundMessageIdsSb.append("," + Long.toString(ssm.getMessageId()));
            }

            String outboundMessagesIds = outboundMessageIdsSb.toString();

            if (outboundMessagesIds.startsWith(",")) {
                outboundMessagesIds = outboundMessagesIds.substring(1);
            }

            cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

            sb = new StringBuilder();
            sb.append("UPDATE OUTBOUND_MESSAGES SET PULLED_BY_CLIENT_FLG = 1 WHERE OUTBOUND_MESSAGE_ID IN (%s)");
            sqlQuery = sb.toString();

            stm = cn.prepareStatement(String.format(sqlQuery, outboundMessagesIds));

            // Execute the Query
            dbStatusCode = stm.executeUpdate();

            // TODO: What should be the best return value?
            return ((dbStatusCode > 0) ? true : false);

        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("console", "ERR: " + ex.getMessage() + " TRACE: " + HelperUtils.convertStackTraceToString(ex));
            return false;
        }
    }
}
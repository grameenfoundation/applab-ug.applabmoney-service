/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService;

import javax.jws.WebMethod;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.WebParam;
import javax.jws.WebService;

import org.applab.AppLabMoneyCore.DatabaseHelper;
import org.applab.AppLabMoneyCore.SystemConfigInfo;
import org.applab.AppLabMoneyCore.TransactionProcessingEngine;

import org.applab.AppLabMoneyService.DataContracts.*;
import org.applab.AppLabMoneyService.Ussd.UssdProcessingEngine;

@WebService(name = "UssdWebServiceInterface", serviceName = "UssdWebServiceInterface", targetNamespace = "http://soap.search.applab/")
@SOAPBinding(style = SOAPBinding.Style.RPC, parameterStyle = ParameterStyle.WRAPPED)
public class AppLabMoneyRxService implements AppLabMoneyRxServiceInterface {
    // Message to subscriber
    private static String destMessage = "";

    @WebMethod(operationName = "sendTransactionRequest", action = "sendTransactionRequest")
    public TransactionResponse sendTransactionRequest(@WebParam(name = "TransactionRequest", partName = "TransactionRequest") TransactionRequest request) {
        TransactionResponse response = null;
        String sourceMsisdn = null;
        String requestCommand = null;
        String referenceId = null;
        try {
            // Initialize the Response
            response = new TransactionResponse();
            response.setStatusCode(-99);
            response.setStatusDescription("NOTEXECUTED");

            // Now accept the request, generate a Transaction Reference and forward it to the application Manager
            sourceMsisdn = request.getSourceMsisdn().trim();
            requestCommand = request.getRequestCommand().trim();

            // Validate the requestCommand
            if (sourceMsisdn.isEmpty() || requestCommand.isEmpty()) {
                response.setStatusCode(1);
                response.setStatusDescription("INALID PARAMETER VALUES");

                return response;
            }

            // Continue, Generate a 13-digit ReferenceId
            referenceId = Long.toString(Math.round(Math.random() * 10000000)).concat(Long.toString(Math.round(Math.random() * 100000)));

            // Format the data as ReferenceID~SourceMsisdn~RequestCommand
            String formattedData = String.format("%s~%s~%s", referenceId, sourceMsisdn, requestCommand);

            // Log the Received Transaction Request
            HelperUtils.writeToLogFile("Receive", formattedData);

            // Now send the Transaction to the Core for processing
            processReceivedData(formattedData);

            // Indicate Success Status
            response.setStatusCode(0);
            response.setStatusDescription("SUCCESSFUL");

            return response;
        }
        catch (Exception ex) {
            // Log the Error to the Server Log
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());

            // Return a Fail status with error message as the description
            response.setStatusCode(-99);
            response.setStatusDescription(ex.getMessage());
            return response;
        }
        finally {

        }
    }

    /*
     * WebMethod for receiving and processing USSD Requests
     */
    @WebMethod()
    public UssdResponse handleUSSDRequest(@WebParam(name = "Request", partName = "Request") UssdRequest request) {
        UssdProcessingEngine upe = null;
        try {
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

}

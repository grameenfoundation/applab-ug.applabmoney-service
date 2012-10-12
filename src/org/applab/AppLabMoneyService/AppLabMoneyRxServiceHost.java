/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService;

import java.io.IOException;
import java.net.*;
import javax.xml.ws.Endpoint;

import org.applab.search.soap.*;

public class AppLabMoneyRxServiceHost {

    private static InetAddress hostIpAddress = null;

    public static void main(String args[]) throws IOException {
        HelperUtils.writeToLogFile("console", "APPLAB MONEY WEB SERVICE RECEIVER");
        HelperUtils.writeToLogFile("console", "===================================");
        HelperUtils.writeToLogFile("console", ".");
        HelperUtils.writeToLogFile("console", "Starting the AppLab Money Receiver Web Service Host Container...");
        HelperUtils.writeToLogFile("console", "Creating AppLab Money Receiver Web Service ...");

        // Log to File
        HelperUtils.writeToLogFile("Server", ".");
        HelperUtils.writeToLogFile("Server", "Starting the AppLab Money Receiver Web Service Host Container...");
        HelperUtils.writeToLogFile("Server", "Creating AppLab Money Receiver Web Service ...");

        // First instantiate the Web Service EndPoint from the Interface
        //AppLabMoneyRxServiceInterface appLabMoneyRxSvc = new AppLabMoneyRxService();
        UssdWebServiceInterface appLabMoneyRxSvc = new UssdWebServiceImpl();

        // Get the Host IP Address
        // get the LocalHost IP Address
        hostIpAddress = InetAddress.getLocalHost();
        String hostIpAddressStr = (hostIpAddress != null) ? hostIpAddress.getHostAddress() : "localhost";

        // Give it an Endpoint Address: This I will be getting from a config file
        String url = "http://".concat(hostIpAddressStr).concat(":9908/AppLabMoneyRxService");
        Endpoint endpoint1 = null;

        try {
            // Create and publish the endpoint at the given address
            HelperUtils.writeToLogFile("console", "AppLabMoneyRxServiceHost.main : Publishing AppLabMoneyRxService...");
            HelperUtils.writeToLogFile("Server", "AppLabMoneyRxServiceHost.main : Publishing AppLabMoneyRxService...");
            endpoint1 = Endpoint.publish(url, appLabMoneyRxSvc);
            HelperUtils.writeToLogFile("console", "AppLabMoneyRxServiceHost.main : Published the Implementor...");
            HelperUtils.writeToLogFile("Server", "AppLabMoneyRxServiceHost.main : Published the Implementor...");
        }
        catch (Exception ex) {
            HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
            HelperUtils.writeToLogFile("console", "AppLab Money Receiver Service Exited Abnormally...");
            HelperUtils.writeToLogFile("Server", "AppLab Money Receiver Service Exiting Abnormally...");
            return;
        }

        HelperUtils.writeToLogFile("console", "AppLab Money Receiver Service has been Hosted and running at ..." + url);
        HelperUtils.writeToLogFile("console", "WSDL Metadata URL: " + url.concat("?WSDL"));
        HelperUtils.writeToLogFile("console", "Listening for Transaction Command Requests...");

        // Log to File
        HelperUtils.writeToLogFile("Server", "AppLab Money Receiver Service has been Hosted and running at ..." + url);
        HelperUtils.writeToLogFile("Server", "WSDL Metadata URL: " + url.concat("?WSDL"));
        HelperUtils.writeToLogFile("Server", "Listening for Transaction Command Requests...");

        System.in.read();
        HelperUtils.writeToLogFile("console", "AppLabMoneyRxServiceHost Exited ...");
        HelperUtils.writeToLogFile("Server", "AppLabMoneyRxServiceHost Exited...");

        if (endpoint1 != null && endpoint1.isPublished()) {
            endpoint1.stop();
        }

        return;
    }
}

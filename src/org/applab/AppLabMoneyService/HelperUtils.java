/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;

public class HelperUtils {
    public static final String TARGET_DATABASE = "MYSQL";
    public static final String MSG_COMPLETED_WAIT_SMS = "Your Transaction is being Processed. You will receive a confirmation SMS Message shortly";
    public static final String MSG_COMPLETED_ABNORMALLY = "Sorry, your Transaction Request Terminated abnormally. Please contact Customer Care for more Information.";
    public static final String MSG_INVALID_INPUT = "Invalid Input.";
    public static final String MSG_UNKNOWN_USSD_CODE = "Unknown USSD Code. Please Contact Customer Service for more information.";
    public static final String ROOT_AM_USSD_CODE = "*178*1#";
    public static final String MSG_INVALID_CHOICE = "You entered an Invalid Selection. \r\n 77. Back (Re-Try) \r\n 99. Cancel";

    public static void writeToLogFile(String targetFolder, String theData) {
        File targetFile = null;
        File targetDirectory = null;
        String logDirectoryPath = null;
        String fileName = null;
        String timestamp = null;
        String finalRecordData = null;
        FileOutputStream strm = null;

        try {
            if (targetFolder.compareToIgnoreCase("console") == 0) {
                // Write on the Console Window
                System.out.println(theData);
            }
            else {
                // Write to the File and the specified targetFolder
                logDirectoryPath = new File("Log").getAbsolutePath();

                // Target Folder
                targetDirectory = new File(logDirectoryPath + File.separator + targetFolder);

                // If it doesn't exist create it
                if (!targetDirectory.exists()) {
                    boolean retVal = targetDirectory.mkdirs();
                    if (!retVal) {
                        // A problem occurred. For now just exit.
                        return;
                    }
                }

                // Now build the FileName based on the Date and Time
                long currentTime = System.currentTimeMillis();
                Date dateTime = new Date(currentTime);

                fileName = String.format("%1$td%1$tb%1$tY@%1$tH00.txt", dateTime);

                // Create the file
                targetFile = new File(targetDirectory.getAbsolutePath() + File.separator + fileName);

                // Append Time and Line Separator to the Data Record
                timestamp = String.format("[%1$tH:%1$tM:%1$tS]", dateTime);
                finalRecordData = String.format("%s %s%s", timestamp, theData.trim(), "\r\n");

                // Now Write the Record to the file
                strm = new FileOutputStream(targetFile, true);
                new PrintStream(strm).print(finalRecordData);

                // close the stream
                strm.close();
            }
        }
        catch (Exception ex) {
            System.err.println("ERR: " + ex.getMessage() + " TRACE: " + ex.getStackTrace());
        }
        finally {
            if (strm != null) {
                try {
                    strm.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

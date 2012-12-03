/*
 * 
 * Copyright (c) 2010 AppLab, Grameen Foundation
 * 
 */

package org.applab.AppLabMoneyService.Ussd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.applab.AppLabMoneyService.AppLabMoneyRxService;
import org.applab.AppLabMoneyService.HelperUtils;
import org.applab.AppLabMoneyService.DataContracts.*;
import org.applab.AppLabMoneyCore.*;

public class UssdProcessingEngine {
	public int isCompleted = 0;
	private boolean transParamCaptureCompleted = false;
	private boolean confirmationPromptDone = false;
	private String sourceMsisdn = "";

	public UssdResponse getResponse(UssdRequest request) {
		UssdResponse appResp = new UssdResponse();
		UssdRequestThread urt = null;
		UserInputSystemResponse uisr = null;

		try {
			// Pre-Execution Tasks
			// a. Set Completed flag to false
			transParamCaptureCompleted = false;
			

			// first, Validate the Request
			if (!validateUssdRequest(request)) {
				appResp.isMenu = false;
				appResp.responseToSubscriber = HelperUtils.MSG_INVALID_INPUT;
				ChargeInfo cInfo = new ChargeInfo();
				cInfo.contentType = "COMPLETED";
				appResp.chargeableInfo = cInfo;
				return appResp;
			}

			// Store the Msisdn at a Module Level
			sourceMsisdn = request.msisdn;

			// 1. Check whether this TransactionId exists
			boolean threadExists = UssdRequestThread.exists(
					request.transactionId, request.msisdn);

			// If the Thread Exists, then retrieve the Thread
			if (threadExists) {
				// i. get the Request Thread from the Database
				urt = UssdRequestThread.getRequestThread(request.transactionId,
						request.msisdn);

				// ii. transform the XML String of the ThreadRequests to
				// retrieve the ArrayList of UserInputs
				urt.setThreadRequests(UssdRequestThread
						.getThreadRequestsFromXml(urt.getXmlThreadHistory()));

				// iii. get the most recent interaction
				String prevPrompt = urt.getPreviousSystemResponse();
				UserInputSystemResponse uisrRecent = urt
						.getPreviousUserInputSystemResponse();

				// iv. Process the User Input.
				if (uisrRecent != null) {
					// Variables to be used in this block of code
					long selectedMenuItemId = 0;
					String externalData1Value = "BACK_MENU";
					String externalData2Value = "";

					// Check whether the previous display was a Menu, Step or
					// Bak_Menu
					if (uisrRecent.getExtData1().toUpperCase()
							.equalsIgnoreCase("MENU")) {
						boolean menuItemHasKeyword = false;
						UssdStepResponse firstStepResp = null;

						// Get the next menu
						// First get the actual menuSelection by User
						String actualItemSelectedByUser = getDisplayItemByUserSelection(
								prevPrompt, request.userInput);

						if (actualItemSelectedByUser != null
								&& !actualItemSelectedByUser.isEmpty()) {
							// Get its MenuItemID
							selectedMenuItemId = getSelectedMenuItemId(actualItemSelectedByUser);

							// If selectedMenuItemId is non-zero
							if (selectedMenuItemId != 0) {
								// Then now use that to retrieve the Sub-Menu
								// OMM: 2012-02-09T03:48
								// Include a procedure to first check whether
								// the selected option should load a sub menu
								// or start Transaction Steps
								menuItemHasKeyword = menuItemHasTransKeyword(selectedMenuItemId);

								// If it has Keyword, then Load the First Step
								// of the Transaction Keyword
								// OMM: here I get a UssdStepMenu, which extends
								// UssdMenu
								if (menuItemHasKeyword) {
									firstStepResp = getFirstUssdStepMenuResponse(
											request, selectedMenuItemId);
									appResp = firstStepResp
											.getBaseUssdResponse();

									// Build the final Transaction Keyword
									// Thread from the UssdStepResponse
									urt.setFinalTransKeyword(firstStepResp
											.getKeywordCode());

									// Check whether the Step has Predefined
									// Input and Flag accordingly
									if (firstStepResp
											.getHasConfirmedPredefInput()) {
										externalData2Value = "BNLOOKUP";
									}

									// Check if the Value for this step is FIXED
									// i.e. preset set extData2 accordingly
									// NB: Ensure PRESET takes priority by
									// setting it last
									if (firstStepResp.isUseFixedValue()
											&& (firstStepResp.getFixedValue() != null && !firstStepResp
													.getFixedValue().isEmpty())) {
										externalData2Value = "PRESET";
									}

									// Specify that we are dealing with a STEP
									externalData1Value = "STEP";
								} else { // MenuItem has no Keyword
									appResp = getUssdSubMenu(request,
											selectedMenuItemId);
									externalData1Value = "MENU";
								}
							} else { // selectedMenuItemId==0
								appResp.responseToSubscriber = HelperUtils.MSG_INVALID_CHOICE;
								externalData1Value = "BACK_MENU";
							}
						} else { // Actual Item selected by User is NULL
							appResp.responseToSubscriber = HelperUtils.MSG_INVALID_CHOICE;
							externalData1Value = "BACK_MENU";
						}

						// ii. Capture the UserInputSystemResponse
						// OMM: I have denormalized some aspects that are from
						// Parent Thread i.e. TransactionID
						uisr = new UserInputSystemResponse();

						// Increment the Sequence Number
						uisr.setSequenceNum(urt.getThreadRequests().size() + 1);
						uisr.setTransactionId(request.transactionId);
						uisr.setUserInput(request.userInput);
						uisr.setExtData1(externalData1Value);
						uisr.setExtData2(externalData2Value);

						// log the response, before committing the same
						uisr.setResponseTitle("Applab Money");
						uisr.setSystemResponse(appResp.responseToSubscriber);

						// Add the new UISR to the existing Thread Requests
						UserInputSystemResponses tRequests = urt
								.getThreadRequests();
						tRequests.add(uisr);
						urt.setThreadRequests(tRequests);

						// Generate the XML Representation of the
						// UssdRequestsThread
						String xmlThreadHistory = UssdRequestThread
								.transformRequestThreadToXml(urt);
						urt.setXmlThreadHistory(xmlThreadHistory);

						// Save the Thread to DB
						boolean retVal = urt.saveThreadChanges();

						// If an error occurs then force Failure
						if (!retVal) {
							appResp.responseToSubscriber = "Error Processing USSD Request";
						}

						// OMM: Strip the Menu Item IDs from the Display String
						// before sending it to Phone
						appResp.responseToSubscriber = stripMenuItemIdsFromDisplay(appResp.responseToSubscriber);

						// If the step has a Fixed Response, call this method
						// Recursively
						// To enter the fixed Value without Prompting the
						// Customer
						// Otherwise let the Customer Enter the Value
						if (menuItemHasKeyword) {
							if (firstStepResp.isUseFixedValue()
									&& (firstStepResp.getFixedValue() != null && !firstStepResp
											.getFixedValue().isEmpty())) {
								// Build a New UssdRequest Object
								UssdRequest requestFixed = new UssdRequest();
								requestFixed.msisdn = request.msisdn;
								requestFixed.transactionId = request.transactionId;
								requestFixed.userInput = firstStepResp
										.getFixedValue();

								// Call this method again-recursive call and
								// pass the new UssdRequest
								UssdResponse respFixed = getResponse(requestFixed);

								// Return the Response
								return respFixed;
							}
						}

						// Return the result
						return appResp;

					} else if (uisrRecent.getExtData1().toUpperCase()
							.equalsIgnoreCase("STEP")) { // If Previous Prompt
															// was a STEP and
															// User
															// has responded

						// Get Answered Step Prompt without the Title i.e. the
						// actual Question, but includes
						// KeywordStepID
						String actualStepPrompt = getAnsweredStepPrompt(prevPrompt);

						// Get the Step ID for what the user has answered to
						long answeredStepId = getAnsweredKeywordStepId(actualStepPrompt);

						// Add This User Input to the threadRequests
						uisr = new UserInputSystemResponse();

						// Increment the Sequence Number
						uisr.setSequenceNum(urt.getThreadRequests().size() + 1);
						uisr.setTransactionId(request.transactionId);
						uisr.setUserInput(request.userInput);

						// If the prevPrompt i.e. previous step had a Predefined
						// Input List
						// then Resolve the selection of the user and get the
						// Actual Option Text e.g. 1. NONE
						// Treat this like a Menu selection and use same
						// procedure
						String userInput = "";
						if (uisrRecent.getExtData2().equalsIgnoreCase(
								"BNLOOKUP")) {
							String selectedPredef = getDisplayItemByUserSelection(
									prevPrompt, request.userInput.trim());
							// Remove the numbering
							if (selectedPredef.contains(".")) {
								selectedPredef = selectedPredef.substring(
										selectedPredef.indexOf(".")+1,
										selectedPredef.length());
							}
							// remove the Input_Item_Id
							if (selectedPredef.contains("~")) {
								selectedPredef = selectedPredef.substring(1,
										selectedPredef.lastIndexOf("~"));
							}
							userInput = selectedPredef;

						} else { // Just take User Input as is
							userInput = request.userInput;
						}

						// Do some fine-tuning on the user Input before
						// appending it to the Transaction Keyword
						// i. Replace Spaces with Asterisks or another wild
						// character
						String refinedUserInput = userInput.trim().replaceAll(
								" ", "*");

						// Update the Final Trans Command to include the new
						// item
						// OMM: These NULLReferencePointer crap are really
						// causing me grief!!!!!!!!!!!!!!!!
						if (urt.getFinalTransKeyword() != null) {
							urt.setFinalTransKeyword(urt.getFinalTransKeyword()
									.concat(" ").concat(refinedUserInput));
						} else {
							urt.setFinalTransKeyword(refinedUserInput);
						}

						// Check whether there is another Step
						UssdStepResponse stepResp = getNextUssdStepMenuResponse(
								request, answeredStepId);
						appResp = stepResp.getBaseUssdResponse();

						// log the response, before committing the same
						if (appResp.responseToSubscriber.contains("\r\n")) {
							uisr.setResponseTitle(appResp.responseToSubscriber
									.substring(0, appResp.responseToSubscriber
											.indexOf("\r\n")));
						} else {
							uisr.setResponseTitle("AppLab Money");
						}
						uisr.setSystemResponse(appResp.responseToSubscriber);

						// Ensure the UISR is defined as a STEP
						uisr.setExtData1("STEP");

						// Check whether to mark as BNLOOKUP
						if (stepResp.getHasConfirmedPredefInput()) {
							uisr.setExtData2("BNLOOKUP");
						}

						// OMM: 2012-02-27
						// If the Prompt has a Fixed Value, then update ExtData2
						// to indicate PRESET-VALUE
						if (stepResp.isUseFixedValue()
								&& (stepResp.getFixedValue() != null && !stepResp
										.getFixedValue().isEmpty())) {
							uisr.setExtData2("PRESET");
						}

						// Add the new UISR to the existing Thread Requests
						// OMM: This code needs to be reviewed
						UserInputSystemResponses tRequests = urt
								.getThreadRequests();
						tRequests.add(uisr);
						urt.setThreadRequests(tRequests);

						// Generate the XML Representation of the
						// UssdRequestsThread
						String xmlThreadHistory = UssdRequestThread
								.transformRequestThreadToXml(urt);
						urt.setXmlThreadHistory(xmlThreadHistory);

						// Save the Thread to DB
						boolean retVal = urt.saveThreadChanges();

						// If an error occurs then force Failure
						if (!retVal) {
							appResp.responseToSubscriber = "Error Processing USSD Request";
						}

						// OMM: Strip the Menu Item IDs from the Display String
						// before sending it to Phone
						appResp.responseToSubscriber = stripMenuItemIdsFromDisplay(appResp.responseToSubscriber);

						// If the Parameter Capture is Complete, then forward
						// the final string to Core Transaction
						// Processing Engine
						// TODO: Check if TRANSACTION_KEYWORD Requires
						// Confirmation
						// I will have an internal servlet that I can just call
						// to invoke the core.
						// so that I separate it from the Ussd Processor
						if (transParamCaptureCompleted) {
							// Prepare the Keyword String for the Core TP Engine
							String transCommand = String.format("%s~%s~%s",
									urt.getTransactionId(),
									urt.getSourceMsisdn(),
									urt.getFinalTransKeyword());
							
							//Check whether the Keyword requires Prompt
							if (true && null != urt.getFinalTransKeyword()) {
							    String confirmationTitle = "CONFIRMATION:\r\n";
							    String confirmationPrompt = getConfirmationPrompt(urt.getFinalTransKeyword());							    
							    String confirmOptions = getConfirmationOptions();
							    String finalPromptMsg = confirmationTitle + confirmationPrompt.concat(confirmOptions);
							    
							    // Add This User Input to the threadRequests
		                        uisr = new UserInputSystemResponse();

		                        // Increment the Sequence Number
		                        uisr.setSequenceNum(urt.getThreadRequests().size() + 1);
		                        uisr.setTransactionId(request.transactionId);
		                        uisr.setUserInput(request.userInput);
		                        
		                        uisr.setSystemResponse(finalPromptMsg);

		                        // Ensure the UISR is defined as a CONFIRM_SCREEN
		                        uisr.setExtData1("CONFIRM_SCREEN");		                        
		                        uisr.setExtData2("BNLOOKUP_CONFIRM");
		                        
		                        appResp.responseToSubscriber = uisr.getSystemResponse();
		                        
		                     // Add the new UISR to the existing Thread Requests
		                        tRequests = urt.getThreadRequests();
		                        tRequests.add(uisr);
		                        urt.setThreadRequests(tRequests);

		                        // Generate the XML Representation of the
		                        // UssdRequestsThread
		                        xmlThreadHistory = UssdRequestThread
		                                .transformRequestThreadToXml(urt);
		                        urt.setXmlThreadHistory(xmlThreadHistory);

		                        // Save the Thread to DB
		                        retVal = urt.saveThreadChanges();

		                        // If an error occurs then force Failure
		                        if (!retVal) {
		                            appResp.responseToSubscriber = "Error Processing USSD Request";
		                        }
		                        	
		                        //For now just return
		                        return appResp;
							}
							// Invoke the Static Method on the RxService
							HelperUtils.writeToLogFile("console", "Sending to TPE: " + transCommand);
							AppLabMoneyRxService
									.processReceivedData(transCommand);
						}

						// If the step has a Fixed Response, call this method
						// Recursively
						// To enter the fixed Value without Prompting the
						// Customer
						// Otherwise let the Customer Enter the Value
						if (stepResp.isUseFixedValue()
								&& (stepResp.getFixedValue() != null && !stepResp
										.getFixedValue().isEmpty())) {
							// Build a New UssdRequest Object
							UssdRequest requestFixed = new UssdRequest();
							requestFixed.msisdn = request.msisdn;
							requestFixed.transactionId = request.transactionId;
							requestFixed.userInput = stepResp.getFixedValue();

							// Call this method again-recursive call and pass
							// the new UssdRequest
							UssdResponse respFixed = getResponse(requestFixed);

							// Return the Response
							return respFixed;
						}

						// then push it to the user
						appResp.isMenu = false;
						return appResp;

					} else if (uisrRecent.getExtData1().toUpperCase()
							.equalsIgnoreCase("BACK_MENU")) { // If it was
																// BACK_MENU
																// used
																// to Correct
																// User
																// Input
						// First get the actual menuSelection by User
						String actualItemSelectedByUser = getDisplayItemByUserSelection(
								prevPrompt, request.userInput);
						// If actualItemSelectedByUser is null, then indicate
						// invalid option
						if (actualItemSelectedByUser != null
								&& !actualItemSelectedByUser.isEmpty()) {
							// First check whether the selection is to
							// Correction of Invalid Input
							if (actualItemSelectedByUser.toUpperCase().trim()
									.startsWith("77.")
									|| actualItemSelectedByUser.toUpperCase()
											.endsWith("BACK")) {
								UserInputSystemResponse uisrRecentValid = null;
								// Load the Previous Menu
								uisrRecentValid = urt
										.getPreviousValidUserInputSystemResponse();
								if (uisrRecentValid != null) {

									uisrRecentValid.setSequenceNum(urt
											.getThreadRequests().size() + 1);

									// This time assign the response to
									// subscriber from the thread
									appResp.responseToSubscriber = uisrRecentValid
											.getSystemResponse();

									// OMM: I need a way of merging these codes
									UserInputSystemResponses tRequests = urt
											.getThreadRequests();
									tRequests.add(uisrRecentValid);
									urt.setThreadRequests(tRequests);

									// Generate the XML Representation of the
									// UssdRequestsThread
									String xmlThreadHistory = UssdRequestThread
											.transformRequestThreadToXml(urt);
									urt.setXmlThreadHistory(xmlThreadHistory);

									// Save the Thread to DB
									boolean retVal = urt.saveThreadChanges();

									// If an error occurs then force Failure
									if (!retVal) {
										appResp.responseToSubscriber = "Error Processing USSD Request";
									}

									// OMM: Strip the Menu Item IDs from the
									// Display String before sending it to Phone
									appResp.responseToSubscriber = stripMenuItemIdsFromDisplay(appResp.responseToSubscriber);

									// Return the result
									return appResp;
								} else { // No Recent Valid Display found
									appResp.responseToSubscriber = "Error Processing USSD Request";
								}
							} else { // User Selected Cancel or Anything else
								appResp.responseToSubscriber = "Your Transaction has been Cancelled.";
							}
						} else {
							appResp.responseToSubscriber = HelperUtils.MSG_INVALID_CHOICE;
							externalData1Value = "BACK_MENU";
						}
					} else { // External Data 1 is Unknown
						appResp.responseToSubscriber = "Error Processing USSD Request";
					}
				} else { // Otherwise, if there is no previous history then
							// display error
					appResp.responseToSubscriber = "Sorry, unable to service your request at this time. Could not retrieve session history";
					appResp.isMenu = false;
					return appResp;
				}
			} else { // If the thread doesn't exist
						// Check whether it is the valid USSD Code for AppLab
						// Money
				if (!request.userInput
						.equalsIgnoreCase(HelperUtils.ROOT_AM_USSD_CODE)) {
					appResp = new UssdResponse();
					appResp.responseToSubscriber = HelperUtils.MSG_UNKNOWN_USSD_CODE;
					// Indicate finished: for lack of a better option am using
					// Charge Info, for now
					ChargeInfo cInfo = new ChargeInfo();
					cInfo.contentType = "COMPLETED";
					appResp.chargeableInfo = cInfo;
					appResp.isMenu = false;

					// Return
					return appResp;
				}
				// i. Create it
				urt = new UssdRequestThread(request.transactionId,
						request.msisdn);
				urt.setAppModule("APPLABMONEY"); // this is mostly on the
													// Dispatcher

				// ii. Capture the first UserInputSystemResponse, usually the
				// root command:
				// I have denormalized some aspects that are from Parent Thread
				// i.e. TransactionID
				uisr = new UserInputSystemResponse();
				uisr.setSequenceNum(1);
				uisr.setTransactionId(request.transactionId);
				uisr.setUserInput(request.userInput);

				// Since this is the first request by user, get the Root Menu
				appResp = getRootAppLabMoneyMenu(request);

				// log the response, before committing the same
				uisr.setResponseTitle("Applab Money");
				uisr.setSystemResponse(appResp.responseToSubscriber);
				uisr.setExtData1("MENU");

				// Add the new UISR to the Thread Requests
				UserInputSystemResponses tRequests = new UserInputSystemResponses();
				tRequests.add(uisr);
				urt.setThreadRequests(tRequests);

				// Generate the XML Representation of the UssdRequestsThread
				String xmlThreadHistory = UssdRequestThread
						.transformRequestThreadToXml(urt);
				urt.setXmlThreadHistory(xmlThreadHistory);

				// Save the Thread to DB
				boolean retVal = urt.saveThreadChanges();

				// If an error occurs then force Failure
				if (!retVal) {
					appResp.responseToSubscriber = "Error Processing USSD Request";
				}

				// OMM: Strip the Menu Item IDs from the Display String before
				// sending it to Phone
				appResp.responseToSubscriber = stripMenuItemIdsFromDisplay(appResp.responseToSubscriber);

				// Return the result
				return appResp;
			}
		}

		catch (Exception e) {
			HelperUtils.writeToLogFile("Server", e.getMessage());
			appResp.responseToSubscriber = "Sorry, Processing Error occurred while processing your request. \r\nPlease contact Customer Care.";
			appResp.isMenu = false;
		}

		return appResp;
	}

	private UssdMenu createRootMenu() {
		Connection cn = null;
		UssdMenu rootMenu = null;

		try {
			// Get Menus to Exclude in final Ussd Screen
			ArrayList<Integer> excludeMenuItemIds = getMenuItemsToExclude(sourceMsisdn);

			rootMenu = new UssdMenu();
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			PreparedStatement stm = cn
					.prepareStatement("SELECT MENU_ITEM_ID, MENU_ITEM_NAME FROM USSD_MENU_ITEMS WHERE PARENT_MENU_ID IS NULL AND ENABLED_FLG=1 ORDER BY MENU_ITEM_ORDER ASC");

			// Execute the Query
			ResultSet result = stm.executeQuery();

			while (result.next()) {
				// Build the String containing menuItemName then menuItemId
				// separated by the Tide character
				String menuItem = String.format("%s~%s", result.getString(2),
						Long.toString(result.getLong(1)));

				// Process Exclusion
				if (excludeMenuItemIds.contains(result.getInt(1))) {
					continue;
				}

				rootMenu.addItem(menuItem);
			}

			return rootMenu;

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return rootMenu;
		}
	}

	private UssdMenu createSubMenu(long parentMenuItemId) {
		Connection cn = null;
		UssdMenu subMenu = null;

		try {
			// Get Menus to Exclude in final Ussd Screen
			ArrayList<Integer> excludeMenuItemIds = getMenuItemsToExclude(sourceMsisdn);

			subMenu = new UssdMenu();
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			PreparedStatement stm = cn
					.prepareStatement(String
							.format("SELECT MENU_ITEM_ID, MENU_ITEM_NAME FROM USSD_MENU_ITEMS WHERE PARENT_MENU_ID = %s AND ENABLED_FLG=1 ORDER BY MENU_ITEM_ORDER ASC",
									Long.toString(parentMenuItemId)));

			// Execute the Query
			ResultSet result = stm.executeQuery();

			while (result.next()) {
				// Build the String containing menuItemName then menuItemId
				// separated by the Tide character
				String menuItem = String.format("%s~%s", result.getString(2),
						Long.toString(result.getLong(1)));

				// Process Exclusion
				if (excludeMenuItemIds.contains(result.getInt(1))) {
					continue;
				}

				subMenu.addItem(menuItem);
			}
			return subMenu;

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return subMenu;
		}

	}

	public UssdResponse getRootAppLabMoneyMenu(UssdRequest request)
			throws Exception {

		// Mark response as a menu
		UssdResponse appResp = new UssdResponse();
		appResp.isFirst = true;

		// Obtain the active categories from the database
		// OMM: the menu at this point has menuItemName~menuItemId
		 UssdMenu rootMenu = createRootMenu();
		 rootMenu.setTitle("me2me");

		// OMM: at this point consider removing the ID
		 appResp.responseToSubscriber = rootMenu.getMenuStringForDisplay();
		
		//OMM: Challenge with the below is that we are hard-coding the ParentMenuId = 5
		//Yet we are not guaranteed that it will always be like that when the database is populated
		//appResp = getUssdSubMenu(request, 5);
		isCompleted = 0;

		return appResp;
	}

	public UssdResponse getUssdSubMenu(UssdRequest request,
			long parentMenuItemId) throws Exception {

		// Mark response as a menu
		UssdResponse appResp = new UssdResponse();
		appResp.isFirst = true;

		// Obtain the active categories from the database
		// OMM: the menu at this point has menuItemName~menuItemId
		UssdMenu subMenu = createSubMenu(parentMenuItemId);
		subMenu.setTitle("Me2Me");

		// OMM: at this point consider removing the ID
		appResp.responseToSubscriber = subMenu.getMenuStringForDisplay();
		isCompleted = 0;
		return appResp;
	}

	/**
	 * Get the UssdStepResponse derived from the UssdStepMenu I use
	 * UssdStepResponse which extends UssdResponse to get more info Then I will
	 * strip it down when sending out to Phone
	 */

	public UssdStepResponse getFirstUssdStepMenuResponse(UssdRequest request,
			long menuItemId) throws Exception {

		// Mark response as a menu
		UssdStepResponse appStepResp = new UssdStepResponse();
		appStepResp.isFirst = true;

		// OMM: the step menu at this point has keywordStepName~keywordStepId
		UssdStepMenu stepMenu = createFirstKeywordStep(menuItemId);
		stepMenu.setTitle(stepMenu.getKeywordName());

		// OMM: at this point consider removing the ID
		appStepResp.responseToSubscriber = stepMenu.getMenuStringForDisplay();

		// OMM: Set the extra extended Properties
		appStepResp.setKeywordCode(stepMenu.getKeywordCode());
		appStepResp.setKeywordStepId(stepMenu.getKeywordStepId());

		// Determine whether the Step has a Fixed Value specified i.e. No User
		// Response required
		appStepResp.setUseFixedValue(stepMenu.getUseFixedValue());
		appStepResp.setFixedValue(stepMenu.getFixedValue());

		// If there is confirmed Pre-defined Input then flag the StepResponse
		if (stepMenu.getHasPredefInput()
				&& (stepMenu.getPredefInputValues() != null && !stepMenu
						.getPredefInputValues().isEmpty())) {
			appStepResp.setHasConfirmedPredefInput(true);
		} else {
			appStepResp.setHasConfirmedPredefInput(false);
		}

		isCompleted = 0;
		return appStepResp;
	}

	/**
	 * Load the First Step of the Transaction
	 */
	private UssdStepMenu createFirstKeywordStep(long menuItemId) {
		Connection cn = null;
		UssdStepMenu stepMenu = null;
		StringBuilder sb = null;

		try {
			stepMenu = new UssdStepMenu();
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			sb = new StringBuilder();
			sb.append("SELECT TK.KEYWORD_CODE, TK.KEYWORD_NAME, KS.KEYWORD_STEP_ID, KS.STEP_MENU_NAME, KS.STEP_NUMBER, KS.HAS_PREDEF_INPUT,KS.PREDEF_INPUT_ID,KS.USE_FIXED_VALUE,KS.FIXED_VALUE");
			sb.append(" FROM KEYWORD_STEPS KS");
			sb.append(" JOIN TRANSACTION_KEYWORDS TK ON KS.KEYWORD_ID = TK.KEYWORD_ID");
			sb.append(" JOIN USSD_MENU_ITEMS UMI ON TK.KEYWORD_ID = UMI.KEYWORD_ID");
			sb.append(" WHERE UMI.MENU_ITEM_ID = %s AND STEP_NUMBER=1");

			PreparedStatement stm = cn.prepareStatement(String.format(
					sb.toString(), Long.toString(menuItemId)));

			// Execute the Query
			ResultSet result = stm.executeQuery();

			// OMM: There will be only 1 Step in each Displayed Screen, Unlike
			// the Sub-Menus
			while (result.next()) {
				// Build the String containing StepMenuName then keywordStepId
				// separated by the Tide character
				String menuItem = String.format("%s~%s", result.getString(4),
						Long.toString(result.getInt(3)));
				stepMenu.addItem(menuItem);
				stepMenu.setKeywordCode(result.getString(1));
				stepMenu.setKeywordName(result.getString(2));
				stepMenu.setKeywordStepId(result.getInt(3));
				stepMenu.setKeywordStepName(result.getString(4));
				stepMenu.setKeywordStepNum(result.getInt(5));
				boolean hasPredefInput = (result.getInt(6) == 1) ? true : false;
				stepMenu.setHasPredefInput(hasPredefInput);
				stepMenu.setPredefInputId(result.getInt(7));
				boolean useFixedValue = (result.getInt(8) == 1) ? true : false;
				stepMenu.setUseFixedValue(useFixedValue);
				stepMenu.setFixedValue(result.getString(9));
			}

			// If the Step has Predefined Inputs Options: then load them also
			// if (stepMenu.getHasPredefInput()) {
			// stepMenu.setPredefInputValues(getPredefInputItems(stepMenu
			// .getPredefInputId()));
			// }
			ArrayList<String> dateListForCrgl = new ArrayList<String>();
			if (stepMenu.getHasPredefInput()) {
				if (stepMenu.getKeywordStepId() == 29) {
					// && (stepMenu.getKeywordCode() == "CRGL")) {
					ArrayList<String> predefListForCrgl = getPredefInputItems(stepMenu
							.getPredefInputId());
					SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy");
					Calendar cal = Calendar.getInstance();
					String maturityDateStr = "";
					Date maturityDate = null;

					for (int countOfMonths = 0; countOfMonths < predefListForCrgl
							.size(); countOfMonths++) {
						// cal.add(Calendar.MONTH, Integer
						// .parseInt(predefListForCrgl.get(countOfMonths)));
						cal.add(Calendar.MONTH, 1);
						if (countOfMonths == 1) {
							cal.add(Calendar.DAY_OF_MONTH, 1);
						}
						maturityDateStr = df.format(cal.getTime());
						dateListForCrgl.add(maturityDateStr);
					}
					stepMenu.setPredefInputValues(dateListForCrgl);
				} else {
					stepMenu.setPredefInputValues(getPredefInputItems(stepMenu
							.getPredefInputId()));
				}
			}
			return stepMenu;
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return stepMenu;
		}
	}

	/**
	 * Get the UssdStepResponse derived from the UssdStepMenu I use
	 * UssdStepResponse which extends UssdResponse to get more info Then I will
	 * strip it down when sending out to Phone This time the Next ResponseMenu
	 * is derived from the current keywordStepId
	 */

	public UssdStepResponse getNextUssdStepMenuResponse(UssdRequest request,
			long currentKeywordStepId) throws Exception {

		// Mark response as a STEP
		UssdStepResponse appStepResp = new UssdStepResponse();
		appStepResp.isFirst = false;

		// OMM: the step menu at this point has keywordStepName~keywordStepId
		UssdStepMenu stepMenu = createNextKeywordStep(currentKeywordStepId);
		stepMenu.setTitle(stepMenu.getKeywordName());

		// Check whether there is a NEXT Response Prompt Really
		// Remember if last step was expected to be final then mark that the
		// Ussd session terminated abnormally
		if (stepMenu.getKeywordStepId() <= 0) {
			appStepResp.responseToSubscriber = HelperUtils.MSG_COMPLETED_WAIT_SMS;

			// mark the session as completed
			isCompleted = 1;

			// Just to be sure
			transParamCaptureCompleted = true;

			// OMM: seems isCompleted is not going to client, so I use
			// chargeableInfo
			ChargeInfo cInfo = new ChargeInfo();
			cInfo.contentType = "COMPLETED";
			appStepResp.chargeableInfo = cInfo;
			return appStepResp;
		}

		// Otherwise, if there is a Next Response Prompt, then continue
		// OMM: at this point display will still contain the ID
		appStepResp.responseToSubscriber = stepMenu.getMenuStringForDisplay();

		// OMM: Set the extra extended Properties
		appStepResp.setKeywordCode(stepMenu.getKeywordCode());
		appStepResp.setKeywordStepId(stepMenu.getKeywordStepId());

		// Determine whether the Step has a Fixed Value specified i.e. No User
		// Response required
		appStepResp.setUseFixedValue(stepMenu.getUseFixedValue());
		appStepResp.setFixedValue(stepMenu.getFixedValue());

		// If there is confirmed Pre-defined Input then flag the StepResponse
		if (stepMenu.getHasPredefInput()
				&& (stepMenu.getPredefInputValues() != null && !stepMenu
						.getPredefInputValues().isEmpty())) {
			appStepResp.setHasConfirmedPredefInput(true);
		} else {
			appStepResp.setHasConfirmedPredefInput(false);
		}

		// OMM: Just a thought: if it is Step.isLastStep then mark the
		// isCompleted = 1
		isCompleted = 0;
		return appStepResp;
	}

	/**
	 * Load the Next Step of the Transaction based on the Current Step ID and
	 * Step Number This Assumes that the Steps are sequential starting from 1
	 * increasing by 1
	 */
	private UssdStepMenu createNextKeywordStep(long currentKeywordStepId) {
		Connection cn = null;
		UssdStepMenu stepMenu = null;
		StringBuilder sb = null;
		ArrayList<String> predefListForCrgl = new ArrayList<String>();
		ArrayList<String> dateListForCrgl = new ArrayList<String>();

		try {
			stepMenu = new UssdStepMenu();
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			// OMM: I should find a way of maintaining state of Step Number to
			// reduce on the Sub-Queries
			sb = new StringBuilder();
			sb.append("SELECT TK.KEYWORD_CODE, TK.KEYWORD_NAME, KS.KEYWORD_STEP_ID, KS.STEP_MENU_NAME, KS.STEP_NUMBER, KS.HAS_PREDEF_INPUT,KS.PREDEF_INPUT_ID,KS.USE_FIXED_VALUE,KS.FIXED_VALUE");
			sb.append(" FROM KEYWORD_STEPS KS");
			sb.append(" JOIN TRANSACTION_KEYWORDS TK ON KS.KEYWORD_ID = TK.KEYWORD_ID");
			sb.append(" WHERE TK.KEYWORD_ID = (SELECT KEYWORD_ID FROM KEYWORD_STEPS WHERE KEYWORD_STEP_ID=%s) AND STEP_NUMBER=(SELECT STEP_NUMBER+1 FROM KEYWORD_STEPS WHERE KEYWORD_STEP_ID=%s)");

			PreparedStatement stm = cn.prepareStatement(String.format(
					sb.toString(), Long.toString(currentKeywordStepId),
					Long.toString(currentKeywordStepId)));

			// Execute the Query
			ResultSet result = stm.executeQuery();

			// OMM: There will be only 1 Step in each Displayed Screen, Unlike
			// the Sub-Menus
			while (result.next()) {
				// Build the String containing StepMenuName then keywordStepId
				// separated by the Tide character
				String menuItem = String.format("%s~%s", result.getString(4),
						Long.toString(result.getInt(3)));
				stepMenu.addItem(menuItem);
				stepMenu.setKeywordCode(result.getString(1));
				stepMenu.setKeywordName(result.getString(2));
				stepMenu.setKeywordStepId(result.getInt(3));
				stepMenu.setKeywordStepName(result.getString(4));
				stepMenu.setKeywordStepNum(result.getInt(5));
				boolean hasPredefInput = (result.getInt(6) == 1) ? true : false;
				stepMenu.setHasPredefInput(hasPredefInput);
				stepMenu.setPredefInputId(result.getInt(7));
				boolean useFixedValue = (result.getInt(8) == 1) ? true : false;
				stepMenu.setUseFixedValue(useFixedValue);
				stepMenu.setFixedValue(result.getString(9));
			}

			// If the Step has Predefined Inputs Options: then load them also
			if (stepMenu.getHasPredefInput()) {
				if (stepMenu.getKeywordStepId() == 29) {
					// && (stepMenu.getKeywordCode() == "CRGL")) {
					predefListForCrgl = getPredefInputItems(stepMenu
							.getPredefInputId());
					dateListForCrgl = new ArrayList<String>();
					SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy");
					Calendar cal = Calendar.getInstance();
					String maturityDateStr = "";
					Date maturityDate = null;

					for (int countOfMonths = 1; countOfMonths <= predefListForCrgl
							.size(); countOfMonths++) {
						cal.add(Calendar.MONTH, 1);
						if (countOfMonths == 1) {
							cal.add(Calendar.DAY_OF_MONTH, 1);
						}
						maturityDateStr = df.format(cal.getTime());
						dateListForCrgl.add(maturityDateStr);
					}
					stepMenu.setPredefInputValues(dateListForCrgl);
				} else {
					stepMenu.setPredefInputValues(getPredefInputItems(stepMenu
							.getPredefInputId()));
				}
			}
			return stepMenu;
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return stepMenu;
		}
	}

	/**
	 * Procedure to get Predef Input Items for a selected PredefInputId
	 */
	private ArrayList<String> getPredefInputItems(long predefInputId) {
		Connection cn = null;
		ArrayList<String> predefInputItems = null;
		StringBuilder sb = null;

		try {
			predefInputItems = new ArrayList<String>();
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			sb = new StringBuilder();
			sb.append("SELECT INPUT_ITEM_ID, INPUT_ITEM_CODE ");
			sb.append(" FROM PREDEF_INPUT_ITEMS");
			sb.append(" WHERE PREDEF_INPUT_ID = %s AND ENABLED_FLG=1");
			sb.append(" ORDER BY INPUT_ITEM_ORDER");

			PreparedStatement stm = cn.prepareStatement(String.format(
					sb.toString(), Long.toString(predefInputId)));

			// Execute the Query
			ResultSet result = stm.executeQuery();

			// OMM: There will be only 1 Step in each Displayed Screen, Unlike
			// the Sub-Menus
			while (result.next()) {
				// Build the String containing PredefInputItemCode then
				// PredefInputItemId separated by the Tide
				// character
				String menuItem = String.format("%s~%s", result.getString(2),
						Long.toString(result.getInt(1)));
				predefInputItems.add(menuItem);
			}

			return predefInputItems;

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return predefInputItems;
		}
	}

	/**
	 * This procedure assumes that the MenuItemID is appended at the end of the
	 * MenuText
	 */
	private String stripMenuItemIdsFromDisplay(String theDisplayString) {

		String finalString = theDisplayString;
		try {
			// OMM: I really would prefer using Regular Expressions here
			String[] displayItems = theDisplayString.split("\r\n");

			// Now loop stripping off the ID
			finalString = "";

			for (int i = 0; i < displayItems.length; i++) {

				// If there is no Tide character do not substring, to avoid -1
				// as index
				if (displayItems[i].contains("~")) {

					// Truncate the last part of the string
					displayItems[i] = displayItems[i].substring(0,
							displayItems[i].lastIndexOf("~"));

				}

				// Rebuild the final String
				finalString += displayItems[i];

				// Determine whether to add the Carriage Return Line Feed
				if (i < displayItems.length - 1) {
					finalString += "\r\n";
				}
			}
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());

			// Force the original string to be returned
			finalString = theDisplayString;
		}

		// Now Return the final String
		return finalString;
	}

	/**
	 * Procedure to get the Menu Item that was Selected by the User
	 */
	private String getDisplayItemByUserSelection(String previousPrompt,
			String userSelection) {
		String actualDisplayItem = "";

		try {
			// First split the previousPrompt
			String[] displayItems = previousPrompt.split("\r\n");

			// Trim the User Selection
			userSelection = userSelection.trim();

			// Then Loop through the Menu and check the numbers
			for (int i = 0; i < displayItems.length; i++) {
				if (displayItems[i].trim().toUpperCase()
						.startsWith(userSelection.toUpperCase())) {
					actualDisplayItem = displayItems[i];
					break;
				}
			}
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			actualDisplayItem = "";
		}

		return actualDisplayItem.trim();
	}

	/**
	 * Get the SelectedMenuItemId
	 */
	private long getSelectedMenuItemId(String selectedMenuItem) {
		long menuItemId = 0;
		try {
			// Get the Last part of the string
			if (selectedMenuItem.contains("~")) {
				String lastPart = selectedMenuItem.substring(selectedMenuItem
						.lastIndexOf("~") + 1);
				menuItemId = Long.parseLong(lastPart);
			}

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			menuItemId = 0;
		}

		return menuItemId;
	}

	/**
	 * Check whether the Selected Menu Item is attached to a Transaction Keyword
	 */
	private boolean menuItemHasTransKeyword(long menuItemId) {
		Connection cn = null;
		PreparedStatement stm = null;
		int keywordId = 0;

		try {
			// Otherwise, get the Connection
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			if (HelperUtils.TARGET_DATABASE.equalsIgnoreCase("MYSQL")) {
				stm = cn.prepareStatement(String
						.format("SELECT IFNULL(KEYWORD_ID,-999) KEYWORD_ID FROM USSD_MENU_ITEMS WHERE MENU_ITEM_ID = %s",
								Long.toString(menuItemId)));
			} else if (HelperUtils.TARGET_DATABASE.equalsIgnoreCase("ORACLE")) {
				stm = cn.prepareStatement(String
						.format("SELECT NVL(KEYWORD_ID,-999) KEYWORD_ID FROM USSD_MENU_ITEMS WHERE MENU_ITEM_ID = %s",
								Long.toString(menuItemId)));
			} else { // Assume SQL Server or die
				stm = cn.prepareStatement(String
						.format("SELECT ISNULL(KEYWORD_ID,-999) KEYWORD_ID FROM USSD_MENU_ITEMS WHERE MENU_ITEM_ID = %s",
								Long.toString(menuItemId)));
			}

			// Execute the Query
			ResultSet result = stm.executeQuery();

			while (result.next()) {
				keywordId = result.getInt(1);
			}

			// if there is a KeywordId then it is linked to a Transaction
			// Keyword
			if (keywordId > 0) {
				return true;
			} else {
				return false;
			}
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return false;
		} finally {
			try {
				if (cn != null) {
					cn.close();
				}
			} catch (Exception ex2) {
				HelperUtils.writeToLogFile("Server", "ERR: " + ex2.getMessage()
						+ " TRACE: " + ex2.getStackTrace());
			}
		}
	}

	/**
	 * Get the keywordStepId for the Previous Prompt that the User has responded
	 * to.
	 */
	private long getAnsweredKeywordStepId(String previousStepPrompt) {
		long keywordStepId = 0;
		try {
			// Get the Last part of the string
			if (previousStepPrompt.contains("~")) {
				String lastPart = previousStepPrompt
						.substring(previousStepPrompt.lastIndexOf("~") + 1);
				keywordStepId = Long.parseLong(lastPart);
			}

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			keywordStepId = 0;
		}

		return keywordStepId;
	}

	/**
	 * Get the Step Prompt that was to the user for which answer we have just
	 * received Important when the Title is displayed together with Prompt
	 */

	private String getAnsweredStepPrompt(String previousPrompt) {
		String actualDisplayItem = "";

		try {
			// First split the previousPrompt
			String[] displayItems = previousPrompt.split("\r\n");

			// Then Loop through the Menu and check the one with the ~
			for (int i = 0; i < displayItems.length; i++) {
				if (displayItems[i].contains("~")) {
					actualDisplayItem = displayItems[i];
					break;
				}
			}
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			actualDisplayItem = "";
		}

		return actualDisplayItem;
	}

	/**
	 * This Procedure validates the incoming Ussd Request before processing it
	 */
	private boolean validateUssdRequest(UssdRequest request) {
		boolean isValid = false;

		try {
			if (request != null) {
				if (request.msisdn != null && !request.msisdn.isEmpty()) {
					if (request.transactionId != null
							&& !request.transactionId.isEmpty()) {
						if (request.userInput != null
								&& !request.userInput.isEmpty()) {
							isValid = true;
						}
					}
				}
			}
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			isValid = false;
		}

		// Now return
		return isValid;
	}

	/***
	 * This Procedure determines which Menu Options to Exclude in the displayed
	 * set of USSD Menu
	 * 
	 * @param currentParentMenuId
	 * @return
	 */
	private ArrayList<Integer> getMenuItemsToExclude(String sourceMsisdn) {
		ArrayList<Integer> theMenuItemIds = null;
		String keywordsToExclude = "";

		try {
			theMenuItemIds = new ArrayList<Integer>();

			// If the Customer is Not Registered or is not a Dealer or Agent,
			// then hide the Dealer Menus
			if (!showDealerMenu(sourceMsisdn)) {
				if (keywordsToExclude.isEmpty()) {
					keywordsToExclude = keywordsToExclude
							.concat("'KYCR','DPST'");
				} else {
					keywordsToExclude = keywordsToExclude.concat(",").concat(
							"'KYCR','DPST'");
				}
			}

			// Check whether to hide Me2Me Activation
			if (org.applab.AppLabMoneyCore.Me2Me.MeToMeCommon
					.checkMeToMeActivation(sourceMsisdn)) {

				if (keywordsToExclude.isEmpty()) {
					keywordsToExclude = keywordsToExclude.concat("'ACTV'");
				} else {
					keywordsToExclude = keywordsToExclude.concat(",").concat(
							"'ACTV'");
				}
			} else {
				// Exclude all the rest of the Me2Me Keywords
				if (keywordsToExclude.isEmpty()) {
					keywordsToExclude = keywordsToExclude
							.concat("'CRGL','MTOM','GBAL','TENQ','REDM','REBT','STOP','ALTG'");
				} else {
					keywordsToExclude = keywordsToExclude.concat(",").concat(
							"'CRGL','MTOM','GBAL','TENQ','REDM','REBT','STOP','ALTG'");
				}
			}

			theMenuItemIds.addAll(getMenuItemIdsToExclude(keywordsToExclude));
			return theMenuItemIds;
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return new ArrayList<Integer>();
		}
	}

	private boolean showDealerMenu(String targetMsisdn) {

		try {
			CustomerInformation sourceCustInfo = CustomerInformation
					.getCustomerAccountInfo(targetMsisdn);
			if (null == sourceCustInfo) {
				return false;
			}

			// Check whether the s|ourceMsisdn is DLER or AGNT			
			int sourceAccountTypeBitmap = sourceCustInfo.getAccountTypeBitmap();
			if ((sourceAccountTypeBitmap & (org.applab.AppLabMoneyCore.HelperUtils.BITMAP_AGNT | org.applab.AppLabMoneyCore.HelperUtils.BITMAP_DLER)) == 0) {
				return false;
			}

			// Otherwise return true
			return true;
		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return false;
		}
	}

	private ArrayList<Integer> getMenuItemIdsToExclude(String keywordsToExclude) {
		Connection cn = null;
		ArrayList<Integer> dealerMenuItemIds = null;
		StringBuffer sb = null;
		
		try {
			dealerMenuItemIds = new ArrayList<Integer>();
			String specificMenuItemNamesToExclude = "'NOTHING'";
			
			cn = DatabaseHelper.getConnection(HelperUtils.TARGET_DATABASE);

			sb = new StringBuffer();
			sb.append("SELECT MENU_ITEM_ID FROM USSD_MENU_ITEMS UMI INNER JOIN TRANSACTION_KEYWORDS TK ON UMI.KEYWORD_ID=TK.KEYWORD_ID ");
			sb.append(" WHERE UPPER(TK.KEYWORD_CODE) IN (%s) OR UPPER(UMI.MENU_ITEM_NAME) IN (%s)");
			String query = sb.toString();

			PreparedStatement stm = cn.prepareStatement(String.format(query,
					keywordsToExclude.toUpperCase(),specificMenuItemNamesToExclude));

			// Execute the Query
			ResultSet result = stm.executeQuery();

			while (result.next()) {
				dealerMenuItemIds.add(result.getInt("MENU_ITEM_ID"));
			}
			return dealerMenuItemIds;

		} catch (Exception ex) {
			HelperUtils.writeToLogFile("Server", "ERR: " + ex.getMessage()
					+ " TRACE: " + ex.getStackTrace());
			return new ArrayList<Integer>();
		}
	}

	private String getConfirmationPrompt(String transactionParameters) {
	    String[] params = null;
	    String transactionKeyword = null;
	    String msg = "Confirm Transaction";
	    
	    
	    try {
	        params = transactionParameters.split(" ");
	        transactionKeyword = params[0].toUpperCase();
	        
	        if(transactionKeyword.equalsIgnoreCase("CRGL")) {
	            //[CRGL, HOME, 54656, .*21*Apr*2013, WEEKLY, PARTIAL, 4321]
	            String cashOutDate = params[3].replaceAll("\\*", " ");
	            String goalName = params[1].replaceAll("\\*", " ");
	            double amount = 0;
	            try {
	                amount = Double.parseDouble(params[2]);
	            } 
	            catch (Exception exAmount) {
	                amount = 0;
	                HelperUtils.writeToLogFile("Server", "ERR: " + exAmount.getMessage()
	                        + " TRACE: " + exAmount.getStackTrace());
	            }
	            	            
	            msg = String.format("Create a %s goal of UGX%,.0f to be cashed-out on %s?\r\n",goalName,amount,cashOutDate);
	        } else if(transactionKeyword.equalsIgnoreCase("MTOM")) {
	            //[MTOM, BUSINESS, 45473, 4321]
	            String goalName = params[1].replaceAll("\\*", " ");
	            double amount = 0;
                try {
                    amount = Double.parseDouble(params[2]);
                } 
                catch (Exception exAmount) {
                    amount = 0;
                    HelperUtils.writeToLogFile("Server", "ERR: " + exAmount.getMessage()
                            + " TRACE: " + exAmount.getStackTrace());
                }
	            msg = String.format("Send UGX%,.0f to your %s goal?\r\n", amount, goalName);
	            
	        } else if(transactionKeyword.equalsIgnoreCase("REDM")) {
	            
                msg = String.format("Cash Out your %s goal?\r\n", params[1]);
	        } else if(transactionKeyword.equalsIgnoreCase("STOP")) {
                
                msg = String.format("Stop your %s goal?\r\n", params[1]);
            } else if(transactionKeyword.equalsIgnoreCase("REBT")) {
                String goalName = params[1].replaceAll("\\*", " ");
                double amount = 0;
                try {
                    amount = Double.parseDouble(params[2]);
                } 
                catch (Exception exAmount) {
                    amount = 0;
                    HelperUtils.writeToLogFile("Server", "ERR: " + exAmount.getMessage()
                            + " TRACE: " + exAmount.getStackTrace());
                }
                msg = String.format("Making an Early-Withdrawal will incur a penalty.\r\nWithdraw UGX%,.0f from your %s goal?\r\n", amount, goalName);
            }
	        
	        
	        return msg;
	    } catch (Exception ex) {
	        return "Please Confirm the Transaction.";
	    }
	}
	
	private String getConfirmationOptions(int predefinedInputId) {
	    try {
	        if(predefinedInputId != -1) {
	            return "\r\n 1. YES \r\n 2. NO";
	        } 
	        else {
	            return "\r\n 1. YES \r\n 2. CANCEL";
	        }	            
	        
	    } catch (Exception ex) {
	        return "\r\n 1. YES \r\n 2. NO";
	    }
	}
	
	private String getConfirmationOptions() {
	    return getConfirmationOptions(-1);
	}
	
}

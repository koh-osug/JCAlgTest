/*
    Copyright (c) 2008-2016 Petr Svenda <petr@svenda.com>

     LICENSE TERMS

     The free distribution and use of this software in both source and binary
     form is allowed (with or without changes) provided that:

       1. distributions of this source code include the above copyright
          notice, this list of conditions and the following disclaimer;

       2. distributions in binary form include the above copyright
          notice, this list of conditions and the following disclaimer
          in the documentation and/or other associated materials;

       3. the copyright holder's name is not used to endorse products
          built using this software without specific written permission.

     ALTERNATIVELY, provided that this notice is retained in full, this product
     may be distributed under the terms of the GNU General Public License (GPL),
     in which case the provisions of the GPL apply INSTEAD OF those given above.

     DISCLAIMER

     This software is provided 'as is' with no explicit or implied warranties
     in respect of its properties, including, but not limited to, correctness
     and/or fitness for purpose.

    Please, report any bugs to author <petr@svenda.com>
*/
package algtestprocess;

import algtestjclient.CardMngr;
import algtestjclient.DirtyLogger;
import algtestjclient.SingleModeTest;
import algtestjclient.Utils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import static java.lang.System.out;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeSet;

/**
 * Generates performance similarity table, contains tooltips with differences in algorithm support.
 * @author rk
 */
public class SupportTable {

    public static final String JCALGTEST_RESULTS_REPO_PATH = AlgTestProcess.GITHUB_RESULTS_REPO_LINK + "tree/master/javacard/Profiles/results/";  

    // if one card results are generated
    public static final String[] JAVA_CARD_VERSION = {"2.1.2", "2.2.1", "2.2.2"};
    public static int jcv = -1;

    // if multiple card results are generated
    private static List<String> java_card_version_array = new ArrayList<String>();
    private static String appletVersion = "";
    private static String packageAIDTestPath = "";

    private static final int JC_SUPPORT_OFFSET = 25;
    private static final int AT_APPLET_OFFSET = 23;
    private static final int PACKAGE_AID_PATH_OFFSET = 17;



     /**
     * Method takes HTML file with two smart card algorithm support results and marks differences between them.
     * @param basePath Path to folder with HTML file which must be named 'AlgTest_html_table.html'.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void compareSupportedAlgs (String inputDir) throws FileNotFoundException, IOException{
        /* String containing input file path. */
        String inputFileName = inputDir + "AlgTest_html_table.html";
        /* String containing output file path. */
        String outputFileName = inputDir + "AlgTest_html_table_comparison.html";
        /* String containing line to search for in HTML file. */
        String lineToSearch = "<tr>";
        /* String containing style information for not matching algorithms in HTML file. */
        String styleInfo = "<tr style='outline: solid'>";

        /* String array for loaded file. */
        ArrayList<String> loadedFile = new ArrayList<>();

        /* Creating object of FileReader. */
        FileReader inputFile = new FileReader(inputFileName);
        BufferedReader reader = new BufferedReader(inputFile);

        String line = null;     // buffer for input file
        /* Loading file to ArrayList object. */
        while ((line = reader.readLine()) != null) {    // read if there is another line to read
            loadedFile.add(line);
        }
        /* Searching for algs in loaded file. */
        for (int i = 0; i < loadedFile.size(); i++){
            if (loadedFile.get(i).contains(lineToSearch)){  // checking if line[i] is HTML row definition
                if(!loadedFile.get(i + 3).contains(">c")){  // so the program doesn't check algorithm's class names
                    String aux = loadedFile.get(i+3).substring(loadedFile.get(i+3).indexOf(">") + 1);   // getting first occurence of '>' char and rest of the string behinf him
                    if (!loadedFile.get(i + 4).contains(aux)){  // checking if next algorithm support is the same
                        loadedFile.set(i, styleInfo);           // setting new string to ArrayList (with border)
                    }
                }
            }
        }

        FileOutputStream output = new FileOutputStream(outputFileName);
        /* Writing to output file. */
        for (int i = 0; i< loadedFile.size(); i++){
            String aux = loadedFile.get(i);
            aux = aux + "\r\n";     // adding end of line to every line written to HTML file
            output.write(aux.getBytes());
            output.flush();
        }
            output.close();
    }

   public static String getCardIdentificationFromFileName(String fileName) {
        String cardIdentification = fileName;
        cardIdentification = cardIdentification.replace('_', ' ');
        cardIdentification = cardIdentification.replace(".csv", "");
        cardIdentification = cardIdentification.replace("3B", ", ATR=3B");
        cardIdentification = cardIdentification.replace("3b", ", ATR=3b");
        cardIdentification = cardIdentification.replace("ALGSUPPORT", "");

        return cardIdentification;
    }
   
    public static String getShortNameFromCardIdentification(String cardIdentification, boolean bJustName) {
        String cardShortName = cardIdentification.substring(0, cardIdentification.indexOf("ATR"));
        // Get rid of '   , ' at the end of card name
        cardShortName = cardShortName.trim();
        if (cardShortName.charAt(cardShortName.length() - 1) == ',') { cardShortName = cardShortName.substring(0, cardShortName.length() - 1); }
        cardShortName = cardShortName.trim();
        
        if (bJustName) {
            if (cardShortName.contains("ICFabDate")) {
                cardShortName = cardShortName.substring(0, cardShortName.indexOf("ICFabDate"));
                cardShortName = cardShortName.trim();
            }            
        }
        return cardShortName;
    }
    
    public static void generateHTMLTable(String inBasePath) throws IOException {
        generateHTMLTable(inBasePath, inBasePath, "", true, null);
    }
    public static void generateHTMLTable(String inBasePath, String outBasePath, String html_name_suffix, boolean bGeneratePackagesSupport, HashMap<String, ArrayList<Integer>> filteredCards) throws IOException {
        String filesPath = inBasePath + "results" + File.separator;
        File dir = new File(filesPath);
        String[] allFilesArray = dir.list();
        ArrayList<String> filesArrayUnsorted = new ArrayList<>();
        
        for (int i = 0; i < allFilesArray.length; i++) {
            File testDir = new File(inBasePath + "results" + File.separator + allFilesArray[i] + File.separator);
            if (!testDir.isDirectory()) {
                filesArrayUnsorted.add(allFilesArray[i]);
            }
        }

        // Sort files by name
        ArrayList<String> filesArray = new ArrayList<>();
        java.util.Collections.sort(filesArrayUnsorted, String.CASE_INSENSITIVE_ORDER);
        // Insert all  but undisclosed
        for (int i = 0; i < filesArrayUnsorted.size(); i++) {
            if (!filesArrayUnsorted.get(i).startsWith("[undisclosed")) {
                filesArray.add(filesArrayUnsorted.get(i));
            }
        }
        // Move [undisclosed... towards end
        for (int i = 0; i < filesArrayUnsorted.size(); i++) {
            if (filesArrayUnsorted.get(i).startsWith("[undisclosed")) {
                filesArray.add(filesArrayUnsorted.get(i));
            }
        }
        
        if ((filesArray != null) && (dir.isDirectory() == true)) {    
            HashMap filesSupport[] = new HashMap[filesArray.size()]; 
            
            for (int i = 0; i < filesArray.size(); i++) {
                filesSupport[i] = new HashMap();
                parseSupportFile(filesPath + filesArray.get(i), filesSupport[i], bGeneratePackagesSupport);
            }            
            //
            // HTML HEADER
            //
            String fileName;
            if (html_name_suffix.isEmpty()) {
                fileName = outBasePath + "AlgTest_html_table.html";
            }
            else {
                fileName = outBasePath + String.format("AlgTest_html_table_%s.html", html_name_suffix);
            }
            FileOutputStream file = new FileOutputStream(fileName);
            String header = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\r\n<html>\r\n<head>"
                    + "<meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\">\r\n"
                    + "<link type=\"text/css\" href=\"style.css\" rel=\"stylesheet\">\r\n"
                    + "<script class=\"jsbin\" src=\"http://ajax.googleapis.com/ajax/libs/jquery/1.4.2/jquery.min.js\"></script>\r\n"
                    + "<title>JavaCard support test</title>\r\n"
                    + "    <link href=\"dist/css/bootstrap.min.css\" rel=\"stylesheet\">\n" +
                    "    <link href=\"dist/css/ie10-viewport-bug-workaround.css\" rel=\"stylesheet\">\n" +
                    "    <script src=\"assets/js/ie-emulation-modes-warning.js\"></script>\n" +
                    "	\n" +
                    "	<link href=\"./dist/supporttable_style.css\" rel=\"stylesheet\">\n" +
                    "	\n" +
                    "	<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.12.0/jquery.min.js\"></script>\n" +
                    "\n" +
                    "	<script src=\"assets/js/checkboxes.js\"></script>\n" +
                    "	\n" +
                    "	<script>$(function(){ $(\"#tab td\").hover(function(){$(\"#tab col\").eq($(this).index()).css({\"border\":\" 2px solid #74828F\"});$(this).closest(\"tr\").css({\"border\":\" 2px solid #74828F\"});},function(){$(\"#tab col\").eq($(this).index()).css({\"border\":\" 0px\"}); $(this).closest(\"tr\").css({\"border\":\" 0px\"});});});</script>\n"
                    + "<script>$(function(){ $(\"#tab td\").hover(function(){$(\"#tab col\").eq($(this).index()).css({\"border\":\" 2px solid #74828F\"});$(this).closest(\"tr\").css({\"border\":\" 2px solid #74828F\"});},function(){$(\"#tab col\").eq($(this).index()).css({\"border\":\" 0px\"}); $(this).closest(\"tr\").css({\"border\":\" 0px\"});});});</script>\r\n"
                    + "</head>\r\n"
                    + "<body style=\"margin-top:50px; padding:20px\">\r\n\r\n";
            if (!html_name_suffix.isEmpty()) {
                header += "<div class=\"container-fluid\">\n<h2 id=\"LIST\">IMPORTANT: This list is limited only to cards with '" + html_name_suffix + "' support.</h2>\r\n";
                header += "See a table with all cards here: <a href=\"AlgTest_html_table.html\">AlgTest_html_table.html</a> \r\n";
            }

            String cardList = "<div class=\"container-fluid\">\n<h3 id=\"LIST\">Tested cards abbreviations</h3>\r\n";

            HashMap<String, Integer> authors = new HashMap<>();
            //String shortNamesList[] = new String[filesArray.size()];            
            for (int i = 0; i < filesArray.size(); i++) {
                String cardIdentification = getCardIdentificationFromFileName(filesArray.get(i));
                String cardShortName = getShortNameFromCardIdentification(cardIdentification, false);
/*                
                String cardIdentification = filesArray.get(i);
                cardIdentification = cardIdentification.replace('_', ' ');
                cardIdentification = cardIdentification.replace(".csv", "");
                cardIdentification = cardIdentification.replace("3B", ", ATR=3B");
                cardIdentification = cardIdentification.replace("3b", ", ATR=3b");
                cardIdentification = cardIdentification.replace("ALGSUPPORT", "");

                String cardShortName = getCardShortName(filesArray.get(i), false);
                String cardShortName = cardIdentification.substring(0, cardIdentification.indexOf("ATR"));
                // Get rid of '   , ' at the end of card name
                cardShortName = cardShortName.trim();
                if (cardShortName.charAt(cardShortName.length() - 1) == ',') { cardShortName = cardShortName.substring(0, cardShortName.length() - 1); }
                cardShortName = cardShortName.trim();

                shortNamesList[i] = cardShortName;
*/
                // Extract providing person name
                String PROVIDED_BY = "provided by ";
                String AND = " and ";
                int startAuthorsOffset = 0;
                if ((startAuthorsOffset = cardIdentification.indexOf(PROVIDED_BY)) > -1) {
                    startAuthorsOffset += PROVIDED_BY.length();

                    if (cardIdentification.indexOf(" and ", startAuthorsOffset) > -1) {
                        // Two authors, extract first one
                        String authorName = cardIdentification.substring(startAuthorsOffset, cardIdentification.indexOf(AND, startAuthorsOffset));
                        if (authors.containsKey(authorName)) { authors.replace(authorName, authors.get(authorName) + 1); }
                        else { authors.put(authorName, 1); }
                        startAuthorsOffset = cardIdentification.indexOf(AND, startAuthorsOffset) + AND.length();
                    }
                    String authorName = cardIdentification.substring(startAuthorsOffset, cardIdentification.indexOf(")", startAuthorsOffset));
                    if (authors.containsKey(authorName)) { authors.replace(authorName, authors.get(authorName) + 1); }
                    else { authors.put(authorName, 1); }
                }

                String cardRestName = cardIdentification.substring(cardIdentification.indexOf("ATR"));
                cardList += "<b>c" + i + "</b>	" + "<a href=\"" + JCALGTEST_RESULTS_REPO_PATH + filesArray.get(i) + "\">" + cardShortName + "</a> , " + cardRestName + ",";

                String cardName = "";
                if (filesSupport[i].containsKey("Performance")) {
                    cardName = (String) filesSupport[i].get("Card name");
                    cardName = cardName.replace(" ", ""); cardName = cardName.replace("_", "");
                    cardList += "&nbsp;<a target=\"_blank\" href=\"run_time/" + cardName + ".html\">Performance</a>,&nbsp;";
                    cardList += "<a target=\"_blank\" href=\"scalability/" + cardName + ".html\">Graphs</a>";
                }
                cardList += "<br>\r\n";
            }
            cardList += "<br>\r\n";

            file.write(header.getBytes());
            file.write(cardList.getBytes());
            file.flush();

            String note = "Note: Some cards in the table come without full identification and ATR (\'undisclosed\') as submitters prefered not to disclose it at the momment. I'm publishing it anyway as the information that some card supporting particular algorithm exists is still interesting. Full identification might be added in future.<br><br>\r\n\r\n";
            file.write(note.getBytes());

            note = "Note: If you have card of unknown type, try to obtain ATR and take a look at smartcard list available here: <a href=\"https://smartcard-atr.apdu.fr/\"> https://smartcard-atr.apdu.fr/</a><br><br>\r\n\r\n";
            file.write(note.getBytes());

            // Create bat script to copy files with results to corresponding folders for the same card type
            for (int i = 0; i < filesArray.size(); i++) {
                String cardIdentification = getCardIdentificationFromFileName(filesArray.get(i));
                String justName = getShortNameFromCardIdentification(cardIdentification, true);
                System.out.println("mkdir \"" + justName + "\"");
                System.out.println("copy ..\\results\\\"" + filesArray.get(i) + "\" \"" + justName + "\"");
            }            
            System.out.println();
            
            HashMap<String, String> cardNameATRMap = new HashMap<>(); 
            for (int i = 0; i < filesArray.size(); i++) {
                String cardIdentification = getCardIdentificationFromFileName(filesArray.get(i));
                String justName = getShortNameFromCardIdentification(cardIdentification, true);
                // Store mapping of card name and ATR
                String cardATR = (String) filesSupport[i].get("Card ATR");
                String cardName = (String) filesSupport[i].get("Card name");
                if ((cardATR == null) || (cardName == null)) {
                    System.out.println(String.format("Warning: Missing card name or ATR in file %s", filesArray.get(i)));
                }
                else {
                    if (!cardName.equalsIgnoreCase(justName)) {
                        System.out.println(String.format("Warning: Mismatch card name for file %s, %s vs. %s", filesArray.get(i), cardName, justName));
                    }
                    cardNameATRMap.put(cardATR, justName);
                }
            }                        
            
            // Store ATR to card name mapping
            String fileNameATRMapping = outBasePath + "atr_cardname.csv";
            FileOutputStream fileAtrName = new FileOutputStream(fileNameATRMapping);
            for (String atr : cardNameATRMap.keySet()) {
                String atr_name = String.format("%s;%s\n\r", atr, cardNameATRMap.get(atr));
                fileAtrName.write(atr_name.getBytes());
            }
            fileAtrName.close();
            

            // Print all providing people names found
            for (String authorName : authors.keySet()) {
                System.out.print(authorName + " (" + authors.get(authorName) + "x), ");
/*
                if (authors.get(authorName) > 1) {
                    System.out.print(authorName + " (" + authors.get(authorName) + " cards), ");
                }
                else {
                    System.out.print(authorName + " (" + authors.get(authorName) + " card), ");
                }
*/
            }
            System.out.print("\n");
            String explain = "<table id=\"explanation\" min-width=\"1000\" border=\"0\" cellspacing=\"2\" cellpadding=\"4\" >\r\n" 
                    + "<tr>\r\n"
                    + "  <td class='dark_index' style=\"min-width:100px\">Symbol</td>\r\n"
                    + "  <td class='dark_index'>Meaning</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_yes'>yes</td>\r\n"
                    + "  <td class='light_info_left'>This particular algorithm was tested and IS supported by given card.</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_no'>no</td>\r\n"
                    + "  <td class='light_info_left'>This particular algorithm was tested and is NOT supported by given card.</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_suspicious'>possibly yes</td>\r\n"
                    + "  <td class='light_info_left'>This particular algorithm was tested and is REPORTED as supported by given card. However, given algorithm was introduced in later version of JavaCard specification than version declared by the card as supported one. Mostly, algorithm is really supported. But it might be possible, that given algorithm is NOT actually supported by card as some cards may create object for requested algorithm and fail only later when object is actually used. Future version of the JCAlgTest will make more thorough tests regarding this behaviour.</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_info'>error(ERROR_CODE)</td>\r\n"
                    + "  <td class='light_info_left'>Card returned specific error other then raising CryptoException.NO_SUCH_ALGORITHM. Most probably, algorithm is NOT supported by given card.</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_info'>?</td>\r\n"
                    + "  <td class='light_info_left'>Card returned unspecific error. Most probably, algorithm is NOT supported by given card.</td>\r\n"
                    + "</tr>\r\n"
                    + "<tr>\r\n"
                    + "  <td class='light_maybe'>-</td>\r\n"
                    + "  <td class='light_info_left'>This particular algorithm was NOT tested. Usually, this equals to unsupported algorithm. Typical example is the addition of new constants introduced by the newer version of JavaCard standard, which are not supported by cards tested before apperance of of new version of specification. The exceptions to this rule are classes that have to be tested manually (at the moment, following information: JavaCard support version, javacardx.apdu.ExtendedLength Extended APDU) where not tested doesn't automatically means not supported. Automated upload and testing of these features will solve this in future.</td>\r\n"
                    + "</tr>\r\n"
                    + "</table>\r\n"
                    + "<br><br>\r\n";
            file.write(explain.getBytes());


            //Checkboxes to show/hide columns in table, JavaScript required
            String checkboxes = "<h4>Click on each checkbox to show/hide corresponding column (card). "
                    + "Use buttons to select group of cards.</h4>\n\t<div class=\"row\" id=\"grpChkBox\">\n";
            
            checkboxes += "<input type=\"button\" class=\"btn btn-default\" id=\"checkAll\" onclick=\"checkAll('grpChkBox')\" value=\"Select all\">\n";
            checkboxes += "<input type=\"button\" class=\"btn btn-default\" id=\"uncheckAll\" onclick=\"uncheckAll('grpChkBox')\" value=\"Deselect all\">\n";
            if (filteredCards != null) {
                // Insert javascript selection buttons
                for (String groupName :  filteredCards.keySet()) {
                    String groupNameNoSpace = groupName.replace(" ", "_");
                    checkboxes += "<script>function checkAll" + groupNameNoSpace +"(divid) {\n" +
                                "  uncheckAll(divid);\n";
                    checkboxes += "  let array = [";
                    int itemIndex = 1;
                    for (Integer cardID : filteredCards.get(groupName)) {
                        checkboxes += "\"card"+cardID+"\"";
                        if (itemIndex < filteredCards.get(groupName).size()) {
                            checkboxes += ", ";
                            itemIndex++;
                        }
                    }
                    checkboxes += "];\n";
                    checkboxes += "  for (let cardName of array) {\n" +
                                    "    let obj = document.getElementById(cardName);\n" +
                                    "    $(obj).prop('checked', true);	\n" +
                                    "    processToggle($(obj), true);\n" +
                                    "  }\n" +
                                    "}</script>\n";
                    checkboxes += "<input type=\"button\" class=\"btn btn-default\" id=\"checkAll"+groupNameNoSpace+"\" onclick=\"checkAll"+groupNameNoSpace+"('grpChkBox')\" value=\"Select all with "+groupName+"\">\n";
                }
            }
            checkboxes += "\n</br></br>\n\n";

            for(int i=0; i<filesArray.size(); i++){
                String cardIdentification = filesArray.get(i);
                cardIdentification = cardIdentification.replace('_', ' ');
                cardIdentification = cardIdentification.replace(".csv", "");
                cardIdentification = cardIdentification.replace("3B", ", ATR=3B");
                cardIdentification = cardIdentification.replace("3b", ", ATR=3b");
                cardIdentification = cardIdentification.replace("ALGSUPPORT", "");
                String cardShortName = cardIdentification.substring(0, cardIdentification.indexOf(",")-1);
                if(i%(filesArray.size() / 3 + 1) == 0)
                    checkboxes += "<div class=\"col-lg-4 .col-sm-4\">\n";

                checkboxes += "\t\t<p style=\"margin:0;\"><input type=\"checkbox\" name=\""+i+"\" id=\"card"+i+"\"/> <b>c"+i+"</b> - "+cardShortName+"</p>\n";
                getShortCardName(filesArray.get(i));
                if(i%(filesArray.size()/3 + 1) == filesArray.size()/3)
                    checkboxes += "\t</div>\n";
            }
            
            checkboxes += "\t<br>\n\t</div>\n</div>\n";
            checkboxes += "\n</br></br>\n\n";
            file.write(checkboxes.getBytes());


            String table = "<table id=\"tab\" width=\"600px\" border=\"0\" cellspacing=\"2\" cellpadding=\"4\">\r\n";
            // Insert helper column identification for mouseover row & column jquery highlight
            table += "<colgroup>";        
            for (int i = 0; i < filesArray.size() + 2; i++) { table += "<col />"; } // + 2 because of column with algorithm name and introducing version
            table += "</colgroup>\r\n";

            file.write(table.getBytes());

            //
            // HTML TABLE HEAD
            //
            file.write("<thead>".getBytes());
            formatTableAlgorithm_HTML(filesArray, SingleModeTest.ALL_CLASSES_STR[0], filesSupport, file);
            file.write("</thead>".getBytes());

            //
            // HTML TABLE BODY
            //
            file.write("<tbody>".getBytes());
            for(int i=1; i<SingleModeTest.ALL_CLASSES_STR.length; i++)
                formatTableAlgorithm_HTML(filesArray, SingleModeTest.ALL_CLASSES_STR[i], filesSupport, file);

            file.write("</tbody>".getBytes());

            //
            // FOOTER
            //
            String footer = "</table>\r\n</div>\r\n\r\n";
            footer += "<script type=\"text/javascript\" src=\"footer.js\"></script>\n";
            footer += "<a href=\"#\" class=\"back-to-top\"></a>\n";
            footer += "\r\n</body>\n</html>";
            file.write(footer.getBytes());

            file.flush();
            file.close();
        }
        else {
            System.out.println("directory '" + filesPath + "' is empty");
        }
    }
    
    private static boolean atLeastOneSupported(HashMap<String, String> filesSupport, String[] algs) {
        for (String alg : algs) {
            if (filesSupport.containsKey(alg)) {
                if (filesSupport.get(alg).equalsIgnoreCase("yes")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static void copyIfAtLeastOneSupported(HashMap<String, String> filesSupport, String[] algs, String targetDir, String fileName) throws IOException {
        if (atLeastOneSupported(filesSupport, algs)) { 
            String target = targetDir + File.separator + new File(fileName).getName();
            Files.copy(new File(fileName).toPath(), new File(target).toPath(), REPLACE_EXISTING);
            
            
        }
    }    
    
    public static void splitBySupport(String basePath, HashMap<String, String> filteredDirs, HashMap<String, ArrayList<Integer>> filteredCards) throws IOException {
        String filesPath = basePath + "results" + File.separator;
        File dir = new File(filesPath);
        String[] allFilesArray = dir.list();
        ArrayList<String> filesArrayUnsorted = new ArrayList<>();
        
        String filterBasePath = basePath + "results_filter" + File.separator;
        
        for (int i = 0; i < allFilesArray.length; i++) {
            File testDir = new File(basePath + "results" + File.separator + allFilesArray[i] + File.separator);
            if (!testDir.isDirectory()) {
                filesArrayUnsorted.add(allFilesArray[i]);
            }
        }

        // Sort files by name
        ArrayList<String> filesArray = new ArrayList<>();
        java.util.Collections.sort(filesArrayUnsorted, String.CASE_INSENSITIVE_ORDER);
        // Insert all  but undisclosed
        for (int i = 0; i < filesArrayUnsorted.size(); i++) {
            if (!filesArrayUnsorted.get(i).startsWith("[undisclosed")) {
                filesArray.add(filesArrayUnsorted.get(i));
            }
        }
        // Move [undisclosed... towards end
        for (int i = 0; i < filesArrayUnsorted.size(); i++) {
            if (filesArrayUnsorted.get(i).startsWith("[undisclosed")) {
                filesArray.add(filesArrayUnsorted.get(i));
            }
        }
        
        if ((filesArray != null) && (dir.isDirectory() == true)) {    
            HashMap filesSupport[] = new HashMap[filesArray.size()]; 
            
            for (int i = 0; i < filesArray.size(); i++) {
                filesSupport[i] = new HashMap();
                parseSupportFile(filesPath + filesArray.get(i), filesSupport[i]);
            }     
            
            // Copy card profiles based on the required supported info
            String[] ECC_ALGS = {"TYPE_EC_FP_PRIVATE LENGTH_EC_FP_112", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_128", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_160", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_192", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_224", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_256", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_384", "TYPE_EC_FP_PRIVATE LENGTH_EC_FP_521"};
            String[] RSA2048BIGGER_ALGS = {"TYPE_RSA_PUBLIC LENGTH_RSA_3072", "TYPE_RSA_PRIVATE LENGTH_RSA_3072", "TYPE_RSA_CRT_PRIVATE LENGTH_RSA_3072", "TYPE_RSA_PUBLIC LENGTH_RSA_4096", "TYPE_RSA_PRIVATE LENGTH_RSA_4096", "TYPE_RSA_CRT_PRIVATE LENGTH_RSA_4096"};
            String[] ECDH_ALGS = {"ALG_EC_SVDP_DH/ALG_EC_SVDP_DH_KDF", "ALG_EC_SVDP_DHC/ALG_EC_SVDP_DHC_KDF", "ALG_EC_SVDP_DH_PLAIN", "ALG_EC_SVDP_DHC_PLAIN", "ALG_EC_PACE_GM", "ALG_EC_SVDP_DH_PLAIN_XY", "ALG_DH_PLAIN"};
            String[] AES_ALGS = {"TYPE_AES LENGTH_AES_128", "TYPE_AES LENGTH_AES_192", "TYPE_AES LENGTH_AES_256"};
            String[] SHA2_ALGS = {"ALG_SHA_256", "ALG_SHA_384", "ALG_SHA_512", "ALG_SHA_224"};
            String[] SHA2_512_ALGS = {"ALG_SHA_512"};
            String[] SHA3_ALGS = {"ALG_SHA3_224", "ALG_SHA3_256", "ALG_SHA3_384", "ALG_SHA3_512"};
            String[] DH_PLAIN_ALGS = {"ALG_EC_SVDP_DH_PLAIN_XY", "ALG_EC_SVDP_DHC_PLAIN", "ALG_EC_SVDP_DH_PLAIN", "ALG_DH_PLAIN"};
            String[] ECDSA_SHA256_ALGS = {"ALG_ECDSA_SHA_256"};

            ArrayList<Integer> eccCards = new ArrayList<>();
            filteredCards.put("ECC", eccCards);
            ArrayList<Integer> bigRSACards = new ArrayList<>();
            filteredCards.put("large RSA", bigRSACards);
            ArrayList<Integer> ecdhCards = new ArrayList<>();
            filteredCards.put("ECDH", ecdhCards);
            ArrayList<Integer> aesCards = new ArrayList<>();
            filteredCards.put("AES", aesCards);
            ArrayList<Integer> sha2Cards = new ArrayList<>();
            filteredCards.put("SHA2", sha2Cards);
            ArrayList<Integer> sha2_512Cards = new ArrayList<>();
            filteredCards.put("SHA2_512b", sha2_512Cards);
            ArrayList<Integer> sha3Cards = new ArrayList<>();
            filteredCards.put("SHA3", sha3Cards);
            ArrayList<Integer> plainECDHCards = new ArrayList<>();
            filteredCards.put("plain ECDH", plainECDHCards);
            ArrayList<Integer> ecdsaCards = new ArrayList<>();
            filteredCards.put("ECDSA", ecdsaCards);
            
            for (int i = 0; i < filesArray.size(); i++) {
                if (atLeastOneSupported(filesSupport[i], ECC_ALGS)) { eccCards.add(i); }
                if (atLeastOneSupported(filesSupport[i], RSA2048BIGGER_ALGS)) { bigRSACards.add(i); }
                if (atLeastOneSupported(filesSupport[i], ECDH_ALGS)) { ecdhCards.add(i); }
                if (atLeastOneSupported(filesSupport[i], AES_ALGS)) { aesCards.add(i); }
                if (atLeastOneSupported(filesSupport[i], SHA2_ALGS)) { sha2Cards.add(i); }
                if (atLeastOneSupported(filesSupport[i], SHA2_512_ALGS)) { sha2_512Cards.add(i); }
                if (atLeastOneSupported(filesSupport[i], SHA3_ALGS)) { sha3Cards.add(i); }
                if (atLeastOneSupported(filesSupport[i], DH_PLAIN_ALGS)) { plainECDHCards.add(i); }
                if (atLeastOneSupported(filesSupport[i], ECDSA_SHA256_ALGS)) { ecdsaCards.add(i); }
            }      
/*            
            String eccDir = filterBasePath + "ecc" + File.separator;
            new File(eccDir + "results").mkdirs(); filteredDirs.put("ecc", eccDir);
            String largeRSADir = filterBasePath + "largeRSA" + File.separator;
            new File(largeRSADir + "results").mkdirs(); filteredDirs.put("largeRSA", largeRSADir);
            String ecdhDir = filterBasePath + "ecdh" + File.separator;
            new File(ecdhDir + "results").mkdirs(); filteredDirs.put("ecdh", ecdhDir);
            String aesDir = filterBasePath + "aes" + File.separator;
            new File(aesDir + "results").mkdirs(); filteredDirs.put("aes", aesDir);
            String sha2Dir = filterBasePath + "sha2" + File.separator;
            new File(sha2Dir + "results").mkdirs();filteredDirs.put("sha2", sha2Dir);
            String sha2_512Dir = filterBasePath + "sha2_512" + File.separator;
            new File(sha2_512Dir + "results").mkdirs();filteredDirs.put("sha2_512", sha2_512Dir);
            String sha3Dir = filterBasePath + "sha3" + File.separator;
            new File(sha3Dir + "results").mkdirs(); filteredDirs.put("sha3", sha3Dir);
            String ecdhplainDir = filterBasePath + "ecdhplain" + File.separator;
            new File(ecdhplainDir + "results").mkdirs(); filteredDirs.put("ecdhplain", ecdhplainDir);
            String ecdsa_sha256Dir = filterBasePath + "ecdsa_sha256" + File.separator;
            new File(ecdsa_sha256Dir + "results").mkdirs(); filteredDirs.put("ecdsa_sha256", ecdsa_sha256Dir);

            for (int i = 0; i < filesArray.size(); i++) {
                copyIfAtLeastOneSupported(filesSupport[i], ECC_ALGS, eccDir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], RSA2048BIGGER_ALGS, largeRSADir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], ECDH_ALGS, ecdhDir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], AES_ALGS, aesDir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], SHA2_ALGS, sha2Dir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], SHA2_512_ALGS, sha2_512Dir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], SHA3_ALGS, sha3Dir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], DH_PLAIN_ALGS, ecdhplainDir + "results", filesPath + filesArray.get(i));
                copyIfAtLeastOneSupported(filesSupport[i], ECDSA_SHA256_ALGS, ecdsa_sha256Dir + "results", filesPath + filesArray.get(i));
            }    
*/
        }
        else {
            System.out.println("directory '" + filesPath + "' is empty");
        }
    }

    static String[] parseCardName(String fileName) {
        String[] names = new String[2];

        String shortCardName = "";
        String cardName = fileName;

        cardName = cardName.replace("_ALGSUPPORT_", "");

        int atrIndex = -1;
        int index = -1;
        if ((index = cardName.indexOf("_3B ")) != -1) {
            atrIndex = index;
        }
        if ((index = cardName.indexOf("_3b ")) != -1) {
            atrIndex = index;
        }
        if ((index = cardName.indexOf("_3b_")) != -1) {
            atrIndex = index;
        }
        if ((index = cardName.indexOf("_3B_")) != -1) {
            atrIndex = index;
        }
        if (atrIndex < 0) {
            shortCardName = cardName.substring(0, atrIndex);
        }
        shortCardName = cardName.substring(0, atrIndex);

        shortCardName = shortCardName.replace('_', ' ');
        cardName = cardName.replace('_', ' ');
        if (cardName.indexOf("(provided") != -1) cardName = cardName.substring(0, cardName.indexOf("(provided"));

        names[0] = cardName;
        names[1] = shortCardName;
        return names;
    }

    static String getShortCardName(String fileName) {
        String[] names = parseCardName(fileName);
        return names[1];
    }
    static String getLongCardName(String fileName) {
        String[] names = parseCardName(fileName);
        return names[0];
    }    
    static void formatTableAlgorithm_HTML(ArrayList<String> filesArray, String[] classInfo, HashMap[] filesSupport, FileOutputStream file) throws IOException {
        // class (e.g., javacardx.crypto.Cipher)
        String algorithm = "<tr>\r\n" + "<td class='dark'>" + classInfo[0] + "</td>\r\n";
        algorithm += "  <td class='dark'>introduced in JC ver.</td>\r\n";
        boolean bPackageAIDSupport = false; // detect specific subsection with AID support
        if (classInfo[0].equalsIgnoreCase("Basic info")) {
            for (int i = 0; i < filesSupport.length; i++) { algorithm += "  <th class='dark_index "+i+"' title = '" + getLongCardName(filesArray.get(i)) + "'>c" + i + "</th>\r\n"; }
        } else {
            if (classInfo[0].contains("Package AID support test")) {
                bPackageAIDSupport = true;
            }
            for (int i = 0; i < filesSupport.length; i++) { algorithm += "  <td class='dark_index' title = '" + getLongCardName(filesArray.get(i)) + "'>c" + i + "</td>\r\n"; }
        }

        String[] jcvArray = java_card_version_array.toArray(new String[java_card_version_array.size()]);
        algorithm += "</tr>\r\n";
        // support for particular algorithm from given class
        for (int i = 0; i < classInfo.length; i++) {
            if (!classInfo[i].startsWith("@@@")) { // ignore special informative types
                String algorithmName = "";
                String fullAlgorithmName = "";
                String algorithmVersion = "";

                if (appletVersion != ""){
                    algorithmName = "AlgTest applet version";
                    algorithmVersion = appletVersion;
                    fullAlgorithmName = algorithmName;
                }
                else{
                    // Parse algorithm name and version of JC which introduced it
                    if (i == 0){continue;}
                    CardMngr cman = new CardMngr(new DirtyLogger(null, true));
                    algorithmName = Utils.GetAlgorithmName(classInfo[i]);
                    if (bPackageAIDSupport) {
                        fullAlgorithmName = String.format("%s (%s)", SingleModeTest.PACKAGE_AID_NAMES_STR.get(algorithmName), algorithmName);
                    }
                    else {
                        fullAlgorithmName = algorithmName;
                    }
                    algorithmVersion = Utils.GetAlgorithmIntroductionVersion(classInfo[i]);
                    if (!Utils.ShouldBeIncludedInOutput(classInfo[i])) continue; // ignore types with ignore flag set (algorith#version#include 1/0)
                }

                algorithm += "<tr>\r\n";
                // Add algorithm name
                algorithm += "  <td class='light'>" + fullAlgorithmName + "</td>\r\n";
                // Add version of JavaCard standard that introduced given algorithm
                if (algorithmVersion == appletVersion){
                    algorithm += "  <td class='light_error'>" + "</td>\r\n";
                    appletVersion ="";
                }
                else{
                algorithm += "  <td class='light_error'>" + algorithmVersion + "</td>\r\n";}

                // Process all files
                for (int fileIndex = 0; fileIndex < filesSupport.length; fileIndex++) {
                    algorithm += "  ";
                    HashMap fileSuppMap = filesSupport[fileIndex];
                    if (fileSuppMap.containsKey(algorithmName)) {
                        String secondToken = (String) fileSuppMap.get(algorithmName);
                        String title = "title='" + getShortCardName(filesArray.get(fileIndex)) + " : " + fullAlgorithmName + " : " + secondToken + "'";
                        switch (secondToken) {
                            case "no": algorithm += "<td class='light_no' " + title + ">no</td>\r\n"; break;
                            case "yes":
                                if (java_card_version_array.size() > 0){
                                    if (algorithmVersion.compareTo(jcvArray[fileIndex]) == 1){
                                        // given algorithm is not present in JavaCard specification used to convert uploaded JCAlgTest applet
                                        // make warning
                                        algorithm += "<td class='light_suspicious' " + title + ">possibly yes</td>\r\n";
                                    }
                                    else {
                                        if (jcvArray[fileIndex].compareTo("not supplied") == 0) {
                                            // version of JavaCard API information was not supplied, assuming valid response
                                        }
                                        algorithm += "<td class='light_yes' " + title + ">yes</td>\r\n";
                                    }
                                }
                                else{
                                    algorithm += "<td class='light_yes' " + title + ">yes</td>\r\n";
                                }
                            break;
                            case "error": algorithm += "<td class='light_error' " + title + ">error</td>\r\n"; break;
                            case "maybe": algorithm += "<td class='light_error' " + title + ">maybe</td>\r\n"; break;
                            default: {
                                algorithm += "<td class='light_info' " + title + ">" + secondToken + "</td>\r\n";
                            }
                        }
                    }
                    else {
                        // algorithm not found in support list
                        algorithm += "<td class='light_maybe'>-</td>\r\n";
                        //algorithm += "<td >&nbsp;</td>\r\n";
                    }
                }
                algorithm += "</tr>\r\n";
            }
        }
        file.write(algorithm.getBytes());
    }

    static void parseAIDSupportFile(String filePath, HashMap suppMap) throws IOException {
        try {
            //create BufferedReader to read csv file
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            String strLine;
            int tokenNumber = 0;
            boolean bSupportStartReached = false;

            //read comma separated file line by line
            while ((strLine = br.readLine()) != null) {
                if (strLine.contains("FULL PACKAGE AID;")) {
                    bSupportStartReached = true;
                    continue;
                }

                if (bSupportStartReached) { // parse all lines till the end of file
                    //break comma separated line using ";"
                    StringTokenizer st = new StringTokenizer(strLine, ";,");

                    String firstToken = "";
                    String secondToken = "";
                    while (st.hasMoreTokens()) {
                        tokenNumber++;
                        String tokenValue = st.nextToken();
                        tokenValue = tokenValue.trim();
                        if (tokenNumber == 1) {
                            firstToken = tokenValue;
                        }
                        if (tokenNumber == 2) {
                            secondToken = tokenValue;
                        }
                    }
                    if (!firstToken.isEmpty()) {
                        suppMap.put(firstToken, secondToken);
                    }

                    //reset token number
                    tokenNumber = 0;
                }
            }
        } catch (Exception e) {
            System.out.println("Exception while reading csv file: " + e);
        }
    }

    static void parseSupportFile(String filePath, HashMap suppMap) throws IOException {
        parseSupportFile(filePath, suppMap, true);
    }
    static void parseSupportFile(String filePath, HashMap suppMap, boolean bGeneratePackagesSupport) throws IOException {
        try {
            //create BufferedReader to read csv file
            BufferedReader br = new BufferedReader( new FileReader(filePath));
            String strLine;
            int lineNumber = 0;
            int tokenNumber = 0;
            boolean bJCSupportVersionPresent = false;
            String key_token_prefix = "";

            //read comma separated file line by line
            while ((strLine = br.readLine()) != null) {
                // in case valid JavaCard support version is present
                if (strLine.contains("JavaCard support version")){
                    String jcSupportVersion = (String)strLine.subSequence(JC_SUPPORT_OFFSET, strLine.length());
                    jcSupportVersion = jcSupportVersion.replace(";", "");
                    if (jcSupportVersion.length() > 0) {
                        java_card_version_array.add(jcSupportVersion);
                        bJCSupportVersionPresent = true;
                    }
                }
                if (strLine.contains("AlgTest applet version")){
                    appletVersion = strLine.substring(AT_APPLET_OFFSET, strLine.length() - 1);
                }
                if (strLine.contains("Package_AID_test") && bGeneratePackagesSupport) {
                    packageAIDTestPath = strLine.substring(PACKAGE_AID_PATH_OFFSET, strLine.length());
                    packageAIDTestPath = packageAIDTestPath.trim();

                    if (!packageAIDTestPath.isEmpty()) {
                        // Open target path and load additional info from here
                        int lastPos = filePath.lastIndexOf('\\');
                        if (lastPos == -1) {
                            lastPos = filePath.lastIndexOf('/');
                        }
                        if (lastPos != -1) {
                            String basePath = filePath.substring(0, lastPos);
                            String aidSupportFilePath = String.format("%s/../aid/%s", basePath, packageAIDTestPath);
                            parseAIDSupportFile(aidSupportFilePath, suppMap);
                        }
                    }
                }
                
                // Establish prefix for the support key token
                if (strLine.contains("javacard.security.InitializedMessageDigest.OneShot")) {
                    key_token_prefix = "INITIALIZEDMESSAGEDIGEST_ONESHOT";
                } else if (strLine.contains("javacard.security.InitializedMessageDigest")) {
                    key_token_prefix = "INITIALIZEDMESSAGEDIGEST";
                } else if (strLine.contains(".OneShot")) {
                    key_token_prefix = "ONESHOT";
                } else if (strLine.contains("javacardx.crypto.Cipher.getInstance(byte cipherAlgorithm")) {
                    key_token_prefix = "";
                }
                

                lineNumber++;

                //break comma separated line using ";"
                StringTokenizer st = new StringTokenizer(strLine, ";,");

                String firstToken = "";
                String secondToken = "";
                while(st.hasMoreTokens()) {
                    tokenNumber++;
                    String tokenValue = st.nextToken();
                    tokenValue = tokenValue.trim();
                    if (tokenNumber == 1) { firstToken = tokenValue; }
                    if (tokenNumber == 2) { secondToken = tokenValue; }
                }
                if (!firstToken.isEmpty()) {
                    String keyToken; 
                    if (key_token_prefix.isEmpty()) {
                        keyToken = firstToken;
                    } 
                    else {
                        keyToken = String.format("%s__%s", key_token_prefix, firstToken);
                    }
                    if (suppMap.containsKey(keyToken)) {
                        int a = 5;
                    }
                    else {
                        suppMap.put(keyToken, secondToken);
                    }
                }

                //reset token number
                tokenNumber = 0;
            }

            if (!bJCSupportVersionPresent) {
                System.out.println("PROBLEM: " + filePath + " does not have 'JavaCard support version' inserted!");
                java_card_version_array.add("not supplied");
            }
        }
        catch(Exception e) {
                System.out.println("Exception while reading csv file: " + e);
        }
    }

    static void generateGPShellScripts() throws IOException {
        String capFileName = "AlgTest_v1.3_";
        String packageAID = "6D797061636B616731";
        String appletAID = "6D7970616330303031";

        // NXP JCOP CJ3A081
        CardProfiles.generateScript(capFileName + "jc2.2.2.cap", packageAID, appletAID, "NXP_JCOP_CJ3A081", "mode_211", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // NXP JCOP CJ2A081
        CardProfiles.generateScript(capFileName + "jc2.2.2.cap", packageAID, appletAID, "NXP_JCOP_CJ2A081", "mode_211", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // NXP JCOP 41 v2.2.1 72K
        CardProfiles.generateScript(capFileName + "jc2.2.1.cap", packageAID, appletAID, "NXP_JCOP_41_v221_72K", "mode_211", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // NXP JCOP CJ3A080
        CardProfiles.generateScript(capFileName + "jc2.2.1.cap", packageAID, appletAID, "NXP_JCOP_CJ3A080", "mode_211", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");

        // Gemalto_TOP_IM_GXP4
        CardProfiles.generateScript(capFileName + "jc2.2.1.cap", packageAID, appletAID, "Gemalto_TOP_IM_GXP4", "mode_201\r\ngemXpressoPro", "A000000018434D00", "-keyind 0 -keyver 0 -key 47454d5850524553534f53414d504c45");
        // Gemalto_GXP_E64_PK
        CardProfiles.generateScript(capFileName + "jc2.1.2.cap", packageAID, appletAID, "Gemalto_GXP_E64_PK", "mode_201", "A000000018434D00", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // Gemalto_GXP_R4_72K
        CardProfiles.generateScript(capFileName + "jc2.2.1.cap", packageAID, appletAID, "Gemalto_GXP_R4_72K", "mode201\r\ngemXpressoPro\n", "A000000018434D00\n", "-keyind 0 -keyver 0 -key 47454d5850524553534f53414d504c45\n");
        // Gemalto_GXP_E32_PK
        CardProfiles.generateScript(capFileName+ "jc2.1.2.cap", packageAID, appletAID, "Gemalto_GXP_E32_PK", "mode_201\r\ngemXpressoPro", "A000000018434D00\n", "-keyind 0 -keyver 0 -key 47454d5850524553534f53414d504c45\n");

        // Oberthur Cosmo Dual 72K
        CardProfiles.generateScript(capFileName + "jc2.1.2.cap", packageAID, appletAID, "Oberthur_Cosmo_Dual_72K", "mode_211", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // TODO: Oberthur Cosmo V7
        // NOTE: neither authentication, nor upload work
        //CardProfiles.generateScript(capFileName + "jc2.2.2.cap", packageAID, appletAID, "Oberthur_Cosmo_V7", "mode_211", "A0000001510000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");

        // Infineon JTOP V2 16K
        CardProfiles.generateScript(capFileName + "jc2.1.2.cap", packageAID, appletAID, "Infineon_JTOP_V2_16K", "mode_201", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");
        // Infineon JTOP Dual Interface 80k - SLJ 52GLA080AL M8.4
        // NOTE: authentication works, but upload fails with 'install_for_load() returns 0x80206A88 (6A88: Referenced data not found.)'
        CardProfiles.generateScript(capFileName + "jc2.2.2.cap", packageAID, appletAID, "Infineon_JTOP_Dual_Interface_80k", "mode_211", "", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");

        // Cyberflex Palmera V5
        CardProfiles.generateScript(capFileName + "jc2.1.2.cap", packageAID, appletAID, "Cyberflex_Palmera_V5", "mode_201", "a000000003000000", "-keyind 0 -keyver 0 -mac_key 404142434445464748494a4b4c4d4e4f -enc_key 404142434445464748494a4b4c4d4e4f");

        // Twin_GCX4_72K_PK
        CardProfiles.generateScript(capFileName + "jc2.2.1.cap", packageAID, appletAID, "Twin_GCX4_72K_PK", "mode_201\r\ngemXpressoPro", "-AID A000000018434D00", "-keyind 0 -keyver 0 -key 47454d5850524553534f53414d504c45");
    }


    private static void printMembers(Member[] mbrs, String s, String longClassName, String shortClassName) throws IllegalArgumentException, IllegalAccessException {
        int allignSpaceLength = 80;
        int methodIndex = 0;
        out.format("    // %s %s:\n", longClassName, s);
	for (Member mbr : mbrs) {
	    if (mbr instanceof Field) {
                Field value = (Field) mbr;
                value.setAccessible(true);

                String result = value.toGenericString();
                result = result.replace(longClassName, shortClassName);
                result = result.replace(".", "_");
                try {
                    Object o = value.get(null);
                    out.format("    %s", result);
                    for (int i = 0; i < allignSpaceLength - result.length(); i++) out.print(" ");
                    out.format("= %d;\n", o);
                }
                catch (NullPointerException e) {
                    // not reasonable value
                    int a = 0;
                }
            }
	    else if (mbr instanceof Constructor)
		out.format("  %s%n", ((Constructor)mbr).toGenericString());
	    else if (mbr instanceof Method) {
                methodIndex++;
                Method value = (Method) mbr;

                String result = value.toGenericString();
                result = result.replace(longClassName, shortClassName);
                result = result.replace(".", "_");

                String msg = String.format("public static final short %s_%s", shortClassName, value.getName());
                out.format("    %s", msg);
                for (int i = 0; i < allignSpaceLength - msg.length(); i++) out.print(" ");
                out.format("= %d;\n", methodIndex);
            }
	}
	if (mbrs.length == 0)
	    out.format("    //  -- No %s --%n", s);
	out.format("%n");
    }

    static void formatClass(String longClassName, String shortClassName) throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException {
        out.format("\n\n\n    // Class %s\n", longClassName);
        Class<?> c = Class.forName(longClassName);
        Field[] fields = c.getDeclaredFields();
        printMembers(fields, "Fields", longClassName, shortClassName);
        Method[] methods = c.getDeclaredMethods();
        printMembers(methods, "Methods", longClassName, shortClassName);
    }

    static void generateJCConstantsFile(String fileName) throws Exception {
        // NOTE: constants will be generated only for JC library version you included
        // BUGBUG: now only to stdout, store into fileName
        out.format("package AlgTest;\n\n");
        out.format("public class JCConsts { \n");

        formatClass("javacard.security.Signature", "Signature");
        formatClass("javacardx.crypto.Cipher", "Cipher");
        formatClass("javacard.security.KeyAgreement", "KeyAgreement");
        formatClass("javacard.security.KeyBuilder", "KeyBuilder");
        formatClass("javacard.security.KeyPair", "KeyPair");
        formatClass("javacard.security.MessageDigest", "MessageDigest");
        formatClass("javacard.security.RandomData", "RandomData");
        formatClass("javacard.security.Checksum", "Checksum");
        formatClass("javacardx.crypto.KeyEncryption", "KeyEncryption");
        formatClass("javacard.security.AESKey", "AESKey");
        formatClass("javacard.security.DESKey", "DESKey");
        formatClass("javacard.security.DSAKey", "DSAKey");
        formatClass("javacard.security.DSAPrivateKey", "DSAPrivateKey");
        formatClass("javacard.security.DSAPublicKey", "DSAPublicKey");
        formatClass("javacard.security.ECKey", "ECKey");
        formatClass("javacard.security.ECPrivateKey", "ECPrivateKey");
        formatClass("javacard.security.ECPublicKey", "ECPublicKey");
        formatClass("javacard.security.HMACKey", "HMACKey");
        formatClass("javacard.security.RSAPrivateCrtKey", "RSAPrivateCrtKey");
        formatClass("javacard.security.RSAPrivateKey", "RSAPrivateKey");
        formatClass("javacard.security.RSAPublicKey", "RSAPublicKey");
        formatClass("javacard.security.SignatureMessageRecovery", "SignatureMessageRecovery");

        out.format("} \n");
    }
}

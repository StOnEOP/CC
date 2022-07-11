import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.net.*;
import java.text.DecimalFormat;
import java.io.*;

public class FFSync {
    public static void main(String args[]) throws IOException {
        // args[0]: Path ; args[1]: IP ; args[2]: Key word
        if (args.length < 3) {
            System.out.println("Error: Number of arguments invalid");
            System.out.println("Syntax: FFSync <folderPath> <IP> <Keyword>");
            System.exit(0);
        }

        String folderPath = args[0];
        String folderName = args[0].substring(args[0].lastIndexOf('/')+1).trim();
        String ip = args[1];
        String keyWord = args[2];
        
        // Creating the logs
        Map<Integer, String> logs = new TreeMap<>();
        Integer countingLogs = 0;

        logs.put(countingLogs++, folderName+" "+ip);
        echo("\nFolder: "+ folderName +"\tIP: "+ ip +"\n");

        // Starting the protocol
        FTRapid protocol = new FTRapid();

        // Starting the UDP server
        DatagramSocket udpSocket = new DatagramSocket(8888);
        logs.put(countingLogs++, "Socket created");
        echo("Socket created. Trying to establish connection...\n");

        // Getting the name of all files in the folder
        List<String> hostFiles = getFiles(folderPath, folderName);

        // Listening to http requests
        FFSync_http obj = new FFSync_http(ip, 0, hostFiles);
        Thread thread = new Thread(obj);
        thread.start();
        logs.put(countingLogs++, "Accepting HTTP Requests");
        echo("Accepting HTTP Requests...\n");

        int task = protocol.startConnection(udpSocket, keyWord, ip, 8888);

        // Verifying the connection
        if (task != -1) {
            logs.put(countingLogs++, "Connection established");
            echo("Connection established!\n\n");

            // Getting the missing files
            List<String> missingFiles = protocol.filesSync(udpSocket, hostFiles, ip, 8888);
            logs.put(countingLogs++, "Missing "+ (missingFiles.size()/2) + " files.");
            echo("Missing "+ (missingFiles.size()/2) + " files:");
            logs.put(countingLogs++, missingFiles.toString());
            for (int i = 0; i < missingFiles.size(); i+=2)
                echo("  "+ missingFiles.get(i));
            echo("\n\n");

            // Verifying if the file download is necessary
            int numberFiles = 0, taskCounter = 0;
            while (taskCounter < 2) {
                if (task == 0) {
                    // Updating the task in http
                    obj.setTask(1);

                    task = 1;
                    echo("---------- RECEIVING FILES --------\n");

                    if (missingFiles.size() != 0) {
                        // Starting the files syncronization
                        int missingFilesIterator = 0;
                        while (numberFiles < (missingFiles.size()/2)) {
                            StringBuilder fileName = new StringBuilder(missingFiles.get(missingFilesIterator));
                            File fileN = new File(folderPath +"/"+ fileName.toString());
                            fileN.createNewFile();

                            // Counting the time of the transfer and receiving the file
                            long startTT = System.nanoTime();
                            Map<Integer, byte[]> fragmentsMap = protocol.receiveTransfer(udpSocket, fileName.toString(), ip, 8888);
                            long endTT = System.nanoTime();
                            double totalTT = (endTT - startTT)/1000000000;
                    
                            // Writing to file
                            FileOutputStream fos = new FileOutputStream(fileN);
                            for (int i = 0; i < fragmentsMap.size(); i++)
                                fos.write(fragmentsMap.get(i));
                            int fileLength = (int) fileN.length();

                            double debit = (fileLength*8)/totalTT;

                            DecimalFormat df = new DecimalFormat("#.###");
                            String debitST = df.format(debit);
                
                            if (Double.isInfinite(debit))
                                debitST = Integer.toString(fileLength*8);
                            
                            // Priting statistics
                            logs.put(countingLogs++, "Received "+ fileName +" in "+ totalTT +"s with debit of "+ debitST +"bits/s");
                            echo("- Received in "+ totalTT +"s with debit of "+ debitST +"bits/s\n");

                            missingFilesIterator += 2;
                            numberFiles++;
                            fos.close();

                            // Updating the files in http
                            obj.setFiles(getFiles(folderPath, folderName));

                            echo("- Transfer completed.\n\n");
                        }
                        echo("No more files to receive.\n\n");
                    }
                    else
                        echo("No files to receive.\n\n");
                    protocol.endTransfer(udpSocket, ip , 8888);
                }
                else {
                    // Updating the task in http
                    obj.setTask(2);

                    // Sending files
                    task = 0;
                    echo("---------- SENDING FILES ----------\n");
                    countingLogs = protocol.sendTransfer(udpSocket, folderPath, hostFiles, ip, 8888, logs, countingLogs);
                }
                taskCounter++;
            }

            logs.put(countingLogs++, "Connection ended.");
            echo("Connection ended.\n");
        }
        else {
            logs.put(countingLogs++, "Connection failed.");
            echo("Connection failed.\n");
        }

        logs.put(countingLogs++, "Stopped receiving HTTP Requests.");
        echo("Stopped receiving HTTP Requests.\n");       
        obj.setRunning(false);
        udpSocket.close();
    }

    // Getting the name, length and date of all files in the folder
    public static List<String> getFiles(String folderPath, String folderName) {
        File folder = new File(folderPath);
        List<String> listFiles = new ArrayList<>();

        if (folder.exists() && folder.isDirectory()) {  // Checking if the directory exists
            File[] files = folder.listFiles();
            for (File file: files) {
                listFiles.add(file.getName());
                String fileLength = String.valueOf(file.length());
                listFiles.add(fileLength);
                String fileDate = String.valueOf(file.lastModified());
                listFiles.add(fileDate);
            }
        }
        else if (!folder.exists())
                folder.mkdir();

        return listFiles;
    }

    // Echo
    public static void echo(String message) {
        System.out.print(message);
    }
}

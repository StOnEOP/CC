import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FTRapid {

    // Starting the connection with the client and returns the task that needs to do first (0 - receive files ; 1 - send files)
    public int startConnection(DatagramSocket hostSocket, String keyWordS, String clientIP, int port) throws IOException {
        FTRapid_packet packetS = new FTRapid_packet(0, 0);
        InetAddress clientAddress = InetAddress.getByName(clientIP);
        packetS.setData(toBytes(keyWordS));
      
        // Sending the keyword to the client socket (3 tries)
        int tries = 0;
        FTRapid_packet packetR = new FTRapid_packet();
        while (tries < 3) {
            send(hostSocket, packetS, clientAddress, port);

            // Receiving the keyword from the socket
            try {
                // Setting the max waiting time
                hostSocket.setSoTimeout(30000);
                packetR = receive(hostSocket);
                while (packetR.getMT() != 0 && packetR.getMT() != 1)
                    packetR = receive(hostSocket);
                tries = 4;
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out after try #"+ (tries+1) +". Retrying conection...");
                tries++;
            }
        }

        // Verifying if a desired packet has been received
        if (tries == 3) {
            System.out.println("Socket timed out waiting for connection packet, after three tries.\n");
            return -1;
        }

        // Verifying if the secret word is valid
        boolean validKW = false;
        String keyWordR = new String(packetR.getData());
        if (keyWordS.regionMatches(0, keyWordR, 2, keyWordR.length()-2) && keyWordR.regionMatches(2, keyWordS, 0, keyWordS.length()))
            validKW = true;

        // Verifying if connection is established
        if ((packetR.getMT() == 0 || packetR.getMT() == 1) && packetR.getConfirmation() == 1 && validKW) {
            packetS.setMT(1);
            packetS.setConfirmation(1);
            if (packetR.getMT() == 0) {
                send(hostSocket, packetS, clientAddress, port);
                return 0;
            }
            return 1;
        } else {
            packetS.setMT(1);
            packetS.setConfirmation(0);
            if (packetR.getMT() == 0)
                send(hostSocket, packetS, clientAddress, port);
            return -1;
        }
    }

    // Dealing with the files synchronization
    public List<String> filesSync(DatagramSocket hostSocket, List<String> filesS, String clientIP, int port) throws IOException {
        // Transforming a list in a string
        StringBuilder filesSAUX = new StringBuilder("empty,");
        if (filesS.size() != 0) {
            filesSAUX = new StringBuilder(filesS.get(0)).append(",");
            for (int i = 1; i < filesS.size(); i++)
                filesSAUX.append(filesS.get(i)).append(",");
        }

        byte[] filesSB = toBytes(filesSAUX.toString());
        FTRapid_packet packetS = new FTRapid_packet(2, 1, filesSB);
        InetAddress clientAddress = InetAddress.getByName(clientIP);

        // Sending the list of files in host folder (3 tries)
        int tries = 0;
        FTRapid_packet packetR = new FTRapid_packet();
        while (tries < 3) {
            send(hostSocket, packetS, clientAddress, port);
        
            // Receiving the list of files of the client folder
            try {
                hostSocket.setSoTimeout(3000);
                packetR = receive(hostSocket);
                while (packetR.getMT() != 2)
                    packetR = receive(hostSocket);
                tries = 4;
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out after try #"+ (tries+1) +". Retrying synchronization of files...");
                tries++;
            }
        }

        // Verifying if a desired packet has been received
        if (tries == 3) {
            System.out.println("Socket timed out waiting for synchronization of files packet, after three tries.\n");
            return new ArrayList<>();
        }

        // Transforming the name of all files of the client in a String[]
        byte[] filesRB = packetR.getData();
        String filesRB_S = new String(filesRB);
        String[] filesR = filesRB_S.split(",");
        filesR[0] = filesR[0].substring(2);

        // Getting the missing files
        List<String> missingFiles = new ArrayList<>();
        if (!filesR[0].equals("empty"))
            for (int i = 0; i < filesR.length; i+=3)
                if (!filesS.contains(filesR[i])) {
                    missingFiles.add(filesR[i]);
                    missingFiles.add(filesR[i+1]);
                }
                else {  // Comparing the 'date'
                    int j = 0;
                    for (j = 0; j < filesS.size(); j+=3) {
                        if (filesR[i].equals(filesS.get(j)) && (Long.valueOf(filesR[i+2]) > Long.valueOf(filesS.get(j+2)))){
                            missingFiles.add(filesR[i]);
                            missingFiles.add(filesR[i+1]);
                            break;
                        }
                    }
                }

        return missingFiles;
    }

    // Receiving a transfer
    public Map<Integer, byte[]> receiveTransfer(DatagramSocket hostSocket, String fileName, String clientIP, int port) throws IOException {
        byte[] fileNameB = toBytes(fileName);
        FTRapid_packet packetS = new FTRapid_packet(4, 2, fileNameB);
        InetAddress clientAddress = InetAddress.getByName(clientIP);
        System.out.println("File pretended: "+ fileName);

        // Asking the client for a file (3 tries)
        int tries = 0;
        FTRapid_packet packetR = new FTRapid_packet();
        while (tries < 3) {
            send(hostSocket, packetS, clientAddress, port);

            // Receiving the number of fragments
            try {
                hostSocket.setSoTimeout(3000);
                packetR = receive(hostSocket);
                while (packetR.getMT() != 5)
                    packetR = receive(hostSocket);
                tries = 4;
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out after try #"+ (tries+1) +". Retrying fragments number...");
                tries++;
            }
        }

        // Verifying if a desired packet has been received
        if (tries == 3) {
            System.out.println("Socket timed out waiting for fragments number packet, after three tries.");
            return new HashMap<>();
        }

        byte[] fragmentsB = packetR.getData();
        String fragmentsS = new String(fragmentsB);
        fragmentsS = fragmentsS.substring(2);
        int fragments = Integer.parseInt(fragmentsS);
        System.out.println("- Number of fragments: "+ fragments);

        // Receiving the fragments of the file
        int fragmentsCounter = 0;
        Map<Integer, byte[]> fragmentsMap = new HashMap<>();
        while (fragmentsCounter < fragments) {
            FTRapid_packet packetFR = new FTRapid_packet();
            try {
                packetFR = receive(hostSocket);
                while (packetFR.getMT() != 6)
                    packetFR = receive(hostSocket);
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out waiting for fragment.");
                break;
            }
            byte[] fileFragment = packetFR.getData();

            FTRapid_fragment fileInfo = new FTRapid_fragment();
            fileInfo.toFragment(fileFragment);

            // Adding the fragment to the map of fragments
            int fragmentNumber = fileInfo.getFragment();
            if (!fragmentsMap.containsKey(fragmentNumber)) {
                fragmentsMap.put(fragmentNumber, fileInfo.getData());
                fragmentsCounter++;
            }

            // Sending acknowledgement
            byte[] ACK_B = toBytes(String.valueOf(fragmentNumber));
            FTRapid_packet packetACK = new FTRapid_packet(7, 6, ACK_B);
            send(hostSocket, packetACK, clientAddress, port);
        }

        return fragmentsMap;
    }

    // Setting up for the file transfer
    public int sendTransfer(DatagramSocket hostSocket, String folderPath, List<String> files, String clientIP, int port, Map<Integer, String> logs, int countingLogs) throws IOException {
        // Receiving the desired file (3 tries)
        int tries = 0;
        FTRapid_packet packetR = new FTRapid_packet();
        while (tries < 3) {
            try {
                hostSocket.setSoTimeout(5000);
                packetR = receive(hostSocket);
                while (packetR.getMT() != 4 && packetR.getMT() != 10)
                    packetR = receive(hostSocket);
                tries = 4;
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timed out after try #"+ (tries+1) +". Retrying file to transfer...");
                tries++;
            }
        }

        // Verifying if a desired packet has been received
        if (tries == 3) {
            System.out.println("Socket timed out waiting for file to transfer packet, after three tries.\n");
            return countingLogs;
        }

        int endTransfer = 0;
        if (packetR.getMT() == 10)
            endTransfer = 1;

        // While the connection is not ended, continue to wait for transfers
        while (endTransfer == 0) {
            byte[] fileNameB = packetR.getData();
            String fileName = new String(fileNameB);
            fileName = fileName.substring(2);
    
            int fileLength = 0;
            for (int i = 0; i < files.size(); i+=3) {
                if (files.get(i).equals(fileName)) {
                    fileLength = Integer.parseInt(files.get(i+1));
                    System.out.println("File pretended by client: "+ files.get(i) +"  ("+ fileLength +" bytes)");
                    break;
                }
            }

            // Buffer with file bytes
            String filePath = folderPath +"/"+ fileName;

            byte[] fileBuffer = new byte[(int) fileLength];
            FileInputStream fis = new FileInputStream(filePath);
            fis.read(fileBuffer);
            fis.close();

            // Counting the time of the transfer and iniciating the transfer
            long startTT = System.nanoTime();
            sendTransferAUX(hostSocket, fileBuffer, fileLength, clientIP, port);
            long endTT = System.nanoTime();
            double totalTT = (endTT - startTT)/1000000000;
            double debit = (fileLength*8)/totalTT;

            DecimalFormat df = new DecimalFormat("#.###");
            String debitST = df.format(debit);

            if (Double.isInfinite(debit))
                debitST = Integer.toString(fileLength*8);

            logs.put(countingLogs++, "Sended "+ fileName +" in "+ totalTT +"s with debit of "+ debitST +"bits/s");
            System.out.println("- Sended in "+ totalTT +"s with debit of "+ debitST +"bits/s\n");

            // Receiving the next desired file (3 tries)
            int triesTWO = 0;
            while (triesTWO < 3) {
                try {
                    hostSocket.setSoTimeout(5000);
                    packetR = receive(hostSocket);
                    while (packetR.getMT() != 4 && packetR.getMT() != 10)
                        packetR = receive(hostSocket);
                    triesTWO = 4;
                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out after try #"+ (triesTWO+1) +". Retrying next file to transfer...");
                    triesTWO++;
                }
            }

            // Verifying if a desired packet has been received
            if (triesTWO == 3) {
                System.out.println("Socket timed out waiting for the next file to transfer packet, after three times.\n");
                endTransfer = 2;
            }

            if (packetR.getMT() == 10) {
                System.out.println("No more files to send.\n");
                endTransfer = 2;
            }
        }
        
        if (endTransfer == 1)
            System.out.println("No files to send.\n");
        
        return countingLogs;
    }

    // Sending the files fragments
    public void sendTransferAUX(DatagramSocket hostSocket, byte[] fileBuffer, double fileLength, String clientIP, int port) throws IOException {
        InetAddress clientAddress = InetAddress.getByName(clientIP);

        // Calculating the number of fragments needed for the file transfer
        int fragmentsNumber = (int) Math.ceil(fileLength/2048);

        // Sending the number of fragments
        byte[] fragmentsNumberB = toBytes(String.valueOf(fragmentsNumber));
        FTRapid_packet packetS = new FTRapid_packet(5, 4, fragmentsNumberB);
        send(hostSocket, packetS, clientAddress, port);

        // Sending the file
        int bufferPosition = 0, fragmentsResended = 0, fragmentsTries = 0 , socketTO = 0;
        for (int fragmentsCounter = 0; fragmentsCounter < fragmentsNumber; ) {
            byte[] fileFragment;

            // Check if it's the last fragment
            if ((fragmentsCounter + 1) != fragmentsNumber) {
                fileFragment = new byte[2048];
                System.arraycopy(fileBuffer, bufferPosition, fileFragment, 0, 2048);
            }
            else {
                fileFragment = new byte[(int) fileLength - bufferPosition];
                System.arraycopy(fileBuffer, bufferPosition, fileFragment, 0, (int) fileLength - bufferPosition);
            }

            // Sending the file fragment
            FTRapid_fragment fileInfo = new FTRapid_fragment(fragmentsCounter, fileFragment);
            byte[] fileInfoB = fileInfo.toBytes();
            FTRapid_packet packetF = new FTRapid_packet(6, 5, fileInfoB);
            send(hostSocket, packetF, clientAddress, port);
            
            // Waiting for acknowledgment
            int ackCounter = 0;
            while (ackCounter < 5) {
                FTRapid_packet packetACK;
                int numberACK = -1;

                // Receiving the acknowledgment
                try {
                    hostSocket.setSoTimeout(100);
                    packetACK = receive(hostSocket);
                    while (packetACK.getMT() != 7)
                        packetACK = receive(hostSocket);
                    byte[] bytesACK = packetACK.getData();
                    String stringACK = new String(bytesACK);
                    numberACK = Integer.parseInt(stringACK.substring(2));
                } catch (SocketTimeoutException e) {
                    socketTO++;
                }

                // Acknowledgment correctly received
                if (numberACK == fragmentsCounter)
                    ackCounter = 6;
                else { // Acknowledgment incorrect, resending the fragment if the var in the loop is odd
                    if (ackCounter % 2 == 1) {
                        send(hostSocket, packetF, clientAddress, port);
                        fragmentsResended++;
                    }
                    ackCounter++;
                }
            }

            // If acknowledgment wasnt received after three tries, try again
            if (ackCounter == 6) {
                fragmentsCounter++;
                bufferPosition += 2048;
                fragmentsTries = 0;
            }
            else {  // After three times trying to send the same fragment, stop the transfer
                fragmentsTries++;
                if (fragmentsTries == 3) {
                    System.out.println("Transfer ended after failing to send the same fragment three times.");
                    break;
                }
            }
        }
        System.out.println("- Number of times socket timed out: "+ socketTO);
        System.out.println("- Fragments resended: "+ fragmentsResended);
    }

    // Ending the connection
    public void endTransfer(DatagramSocket hostSocket, String clientIP, int port) throws IOException {
        InetAddress clientAddress = InetAddress.getByName(clientIP);
        FTRapid_packet packetS = new FTRapid_packet(10, 10);
        send(hostSocket, packetS, clientAddress, port);
    }

    // Sending a packet to the client
    public void send(DatagramSocket socket, FTRapid_packet packetS, InetAddress clientAddress, int port) throws IOException {
        byte[] packetSB = packetS.toBytes();
        DatagramPacket dp = new DatagramPacket(packetSB, packetSB.length, clientAddress, port);
        socket.send(dp);
    }

    // Receiving a packet from the client
    public FTRapid_packet receive(DatagramSocket socket) throws IOException {
        byte[] packetRB = new byte[2500];
        DatagramPacket dp = new DatagramPacket(packetRB, packetRB.length);
        socket.receive(dp);
        FTRapid_packet packetR = new FTRapid_packet();
        packetR.toPacket(packetRB);
        return packetR;
    }

    // Transforming a string in bytes
    public byte[] toBytes(String message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeUTF(message);
        
        dos.close();
        baos.flush();
        return baos.toByteArray();
    }
}

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

public class FFSync_http implements Runnable {
    String clientIP;
    int task;
    List<String> files;
    boolean running;

    FFSync_http(String clientIP, int task, List<String> files) {
        this.clientIP = clientIP;
        this.task = task;
        this.files = files;
        this.running = true;
    }

    // Setters
    public void setTask(int task) {
        this.task = task;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    // Thread Code
    public void run() {
        try {
            ServerSocket ss = new ServerSocket(8888);
            
            // While the socket in the FFSync is open, the http requests is also open
            while(this.running) {
                Socket conn = null;
                // Defining a timeout to know when the FFSync socket is closed and the http requests needs to end
                try {
                    ss.setSoTimeout(1000);
                    conn = ss.accept();
                } catch(SocketTimeoutException e) {}

                if (conn != null) {
                    OutputStream out = conn.getOutputStream();

                    // Defining the task text
                    String taskST = new String();
                    if (this.task == 0)
                        taskST = "Waiting for connection";
                    else if (this.task == 1)
                            taskST = "Receiving files";
                        else if (this.task == 2)
                                taskST = "Sending files";

                    // Creating the files in folder text
                    StringBuilder filesSB = new StringBuilder();
                    for (int i = 0; i < this.files.size(); i+=3) {
                        if (i+3 == this.files.size()) {
                            filesSB.append(this.files.get(i)).append(" (").append(this.files.get(i+1)).append(" bytes)");
                            break;
                        }
                        else
                            filesSB.append(this.files.get(i)).append(" (").append(this.files.get(i+1)).append(" bytes); ");
                    }

                    // Building the html
                    StringBuilder sb = new StringBuilder("<html><head><title>FFSync</title></head>")
                                            .append("<body><h1>FFSync</h1><p><b>Connected to</b> ").append(this.clientIP)
                                            .append("</p><p><b>Task:</b> ").append(taskST)
                                            .append("</p><p><b>Folder files:</b> ").append(filesSB.toString())
                                            .append("</p></body></html>");
                    byte[] response = sb.toString().getBytes("ASCII");
        
                    String statusLine = "HTTP/1.1 200 OK\r\n";
                    out.write(statusLine.getBytes("ASCII"));
        
                    String contentLength = "Content-Length: " + response.length + "\r\n";
                    out.write(contentLength.getBytes("ASCII"));
                    out.write("\r\n".getBytes("ASCII"));
                    
                    out.write(response);

                    out.flush();
                }
            }
            ss.close();
        }
        catch(IOException e) {};
    }
}

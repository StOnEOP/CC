import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FTRapid_packet {
    int messageType;
    int confirmation;
    int size;
    byte[] data = {};

    FTRapid_packet(){}

    FTRapid_packet(int messageType, int confirmation) {
        this.messageType = messageType;
        this.confirmation = confirmation;
        this.size = packetSize();
    }

    FTRapid_packet(int messageType, int confirmation, byte[] data) {
        this.messageType = messageType;
        this.confirmation = confirmation;
        this.data = data;
        this.size = packetSize();
    }

    // Getters
    public int getMT() {
        return this.messageType;
    }

    public int getConfirmation() {
        return this.confirmation;
    }

    public int getSize() {
        return this.size;
    }
    
    public byte[] getData() {
        return this.data;
    }

    // Setters
    public void setMT(int messageType) {
        this.messageType = messageType;
    }

    public void setConfirmation(int confirmation) {
        this.confirmation = confirmation;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.size = packetSize();
    }

    // Getting the size of the packet
    public int packetSize() {
        return 12 + data.length;
    }

    // Transforming a packet in bytes
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(messageType);
        dos.writeInt(confirmation);
        dos.writeInt(size);
        dos.write(data);

        dos.close();
        baos.flush();
        return baos.toByteArray();
    }

    // Transforming bytes in a packet
    public void toPacket(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        messageType = dis.readInt();
        confirmation = dis.readInt();
        size = dis.readInt();
        this.data = dis.readNBytes(size-12);

        if (messageType != 1)
            confirmation++;
        
        dis.close();
        bais.close();
    }
}

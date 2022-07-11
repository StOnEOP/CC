import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FTRapid_fragment {
    int fragment;
    int size;
    byte[] data = {};

    FTRapid_fragment() {}

    FTRapid_fragment(int fragment, byte[] data) {
        this.fragment = fragment;
        this.data = data;
        this.size = fileSize();
    }

    // Getters
    public int getFragment() {
        return this.fragment;
    }

    public byte[] getData() {
        return this.data;
    }

    // Setters
    public void setFragment(int fragment) {
        this.fragment = fragment;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.size = fileSize();
    }

    // Getting the size of the file
    public int fileSize() {
        return 8 + data.length;
    }

    // Transforming a fileInfo in bytes
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(fragment);
        dos.writeInt(size);
        dos.write(data);

        dos.close();
        baos.flush();
        return baos.toByteArray();
    }

    // Transforming bytes in a fileInfo
    public void toFragment(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        fragment = dis.readInt();
        size = dis.readInt();
        this.data = dis.readNBytes(size-8);
        
        dis.close();
        bais.close();
    }
}
